import { useEffect, useRef, useState, useCallback } from 'react'
import { createStompClient } from '../utils/websocket'
import { getDriverLocation } from '../utils/api'

// How often the REST fallback re-fetches the driver location. WebSocket pushes
// are instant; this poll only guarantees the map keeps updating when the socket
// is down, reconnecting, or blocked by a network/proxy. 5s matches the refresh
// cadence customers are used to in Rapido/Zomato-style live tracking.
const POLL_INTERVAL = 5000

/**
 * Customer-side live driver location.
 *
 * Two update sources feed one place so the map never goes stale:
 *  1. WebSocket (STOMP) subscription  -> instant push the moment the driver moves
 *  2. REST poll every 5s              -> safety net if the socket drops/reconnects
 *
 * Both update React state only, so the driver marker moves without any page
 * reload. Returns the latest location plus freshness/connection info so the UI
 * can show a "LIVE / updated Ns ago" badge.
 *
 * @param bookingId the trip to track
 * @param active    only run when this map belongs to a customer (not the driver)
 */
export default function useLiveDriverLocation({ bookingId, active }) {
  const [location, setLocation] = useState(null) // { latitude, longitude, heading, timestamp }
  const [lastUpdate, setLastUpdate] = useState(null) // ms epoch of the last accepted update
  const [socketLive, setSocketLive] = useState(false)
  const clientRef = useRef(null)
  const lastTsRef = useRef(0)

  // Single sink for both sources. Ignore out-of-order/duplicate points so a slow
  // REST poll can't overwrite a newer WebSocket push.
  const apply = useCallback((loc) => {
    if (!loc || loc.latitude == null || loc.longitude == null) return
    const ts = loc.timestamp || Date.now()
    if (ts < lastTsRef.current) return
    lastTsRef.current = ts
    setLocation(loc)
    setLastUpdate(Date.now())
  }, [])

  useEffect(() => {
    if (!active || !bookingId) return

    let token = null
    try { token = localStorage.getItem('nj_token') } catch { /* storage unavailable */ }

    // --- Source 1: WebSocket push (primary) ---
    const client = createStompClient(token)
    clientRef.current = client
    client.onConnect = () => {
      setSocketLive(true)
      client.subscribe(`/topic/booking/${bookingId}/location`, (message) => {
        try { apply(JSON.parse(message.body)) } catch { /* ignore bad frame */ }
      })
    }
    client.onStompError = () => setSocketLive(false)
    client.onWebSocketClose = () => setSocketLive(false)
    client.activate()

    // --- Source 2: REST poll every 5s (fallback / safety net) ---
    const poll = () => {
      getDriverLocation(bookingId)
        .then((r) => { if (r.data) apply(r.data) })
        .catch(() => {})
    }
    poll() // fetch immediately so the first paint isn't empty
    const pollId = setInterval(poll, POLL_INTERVAL)

    return () => {
      clearInterval(pollId)
      if (clientRef.current) clientRef.current.deactivate()
    }
  }, [bookingId, active, apply])

  return { location, lastUpdate, socketLive }
}
