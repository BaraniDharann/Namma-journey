// Stress test: raise the request RATE step by step until the backend stops keeping up, to find
// the capacity of one instance. Traffic mix ~ real usage: mostly reads, some bookings.
//
// Not pass/fail like booking-flow.js - read where p95 and errors turn upward in the output (or on
// the Grafana dashboard while it runs). That knee is one instance's capacity on this hardware.
//
//   k6 run -e BASE_URL=http://localhost:8090 -e TOKENS=/data/tokens.json loadtest/k6/stress.js
import http from 'k6/http'
import { check } from 'k6'
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8090'
const tokens = JSON.parse(open(__ENV.TOKENS || './tokens.json'))

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 50, timeUnit: '1s',
      preAllocatedVUs: 200, maxVUs: 1500,
      stages: [
        { duration: '30s', target: 200 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 800 },
        { duration: '30s', target: 1200 },
        { duration: '30s', target: 1600 },
      ],
    },
  },
  // Record per-stage latency so the knee is visible in the summary.
  thresholds: { http_req_failed: ['rate<0.05'] },
}

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)]

export default function () {
  const roll = Math.random()
  if (roll < 0.55) {
    check(http.get(`${BASE}/api/public/packages`, { tags: { kind: 'read' } }), { ok: (r) => r.status === 200 })
  } else if (roll < 0.85) {
    const u = pick(tokens.users)
    check(http.get(`${BASE}/api/user/${u.id}/bookings?page=0&size=10`, { headers: { Authorization: `Bearer ${u.token}` }, tags: { kind: 'read' } }),
      { ok: (r) => r.status === 200 })
  } else {
    const u = pick(tokens.users)
    const d = new Date(Date.now() + (1 + Math.floor(Math.random() * 300)) * 86400000).toISOString().slice(0, 10)
    const body = JSON.stringify({
      userName: 'Stress', userPhone: '9876543210', fromPlace: 'Chennai', toPlace: 'Vellore',
      fromLat: 13.08, fromLon: 80.27, toLat: 12.92, toLon: 79.13, fromDate: d, toDate: d,
      travelMembers: 2, acType: 'AC', bookingType: 'DISTANCE_BASED',
    })
    check(http.post(`${BASE}/api/user/${u.id}/bookings`, body, {
      headers: { Authorization: `Bearer ${u.token}`, 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
      tags: { kind: 'booking' },
    }), { ok: (r) => r.status === 201 })
  }
}
