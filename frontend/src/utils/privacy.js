/**
 * Masking for personal details shown on screen. Phone numbers keep only their last two digits
 * ("xxxxxxxx10"), emails keep the first letter and the domain ("t•••••@gmail.com"), and ID
 * numbers (Aadhaar, licence, PAN) keep their last four characters. Calls still work: the real
 * number goes only into the tel: link, never onto the page.
 */
export function maskPhone(value) {
  if (value == null || value === '') return ''
  const digits = String(value).replace(/\D/g, '')
  if (digits.length <= 2) return 'x'.repeat(digits.length)
  const local = digits.length > 10 ? digits.slice(-10) : digits
  return 'x'.repeat(local.length - 2) + local.slice(-2)
}

export function maskEmail(value) {
  if (value == null || value === '') return ''
  const s = String(value)
  const at = s.indexOf('@')
  if (at <= 0) return 'x'.repeat(s.length)
  return s[0] + 'x'.repeat(Math.max(at - 1, 3)) + s.slice(at)
}

export function maskId(value, keep = 4) {
  if (value == null || value === '') return ''
  const s = String(value).replace(/\s/g, '')
  if (s.length <= keep) return 'x'.repeat(s.length)
  return 'x'.repeat(s.length - keep) + s.slice(-keep)
}

/** tel: href for a number that is never displayed. */
export function telHref(value) {
  const digits = String(value || '').replace(/[^\d+]/g, '')
  return digits ? `tel:${digits}` : undefined
}
