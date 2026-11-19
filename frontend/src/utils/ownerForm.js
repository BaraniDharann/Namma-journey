// Checks for the travels owner application, mirroring OwnerApplicationChecks / OwnerSignupService
// on the server so the owner hears about a typo before uploading three images. The server checks
// everything again; these only save a round trip.

import { F, validAadhaar } from './indianFormats'

export { validAadhaar }

const norm = (s) => String(s ?? '').replace(/\s+/g, '').toUpperCase()

// The same official formats as every other form (utils/indianFormats, IndianFormats.java).
export const validPan = (s) => !F.pan.error(norm(s))
export const validIfsc = (s) => !F.ifsc.error(norm(s))
export const validUpi = (s) => !F.upi.error(String(s ?? '').trim())
export const validAccount = (s) => !F.account.error(String(s ?? '').replace(/\s/g, ''))
export const validPhone = (s) => !F.mobile.error(String(s ?? '').trim())

const DOCS = [['registrationDoc', 'Business registration document'], ['panDoc', 'PAN card'], ['aadhaarDoc', 'Aadhaar card']]
const MAX_DOC_BYTES = 5 * 1024 * 1024

/**
 * The first problem on one step of the application wizard (0 travels, 1 identity, 2 bank,
 * 3 documents), as { field, message }, or null when the step is complete. `field` is the form
 * key to put the cursor on.
 */
export function ownerStepProblem(step, f, docs, documentsOptional) {
  const p = (field, message) => ({ field, message })
  if (step === 0) {
    if (!f.travelsName?.trim()) return p('travelsName', 'Enter your travels name')
    if (F.business.error(f.travelsName, 'Travels name')) return p('travelsName', F.business.error(f.travelsName, 'Travels name'))
    if (!f.contactName?.trim()) return p('contactName', "Enter the contact person's name")
    if (F.name.error(f.contactName, "Contact person's name")) return p('contactName', F.name.error(f.contactName, "Contact person's name"))
    if (F.email.error(f.email)) return p('email', 'Enter a valid email address')
    if (!validPhone(f.phone)) return p('phone', F.mobile.error(''))
    if (!f.serviceAreas?.length && !f.cityIds?.length) return p('serviceAreas', 'Choose where you serve: a city, a state or the whole of India')
  }
  if (step === 1) {
    if (!validPan(f.panNumber)) return p('panNumber', F.pan.error(''))
    const aadhaar = String(f.aadhaarNumber ?? '').replace(/\s/g, '')
    if (F.aadhaar.error(aadhaar)) return p('aadhaarNumber', F.aadhaar.error(aadhaar))
  }
  if (step === 2) {
    if (!f.bankAccountHolder?.trim()) return p('bankAccountHolder', 'Enter the bank account holder name')
    if (F.name.error(f.bankAccountHolder, 'Bank account holder name')) return p('bankAccountHolder', F.name.error(f.bankAccountHolder, 'Bank account holder name'))
    if (!validAccount(f.bankAccountNumber)) return p('bankAccountNumber', F.account.error(''))
    if (!validIfsc(f.bankIfsc)) return p('bankIfsc', F.ifsc.error(''))
    if (!f.bankName?.trim()) return p('bankName', 'Enter the bank name')
    if (F.business.error(f.bankName, 'Bank name')) return p('bankName', F.business.error(f.bankName, 'Bank name'))
    if (!validUpi(f.upiId)) return p('upiId', F.upi.error(''))
  }
  if (step === 3) {
    for (const [key, label] of DOCS) {
      const file = docs[key]
      if (!file && !documentsOptional) return p(key, `Upload the ${label} image`)
      if (file?.size > MAX_DOC_BYTES) return p(key, `${label} must be 5 MB or smaller`)
    }
    if (!f.aadhaarConsent) return p('aadhaarConsent', 'Please give consent for offline Aadhaar verification')
  }
  return null
}

export const OWNER_STEPS = 4

/** The first problem with the whole application, in plain words; '' when it can be sent. */
export function ownerFormError(f, docs, documentsOptional) {
  for (let s = 0; s < OWNER_STEPS; s++) {
    const problem = ownerStepProblem(s, f, docs, documentsOptional)
    if (problem) return problem.message
  }
  return ''
}
