// Which success popup a finished write shows. Every POST / PUT / PATCH / DELETE that succeeds goes
// through describeSuccess (api.js); null means "no popup": background calls (live location,
// read receipts), logins that navigate away, and steps in the middle of a flow (a payment order).

const SKIP = [
  /^\/auth\//, // login, OTP, sign-up steps: those pages move on by themselves
  /^\/notifications\//,
  /\/presence$/,
  /\/location\/update$/,
  /\/safety\/answer$/,
  /\/sos$/, // the SOS screen has its own loud confirmation
  /\/telegram-link$/, // returns a link the page opens
  /\/razorpay\/order$/,
  /\/end-trip-photo$/,
  /^\/bus\/holds$/, // seats held: the next step is paying
  /^\/bus\/tickets\/[^/]+\/pay$/,
  /^\/bus\/tickets\/[^/]+\/release$/,
]

// [method or '*', path pattern, title, message]. First match wins, so specific rules come first.
const RULES = [
  // Traveller
  ['post', /^\/user\/[^/]+\/bookings$/, 'Trip booked', 'Your booking is confirmed. We will keep you posted.'],
  ['delete', /^\/user\/[^/]+\/bookings\/[^/]+$/, 'Booking cancelled', 'Your booking has been cancelled.'],
  ['put', /^\/user\/[^/]+\/bookings\/[^/]+$/, 'Booking updated', 'Your changes to this trip are saved.'],
  ['post', /\/payment\/razorpay\/verify$/, 'Payment successful', 'Your payment went through. Your trip is all set.'],
  ['post', /^\/user\/[^/]+\/bookings\/[^/]+\/payment$/, 'Payment sent', 'We will confirm it as soon as it arrives.', 'calm'],
  ['post', /\/reviews?$/, 'Thanks for your review', 'It helps other travellers choose.'],
  ['post', /^\/user\/[^/]+\/package-bookings$/, 'Package booked', 'Your holiday package is booked.'],
  ['post', /\/package-bookings\/[^/]+\/cancel$/, 'Package booking cancelled'],
  ['post', /\/emergency-contacts\/[^/]+\/invite$/, 'Invite sent'],
  ['post', /\/emergency-contacts$/, 'Emergency contact added'],
  ['delete', /\/emergency-contacts\/[^/]+$/, 'Emergency contact removed'],
  ['put', /^\/user\/[^/]+\/password$/, 'Password changed', 'Use your new password next time you sign in.'],
  ['put', /\/profile$/, 'Profile updated', 'Your details are saved.'],
  ['post', /\/close$/, 'Account closed', 'Your account has been closed.'],
  ['post', /^\/bus\/tickets\/[^/]+\/verify$/, 'Payment successful', 'Your bus ticket is booked.'],
  ['post', /^\/bus\/tickets\/[^/]+\/cancel$/, 'Ticket cancelled', 'Any refund goes back to how you paid.'],
  // Driver
  ['post', /\/bookings\/[^/]+\/action$/, 'Done', 'The trip has been updated.'],
  ['post', /\/start-trip$/, 'Trip started', 'Drive safe.'],
  ['post', /\/end-trip$/, 'Trip ended', 'Collect the payment from the passenger.'],
  ['post', /\/cash-payment$/, 'Cash payment recorded'],
  ['post', /\/dues\/payments$/, 'Payment sent', 'Your owner will confirm it shortly.', 'calm'],
  ['put', /^\/driver\/[^/]+\/availability$/, 'Status updated'],
  ['put', /\/documents$/, 'Documents submitted', 'We will review them shortly.'],
  ['put', /\/home-area$/, 'Area saved', 'You will get City rides around this town.'],
  ['put', /\/modes$/, 'Ride types saved'],
  ['put', /\/payout-upi$/, 'UPI ID saved'],
  // Owner
  ['post', /^\/owner\/bookings\/[^/]+\/assign-driver$/, 'Driver assigned', 'The traveller will see it in their bookings.'],
  ['post', /^\/owner\/drivers$/, 'Driver added'],
  ['delete', /^\/owner\/drivers\/[^/]+$/, 'Driver removed'],
  ['post', /^\/owner\/drivers\/[^/]+\/approve$/, 'Driver approved', 'They can go online and take rides now.'],
  ['post', /^\/owner\/drivers\/[^/]+\/reject$/, 'Application rejected', 'The driver has been told what to fix.'],
  ['put', /^\/owner\/drivers\/[^/]+\/checks\//, 'Check saved'],
  ['post', /^\/owner\/vehicles$/, 'Vehicle added'],
  ['post', /^\/owner\/vehicles\/[^/]+\/activate$/, 'Vehicle switched on'],
  ['post', /^\/owner\/vehicles\/[^/]+\/deactivate$/, 'Vehicle switched off'],
  ['post', /^\/owner\/packages$/, 'Package created'],
  ['delete', /^\/owner\/packages\/[^/]+$/, 'Package deleted'],
  ['post', /^\/owner\/packages\/[^/]+\/toggle$/, 'Package updated'],
  ['post', /\/package-bookings\/[^/]+\/confirm$/, 'Package booking confirmed'],
  ['post', /\/package-bookings\/[^/]+\/complete$/, 'Package marked complete'],
  ['post', /^\/owner\/payments\/[^/]+\/verify$/, 'Payment verified'],
  ['post', /^\/owner\/dues\/[^/]+\/confirm$/, 'Payment confirmed'],
  ['post', /^\/owner\/dues\/[^/]+\/reject$/, 'Payment rejected'],
  ['post', /^\/owner\/(owner-)?payouts\//, 'Payout recorded'],
  ['post', /^\/owner\/cities$/, 'City added'],
  ['put', /^\/owner\/(trip-rates|city-fares)\//, 'Prices saved'],
  ['post', /^\/owner\/change-password$/, 'Password changed', 'Use your new password next time you sign in.'],
  ['put', /^\/owner\/me\/payout$/, 'Bank details saved'],
  ['put', /^\/owner\/me$/, 'Profile updated', 'Your details are saved.'],
  ['post', /^\/owner\/bus\/buses$/, 'Bus added'],
  ['post', /^\/owner\/bus\/buses\/[^/]+\/submit$/, 'Bus sent for review', 'We will tell you once it is approved.'],
  ['put', /^\/owner\/bus\/buses\/[^/]+\/seats$/, 'Seat layout saved'],
  ['delete', /\/photos\/[^/]+$/, 'Photo removed'],
  ['post', /^\/owner\/bus\/routes$/, 'Route created'],
  ['post', /^\/owner\/bus\/routes\/[^/]+\/publish$/, 'Route published', 'Travellers can book it now.'],
  ['post', /^\/owner\/bus\/routes\/[^/]+\/pause$/, 'Route paused'],
  ['post', /^\/owner\/bus\/trips\/[^/]+\/cancel$/, 'Bus trip cancelled', 'Passengers will be refunded.'],
  ['post', /^\/owner\/bus\/trips\/[^/]+\/blocks$/, 'Seats blocked'],
  ['post', /^\/owner\/bus\/trips\/[^/]+\/unblock$/, 'Seats released'],
  ['put', /^\/owner\/bus\/trips\/[^/]+\/crew$/, 'Crew saved'],
  ['post', /^\/owner\/bus\/places$/, 'Place added'],
  // Admin
  ['post', /^\/admin\/owners\/[^/]+\/approve$/, 'Owner approved', 'Their login details are on the way.'],
  ['post', /^\/admin\/owners\/[^/]+\/reject$/, 'Owner rejected'],
  ['post', /^\/admin\/owners\/[^/]+\/resend-credentials$/, 'Login details sent'],
  ['post', /^\/admin\/bus\/buses\/[^/]+\/approve$/, 'Bus approved'],
  ['post', /^\/admin\/bus\/buses\/[^/]+\/reject$/, 'Bus rejected'],
  ['post', /^\/admin\/bus\/payouts\/[^/]+\/paid$/, 'Payout marked paid'],
]

const FALLBACK = { post: 'Done', put: 'Saved', patch: 'Saved', delete: 'Deleted' }

/** The popup for a successful write, or null for none. `url` may be relative to /api and carry a query. */
function readBody(body) {
  if (!body) return {}
  if (typeof body === 'string') { try { return JSON.parse(body) || {} } catch { return {} } }
  return typeof body === 'object' ? body : {}
}

export function describeSuccess(method, url, body) {
  const m = String(method || 'get').toLowerCase()
  if (!FALLBACK[m]) return null
  const path = String(url || '').replace(/^https?:\/\/[^/]+/, '').replace(/^\/api(?=\/)/, '').split('?')[0]
  if (SKIP.some((re) => re.test(path))) return null
  if (m === 'post' && /\/bookings\/[^/]+\/action$/.test(path)) {
    const action = String(readBody(body).action || '').toUpperCase()
    if (action === 'ACCEPT') return { title: 'Ride accepted', message: 'Head to the pickup. The passenger has been told.', tone: 'party' }
    if (action === 'REJECT') return { title: 'Ride declined', message: 'It goes to the next driver.', tone: 'calm' }
  }
  const rule = RULES.find(([rm, re]) => (rm === '*' || rm === m) && re.test(path))
  if (!rule) return { title: FALLBACK[m], tone: 'party' }
  const [, , title, message, tone] = rule
  return { title, message, tone: tone || 'party' }
}

/**
 * Shows the success popup with the page's own words (no hook needed). It replaces the generic
 * popup the same API call raised, so the person sees one popup, not two.
 */
export function showSuccess(title, message, tone = 'party') {
  if (typeof window === 'undefined') return
  try {
    window.dispatchEvent(new CustomEvent('nj:action-success', { detail: { title, message, tone, explicit: true } }))
  } catch { /* old browser */ }
}
