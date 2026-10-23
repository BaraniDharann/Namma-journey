/**
 * Which screen a driver sees, from the verification the server reports. Anything we cannot
 * read yet is 'loading', never 'app', so an unverified driver never flashes the dashboard.
 */
export function gateView(verification) {
  switch (verification?.status) {
    case 'APPROVED': return 'app'
    case 'UNDER_REVIEW': return 'review'
    case 'REJECTED': return 'rejected'
    default: return 'loading'
  }
}

export const DOCUMENTS = [
  { key: 'photo', label: 'Your photo', icon: 'user' },
  { key: 'licensePhoto', label: 'Driving licence', icon: 'id' },
  { key: 'aadhaarPhoto', label: 'Aadhaar card', icon: 'id' },
  { key: 'rcPhoto', label: 'Vehicle RC', icon: 'car' },
]

// Each driver page renders its own DashboardLayout, so the gate remounts on every navigation.
// Remembering the last status per driver lets an approved driver see the page at once while a
// background check confirms it. Keyed by id, so another driver never inherits it.
const lastVerification = new Map()
export const getCachedVerification = (driverId) => lastVerification.get(String(driverId)) ?? null
export const setCachedVerification = (driverId, v) => { lastVerification.set(String(driverId), v) }
export const clearVerificationCache = () => lastVerification.clear()
