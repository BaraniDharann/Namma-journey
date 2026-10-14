// Idempotency keys for actions that must never happen twice: creating a booking, booking a
// package, starting a payment. The server (IdempotencyFilter) runs the first request carrying a
// key and answers any repeat with that same result, so a double tap or a retry after a dropped
// connection cannot create a second booking.
//
// The key belongs to the user's ACTION, not to one HTTP call. A component holds one actionKey,
// passes key.get() on every submit, and calls key.reset() only after the action succeeded. A
// key minted per call would give a double tap two different keys and protect nothing.

function uuid() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // randomUUID needs a secure context (https or localhost); fall back to getRandomValues, then
  // to Math.random, shaped as a v4 UUID either way.
  const bytes = new Uint8Array(16)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256)
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function actionKey() {
  let key = uuid()
  return {
    get: () => key,
    reset: () => { key = uuid() },
  }
}

export function idempotencyHeaders(key) {
  return key ? { 'Idempotency-Key': key } : {}
}
