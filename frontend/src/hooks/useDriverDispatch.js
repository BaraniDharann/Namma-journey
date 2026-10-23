import { useCallback, useEffect, useRef, useState } from 'react'
import { createStompClient } from '../utils/websocket'
import { getDriverBookingsFresh, getDriverProfile, reportDriverPresence } from '../utils/api'

// An online driver's position is what Ride Now searches. The server forgets a driver 60s after
// their last report, so 15s leaves room for a few missed sends before they drop out.
const PRESENCE_EVERY_MS = 15000
// Offers are pushed over the socket; this poll is the safety net when the socket is down.
const POLL_EVERY_MS = 8000

/** Tells every driver screen that online/offline changed, or that the trip list did. */
export const DRIVER_STATUS_EVENT = 'nj:driver-status'
export const DRIVER_TRIPS_EVENT = 'nj:driver-trips-changed'

/**
 * Everything a driver needs to receive Ride Now offers, on whichever driver page is open:
 * reports the phone's position while online, listens for offers, and returns the live one.
 *
 * While a trip is in progress the live-tracking sender already reports position (and the server
 * counts that as presence), so this hook stays quiet rather than send a second, competing fix.
 */
export default function useDriverDispatch(driverId) {
  const [online, setOnline] = useState(null)
  const [offer, setOffer] = useState(null) // { booking, deadline }
  const [locationIssue, setLocationIssue] = useState(null)
  const [onTrip, setOnTrip] = useState(false)
  const deadlines = useRef(new Map())

  useEffect(() => {
    if (!driverId) return
    getDriverProfile(driverId).then((r) => setOnline(r.data?.status === 'ACTIVE')).catch(() => setOnline(false))
    const onStatus = (e) => setOnline(e.detail === 'ACTIVE')
    window.addEventListener(DRIVER_STATUS_EVENT, onStatus)
    return () => window.removeEventListener(DRIVER_STATUS_EVENT, onStatus)
  }, [driverId])

  const refresh = useCallback(() => {
    if (!driverId) return
    getDriverBookingsFresh(driverId).then((r) => {
      const trips = r.data || []
      setOnTrip(trips.some((b) => b.status === 'STARTED' && !b.tripEndedAt))
      const live = trips.find((b) => b.status === 'PENDING' && b.dispatchState === 'OFFERED' && b.offerSecondsLeft > 0)
      if (!live) { setOffer(null); return }
      // The deadline is fixed the first time this offer is seen, so a later poll never resets the countdown.
      if (!deadlines.current.has(live.bookingId)) {
        deadlines.current.set(live.bookingId, Date.now() + live.offerSecondsLeft * 1000)
      }
      setOffer({ booking: live, deadline: deadlines.current.get(live.bookingId) })
    }).catch(() => {})
  }, [driverId])

  // Offers: socket push for speed, poll as the safety net.
  useEffect(() => {
    if (!driverId || !online) { setOffer(null); return }
    let token = null
    try { token = localStorage.getItem('nj_token') } catch { /* storage unavailable */ }
    const client = createStompClient(token)
    client.onConnect = () => {
      client.subscribe(`/topic/driver/${driverId}/offers`, () => {
        refresh()
        window.dispatchEvent(new Event(DRIVER_TRIPS_EVENT))
      })
    }
    client.activate()
    refresh()
    const poll = setInterval(refresh, POLL_EVERY_MS)
    return () => {
      clearInterval(poll)
      client.deactivate()
    }
  }, [driverId, online, refresh])

  // Presence: where this driver is, for as long as they are online and not on a trip.
  useEffect(() => {
    if (!driverId || !online || onTrip) return
    if (!navigator.geolocation) {
      setLocationIssue('This device cannot share its location, so nearby rides cannot reach you.')
      return
    }
    let last = null
    let lastSent = 0
    const send = () => {
      if (!last) return
      lastSent = Date.now()
      reportDriverPresence(driverId, last[0], last[1]).catch(() => {})
    }
    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        setLocationIssue(null)
        const first = !last
        last = [pos.coords.latitude, pos.coords.longitude]
        if (first || Date.now() - lastSent >= PRESENCE_EVERY_MS) send()
      },
      (err) => {
        setLocationIssue(err.code === 1
          ? 'Location is blocked. Allow location for this site to get rides near you.'
          : 'Waiting for your location. Nearby rides reach you once it is found.')
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 }
    )
    // A phone standing still may not fire watchPosition again; keep the last fix alive anyway.
    const heartbeat = setInterval(() => { if (Date.now() - lastSent >= PRESENCE_EVERY_MS) send() }, PRESENCE_EVERY_MS)
    return () => {
      navigator.geolocation.clearWatch(watchId)
      clearInterval(heartbeat)
    }
  }, [driverId, online, onTrip])

  return { online, offer, locationIssue, refresh }
}
