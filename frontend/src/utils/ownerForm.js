// Checks for the travels owner application, mirroring OwnerApplicationChecks / OwnerSignupService
// on the server so the owner hears about a typo before uploading three images. The server checks
// everything again; these only save a round trip.

const norm = (s) => String(s ?? '').replace(/\s+/g, '').toUpperCase()

export const validPan = (s) => /^[A-Z]{5}[0-9]{4}[A-Z]$/.test(norm(s))
export const validIfsc = (s) => /^[A-Z]{4}0[A-Z0-9]{6}$/.test(String(s ?? '').trim().toUpperCase())
export const validUpi = (s) => /^[\w.-]{2,}@[a-zA-Z]{2,}$/.test(String(s ?? '').trim())
export const validAccount = (s) => /^\d{9,18}$/.test(String(s ?? '').trim())
export const validPhone = (s) => /^[6-9]\d{9}$/.test(String(s ?? '').trim())

const D = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 2, 3, 4, 0, 6, 7, 8, 9, 5], [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7], [4, 0, 1, 2, 3, 9, 5, 6, 7, 8], [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2], [7, 6, 5, 9, 8, 2, 1, 0, 4, 3], [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
]
const P = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 5, 7, 6, 2, 8, 3, 0, 9, 4], [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7], [9, 4, 5, 3, 1, 2, 6, 8, 7, 0], [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5], [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
]

/** 12 digits, not starting 0 or 1, last digit the Verhoeff check digit. */
export function validAadhaar(s) {
  const digits = String(s ?? '').replace(/\s+/g, '')
  if (!/^[2-9]\d{11}$/.test(digits)) return false
  let c = 0
  for (let i = 0; i < digits.length; i++) {
    c = D[c][P[i % 8][Number(digits[digits.length - 1 - i])]]
  }
  return c === 0
}

const DOCS = [['registrationDoc', 'Business registration document'], ['panDoc', 'PAN card'], ['aadhaarDoc', 'Aadhaar card']]
const MAX_DOC_BYTES = 5 * 1024 * 1024

/** The first problem with the application, in plain words; '' when it can be sent. */
export function ownerFormError(f, docs, documentsOptional) {
  if (!f.travelsName?.trim()) return 'Enter your travels name'
  if (!f.contactName?.trim()) return "Enter the contact person's name"
  if (!/^\S+@\S+\.\S+$/.test(f.email?.trim() ?? '')) return 'Enter a valid email address'
  if (!validPhone(f.phone)) return 'Enter a 10-digit mobile number'
  if (!f.cityIds?.length) return 'Choose at least one city you serve'
  if (!validPan(f.panNumber)) return 'PAN must look like ABCDE1234F'
  if (!validAadhaar(f.aadhaarNumber)) return 'Aadhaar number is not valid - check the 12 digits'
  if (!f.bankAccountHolder?.trim()) return 'Enter the bank account holder name'
  if (!validAccount(f.bankAccountNumber)) return 'Bank account number must be 9 to 18 digits'
  if (!validIfsc(f.bankIfsc)) return 'IFSC must look like SBIN0001234'
  if (!f.bankName?.trim()) return 'Enter the bank name'
  if (!validUpi(f.upiId)) return 'UPI ID must look like name@bank'
  for (const [key, label] of DOCS) {
    const file = docs[key]
    if (!file && !documentsOptional) return `Upload the ${label} image`
    if (file?.size > MAX_DOC_BYTES) return `${label} must be 5 MB or smaller`
  }
  if (!f.aadhaarConsent) return 'Please give consent for offline Aadhaar verification'
  return ''
}
