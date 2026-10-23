import { describe, it, expect } from 'vitest'
import { rejectReasonError } from './rejectReason'

describe('rejectReasonError', () => {
  it('accepts 5 to 500 characters after trimming', () => {
    expect(rejectReasonError('abcde')).toBeNull()
    expect(rejectReasonError('  abcde  ')).toBeNull()
    expect(rejectReasonError('a'.repeat(500))).toBeNull()
  })
  it('rejects empty, blank and too-short text', () => {
    expect(rejectReasonError('')).toEqual(expect.any(String))
    expect(rejectReasonError(undefined)).toEqual(expect.any(String))
    expect(rejectReasonError('     ')).toEqual(expect.any(String))
    expect(rejectReasonError('  abcd  ')).toEqual(expect.any(String))
  })
  it('rejects text over 500 characters after trimming', () => {
    expect(rejectReasonError('a'.repeat(501))).toEqual(expect.any(String))
    expect(rejectReasonError(` ${'a'.repeat(501)} `)).toEqual(expect.any(String))
  })
})
