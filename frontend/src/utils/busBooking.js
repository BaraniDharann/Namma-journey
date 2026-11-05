// The customer's bus booking: seat choice, fare breakup, the gender rule on the seat map,
// the hold countdown, the cancellation table, ticket tabs and payout totals. Pure functions
// (plus a small sessionStorage draft) so the pages and the tests share one set of rules.

// --- Seats -----------------------------------------------------------------------------------

const SELECTABLE = new Set(['AVAILABLE', 'FEMALE_ONLY', 'MALE_ONLY'])

/** The state to draw. A seat with no fare for this pair cannot be sold, so it shows as Sold. */
export function seatState(seat) {
  if (seat.fare == null) return 'SOLD'
  return seat.state || 'SOLD'
}

export const isSelectable = (seat) => SELECTABLE.has(seatState(seat))

/**
 * The gender a passenger on this seat must have: F next to a woman (or on a ladies' seat),
 * M next to a man, null when anyone may sit there. Mirrors the server's rule during the hold.
 */
export function genderLock(seat) {
  const s = seat ? seatState(seat) : null
  if (s === 'FEMALE_ONLY') return 'F'
  if (s === 'MALE_ONLY') return 'M'
  return null
}

/**
 * Click on a seat: select or unselect it. Returns { selected, error } with `selected` a new
 * array of labels; `error` explains a refused click.
 */
export function toggleSeat(selected, seat, max) {
  const list = selected || []
  if (list.includes(seat.label)) return { selected: list.filter((l) => l !== seat.label), error: '' }
  if (!isSelectable(seat)) return { selected: list, error: `Seat ${seat.label} is not available.` }
  if (max && list.length >= max) return { selected: list, error: `You can book up to ${max} seat${max === 1 ? '' : 's'} at a time.` }
  return { selected: [...list, seat.label], error: '' }
}

/** Distinct fares of the seats a customer can still pick, cheapest first (the "₹1460 ₹1760" chips). */
export function priceChips(seats) {
  return [...new Set((seats || []).filter(isSelectable).map((s) => Number(s.fare)))].sort((a, b) => a - b)
}

// --- Money -----------------------------------------------------------------------------------

/** Half-up to paise, like the server. */
export function round2(n) {
  const v = Number(n) || 0
  // toPrecision drops binary noise first (1.005 * 100 is 100.49999…), then rounds half-up.
  return (Math.sign(v) * Math.round(Number((Math.abs(v) * 100).toPrecision(15)))) / 100
}

/**
 * Fare breakup for the chosen seats. GST is worked out per seat and rounded per seat, as the
 * server does on each passenger, then added up.
 */
export function fareBreakup(seats, gstPercent) {
  const pct = Number(gstPercent) || 0
  let fare = 0
  let gst = 0
  for (const s of seats || []) {
    const f = Number(s.fare) || 0
    fare += f
    gst += round2((f * pct) / 100)
  }
  fare = round2(fare)
  gst = round2(gst)
  return { count: (seats || []).length, fare, gst, gstPercent: pct, total: round2(fare + gst) }
}

// --- Passengers ------------------------------------------------------------------------------

/** 10-digit Indian mobile, with an optional +91 / 0 prefix and spaces or dashes. */
export function cleanPhone(v) {
  const digits = String(v || '').replace(/[^\d]/g, '')
  const local = digits.length === 12 && digits.startsWith('91') ? digits.slice(2) : digits.length === 11 && digits.startsWith('0') ? digits.slice(1) : digits
  return /^[6-9]\d{9}$/.test(local) ? local : null
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** One passenger row per chosen seat, keeping what was already typed and applying gender locks. */
export function passengerRows(seats, previous = []) {
  return (seats || []).map((seat) => {
    const old = previous.find((p) => p.seatLabel === seat.label) || {}
    const lock = genderLock(seat)
    return { seatLabel: seat.label, name: old.name || '', age: old.age ?? '', gender: lock || old.gender || '' }
  })
}

/** What is still wrong with the passenger form, in words; empty when it can be sent. */
export function bookingProblems({ passengers, seats, contactPhone, contactEmail, extraPhone }) {
  const problems = []
  for (const p of passengers || []) {
    const seat = (seats || []).find((s) => s.label === p.seatLabel)
    const lock = genderLock(seat)
    const age = Number(p.age)
    if (String(p.name || '').trim().length < 2) problems.push(`Enter the name for seat ${p.seatLabel}.`)
    if (!Number.isInteger(age) || age < 1 || age > 120) problems.push(`Enter an age (1 to 120) for seat ${p.seatLabel}.`)
    if (p.gender !== 'M' && p.gender !== 'F') problems.push(`Choose a gender for seat ${p.seatLabel}.`)
    else if (lock && p.gender !== lock) problems.push(`Seat ${p.seatLabel} is for ${lock === 'F' ? 'women' : 'men'} only.`)
  }
  if (!cleanPhone(contactPhone)) problems.push('Enter a 10-digit mobile number.')
  if (!EMAIL.test(String(contactEmail || '').trim())) problems.push('Enter an email address for the ticket.')
  if (String(extraPhone || '').trim() && !cleanPhone(extraPhone)) problems.push('The extra number must be a 10-digit mobile number.')
  return problems
}

// --- Time ------------------------------------------------------------------------------------

/** Whole seconds from `now` to an ISO date-time, never below 0. */
export function secondsUntil(iso, now = Date.now()) {
  if (!iso) return 0
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return 0
  return Math.max(0, Math.floor((t - now) / 1000))
}

/** "09:41" for a hold countdown; hours appear only past 60 minutes ("1:02:05"). */
export function formatCountdown(seconds) {
  const s = Math.max(0, Math.floor(Number(seconds) || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = String(s % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${String(m).padStart(2, '0')}:${ss}`
}

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** "Mon 10 Nov, 21:30" from an ISO local date-time, read from the text (no time-zone shift). */
export function whenText(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(iso || '')
  if (!m) return '—'
  const [, y, mo, d, hh, mm] = m
  const day = DAYS[new Date(Number(y), Number(mo) - 1, Number(d)).getDay()]
  const date = `${day} ${Number(d)} ${MONTHS[Number(mo) - 1]}`
  return hh ? `${date}, ${hh}:${mm}` : date
}

/** "Mon 10 Nov 2026" for a yyyy-MM-dd date. */
export function dayText(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '')
  return m ? `${whenText(iso.slice(0, 10))} ${m[1]}` : '—'
}

// --- Cancellation table ----------------------------------------------------------------------

/**
 * The cancellation slabs as rows a customer can read: when (with clock times) and the charge.
 * Slabs come highest `minHours` first; `until` is the last moment a slab applies and is null
 * for the last one.
 */
export function slabRows(slabs) {
  const list = slabs || []
  return list.map((slab, i) => {
    const prev = i > 0 ? list[i - 1].until : null
    let when
    if (i === 0) when = slab.until ? `Until ${whenText(slab.until)}` : 'Any time before boarding'
    else if (slab.until) when = prev ? `${whenText(prev)} to ${whenText(slab.until)}` : `Until ${whenText(slab.until)}`
    else when = prev ? `After ${whenText(prev)}` : 'Until boarding'
    const percent = Number(slab.percent) || 0
    return {
      when,
      percent,
      charge: `${percent}%`,
      refund: percent >= 100 ? 'No refund' : `${100 - percent}% of the fare + GST back`,
    }
  })
}

// --- Tickets ---------------------------------------------------------------------------------

export const TICKET_STATUS = {
  PENDING: ['wait', 'Awaiting payment'],
  CONFIRMED: ['ok', 'Confirmed'],
  PARTIALLY_CANCELLED: ['go', 'Partly cancelled'],
  CANCELLED: ['stop', 'Cancelled'],
  SERVICE_CANCELLED: ['stop', 'Cancelled by operator'],
  EXPIRED: ['stop', 'Payment expired'],
  COMPLETED: ['ok', 'Completed'],
}

export const PASSENGER_STATUS = {
  CONFIRMED: ['ok', 'Confirmed'],
  CANCELLED: ['stop', 'Cancelled'],
  PENDING: ['wait', 'Pending'],
}

export const REFUND_STATUS = {
  PENDING: ['wait', 'Pending'],
  QUEUED: ['wait', 'Queued'],
  PROCESSING: ['go', 'Processing'],
  PROCESSED: ['ok', 'Refunded'],
  COMPLETED: ['ok', 'Refunded'],
  FAILED: ['stop', 'Failed'],
}

/** Which tab of My bus tickets a ticket belongs in; null hides it (an unpaid hold). */
export function ticketTab(t) {
  if (['CANCELLED', 'SERVICE_CANCELLED', 'EXPIRED'].includes(t.status)) return 'CANCELLED'
  if (t.status === 'COMPLETED') return 'COMPLETED'
  if (t.status === 'CONFIRMED' || t.status === 'PARTIALLY_CANCELLED') {
    if (t.tripStatus === 'COMPLETED') return 'COMPLETED'
    if (t.tripStatus === 'CANCELLED') return 'CANCELLED'
    return 'UPCOMING'
  }
  return null
}

/** Upcoming soonest first; past tabs newest first. */
export function ticketsForTab(tickets, tab) {
  const list = (tickets || []).filter((t) => ticketTab(t) === tab)
  const key = (t) => `${t.travelDate || ''}T${(t.boardingPoint?.at || '').slice(11)}`
  return list.sort((a, b) => (tab === 'UPCOMING' ? key(a).localeCompare(key(b)) : key(b).localeCompare(key(a))))
}

/** Seats of a ticket that can still be cancelled. */
export const cancellableSeats = (ticket) => (ticket?.passengers || []).filter((p) => p.status === 'CONFIRMED').map((p) => p.seatLabel)

// --- Payouts ---------------------------------------------------------------------------------

/** Totals per payout status: count, fares travelled, charges kept, gross, commission, net. */
export function payoutTotals(payouts) {
  const blank = () => ({ count: 0, faresTravelled: 0, chargesKept: 0, gross: 0, commission: 0, net: 0 })
  const out = { DUE: blank(), PAID: blank() }
  for (const p of payouts || []) {
    const t = out[p.status]
    if (!t) continue
    t.count++
    for (const k of ['faresTravelled', 'chargesKept', 'gross', 'commission', 'net']) t[k] = round2(t[k] + (Number(p[k]) || 0))
  }
  return out
}

// --- Draft kept across the login detour ------------------------------------------------------

const DRAFT_KEY = 'nj_bus_draft'
const DRAFT_TTL = 30 * 60 * 1000

/** Keeps the seat choice, points and passengers while the customer signs in. */
export function saveDraft(draft, now = Date.now()) {
  try { sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ ...draft, savedAt: now })) } catch { /* private mode: nothing kept */ }
}

/** The draft for this trip (and stop pair), if it is recent; otherwise null. */
export function loadDraft(tripId, from, to, now = Date.now()) {
  try {
    const d = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || 'null')
    if (!d || String(d.tripId) !== String(tripId) || String(d.from) !== String(from) || String(d.to) !== String(to)) return null
    if (!d.savedAt || now - d.savedAt > DRAFT_TTL) return null
    return d
  } catch {
    return null
  }
}

export function clearDraft() {
  try { sessionStorage.removeItem(DRAFT_KEY) } catch { /* nothing to clear */ }
}

export const PAYOUT_STATUS = { DUE: ['wait', 'Due'], PAID: ['ok', 'Paid'] }
