import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { pollUserBookings, getDriverLocation } from '../../utils/api'
import { haversineKm } from '../map/geo'
import { vehicleIcon } from '../../dash/vehicles'
import { pickActiveRide, describeRide, STEPS, stepIndex } from './activeRide'
import RideTrackerSheet from './RideTrackerSheet'

const LIVE_MS = 10000 // a ride is live: check often
const IDLE_MS = 30000 // nothing live: a light check, so a driver accepting still pops the card

const minKey = (id, step) => `nj_ride_min_${id}_${step}`
const readMin = (id, step) => { try { return sessionStorage.getItem(minKey(id, step)) === '1' } catch { return false } }
const writeMin = (id, step) => { try { sessionStorage.setItem(minKey(id, step), '1') } catch { /* storage blocked: it just re-opens */ } }

/**
 * The Zomato-style live card a traveller sees on every screen while a ride is on: who is coming,
 * how far away, and a step strip (booked → assigned → on the way → arrived → on trip). Tapping it
 * opens the live map. It can be tucked into a small pill; a new step pops it open again.
 *
 * Also opens the map for `?track=<bookingId>`, the link in the Telegram "driver accepted" message.
 */
export default function ActiveRideCard({ userId }) {
  const location = useLocation()
  const navigate = useNavigate()
  const [bookings, setBookings] = useState(null)
  const [distanceKm, setDistanceKm] = useState(null)
  const [trackId, setTrackId] = useState(null)
  const [minimized, setMinimized] = useState(false)
  const prevStepRef = useRef(null)

  const ride = pickActiveRide(bookings)
  const info = ride ? describeRide(ride, distanceKm) : null
  const rideId = ride?.bookingId
  const step = info?.step

  const load = useCallback(async () => {
    if (!userId) return
    try {
      const r = await pollUserBookings(userId)
      const list = r.data || []
      const live = pickActiveRide(list)
      let km = null
      if (live?.status === 'CONFIRMED' && live.driver && live.fromLat != null) {
        const loc = await getDriverLocation(live.bookingId).then((x) => x.data, () => null)
        if (loc?.latitude != null) km = haversineKm([loc.latitude, loc.longitude], [live.fromLat, live.fromLon])
      }
      // Both together, so the card never flashes "assigned" before the distance lands.
      setDistanceKm(km)
      setBookings(list)
    } catch { /* offline for a moment: keep the last card */ }
  }, [userId])

  // Poll while the tab is visible; faster while a ride is live.
  useEffect(() => {
    let timer
    let stopped = false
    const tick = async () => {
      if (document.visibilityState === 'visible') await load()
      if (!stopped) timer = setTimeout(tick, rideId ? LIVE_MS : IDLE_MS)
    }
    tick()
    const onShow = () => { if (document.visibilityState === 'visible') load() }
    document.addEventListener('visibilitychange', onShow)
    return () => { stopped = true; clearTimeout(timer); document.removeEventListener('visibilitychange', onShow) }
  }, [load, rideId])

  // A new step (driver accepted, arrived, trip started) re-opens the card and says so once.
  useEffect(() => {
    if (!rideId || !step) { prevStepRef.current = null; return }
    const key = `${rideId}:${step}`
    const prev = prevStepRef.current
    prevStepRef.current = key
    setMinimized(readMin(rideId, step))
    // Only real news gets a toast: a driver taking the ride, arriving, or the trip starting.
    // "assigned" <-> "on the way" just tracks the GPS fix coming and going.
    const prevStep = prev?.split(':')[1]
    const moving = (s) => s === 'assigned' || s === 'coming'
    const news = prev && prev !== key && !(moving(prevStep) && moving(step))
    if (news && info) {
      toast(info.title, { icon: '🚖', id: `ride-${key}`, duration: 5000 })
      if (navigator.vibrate) navigator.vibrate(120)
    }
  }, [rideId, step]) // eslint-disable-line react-hooks/exhaustive-deps

  // ?track=<id> from Telegram, or the Track button on My Bookings: open the map for that booking.
  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const id = params.get('track')
    if (!id) return
    setTrackId(id)
    params.delete('track')
    const rest = params.toString()
    navigate({ pathname: location.pathname, search: rest ? `?${rest}` : '' }, { replace: true })
  }, [location.search, location.pathname, navigate])

  const tracked = trackId ? (bookings || []).find((b) => String(b.bookingId) === String(trackId)) : null
  // The map only makes sense once a driver is on it; until the list loads keep waiting quietly.
  useEffect(() => {
    if (trackId && bookings && (!tracked || !['CONFIRMED', 'STARTED'].includes(tracked.status))) {
      if (tracked) toast('This ride is not live right now.', { id: 'track-not-live' })
      setTrackId(null)
    }
  }, [trackId, bookings, tracked])

  const tuckAway = (e) => {
    e.stopPropagation()
    writeMin(rideId, step)
    setMinimized(true)
  }
  const canTrack = ride && ['CONFIRMED', 'STARTED'].includes(ride.status) && ride.driver
  const open = () => { if (canTrack) setTrackId(ride.bookingId); else navigate('/user/bookings') }
  const at = info ? stepIndex(info.step) : -1

  return (
    <>
      <AnimatePresence>
        {ride && info && !trackId && (
          minimized ? (
            <motion.button
              key="pill"
              type="button"
              className={`ar-pill is-${info.tone}`}
              onClick={() => setMinimized(false)}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              aria-label={`${info.title}. Show ride status`}
            >
              <span className="ar-dot" aria-hidden="true" />
              <Icon name={ride.driver ? vehicleIcon(ride.driver.vehicleType || ride.vehicleType) : 'search'} size={18} />
              <span className="ar-pill-txt">{info.tone === 'search' ? 'Finding driver' : info.step === 'started' ? 'On trip' : 'Ride live'}</span>
            </motion.button>
          ) : (
            <motion.div
              key={`card-${rideId}`}
              className={`ar-card is-${info.tone}`}
              role="status"
              aria-live="polite"
              initial={{ opacity: 0, y: 40 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 40 }}
              transition={{ type: 'spring', stiffness: 380, damping: 32 }}
            >
              <button type="button" className="ar-main" onClick={open} aria-label={`${info.title}. ${canTrack ? 'Open live map' : 'Open my bookings'}`}>
                <span className="ar-badge" aria-hidden="true">
                  <Icon name={ride.driver ? vehicleIcon(ride.driver.vehicleType || ride.vehicleType) : 'search'} size={22} />
                </span>
                <span className="ar-copy">
                  <b>{info.title}</b>
                  <small>{info.text}</small>
                </span>
                <span className="ar-go" aria-hidden="true">
                  {canTrack ? <><Icon name="map" size={16} /><span>Track</span></> : <Icon name="arrow" size={16} />}
                </span>
              </button>
              <ol className="ar-steps" aria-label="Ride progress">
                {STEPS.map((s, i) => (
                  <li key={s.key} className={i < at ? 'is-done' : i === at ? 'is-now' : undefined} aria-current={i === at ? 'step' : undefined}>
                    <span className="ar-step-ic"><Icon name={i < at ? 'check' : s.icon} size={12} /></span>
                    <span className="ar-step-lb">{s.label}</span>
                  </li>
                ))}
              </ol>
              {ride.driver?.mobile && info.tone !== 'search' && (
                <a className="ar-call" href={`tel:${ride.driver.mobile}`} aria-label={`Call ${ride.driver.name || 'driver'}`}>
                  <Icon name="phone" size={16} />
                </a>
              )}
              <button type="button" className="ar-hide" onClick={tuckAway} aria-label="Minimise ride status">
                <Icon name="x" size={14} />
              </button>
            </motion.div>
          )
        )}
      </AnimatePresence>

      {tracked && ['CONFIRMED', 'STARTED'].includes(tracked.status) && (
        <RideTrackerSheet
          booking={tracked}
          info={describeRide(tracked, tracked.bookingId === rideId ? distanceKm : null)}
          onClose={() => setTrackId(null)}
        />
      )}
    </>
  )
}
