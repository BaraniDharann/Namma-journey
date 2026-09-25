/**
 * Geometry helpers shared by the live-tracking map pieces.
 *
 * Everything here works on `[lat, lon]` pairs, the order leaflet expects, and
 * measures along the real ground distance rather than the index of a waypoint.
 * That distinction is the whole point of this module: an OSRM geometry packs
 * waypoints tightly around junctions and sparsely on a highway, so anything
 * that advances "one point per tick" moves in visible fits and starts.
 */

const toRad = (d) => (d * Math.PI) / 180
const toDeg = (r) => (r * 180) / Math.PI

/** Compass bearing in degrees (0 = north) from one point to another. */
export function bearing(from, to) {
  const dLon = toRad(to[1] - from[1])
  const y = Math.sin(dLon) * Math.cos(toRad(to[0]))
  const x = Math.cos(toRad(from[0])) * Math.sin(toRad(to[0])) -
    Math.sin(toRad(from[0])) * Math.cos(toRad(to[0])) * Math.cos(dLon)
  return (toDeg(Math.atan2(y, x)) + 360) % 360
}

/**
 * Great-circle distance in km. Mirrors RoutingService.haversineDistance on the
 * backend, including its 6371 km radius, so a client-side estimate never
 * contradicts a server one.
 */
export function haversineKm(from, to) {
  const R = 6371
  const dLat = toRad(to[0] - from[0])
  const dLon = toRad(to[1] - from[1])
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(from[0])) * Math.cos(toRad(to[0])) * Math.sin(dLon / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export const haversineM = (from, to) => haversineKm(from, to) * 1000

/**
 * Running distance in metres to each point of a path, so a position can be
 * looked up by "how far along" instead of "which waypoint".
 *
 * @returns {{ cum: number[], total: number }}
 */
export function cumulativeDistances(path) {
  const cum = [0]
  let total = 0
  for (let i = 1; i < path.length; i++) {
    total += haversineM(path[i - 1], path[i])
    cum.push(total)
  }
  return { cum, total }
}

/**
 * The point `metres` along a path, plus the heading of the segment it sits on.
 * Distances outside the path are clamped to its ends.
 */
export function pointAtDistance(path, cum, metres) {
  if (!path || path.length === 0) return null
  if (path.length === 1) return { pos: path[0], heading: 0 }

  const total = cum[cum.length - 1]
  const d = Math.min(Math.max(metres, 0), total)

  // Walk forward from the segment the distance falls in. Binary search would be
  // asymptotically nicer, but the animation only ever steps forward a few metres
  // per frame, so a scan from a remembered index is both simpler and faster.
  let i = 1
  while (i < cum.length - 1 && cum[i] < d) i++

  const segStart = cum[i - 1]
  const segLen = cum[i] - segStart
  const t = segLen > 0 ? (d - segStart) / segLen : 0
  const from = path[i - 1]
  const to = path[i]

  return {
    pos: [from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t],
    heading: bearing(from, to),
    index: i,
  }
}

/** Index of the path point closest to `pos` (squared degrees is enough to rank). */
export function nearestIndex(path, pos) {
  if (!path || !pos) return 0
  let best = Infinity
  let bestIdx = 0
  for (let i = 0; i < path.length; i++) {
    const d = (path[i][0] - pos[0]) ** 2 + (path[i][1] - pos[1]) ** 2
    if (d < best) { best = d; bestIdx = i }
  }
  return bestIdx
}

/**
 * Shortest signed turn from one heading to another, in (-180, 180].
 * Without this a car crossing north spins 350 degrees the wrong way.
 */
export function angleDelta(from, to) {
  return ((((to - from) % 360) + 540) % 360) - 180
}

/** Straight-line path, so a route always renders even when OSRM is unavailable. */
export function buildStraightRoute(fromLat, fromLon, toLat, toLon, steps = 60) {
  const pts = []
  for (let i = 0; i <= steps; i++) {
    const t = i / steps
    pts.push([fromLat + (toLat - fromLat) * t, fromLon + (toLon - fromLon) * t])
  }
  return pts
}
