// Which of a traveller's bookings is "live" right now, and what to tell them about it. Kept free of
// React so the rules can be unit-tested; ActiveRideCard renders whatever this returns.

/** Today as YYYY-MM-DD in the browser's own zone (bookings carry plain local dates). */
export function todayIso(now = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`
}

const isRideNow = (b) => b?.dispatchMode === 'RIDE_NOW'
const isCity = (b) => b?.serviceMode === 'CITY'

/**
 * The one booking worth a floating live card, or null.
 *  - a trip in progress (STARTED) always wins;
 *  - then a confirmed ride whose driver is on the way: a Ride Now / City ride, or any trip that
 *    starts today;
 *  - then a Ride Now still searching for a driver.
 * Scheduled trips for a later day stay on My Bookings; they are not "on the way" yet.
 */
export function pickActiveRide(bookings, now = new Date()) {
  const list = Array.isArray(bookings) ? bookings : []
  const today = todayIso(now)
  const started = list.find((b) => b.status === 'STARTED')
  if (started) return started
  const coming = list.find((b) => b.status === 'CONFIRMED' && b.driver
    && (isRideNow(b) || isCity(b) || String(b.fromDate || '') === today))
  if (coming) return coming
  return list.find((b) => b.status === 'PENDING' && isRideNow(b) && b.dispatchState !== 'NO_DRIVER') || null
}

/** The steps shown under the card, in order. */
export const STEPS = [
  { key: 'booked', label: 'Booked', icon: 'check' },
  { key: 'assigned', label: 'Driver assigned', icon: 'wheel' },
  { key: 'coming', label: 'On the way', icon: 'nav' },
  { key: 'arrived', label: 'Arrived', icon: 'pin' },
  { key: 'started', label: 'On trip', icon: 'route' },
]

const ARRIVED_KM = 0.15

/**
 * What the card says for a booking.
 * @param distanceKm how far the driver is from the pickup, when a live location is known
 * @returns {{ step: string, title: string, text: string, tone: 'search'|'live'|'trip' }}
 */
export function describeRide(b, distanceKm = null) {
  const first = String(b?.driver?.name || '').trim().split(/\s+/)[0] || 'Your driver'
  const plate = [b?.driver?.vehicleModel, b?.driver?.vehicleNumber].filter(Boolean).join(' · ')
  const dest = shortName(b?.toPlace)

  if (b?.status === 'STARTED') {
    return { step: 'started', tone: 'trip', title: `On the way to ${dest}`, text: plate ? `With ${first} · ${plate}` : `With ${first}` }
  }
  if (b?.status === 'CONFIRMED' && b?.driver) {
    if (distanceKm != null && distanceKm <= ARRIVED_KM) {
      return { step: 'arrived', tone: 'live', title: `${first} has arrived`, text: plate ? `Look for ${plate} at your pickup` : 'Meet your driver at the pickup point' }
    }
    if (distanceKm != null) {
      // Distance, not minutes: the map works out the real road ETA, and two different ETAs on
      // one screen would only confuse.
      const away = distanceKm < 1 ? `${Math.max(50, Math.round(distanceKm * 1000 / 50) * 50)} m` : `${distanceKm.toFixed(1)} km`
      return { step: 'coming', tone: 'live', title: `${first} is on the way`, text: `${away} away${plate ? ` · ${plate}` : ''}` }
    }
    return { step: 'assigned', tone: 'live', title: `${first} is on the way`, text: plate || 'Tap to see where your driver is' }
  }
  if (b?.dispatchState === 'OFFERED') {
    return { step: 'booked', tone: 'search', title: 'A driver is looking at your ride', text: 'Waiting for them to accept…' }
  }
  return { step: 'booked', tone: 'search', title: 'Finding a driver near you', text: 'Searching close to your pickup first' }
}

/** "Anna Nagar, Chennai, Tamil Nadu" -> "Anna Nagar". */
export function shortName(place) {
  return String(place || 'your drop').split(',')[0].trim() || 'your drop'
}

export const stepIndex = (key) => STEPS.findIndex((s) => s.key === key)
