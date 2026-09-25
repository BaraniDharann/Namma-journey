import { useEffect, useRef } from 'react'
import { useMap } from 'react-leaflet'
import L from 'leaflet'
import { createVehicleIcon } from './vehicleIcons'
import {
  angleDelta, cumulativeDistances, haversineM, nearestIndex, pointAtDistance,
} from './geo'

/**
 * Live vehicle markers.
 *
 * Location fixes arrive every few seconds, so whatever draws them has to invent
 * the frames in between or the car teleports. Two things make that motion read
 * as navigation rather than as a jumping pin:
 *
 *  1. Between two fixes the marker travels along the *route geometry*, not the
 *     straight line joining them, so it stays on the road through bends.
 *  2. Position and rotation are written straight to the existing DOM node on
 *     each animation frame. Re-creating the leaflet icon to change the heading
 *     replaces the node, which restarts its transition and looks like a twitch.
 */

const MIN_LEG_MS = 900
const MAX_LEG_MS = 6000
const DEFAULT_LEG_MS = 2500
// Past this the fix is a jump (GPS recovered, driver re-routed, trip restarted)
// rather than travel, so the marker snaps instead of crawling across the map.
const TELEPORT_M = 3000
// Share of the remaining turn taken per frame. Low enough to look like a
// steering wheel, high enough to finish the turn inside one leg.
const TURN_EASE = 0.12
// How long a route preview takes end to end, whatever its length. A real road
// speed would leave an intercity route looking motionless.
const CRUISE_MS = 40000

const clamp = (n, lo, hi) => Math.min(Math.max(n, lo), hi)

function withDistances(path) {
  const { cum, total } = cumulativeDistances(path)
  return { path, cum, total }
}

/**
 * The path the car should take from `from` to `to`: the slice of the route
 * between them where that makes sense, a straight line where it does not.
 */
function planPath(route, from, to) {
  const direct = [from, to]
  if (!route || route.length < 2) return withDistances(direct)

  const i0 = nearestIndex(route, from)
  const i1 = nearestIndex(route, to)
  // Same segment, or moving backwards along the route: nothing to follow.
  if (i1 <= i0) return withDistances(direct)

  const candidate = withDistances([from, ...route.slice(i0 + 1, i1 + 1), to])
  const asCrow = haversineM(from, to)
  // When the driver is off-route the two nearest points can land on unrelated
  // parts of the geometry, which would send the car on a detour it never took.
  if (asCrow > 0 && candidate.total > asCrow * 4 + 200) return withDistances(direct)
  return candidate
}

/** Creates the leaflet marker and hands back imperative draw helpers. */
function useVehicleLayer({ vehicle, initialPos, tooltip, follow }) {
  const map = useMap()
  const markerRef = useRef(null)
  const rotRef = useRef(null)
  const stateRef = useRef({ pos: initialPos || null, heading: 0 })
  const followRef = useRef(follow)
  const lastPanRef = useRef(0)
  const initialPosRef = useRef(initialPos)

  useEffect(() => { followRef.current = follow }, [follow])
  useEffect(() => { initialPosRef.current = initialPos }, [initialPos])

  useEffect(() => {
    if (!map) return undefined
    const start = stateRef.current.pos || initialPosRef.current
    if (!start) return undefined

    const marker = L.marker(start, {
      icon: createVehicleIcon(vehicle),
      zIndexOffset: 1000,
      interactive: Boolean(tooltip),
      keyboard: false,
    })
    if (tooltip) marker.bindTooltip(tooltip, { direction: 'top', offset: [0, -20] })
    marker.addTo(map)
    markerRef.current = marker
    rotRef.current = marker.getElement()?.querySelector('.nj-veh-rot')

    return () => {
      marker.remove()
      markerRef.current = null
      rotRef.current = null
    }
    // `initialPos` only seeds the first paint; later positions arrive through draw().
  }, [map, vehicle, tooltip])

  /**
   * Slide the map only once the car nears an edge, the way a navigation view
   * behaves. Re-centring on every fix fights the marker's own motion, which is
   * what made the map feel like it was shuddering.
   */
  const keepInView = (pos) => {
    if (!followRef.current || !map) return
    const now = performance.now()
    if (now - lastPanRef.current < 1200) return
    const size = map.getSize()
    if (!size.x || !size.y) return
    const p = map.latLngToContainerPoint(pos)
    const marginX = size.x * 0.28
    const marginY = size.y * 0.28
    if (p.x < marginX || p.x > size.x - marginX || p.y < marginY || p.y > size.y - marginY) {
      lastPanRef.current = now
      map.panTo(pos, { animate: true, duration: 1.1, easeLinearity: 0.4 })
    }
  }

  const draw = () => {
    const marker = markerRef.current
    const state = stateRef.current
    if (!marker || !state.pos) return
    marker.setLatLng(state.pos)
    if (!rotRef.current) rotRef.current = marker.getElement()?.querySelector('.nj-veh-rot')
    if (rotRef.current) rotRef.current.style.transform = `rotate(${state.heading.toFixed(1)}deg)`
    keepInView(state.pos)
  }

  /** Ease the drawn heading toward `target`; returns true once it has settled. */
  const turnToward = (target) => {
    if (target == null || Number.isNaN(target)) return true
    const state = stateRef.current
    const delta = angleDelta(state.heading, target)
    if (Math.abs(delta) <= 0.5) { state.heading = target; return true }
    state.heading += delta * TURN_EASE
    return false
  }

  return { map, markerRef, stateRef, draw, turnToward }
}

/**
 * Driver marker fed by live location fixes.
 *
 * @param position    latest `[lat, lon]` fix
 * @param heading     heading reported with the fix, when the source has one
 * @param routeCoords trip geometry the car should stay on between fixes
 * @param follow      keep the car in view as it moves
 */
export function SmoothVehicleMarker({ position, heading, routeCoords, vehicle, follow, tooltip }) {
  const { stateRef, markerRef, draw, turnToward } = useVehicleLayer({
    vehicle, initialPos: position, tooltip, follow,
  })
  const legRef = useRef(null)
  const rafRef = useRef(null)
  const lastFixAtRef = useRef(0)
  const routeRef = useRef(routeCoords)
  const headingRef = useRef(heading)

  // Kept in refs, and synced before the leg effect below runs, because a new
  // heading on its own must not restart the animation mid-turn.
  useEffect(() => { routeRef.current = routeCoords }, [routeCoords])
  useEffect(() => { headingRef.current = heading }, [heading])

  useEffect(() => {
    if (!position) return

    const runFrame = () => {
      const leg = legRef.current
      let settled = true

      if (leg) {
        const t = clamp((performance.now() - leg.start) / leg.duration, 0, 1)
        const at = pointAtDistance(leg.path, leg.cum, leg.total * t)
        if (at) {
          stateRef.current.pos = at.pos
          // Hold the fix's own heading once the leg is walked out, so a parked
          // car keeps pointing the way the driver reported.
          const target = t >= 1 && leg.endHeading != null ? leg.endHeading : at.heading
          const turned = turnToward(target)
          settled = t >= 1 && turned
        }
      }

      draw()
      if (settled) { legRef.current = null; rafRef.current = null; return }
      rafRef.current = requestAnimationFrame(runFrame)
    }

    const startLoop = () => {
      if (rafRef.current == null) rafRef.current = requestAnimationFrame(runFrame)
    }

    const state = stateRef.current
    const fixHeading = headingRef.current

    if (!state.pos) {
      state.pos = position
      state.heading = fixHeading || 0
      draw()
      return
    }

    const now = Date.now()
    const gap = lastFixAtRef.current ? now - lastFixAtRef.current : 0
    lastFixAtRef.current = now

    const moved = haversineM(state.pos, position)

    if (moved < 0.8) {
      // Same point re-sent: only the heading can have changed.
      if (fixHeading != null && Math.abs(angleDelta(state.heading, fixHeading)) > 0.5) {
        legRef.current = {
          path: [state.pos, state.pos],
          cum: [0, 0],
          total: 0,
          start: performance.now(),
          duration: MIN_LEG_MS,
          endHeading: fixHeading,
        }
        startLoop()
      }
      return
    }

    if (moved > TELEPORT_M) {
      legRef.current = null
      state.pos = position
      if (fixHeading != null) state.heading = fixHeading
      draw()
      return
    }

    // Pace the leg to the observed update interval so the car is still moving
    // when the next fix lands, instead of arriving early and sitting still.
    legRef.current = {
      ...planPath(routeRef.current, state.pos, position),
      start: performance.now(),
      duration: clamp(gap || DEFAULT_LEG_MS, MIN_LEG_MS, MAX_LEG_MS),
      endHeading: fixHeading ?? null,
    }
    startLoop()
    // Only a new fix starts a leg; the heading rides along with it and must not
    // restart the animation on its own.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [position])

  useEffect(() => () => {
    if (rafRef.current != null) cancelAnimationFrame(rafRef.current)
    rafRef.current = null
  }, [])

  // Repaint after the underlying layer remounts (vehicle or tooltip change).
  useEffect(() => {
    if (markerRef.current) draw()
  })

  return null
}

/**
 * Route preview: a car cruising the whole route at a steady pace, shown while
 * the driver's live location has not arrived yet. It covers the route in a
 * fixed time rather than at a road speed, so the same animation reads on a
 * 5 km hop and on a 500 km run.
 */
export function RouteCruiseMarker({ routeCoords, vehicle, tooltip }) {
  const { stateRef, draw, turnToward } = useVehicleLayer({
    vehicle, initialPos: routeCoords?.[0], tooltip, follow: false,
  })
  const rafRef = useRef(null)

  useEffect(() => {
    if (!routeCoords || routeCoords.length < 2) return undefined
    const { cum, total } = cumulativeDistances(routeCoords)
    if (total <= 0) return undefined

    let startedAt = performance.now()
    const runFrame = () => {
      if (performance.now() - startedAt >= CRUISE_MS) startedAt = performance.now()
      const t = clamp((performance.now() - startedAt) / CRUISE_MS, 0, 1)
      const at = pointAtDistance(routeCoords, cum, total * t)
      if (at) {
        stateRef.current.pos = at.pos
        turnToward(at.heading)
        draw()
      }
      rafRef.current = requestAnimationFrame(runFrame)
    }

    rafRef.current = requestAnimationFrame(runFrame)
    return () => {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current)
      rafRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeCoords])

  return null
}
