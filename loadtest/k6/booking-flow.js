// Load test: the four things real traffic does at once.
//
//   browse    - anonymous visitors on the landing page (packages, pricing, reviews)
//   book      - signed-in travellers creating bookings, each followed by a deliberate retry with
//               the same Idempotency-Key, exactly like a double tap or a lost response
//   dashboard - signed-in travellers loading their bookings and notification badge
//   tracking  - drivers posting GPS every 2s while the owner's map polls the position
//
// Pass/fail is decided by the thresholds at the bottom, so a regression fails the run.
//
//   k6 run -e BASE_URL=http://localhost:8090 -e TOKENS=/data/tokens.json loadtest/k6/booking-flow.js
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter } from 'k6/metrics'
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8090'
const tokens = JSON.parse(open(__ENV.TOKENS || './tokens.json'))

const replayed = new Counter('idempotent_replays')
const duplicateBookings = new Counter('duplicate_bookings_created')

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus', exec: 'browse',
      stages: [{ duration: '30s', target: 40 }, { duration: '2m', target: 40 }, { duration: '20s', target: 0 }],
    },
    book: {
      executor: 'ramping-vus', exec: 'book',
      stages: [{ duration: '30s', target: 15 }, { duration: '2m', target: 15 }, { duration: '20s', target: 0 }],
    },
    dashboard: {
      executor: 'ramping-vus', exec: 'dashboard',
      stages: [{ duration: '30s', target: 25 }, { duration: '2m', target: 25 }, { duration: '20s', target: 0 }],
    },
    tracking: {
      executor: 'constant-vus', exec: 'tracking', vus: 5, duration: '2m50s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:read}': ['p(95)<300'],
    'http_req_duration{kind:booking}': ['p(95)<800'],
    'http_req_duration{kind:tracking}': ['p(95)<200'],
    duplicate_bookings_created: ['count==0'],
  },
}

const auth = (token, extra = {}) => ({ headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...extra } })
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)]

// One real booking per driver for the tracking scenario to report positions against.
export function setup() {
  const user = tokens.users[0]
  const trips = []
  for (let i = 0; i < tokens.drivers.length; i++) {
    const res = http.post(`${BASE}/api/user/${user.id}/bookings`, JSON.stringify(bookingBody(400 + i)), auth(user.token))
    if (res.status === 201) trips.push(res.json('bookingId'))
  }
  return { trips }
}

function bookingBody(dayOffset) {
  const d = new Date(Date.now() + dayOffset * 86400000).toISOString().slice(0, 10)
  return {
    userName: 'Load User', userPhone: '9876543210',
    fromPlace: 'Chennai', toPlace: 'Mahabalipuram',
    fromLat: 13.0827, fromLon: 80.2707, toLat: 12.6208, toLon: 80.1945,
    fromDate: d, toDate: d, travelMembers: 3, acType: 'AC',
    bookingType: 'DISTANCE_BASED',
  }
}

export function browse() {
  const tags = { tags: { kind: 'read' } }
  check(http.get(`${BASE}/api/public/packages`, tags), { 'packages 200': (r) => r.status === 200 })
  check(http.get(`${BASE}/api/public/pricing`, tags), { 'pricing 200': (r) => r.status === 200 })
  check(http.get(`${BASE}/api/public/reviews`, tags), { 'reviews 200': (r) => r.status === 200 })
  sleep(1 + Math.random())
}

export function book() {
  const user = pick(tokens.users)
  const key = uuidv4()
  const body = JSON.stringify(bookingBody(1 + Math.floor(Math.random() * 300)))
  const params = auth(user.token, { 'Idempotency-Key': key })
  params.tags = { kind: 'booking' }

  const first = http.post(`${BASE}/api/user/${user.id}/bookings`, body, params)
  check(first, { 'booking created (201)': (r) => r.status === 201 })

  // The double tap: same key, same body. Must return the SAME booking, not create a new one.
  const retry = http.post(`${BASE}/api/user/${user.id}/bookings`, body, params)
  const same = retry.status === 201 && retry.json('bookingId') === first.json('bookingId')
  check(retry, { 'retry replays the first booking': () => same })
  if (retry.headers['Idempotent-Replayed'] === 'true') replayed.add(1)
  if (retry.status === 201 && !same) duplicateBookings.add(1)
  sleep(2 + Math.random() * 2)
}

export function dashboard() {
  const user = pick(tokens.users)
  const params = auth(user.token)
  params.tags = { kind: 'read' }
  check(http.get(`${BASE}/api/user/${user.id}/bookings`, params), { 'my bookings 200': (r) => r.status === 200 })
  check(http.get(`${BASE}/api/notifications/unread-count?recipientId=${user.id}&role=ROLE_USER`, params), { 'unread 200': (r) => r.status === 200 })
  sleep(3 + Math.random() * 2)
}

export function tracking(data) {
  const i = (__VU - 1) % tokens.drivers.length
  const driver = tokens.drivers[i]
  const bookingId = data.trips[i]
  if (!bookingId) { sleep(2); return }

  const update = auth(driver.token)
  update.tags = { kind: 'tracking' }
  const pos = { driverId: driver.id, bookingId, latitude: 13.08 - Math.random() * 0.4, longitude: 80.27 - Math.random() * 0.1, heading: 180, timestamp: Date.now() }
  check(http.post(`${BASE}/api/driver/location/update`, JSON.stringify(pos), update), { 'location accepted': (r) => r.status === 200 })

  const read = auth(tokens.owner)
  read.tags = { kind: 'tracking' }
  check(http.get(`${BASE}/api/driver/location/${bookingId}`, read), { 'owner sees position': (r) => r.status === 200 })
  sleep(2)
}
