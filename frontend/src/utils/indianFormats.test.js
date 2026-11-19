import { describe, it, expect } from 'vitest'
import { F, fieldProps } from './indianFormats'

describe('typing filters keep only what the format allows', () => {
  it('Aadhaar: digits only, never more than 12', () => {
    expect(F.aadhaar.clean('2345 6789 0124 999')).toBe('234567890124')
    expect(F.aadhaar.clean('abcd12')).toBe('12')
  })
  it('mobile: digits only, 10 at most', () => {
    expect(F.mobile.clean('+91 95979-65911')).toBe('919597965911'.slice(0, 10))
    expect(F.mobile.clean('95979659111234')).toBe('9597965911')
  })
  it('PAN: letters where letters go, digits where digits go', () => {
    expect(F.pan.clean('abcpe1234f')).toBe('ABCPE1234F')
    expect(F.pan.clean('1a2b3c4d5e')).toBe('ABCDE')
    expect(F.pan.clean('ABCPE1234FXYZ')).toBe('ABCPE1234F')
  })
  it('licence, IFSC and plate: upper case, no symbols, length capped', () => {
    expect(F.licence.clean('tn01-2019 0001234-999')).toBe('TN0120190001234')
    expect(F.ifsc.clean('sbin0001234xyz')).toBe('SBIN0001234')
    expect(F.plate.clean('tn-01-ab-1234@')).toBe('TN01AB1234')
  })
  it('names: no digits or symbols, 60 at most; Tamil kept', () => {
    expect(F.name.clean('Ravi123 <Kumar>')).toBe('Ravi Kumar')
    expect(F.name.clean('பரணி')).toBe('பரணி')
    expect(F.name.clean('x'.repeat(80))).toHaveLength(60)
  })
})

describe('errors say what is wrong (negative cases)', () => {
  it('Aadhaar', () => {
    expect(F.aadhaar.error('234567890124')).toBe('')
    expect(F.aadhaar.error('12345')).toMatch(/exactly 12/)
    expect(F.aadhaar.error('234567890123')).toMatch(/not valid/) // wrong check digit
    expect(F.aadhaar.error('034567890124')).toMatch(/not valid/) // starts with 0
  })
  it('PAN holder type and shape', () => {
    expect(F.pan.error('ABCPE1234F')).toBe('')
    expect(F.pan.error('ABCDE1234F')).not.toBe('') // D is not a holder type
    expect(F.pan.error('ABCP1234F')).not.toBe('')
  })
  it('licence', () => {
    expect(F.licence.error('TN0120190001234')).toBe('')
    expect(F.licence.error('DL1')).not.toBe('')
    expect(F.licence.error('TN0118990001234')).not.toBe('') // year 1899
  })
  it('mobile, plate, email, password', () => {
    expect(F.mobile.error('5597965911')).not.toBe('')
    expect(F.plate.error('TN 01 AB 1234')).toBe('')
    expect(F.plate.error('22 BH 1234 AA')).toBe('')
    expect(F.plate.error('TN0A1234')).not.toBe('')
    expect(F.email.error('a@b')).not.toBe('')
    expect(F.password.error('Lokesh123@')).toBe('')
    expect(F.password.error('allletters')).toMatch(/letter and one number/)
    expect(F.password.error('short1')).toMatch(/8 to 64/)
  })
})

describe('fieldProps', () => {
  it('caps the input and cleans on change', () => {
    let v = ''
    const props = fieldProps('aadhaar', '', (x) => { v = x })
    expect(props.maxLength).toBe(12)
    expect(props.inputMode).toBe('numeric')
    props.onChange({ target: { value: '12ab34 56' } })
    expect(v).toBe('123456')
  })
})
