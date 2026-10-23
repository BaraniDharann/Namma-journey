import { describe, it, expect } from 'vitest'
import { assertCodeResent } from './codeResend'

describe('assertCodeResent', () => {
  it('accepts a response that says the email is still unverified', () => {
    const res = { data: { emailUnverified: true, email: 'a@b.co' } }
    expect(assertCodeResent(res)).toBe(res)
  })
  it('refuses when no code was sent', () => {
    expect(() => assertCodeResent({ data: { token: 't' } })).toThrow(/already confirmed/)
    expect(() => assertCodeResent({ data: {} })).toThrow()
    expect(() => assertCodeResent(undefined)).toThrow()
  })
})
