import { describe, it, expect } from 'vitest'
import { gateView } from './gateView'

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
