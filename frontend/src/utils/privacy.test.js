import { describe, it, expect } from 'vitest'
import { maskPhone, maskEmail, maskId, telHref } from './privacy'

describe('privacy masks', () => {
  it('keeps only the last two digits of a phone', () => {
    expect(maskPhone('9876543210')).toBe('xxxxxxxx10')
    expect(maskPhone('+91 98765 43210')).toBe('xxxxxxxx10')
    expect(maskPhone('')).toBe('')
    expect(maskPhone(null)).toBe('')
  })
  it('masks the email name, keeps the domain', () => {
    expect(maskEmail('ravi@gmail.com')).toBe('rxxx@gmail.com')
    expect(maskEmail('')).toBe('')
  })
  it('keeps the last four of an ID', () => {
    expect(maskId('1234 5678 9012')).toBe('xxxxxxxx9012')
  })
  it('builds a tel link without spaces', () => {
    expect(telHref('+91 98765 43210')).toBe('tel:+919876543210')
    expect(telHref('')).toBeUndefined()
  })
})
