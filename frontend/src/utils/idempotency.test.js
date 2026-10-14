import { describe, expect, it } from 'vitest'
import { actionKey, idempotencyHeaders } from './idempotency'

describe('actionKey', () => {
  it('keeps the same key across repeated taps of one action', () => {
    const key = actionKey()
    expect(key.get()).toBe(key.get())
  })

  it('issues a fresh key once the action has succeeded', () => {
    const key = actionKey()
    const first = key.get()
    key.reset()
    expect(key.get()).not.toBe(first)
  })

  it('produces UUID-shaped keys', () => {
    expect(actionKey().get()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })

  it('works where crypto.randomUUID is missing (older browsers, plain http)', () => {
    const original = globalThis.crypto?.randomUUID
    try {
      if (globalThis.crypto) globalThis.crypto.randomUUID = undefined
      const key = actionKey()
      expect(key.get()).toMatch(/^[0-9a-f-]{36}$/)
      const first = key.get()
      key.reset()
      expect(key.get()).not.toBe(first)
    } finally {
      if (globalThis.crypto) globalThis.crypto.randomUUID = original
    }
  })
})

describe('idempotencyHeaders', () => {
  it('sends the key as the Idempotency-Key header', () => {
    expect(idempotencyHeaders('abc')).toEqual({ 'Idempotency-Key': 'abc' })
  })

  it('sends nothing when the caller has no key', () => {
    expect(idempotencyHeaders(undefined)).toEqual({})
  })
})
