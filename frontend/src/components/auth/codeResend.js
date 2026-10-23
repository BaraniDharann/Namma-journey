/**
 * A resend goes through driver login, which only emails a code while the email is still
 * unverified. Anything else means no code was sent, so say so instead of claiming it was.
 */
export function assertCodeResent(res) {
  if (res?.data?.emailUnverified !== true) {
    throw new Error('Your email is already confirmed. Please sign in.')
  }
  return res
}
