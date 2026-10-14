// Mints JWTs for the seeded load-test accounts (see loadtest/README.md).
//
// ONLY for a throwaway environment: it needs the JWT signing secret, so run it against a load-test
// backend started with its own JWT_SECRET - never your real one.
//
//   node loadtest/mint-tokens.mjs <jwt-secret> <out.json> [users=50] [drivers=1,2,3,4,5]
import { createHmac } from 'node:crypto'
import { writeFileSync } from 'node:fs'

const [secret, out, userCount = '50', driverIds = '1,2,3,4,5'] = process.argv.slice(2)
if (!secret || !out) {
  console.error('usage: node mint-tokens.mjs <jwt-secret> <out.json> [users] [driverIds]')
  process.exit(1)
}

const b64url = (buf) => Buffer.from(buf).toString('base64url')

// Same claims JwtUtil.generateToken writes: subject + userId + role, HS256.
function jwt(subject, role) {
  const now = Math.floor(Date.now() / 1000)
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = b64url(JSON.stringify({ role, userId: subject, sub: subject, iat: now, exp: now + 6 * 3600 }))
  const signature = createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url')
  return `${header}.${payload}.${signature}`
}

// Seeded user ids are deterministic: 00000000-0000-4000-8000-<12-digit index>.
const users = Array.from({ length: Number(userCount) }, (_, i) => {
  const id = `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`
  return { id, token: jwt(id, 'ROLE_USER') }
})
const drivers = driverIds.split(',').map((id) => ({ id: Number(id), token: jwt(id, 'ROLE_DRIVER') }))
const owner = jwt('1', 'ROLE_OWNER')

writeFileSync(out, JSON.stringify({ users, drivers, owner }, null, 2))
console.log(`wrote ${users.length} user, ${drivers.length} driver and 1 owner token(s) to ${out}`)
