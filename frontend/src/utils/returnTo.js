// Where to go after signing in, when a public page sent the customer to /login mid-task (a bus
// booking). Only paths inside this app are accepted, so a crafted value cannot redirect away.

const KEY = 'nj_return_to'

export const safePath = (p) => (typeof p === 'string' && p.startsWith('/') && !p.startsWith('//') && !p.startsWith('/\\') ? p : null)

export function rememberReturn(path) {
  const p = safePath(path)
  try { if (p) sessionStorage.setItem(KEY, p) } catch { /* storage blocked: the login lands on the dashboard */ }
}

/** The remembered path, removed as it is read; null when there is none. */
export function takeReturn() {
  try {
    const p = safePath(sessionStorage.getItem(KEY))
    sessionStorage.removeItem(KEY)
    return p
  } catch {
    return null
  }
}

export function forgetReturn() {
  try { sessionStorage.removeItem(KEY) } catch { /* nothing to forget */ }
}
