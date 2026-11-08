// The admin's rail: the admin reviews travels owners only; an approved owner's buses go live
// without a second review.
export const adminNavItems = [
  { path: '/admin/owners', icon: '', label: 'Owner applications' },
  { path: '/admin/bus-payouts', icon: '', label: 'Bus payouts' },
]

export const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback

export const when = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—')
