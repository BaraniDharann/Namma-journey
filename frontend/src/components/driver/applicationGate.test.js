import { describe, it, expect } from 'vitest'
import { gateView, getCachedVerification, setCachedVerification, clearVerificationCache } from './gateView'

describe('gateView', () => {
  it('lets approved drivers into the app', () => {
    expect(gateView({ status: 'APPROVED' })).toBe('app')
  })
  it('shows the review screen while under review', () => {
    expect(gateView({ status: 'UNDER_REVIEW' })).toBe('review')
  })
  it('shows the re-upload screen when rejected', () => {
    expect(gateView({ status: 'REJECTED' })).toBe('rejected')
  })
  it('stays loading until the status is known', () => {
    expect(gateView(null)).toBe('loading')
    expect(gateView(undefined)).toBe('loading')
    expect(gateView({})).toBe('loading')
  })
})

describe('verification cache', () => {
  it('remembers the last status per driver and never leaks across drivers', () => {
    clearVerificationCache()
    expect(getCachedVerification(7)).toBeNull()
    setCachedVerification(7, { status: 'APPROVED' })
    expect(gateView(getCachedVerification(7))).toBe('app')
    expect(getCachedVerification('7')).toEqual({ status: 'APPROVED' })
    expect(getCachedVerification(8)).toBeNull()
    clearVerificationCache()
    expect(getCachedVerification(7)).toBeNull()
  })
})
