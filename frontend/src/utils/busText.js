// Small display helpers shared by the owner and admin bus screens.

export const BUS_STATUS = {
  DRAFT: ['wait', 'Draft'],
  PENDING: ['go', 'Going into service'],
  APPROVED: ['ok', 'In service'],
  REJECTED: ['stop', 'Rejected'],
}

export const ROUTE_STATUS = {
  DRAFT: ['wait', 'Draft'],
  PUBLISHED: ['ok', 'Published'],
  PAUSED: ['stop', 'Paused'],
}

export const TRIP_STATUS = {
  SCHEDULED: ['go', 'Scheduled'],
  DEPARTED: ['live', 'On the road'],
  COMPLETED: ['ok', 'Completed'],
  CANCELLED: ['stop', 'Cancelled'],
}

export const DOC_TYPES = [
  ['RC', 'Registration certificate (RC)'],
  ['PERMIT', 'Permit'],
  ['INSURANCE', 'Insurance'],
  ['FITNESS', 'Fitness certificate'],
]

export const PERMIT_TYPES = [
  ['STAGE_CARRIAGE', 'Stage carriage'],
  ['CONTRACT_CARRIAGE', 'Contract carriage'],
  ['ALL_INDIA_TOURIST', 'All India tourist'],
]

export const AMENITIES = [
  'Water bottle', 'Blanket', 'Pillow', 'Charging point', 'Reading light', 'Wi-Fi', 'CCTV',
  'GPS tracking', 'Emergency exit', 'Fire extinguisher', 'First aid', 'Toilet', 'Snacks', 'Movie',
]

/** Today as yyyy-MM-dd in the browser's own time zone (not UTC). */
export function isoDate(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function addDays(iso, days) {
  const [y, m, d] = iso.split('-').map(Number)
  return isoDate(new Date(y, m - 1, d + days))
}

/** Whole days from `today` to `iso` (negative when already past). */
export function daysUntil(iso, today = isoDate()) {
  const toUtc = (s) => { const [y, m, d] = s.split('-').map(Number); return Date.UTC(y, m - 1, d) }
  return Math.round((toUtc(iso) - toUtc(today)) / 86400000)
}

/** Documents that have expired or expire within `withinDays`, soonest first. */
export function expiringDocs(documents, today = isoDate(), withinDays = 15) {
  return (documents || [])
    .filter((d) => d.expiresOn && daysUntil(d.expiresOn, today) <= withinDays)
    .sort((a, b) => a.expiresOn.localeCompare(b.expiresOn))
}

export const docName = (type) => DOC_TYPES.find(([t]) => t === type)?.[1] || type

/** Uploaded files are served from the backend's /uploads/…, next to /api. */
export function uploadUrl(path) {
  if (!path) return ''
  if (/^(https?:|data:|blob:)/.test(path)) return path
  const base = (import.meta.env?.VITE_API_BASE_URL || '').replace(/\/api\/?$/, '')
  return `${base}${path.startsWith('/') ? '' : '/'}${path}`
}

// A loss reads "−₹2,500", not "₹-2,500".
export const inr = (n) => { const v = Number(n || 0); return `${v < 0 ? '−' : ''}₹${Math.abs(v).toLocaleString('en-IN', { maximumFractionDigits: 2 })}` }

export const dateText = (iso) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }) : '—')

export const timeText = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—')

export const errorText = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || fallback
