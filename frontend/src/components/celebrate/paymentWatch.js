/**
 * Remembers the UPI payments a traveller says they have sent ("I've paid"), so the app can
 * celebrate once the owner verifies them. Stored per traveller in localStorage; every access is
 * guarded because storage can be blocked or hold junk.
 */
export function createPaymentWatch(getStorage = () => window.localStorage, userId = 'anon') {
  const key = `nj_paid_${userId}`

  const read = () => {
    try {
      const raw = getStorage().getItem(key)
      const list = raw ? JSON.parse(raw) : []
      return Array.isArray(list) ? list.filter((p) => p && p.paymentId) : []
    } catch {
      return []
    }
  }
  const write = (list) => {
    try {
      if (list.length) getStorage().setItem(key, JSON.stringify(list))
      else getStorage().removeItem(key)
    } catch { /* storage blocked: the celebration is a nicety, not state */ }
  }

  return {
    pending: read,
    remember({ paymentId, amount, label }) {
      if (!paymentId) return
      const list = read()
      if (list.some((p) => p.paymentId === paymentId)) return
      write([...list, { paymentId, amount: Number(amount) || 0, label: label || '', at: Date.now() }])
    },
    /** Given the traveller's payments from the API, return the remembered ones now verified. */
    settle(payments = []) {
      const list = read()
      if (!list.length) return []
      const verified = new Map(
        payments.filter((p) => String(p.status).toUpperCase() === 'VERIFIED').map((p) => [p.paymentId, p]),
      )
      const done = list.filter((p) => verified.has(p.paymentId))
      if (!done.length) return []
      write(list.filter((p) => !verified.has(p.paymentId)))
      return done.map((p) => ({ ...p, amount: Number(verified.get(p.paymentId).amount) || p.amount }))
    },
  }
}
