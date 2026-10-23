// Mirrors the server's rule (5-500 characters after trimming) so the owner sees the problem
// as they type instead of after a round trip.
export const REJECT_MIN = 5
export const REJECT_MAX = 500

export function rejectReasonError(text) {
  const len = (text ?? '').trim().length
  if (len < REJECT_MIN) return `Give a reason of at least ${REJECT_MIN} characters so the driver knows what to fix.`
  if (len > REJECT_MAX) return `Keep the reason to ${REJECT_MAX} characters or fewer.`
  return null
}
