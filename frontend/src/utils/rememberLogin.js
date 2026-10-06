/**
 * Helpers for the browser's own password manager. After a successful sign-in we hand the
 * credential to the Credential Management API (Chrome, Edge, Android), which raises the
 * browser's "Save password?" prompt; next visit, focusing the username field offers it back.
 * Browsers without the API (Firefox, Safari) fall back to their form-submit heuristics, which
 * work because every login form keeps real username / password fields.
 */
export async function offerToSaveLogin(id, password, name) {
  try {
    if (!id || !password || typeof window === 'undefined' || !window.PasswordCredential || !navigator.credentials?.store) return false
    await navigator.credentials.store(new window.PasswordCredential({ id, password, name: name || id }))
    return true
  } catch {
    return false
  }
}

const SKIP_KEY = 'nj_save_pw_skipped'

/** A traveller who said "Not now" is not asked again on this browser. */
export function wasSaveOfferSkipped(email) {
  try { return (JSON.parse(localStorage.getItem(SKIP_KEY)) || []).includes(String(email).toLowerCase()) } catch { return false }
}

export function skipSaveOffer(email) {
  try {
    const list = JSON.parse(localStorage.getItem(SKIP_KEY)) || []
    const e = String(email).toLowerCase()
    if (!list.includes(e)) localStorage.setItem(SKIP_KEY, JSON.stringify([...list, e]))
  } catch { /* storage blocked: we simply ask again next time */ }
}
