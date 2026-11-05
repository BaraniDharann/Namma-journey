// The admin's rail: one job for now, reviewing travels owner applications.
export const adminNavItems = [
  { path: '/admin/owners', icon: '', label: 'Owner applications' },
]

export const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback

export const when = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—')
