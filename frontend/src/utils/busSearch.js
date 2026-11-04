// Bus search results: sorting, filtering and the small time/rating texts on each card.
// Everything here is pure so the results page and the tests share one set of rules.
//
// A search card (see the spec's JSON contract):
// {tripId, travelsName, busName, typeLabel, ac, hasSleeper, hasSeater, departureAt, arrivalAt,
//  durationMinutes, ratingAvg, ratingCount, minFare, seatsLeft, singleSeatsLeft, amenities,
//  boardingPoints:[{id, name, at}], droppingPoints:[{id, name, at}]}

export const SORTS = [
  ['CHEAPEST', 'Cheapest'],
  ['EARLIEST', 'Earliest departure'],
  ['RATED', 'Highest rated'],
  ['FASTEST', 'Fastest'],
]

export const DEPARTURE_BUCKETS = [
  ['EARLY', 'Before 6 am'],
  ['MORNING', '6 am – 12 pm'],
  ['AFTERNOON', '12 pm – 6 pm'],
  ['NIGHT', 'After 6 pm'],
]

/** Hours since midnight (with minutes as a fraction) of an ISO local date-time; null when missing. */
export function hourOf(iso) {
  const m = /T(\d{2}):(\d{2})/.exec(iso || '')
  return m ? Number(m[1]) + Number(m[2]) / 60 : null
}

/** Which departure bucket a time falls in. 06:00 is morning, 12:00 afternoon, 18:00 night. */
export function departureBucket(iso) {
  const h = hourOf(iso)
  if (h == null) return null
  if (h < 6) return 'EARLY'
  if (h < 12) return 'MORNING'
  if (h < 18) return 'AFTERNOON'
  return 'NIGHT'
}

/** "21:30" straight from the ISO text, so the browser's time zone never shifts it. */
export function clockText(iso) {
  const m = /T(\d{2}):(\d{2})/.exec(iso || '')
  return m ? `${m[1]}:${m[2]}` : '—'
}

/** Whole calendar days from the departure date to the arrival date (0 = same day, 1 = next day). */
export function dayShift(departureAt, arrivalAt) {
  if (!departureAt || !arrivalAt) return 0
  const utc = (iso) => { const [y, m, d] = iso.slice(0, 10).split('-').map(Number); return Date.UTC(y, m - 1, d) }
  return Math.round((utc(arrivalAt) - utc(departureAt)) / 86400000)
}

/** "7h 30m", "45m", "12h". */
export function durationText(minutes) {
  const total = Math.max(0, Math.round(Number(minutes) || 0))
  const h = Math.floor(total / 60)
  const m = total % 60
  if (!h) return `${m}m`
  return m ? `${h}h ${m}m` : `${h}h`
}

const rated = (t) => Number(t.ratingCount) > 0

/** "★ 4.3" or "New" for a bus nobody has rated yet. */
export const ratingText = (t) => (rated(t) ? `★ ${Number(t.ratingAvg).toFixed(1)}` : 'New')

const byDeparture = (a, b) => String(a.departureAt || '').localeCompare(String(b.departureAt || '')) || (a.tripId - b.tripId)
const num = (v, fallback) => (v == null || v === '' || Number.isNaN(Number(v)) ? fallback : Number(v))

const COMPARE = {
  CHEAPEST: (a, b) => num(a.minFare, Infinity) - num(b.minFare, Infinity) || byDeparture(a, b),
  EARLIEST: byDeparture,
  // Rated buses first, best average first, more ratings break ties; unrated ones keep departure order.
  RATED: (a, b) => {
    if (rated(a) !== rated(b)) return rated(a) ? -1 : 1
    return num(b.ratingAvg, 0) - num(a.ratingAvg, 0) || num(b.ratingCount, 0) - num(a.ratingCount, 0) || byDeparture(a, b)
  },
  FASTEST: (a, b) => num(a.durationMinutes, Infinity) - num(b.durationMinutes, Infinity) || byDeparture(a, b),
}

/** A sorted copy; an unknown key falls back to Cheapest. */
export function sortTrips(trips, key = 'CHEAPEST') {
  return [...(trips || [])].sort(COMPARE[key] || COMPARE.CHEAPEST)
}

export const emptyFilters = () => ({
  acTypes: [], // 'AC' | 'NONAC'
  kinds: [], // 'SLEEPER' | 'SEATER'
  singleOnly: false,
  buckets: [], // DEPARTURE_BUCKETS keys
  boarding: [], // boarding point names
  dropping: [], // dropping point names
  travels: [], // travels names
  minPrice: null,
  maxPrice: null,
})

/** How many filter groups are switched on (for the "Filters (3)" button). */
export function activeFilterCount(f) {
  let n = 0
  for (const k of ['acTypes', 'kinds', 'buckets', 'boarding', 'dropping', 'travels']) if (f[k]?.length) n++
  if (f.singleOnly) n++
  if (f.minPrice != null || f.maxPrice != null) n++
  return n
}

/**
 * Trips that pass every switched-on filter. Within one group the choices are OR (Sleeper or
 * Seater); across groups they are AND (A/C and Sleeper).
 */
export function filterTrips(trips, f = emptyFilters()) {
  const has = (list, v) => list.includes(v)
  return (trips || []).filter((t) => {
    if (f.acTypes?.length && !((t.ac && has(f.acTypes, 'AC')) || (!t.ac && has(f.acTypes, 'NONAC')))) return false
    if (f.kinds?.length && !((t.hasSleeper && has(f.kinds, 'SLEEPER')) || (t.hasSeater && has(f.kinds, 'SEATER')))) return false
    if (f.singleOnly && !(Number(t.singleSeatsLeft) > 0)) return false
    if (f.buckets?.length && !has(f.buckets, departureBucket(t.departureAt))) return false
    if (f.boarding?.length && !(t.boardingPoints || []).some((p) => has(f.boarding, p.name))) return false
    if (f.dropping?.length && !(t.droppingPoints || []).some((p) => has(f.dropping, p.name))) return false
    if (f.travels?.length && !has(f.travels, t.travelsName)) return false
    const fare = num(t.minFare, null)
    if (f.minPrice != null && (fare == null || fare < f.minPrice)) return false
    if (f.maxPrice != null && (fare == null || fare > f.maxPrice)) return false
    return true
  })
}

const uniqueSorted = (values) => [...new Set(values.filter(Boolean))].sort((a, b) => a.localeCompare(b))

/** The choices the filter panel offers, taken from the results themselves. */
export function filterOptions(trips) {
  const list = trips || []
  const fares = list.map((t) => num(t.minFare, null)).filter((v) => v != null)
  return {
    boarding: uniqueSorted(list.flatMap((t) => (t.boardingPoints || []).map((p) => p.name))),
    dropping: uniqueSorted(list.flatMap((t) => (t.droppingPoints || []).map((p) => p.name))),
    travels: uniqueSorted(list.map((t) => t.travelsName)),
    minFare: fares.length ? Math.min(...fares) : 0,
    maxFare: fares.length ? Math.max(...fares) : 0,
  }
}

/** Adds the value when missing, removes it when present (a checkbox in a filter group). */
export const toggleIn = (list, value) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value])
