// The admin's rail: reviewing travels owner applications and the buses they list.
export const adminNavItems = [
  { path: '/admin/owners', icon: '', label: 'Owner applications' },
  { path: '/admin/bus-approvals', icon: '', label: 'Bus approvals' },
  { path: '/admin/bus-payouts', icon: '', label: 'Bus payouts' },
]

export const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback

export const when = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—')
