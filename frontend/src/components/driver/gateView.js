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
