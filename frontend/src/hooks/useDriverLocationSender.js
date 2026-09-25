import { useEffect, useRef } from 'react'
import { createStompClient } from '../utils/websocket'
import { updateDriverLocation, getDriverLocation } from '../utils/api'
import { bearing, cumulativeDistances, pointAtDistance, nearestIndex } from '../components/map/geo'

// How often a simulated fix is published, and how long the simulation takes to
// cover a route end to end. Pacing by wall clock rather than by road speed
// keeps a 5 km hop and a 500 km run both watchable.
const SIM_TICK_MS = 2500
const SIM_TRAVERSAL_MS = 5 * 60 * 1000

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

    // The resume lookup below is async, so the effect can be torn down before it settles.
    // Without this the interval would be created after cleanup had already run.
    let cancelled = false

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

    /**
     * Where this trip had already got to, or null if it has not published a fix yet.
     *
     * Everything below waits for this. A driver refreshing mid-trip remounts the whole hook,
     * and the published fix is the single source of truth for the passenger's map — so any
     * position emitted before we know the trip's progress rewinds it for both of them. That is
     * the bug this answers: refresh at "7 km left" and the car teleported back to the pickup.
     */
    const lastFix = () =>
      getDriverLocation(bookingId)
        .then((res) => {
          const lat = Number(res?.data?.latitude)
          const lon = Number(res?.data?.longitude)
          return Number.isFinite(lat) && Number.isFinite(lon) ? [lat, lon] : null
        })
        .catch(() => null) // 404 — nothing published yet, which is the normal first run

    const startMovement = (resumeFrom) => {
      if (cancelled) return

      // --- Movement source 1: simulate driving along the route ---
      if (routeCoords && routeCoords.length >= 2) {
        let realGpsMoving = false
        // Advance a fixed *distance* per tick, not a fixed number of waypoints.
        // OSRM packs waypoints tightly around junctions and sparsely on open
        // highway, so stepping by index made the car crawl through towns and
        // then leap between them — the stutter passengers were seeing.
        const { cum, total } = cumulativeDistances(routeCoords)
        const metresPerTick = total / (SIM_TRAVERSAL_MS / SIM_TICK_MS)
        let travelled = resumeFrom ? cum[nearestIndex(routeCoords, resumeFrom)] ?? 0 : 0

        const at0 = pointAtDistance(routeCoords, cum, travelled)
        if (at0) emit(at0.pos[0], at0.pos[1], at0.heading)

        timerRef.current = setInterval(() => {
          if (realGpsMoving) return // a moving real fix takes over
          travelled += metresPerTick
          if (travelled > total) travelled = 0 // loop for demo continuity
          const at = pointAtDistance(routeCoords, cum, travelled)
          if (at) emit(at.pos[0], at.pos[1], at.heading)
        }, SIM_TICK_MS)

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
        //
        // A device that is sitting still reports the same fix over and over. On a trip that has
        // already made progress, publishing that would drag the trip back to wherever the phone
        // thinks it is — which is exactly what happened on refresh, because this branch runs for
        // the moment between mount and the route arriving. So once a trip has a fix, a stationary
        // device only updates the driver's own screen; it has to actually move to be published.
        // prev starts unknown, not at the trip position: the question is whether the DEVICE has
        // moved between two of its own readings. Seeding it with the trip's position would make
        // the very first reading look like a huge jump and publish it — the rewind all over again.
        let prev = null
        watchIdRef.current = navigator.geolocation.watchPosition(
          (pos) => {
            const cur = [pos.coords.latitude, pos.coords.longitude]
            const moved = prev ? Math.abs(cur[0] - prev[0]) + Math.abs(cur[1] - prev[1]) > 0.0001 : false
            if (!resumeFrom || moved) {
              emit(cur[0], cur[1], pos.coords.heading)
            } else if (onPositionRef.current) {
              onPositionRef.current(cur[0], cur[1], pos.coords.heading)
            }
            prev = cur
          },
          (err) => console.warn('Geolocation error:', err.message),
          { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 }
        )
      }
    }

    lastFix().then(startMovement)

    return () => {
      cancelled = true
      if (timerRef.current) clearInterval(timerRef.current)
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current)
      if (clientRef.current) clientRef.current.deactivate()
    }
  }, [active, bookingId, driverId, routeCoords])
}
