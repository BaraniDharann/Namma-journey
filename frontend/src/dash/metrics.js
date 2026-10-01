/**
 * Pure derivations for the dashboards: everything a card or chart shows is computed here from
 * the API payloads, so the numbers can be unit-tested without rendering anything.
 */

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** Whole rupees in the Indian grouping: 138005.134 → "₹1,38,005". */
export const inr = (v) => '₹' + Math.round(num(v)).toLocaleString('en-IN')

/** "Kanchipuram Bus Stand, Kanchipuram, Tamil Nadu" → "Kanchipuram Bus Stand". */
export const shortPlace = (s) => (s ? String(s).split(',')[0].trim() || '—' : '—')

/** 'YYYY-MM-DD' as a local calendar date (no UTC shift). */
export function localDate(s) {
  if (!s) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(s))
  return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null
}

const dayKey = (d) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate())
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
export const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
export const TIME_BANDS = ['6–9', '9–12', '12–3', '3–6', '6–9p', '9–12p']

/** Booking statuses in display order, each with the chart slot it always keeps. */
export const STATUS_META = {
  COMPLETED: { label: 'Completed', slot: 's3' },
  PENDING: { label: 'Pending', slot: 's4' },
  CONFIRMED: { label: 'Confirmed', slot: 's1' },
  STARTED: { label: 'Started', slot: 's2' },
  CANCELLED: { label: 'Cancelled', slot: 's5' },
}

export function statusSegments(bookings = []) {
  const counts = {}
  bookings.forEach((b) => { counts[b.status] = (counts[b.status] || 0) + 1 })
  return Object.entries(STATUS_META)
    .filter(([k]) => counts[k])
    .map(([k, m]) => [m.label, counts[k], m.slot])
}

function rank(keys, limit) {
  const counts = new Map()
  keys.forEach((k) => counts.set(k, (counts.get(k) || 0) + 1))
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, limit)
}

export const topRoutes = (bookings = [], limit = 5) =>
  rank(bookings.map((b) => `${shortPlace(b.fromPlace)} → ${shortPlace(b.toPlace)}`), limit)

const notCancelled = (b) => b.status !== 'CANCELLED'

export const topDestinations = (bookings = [], limit = 5) =>
  rank(bookings.filter(notCancelled).map((b) => shortPlace(b.toPlace)), limit)

function bandOf(hour) {
  if (hour >= 6 && hour < 9) return 0
  if (hour >= 9 && hour < 12) return 1
  if (hour >= 12 && hour < 15) return 2
  if (hour >= 15 && hour < 18) return 3
  if (hour >= 18 && hour < 21) return 4
  return 5 // 9pm–6am counts as the night band
}

/** Weekday (Mon..Sun) × time band counts of when bookings were made. */
export function bookingHeatmap(bookings = []) {
  const m = Array.from({ length: 7 }, () => Array(6).fill(0))
  bookings.forEach((b) => {
    if (!b?.bookingDate) return
    const d = new Date(b.bookingDate)
    if (Number.isNaN(d.getTime())) return
    m[(d.getDay() + 6) % 7][bandOf(d.getHours())]++
  })
  return m
}

/** Drivers out on a started trip, against the whole fleet. */
export function fleetBusy(bookings = [], drivers = []) {
  const busy = new Set(bookings.filter((b) => b.status === 'STARTED' && b.driverId != null).map((b) => b.driverId)).size
  const total = drivers.length
  return { busy, total, pct: total ? busy / total : 0 }
}

export const waitingForDriver = (bookings = []) =>
  bookings.filter((b) => b.status === 'PENDING' && b.driverId == null).length

/** The owner's monthly series, January up to the current month. */
export function monthSeries(series, today = new Date()) {
  const byMonth = new Map((series?.months || []).map((m) => [m.month, m]))
  return Array.from({ length: today.getMonth() + 1 }, (_, i) => {
    const m = byMonth.get(i + 1)
    return { label: MONTHS[i], revenue: num(m?.totalRevenue), trips: num(m?.totalTrips) }
  })
}

export function pctChange(current, previous) {
  const p = num(previous)
  if (!p) return null
  return ((num(current) - p) / p) * 100
}

/* ── driver ── */

const completed = (bookings) => bookings.filter((b) => b.status === 'COMPLETED')

export function todayStats(bookings = [], today = new Date()) {
  const key = dayKey(today)
  const done = completed(bookings).filter((b) => {
    const d = localDate(b.fromDate)
    return d && dayKey(d) === key
  })
  return {
    earned: done.reduce((s, b) => s + num(b.totalAmount), 0),
    trips: done.length,
    km: done.reduce((s, b) => s + num(b.distanceKm), 0),
  }
}

/** Completed-trip earnings for each day of the current Monday–Sunday week. */
export function earningsThisWeek(bookings = [], today = new Date()) {
  const monday = startOfDay(today)
  monday.setDate(monday.getDate() - ((today.getDay() + 6) % 7))
  const days = WEEKDAYS.map((label, i) => {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    return { label, key: dayKey(d), value: 0 }
  })
  const index = new Map(days.map((d, i) => [d.key, i]))
  completed(bookings).forEach((b) => {
    const d = localDate(b.fromDate)
    const i = d ? index.get(dayKey(d)) : undefined
    if (i !== undefined) days[i].value += num(b.totalAmount)
  })
  return days.map(({ label, value }) => ({ label, value }))
}

/** Completed trips as a share of every trip assigned to the driver. */
export function completionRate(bookings = []) {
  const assigned = bookings.filter((b) => ['CONFIRMED', 'STARTED', 'COMPLETED'].includes(b.status)).length
  return assigned ? completed(bookings).length / assigned : 0
}

/** The trip in progress, otherwise the soonest confirmed one from today on. */
export function nextTrip(bookings = [], today = new Date()) {
  const started = bookings.find((b) => b.status === 'STARTED')
  if (started) return started
  const from = startOfDay(today).getTime()
  const upcoming = bookings
    .filter((b) => b.status === 'CONFIRMED')
    .map((b) => ({ b, t: localDate(b.fromDate)?.getTime() ?? Infinity }))
    .filter(({ t }) => t >= from)
    .sort((x, y) => x.t - y.t)
  return upcoming[0]?.b ?? null
}

export function typeMix(bookings = []) {
  const distance = bookings.filter((b) => b.bookingType === 'DISTANCE_BASED').length
  const hourly = bookings.filter((b) => b.bookingType === 'HOUR_BASED').length
  return [['Distance', distance, 's1'], ['Hourly', hourly, 's2']].filter((s) => s[1])
}

/* ── traveller ── */

/** Spend on trips that were not cancelled, per month, for the last `months` months. */
export function spendByMonth(bookings = [], today = new Date(), months = 6) {
  const out = Array.from({ length: months }, (_, i) => {
    const d = new Date(today.getFullYear(), today.getMonth() - (months - 1 - i), 1)
    return { label: MONTHS[d.getMonth()], y: d.getFullYear(), m: d.getMonth(), value: 0 }
  })
  bookings.filter(notCancelled).forEach((b) => {
    const d = localDate(b.fromDate)
    const slot = d && out.find((o) => o.y === d.getFullYear() && o.m === d.getMonth())
    if (slot) slot.value += num(b.totalAmount)
  })
  return out.map(({ label, value }) => ({ label, value }))
}

export function tripLengthSegments(bookings = []) {
  const live = bookings.filter(notCancelled)
  const days = (b) => num(b.travelDays) || 1
  return [
    ['1 day', live.filter((b) => days(b) <= 1).length, 's1'],
    ['2–3 days', live.filter((b) => days(b) >= 2 && days(b) <= 3).length, 's2'],
    ['4+ days', live.filter((b) => days(b) >= 4).length, 's3'],
  ].filter((s) => s[1])
}

export function daysUntil(dateStr, today = new Date()) {
  const d = localDate(dateStr)
  if (!d) return null
  return Math.round((d.getTime() - startOfDay(today).getTime()) / 86400000)
}

export const totalKm = (bookings = []) =>
  Math.round(bookings.filter(notCancelled).reduce((s, b) => s + num(b.distanceKm), 0))
