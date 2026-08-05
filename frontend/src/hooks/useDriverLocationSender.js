import { useEffect, useRef } from 'react'
import { createStompClient } from '../utils/websocket'
import { updateDriverLocation } from '../utils/api'

function bearing(from, to) {
  const toRad = (d) => (d * Math.PI) / 180
  const toDeg = (r) => (r * 180) / Math.PI
  const dLon = toRad(to[1] - from[1])
  const y = Math.sin(dLon) * Math.cos(toRad(to[0]))
  const x = Math.cos(toRad(from[0])) * Math.sin(toRad(to[0])) -
    Math.sin(toRad(from[0])) * Math.cos(toRad(to[0])) * Math.cos(dLon)
  return (toDeg(Math.atan2(y, x)) + 360) % 360
}

/**
 * Drives the driver's live location.
 *
 * On a real phone the device GPS reports a moving position. On a desktop the
 * GPS fix is static (or denied), which is why the car never appeared to move.
 * When we have the trip route we therefore "drive" the car along it on a timer
 * so the location pipeline (backend store + user-side map) always shows motion;
 * a genuinely moving real-GPS fix overrides the simulation.
 *
 * @param onPosition (lat, lon, heading) callback so the driver's own map can
 *   render the same position we publish to the backend.
 */
export default function useDriverLocationSender({ bookingId, driverId, active, routeCoords, onPosition }) {
  const clientRef = useRef(null)
  const watchIdRef = useRef(null)
  const timerRef = useRef(null)
  const lastSentRef = useRef(0)
  const connectedRef = useRef(false)
  const onPositionRef = useRef(onPosition)
  onPositionRef.current = onPosition

  useEffect(() => {
    if (!active || !bookingId || !driverId) return

    let token = null
    try { token = localStorage.getItem('nj_token') } catch { /* storage unavailable */ }
    const client = createStompClient(token)
    clientRef.current = client
    connectedRef.current = false

    const publish = (lat, lon, heading) => {
      const now = Date.now()
      if (now - lastSentRef.current < 2000) return
      lastSentRef.current = now

      const payload = {
        driverId,
        bookingId,
        latitude: lat,
        longitude: lon,
        heading: heading || 0,
        timestamp: now,
      }

      if (connectedRef.current && client.connected) {
        client.publish({ destination: '/app/location.update', body: JSON.stringify(payload) })
      } else {
        updateDriverLocation(payload).catch(() => {})
      }
    }

    const emit = (lat, lon, heading) => {
      if (onPositionRef.current) onPositionRef.current(lat, lon, heading)
      publish(lat, lon, heading)
    }

    client.onConnect = () => { connectedRef.current = true }
    client.onStompError = () => { connectedRef.current = false }
    client.onWebSocketClose = () => { connectedRef.current = false }
    client.activate()

    // --- Movement source 1: simulate driving along the route ---
    if (routeCoords && routeCoords.length >= 2) {
      let idx = 0
      let realGpsMoving = false
      // Advance a fixed number of waypoints per tick so the car moves at a
      // visible pace whether the route has 60 points (straight-line fallback)
      // or several hundred (a full OSRM geometry on a long intercity trip).
      const stride = Math.max(1, Math.floor(routeCoords.length / 120))
      emit(routeCoords[0][0], routeCoords[0][1], bearing(routeCoords[0], routeCoords[1]))
      timerRef.current = setInterval(() => {
        if (realGpsMoving) return // a moving real fix takes over
        const from = routeCoords[idx]
        const to = routeCoords[Math.min(idx + stride, routeCoords.length - 1)]
        emit(from[0], from[1], bearing(from, to))
        idx = idx + stride >= routeCoords.length ? 0 : idx + stride // loop for demo continuity
      }, 2500)

      // Still listen to real GPS; if it actually moves, let it win.
      if (navigator.geolocation) {
        let prev = null
        watchIdRef.current = navigator.geolocation.watchPosition(
          (pos) => {
            const cur = [pos.coords.latitude, pos.coords.longitude]
            if (prev) {
              const moved = Math.abs(cur[0] - prev[0]) + Math.abs(cur[1] - prev[1])
              if (moved > 0.0001) { // ~10m: real movement detected
                realGpsMoving = true
                emit(cur[0], cur[1], pos.coords.heading || bearing(prev, cur))
              }
            }
            prev = cur
          },
          () => {},
          { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 }
        )
      }
    } else if (navigator.geolocation) {
      // --- Movement source 2: no route yet, fall back to raw GPS ---
      watchIdRef.current = navigator.geolocation.watchPosition(
        (pos) => emit(pos.coords.latitude, pos.coords.longitude, pos.coords.heading),
        (err) => console.warn('Geolocation error:', err.message),
        { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 }
      )
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current)
      if (clientRef.current) clientRef.current.deactivate()
    }
  }, [active, bookingId, driverId, routeCoords])
}
