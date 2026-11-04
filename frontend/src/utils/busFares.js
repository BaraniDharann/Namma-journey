// Route fares, cancellation slabs and run days for the owner's bus route editor.

/** Every (from, to) stop pair a passenger can buy, in order: 0→1, 0→2, …, 1→2, … */
export function stopPairs(stopCount) {
  const pairs = []
  for (let from = 0; from < stopCount; from++) {
    for (let to = from + 1; to < stopCount; to++) pairs.push({ fromSeq: from, toSeq: to })
  }
  return pairs
}

/** Nearest ₹10, never below ₹10. */
export const roundFare = (n) => Math.max(10, Math.round(n / 10) * 10)

const filled = (v) => v !== '' && v != null && Number(v) > 0

/**
 * Fills the empty fares for the shorter trips from the full-route fare, by their share of the
 * stops: a ride covering 2 of 4 legs costs half the full fare. Fares the owner typed are kept.
 * `grid` is { [`${fromSeq}-${toSeq}-${seatClass}`]: fare }; returns a new grid.
 */
export function fillMiddleFares(grid, stopCount, seatClasses) {
  const out = { ...grid }
  const legs = stopCount - 1
  if (legs < 1) return out
  for (const cls of seatClasses) {
    const full = grid[fareKey(0, legs, cls)]
    if (!filled(full)) continue
    for (const { fromSeq, toSeq } of stopPairs(stopCount)) {
      const key = fareKey(fromSeq, toSeq, cls)
      if (!filled(out[key])) out[key] = roundFare((Number(full) * (toSeq - fromSeq)) / legs)
    }
  }
  return out
}

export const fareKey = (fromSeq, toSeq, seatClass) => `${fromSeq}-${toSeq}-${seatClass}`

/** The API's fare list as an editable grid. */
export function faresToGrid(fares) {
  const grid = {}
  for (const f of fares || []) grid[fareKey(f.fromSeq, f.toSeq, f.seatClass)] = f.fare
  return grid
}

/** The grid back to the API's fare list: only filled cells for pairs and classes that still exist. */
export function gridToFares(grid, stopCount, seatClasses) {
  const fares = []
  for (const { fromSeq, toSeq } of stopPairs(stopCount)) {
    for (const seatClass of seatClasses) {
      const v = grid[fareKey(fromSeq, toSeq, seatClass)]
      if (filled(v)) fares.push({ fromSeq, toSeq, seatClass, fare: Number(v) })
    }
  }
  return fares
}

/**
 * Stops were reordered or removed: carry each fare over to the stops' new positions, by place.
 * `oldPlaces` and `newPlaces` are the place ids in stop order.
 */
export function remapFareGrid(grid, oldPlaces, newPlaces) {
  const out = {}
  for (const [key, fare] of Object.entries(grid)) {
    const [from, to, ...cls] = key.split('-')
    const nf = newPlaces.indexOf(oldPlaces[Number(from)])
    const nt = newPlaces.indexOf(oldPlaces[Number(to)])
    if (nf >= 0 && nt > nf) out[fareKey(nf, nt, cls.join('-'))] = fare
  }
  return out
}

// --- Cancellation policy -------------------------------------------------------------------

export const DEFAULT_POLICY = [
  { minHours: 48, percent: 10 },
  { minHours: 24, percent: 25 },
  { minHours: 12, percent: 50 },
  { minHours: 0, percent: 100 },
]

/** "More than 48 h before boarding: 10% charge" for one slab. */
export function slabText(slab, index, slabs) {
  const higher = index > 0 ? slabs[index - 1].minHours : null
  const charge = `${slab.percent}% charge${Number(slab.percent) >= 100 ? ' (no refund)' : ''}`
  if (Number(slab.minHours) === 0) {
    return higher != null ? `Less than ${higher} h before boarding: ${charge}` : `Any time before boarding: ${charge}`
  }
  if (higher == null) return `More than ${slab.minHours} h before boarding: ${charge}`
  return `${slab.minHours} to ${higher} h before boarding: ${charge}`
}

/** Slabs sorted by hours, highest first, as the server expects; problems listed for the owner. */
export function normalisePolicy(slabs) {
  return [...slabs]
    .map((s) => ({ minHours: Number(s.minHours), percent: Number(s.percent) }))
    .sort((a, b) => b.minHours - a.minHours)
}

export function policyProblems(slabs) {
  const problems = []
  if (!slabs.length) problems.push('Add at least one cancellation slab.')
  const hours = slabs.map((s) => Number(s.minHours))
  if (slabs.some((s) => s.minHours === '' || Number(s.minHours) < 0)) problems.push('Hours must be 0 or more.')
  if (slabs.some((s) => s.percent === '' || Number(s.percent) < 0 || Number(s.percent) > 100)) problems.push('Charge must be 0 to 100%.')
  if (new Set(hours).size !== hours.length) problems.push('Two slabs have the same hours.')
  if (!hours.includes(0)) problems.push('Add a slab for 0 hours (the last hours before boarding).')
  return problems
}

// --- Run days ------------------------------------------------------------------------------

export const WEEKDAYS = [
  [1, 'Mon'], [2, 'Tue'], [3, 'Wed'], [4, 'Thu'], [5, 'Fri'], [6, 'Sat'], [7, 'Sun'],
]

export function runDaysText(days) {
  const set = [...new Set(days || [])].sort()
  if (set.length === 7) return 'Daily'
  if (!set.length) return 'No days'
  return set.map((d) => WEEKDAYS.find(([n]) => n === d)?.[1]).join(', ')
}

// --- Route checks --------------------------------------------------------------------------

const boards = (p) => p.kind === 'BOARDING' || p.kind === 'BOTH'
const drops = (p) => p.kind === 'DROPPING' || p.kind === 'BOTH'

/** What the server would refuse about a route, in the owner's words (mirrors the spec rules). */
export function routeProblems(route) {
  const problems = []
  const stops = route.stops || []
  if (!route.busId) problems.push('Choose a bus.')
  if (!(route.runDays || []).length) problems.push('Pick at least one day the bus runs.')
  if (!route.activeFrom) problems.push('Set the first date of the route.')
  if (route.activeTo && route.activeFrom && route.activeTo < route.activeFrom) problems.push('The last date is before the first date.')
  const max = Number(route.maxSeatsPerBooking)
  if (!Number.isInteger(max) || max < 1 || max > 10) problems.push('Seats per booking must be 1 to 10.')
  if (stops.length < 2) problems.push('Add at least two stops.')
  if (new Set(stops.map((s) => s.placeId)).size !== stops.length) problems.push('A town appears twice in the stops.')
  if (stops.length >= 2) {
    if (!(stops[0].points || []).some(boards)) problems.push(`Add a boarding point at ${stops[0].placeName || 'the first stop'}.`)
    const last = stops[stops.length - 1]
    if (!(last.points || []).some(drops)) problems.push(`Add a dropping point at ${last.placeName || 'the last stop'}.`)
  }
  for (const s of stops) {
    for (const p of s.points || []) {
      if (!String(p.name || '').trim()) problems.push(`A point at ${s.placeName} has no name.`)
      if (!/^\d{2}:\d{2}$/.test(p.time || '')) problems.push(`Set a time for every point at ${s.placeName}.`)
    }
  }
  return [...new Set(problems)]
}
