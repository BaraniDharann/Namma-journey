import { describe, it, expect } from 'vitest'
import { validPan, validIfsc, validAadhaar, validUpi, validAccount, validPhone, ownerFormError } from './ownerForm'

describe('owner application field checks (mirror OwnerApplicationChecks on the server)', () => {
  it('PAN: five letters, four digits, a letter', () => {
    expect(validPan('abcde1234f')).toBe(true)
    expect(validPan(' ABCDE1234F ')).toBe(true)
    expect(validPan('ABCD1234F')).toBe(false)
    expect(validPan('ABCDE12345')).toBe(false)
  })
  it('IFSC: four letters, a zero, six letters or digits', () => {
    expect(validIfsc('SBIN0001234')).toBe(true)
    expect(validIfsc('hdfc0abc123')).toBe(true)
    expect(validIfsc('SBIN1001234')).toBe(false)
  })
  it('Aadhaar: 12 digits passing the Verhoeff check', () => {
    expect(validAadhaar('2345 6789 0124')).toBe(true)
    expect(validAadhaar('234567890123')).toBe(false)
    expect(validAadhaar('1234')).toBe(false)
  })
  it('UPI, bank account and phone', () => {
    expect(validUpi('ravi@okaxis')).toBe(true)
    expect(validUpi('not-a-upi')).toBe(false)
    expect(validAccount('12345678906789')).toBe(true)
    expect(validAccount('12ab')).toBe(false)
    expect(validPhone('9876543210')).toBe(true)
    expect(validPhone('12345')).toBe(false)
  })
})

describe('ownerFormError', () => {
  const good = {
    travelsName: 'Ravi Travels', contactName: 'Ravi', email: 'ravi@example.com', phone: '9876543210',
    cityIds: [1], panNumber: 'ABCDE1234F', aadhaarNumber: '234567890124', bankAccountHolder: 'Ravi',
    bankAccountNumber: '12345678906789', bankIfsc: 'SBIN0001234', bankName: 'SBI', upiId: 'ravi@okaxis',
    aadhaarConsent: true,
  }
  const docs = { registrationDoc: {}, panDoc: {}, aadhaarDoc: {} }

  it('is empty for a complete application', () => {
    expect(ownerFormError(good, docs, false)).toBe('')
  })
  it('names the first problem', () => {
    expect(ownerFormError({ ...good, cityIds: [] }, docs, false)).toMatch(/city/i)
    expect(ownerFormError({ ...good, aadhaarConsent: false }, docs, false)).toMatch(/consent/i)
    expect(ownerFormError(good, { ...docs, panDoc: null }, false)).toMatch(/PAN card/)
  })
  it('lets a resubmission keep the documents already sent', () => {
    expect(ownerFormError(good, { registrationDoc: null, panDoc: null, aadhaarDoc: null }, true)).toBe('')
  })
})
