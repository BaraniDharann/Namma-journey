import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useAuth } from '../../context/AuthContext'
import { pollUserPayments } from '../../utils/api'
import { createPaymentWatch } from './paymentWatch'

/**
 * One success popup for the whole app, GPay style. Every successful POST / PUT / DELETE raises it
 * by itself (api.js → 'nj:action-success'); a page that wants its own words calls
 *
 *   const celebrate = useCelebrate()
 *   celebrate({ title: 'Trip booked', message: 'Coimbatore → Ooty', actionLabel: 'View trip', onAction })
 *
 * or showSuccess(title, message) from utils/actionSuccess, and that replaces the generic popup the
 * same call raised, so one action shows one popup. `tone: 'calm'` is for good news that is not
 * final yet (a payment sent, not confirmed).
 */

const CelebrateContext = createContext(() => {})
export const useCelebrate = () => useContext(CelebrateContext)

const EASE = [0.16, 1, 0.3, 1]
const AUTO_CLOSE_MS = 2200

/**
 * The GPay-style "done" moment: a solid circle pops in with a little overshoot, two ripples
 * spread out from it and a white tick draws itself; then the words rise in. Good news that is
 * not final yet (`tone: 'calm'`) gets an amber clock instead of the tick.
 */
function Popup({ item, onClose }) {
  const reduced = useReducedMotion()
  const btnRef = useRef(null)
  const calm = item.tone === 'calm'
  // Long enough to read: the base, plus a little per character of the message.
  const autoClose = item.autoClose ?? (item.onAction ? null
    : Math.min(4800, AUTO_CLOSE_MS + 35 * String(item.message || '').length))

  useEffect(() => {
    btnRef.current?.focus({ preventScroll: true })
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    const t = autoClose ? setTimeout(onClose, autoClose) : null
    return () => { document.removeEventListener('keydown', onKey); clearTimeout(t) }
  }, [onClose, autoClose])

  const act = () => {
    onClose()
    item.onAction?.()
  }

  return (
    <motion.div
      className="cel-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, transition: { duration: 0.18 } }}
      transition={{ duration: 0.22 }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <motion.div
        className={`cel-box${calm ? ' is-calm' : ''}${reduced ? ' is-still' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cel-title"
        aria-describedby={item.message ? 'cel-msg' : undefined}
        initial={{ opacity: 0, y: 18, scale: 0.94 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 8, scale: 0.97, transition: { duration: 0.16 } }}
        transition={{ duration: 0.38, ease: EASE }}
      >
        <div className="cel-mark" aria-hidden="true">
          <span className="cel-ripple" />
          <span className="cel-ripple cel-ripple-2" />
          <span className="cel-disc">
            {calm ? (
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="8.5" /><path className="cel-hand" d="M12 7.5V12l3 2" /></svg>
            ) : (
              <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path className="cel-tick" d="M5.5 12.5l4.2 4.2L18.5 8" /></svg>
            )}
          </span>
        </div>
        <h2 id="cel-title" className="cel-rise">{item.title}</h2>
        {item.message && <p id="cel-msg" className="cel-rise cel-rise-2">{item.message}</p>}
        <div className="cel-actions cel-rise cel-rise-3">
          {item.onAction && item.actionLabel && (
            <button type="button" className="cel-btn" onClick={act} ref={btnRef}>{item.actionLabel}</button>
          )}
          <button type="button" className={item.onAction ? 'cel-btn cel-btn-ghost' : 'cel-btn'} onClick={onClose} ref={item.onAction ? undefined : btnRef}>
            {item.closeLabel || (item.onAction ? 'Close' : 'Done')}
          </button>
        </div>
        {autoClose && !reduced && <span className="cel-timer" style={{ animationDuration: `${autoClose}ms` }} aria-hidden="true" />}
      </motion.div>
    </motion.div>
  )
}

/** Polls a traveller's payments while any "I've paid" is waiting, and celebrates verification. */
function PaymentWatcher({ celebrate }) {
  const { user } = useAuth()
  const userId = user?.role === 'ROLE_USER' ? user.userId : null
  useEffect(() => {
    if (!userId) return undefined
    const watch = createPaymentWatch(() => window.localStorage, userId)
    let alive = true
    const tick = async () => {
      if (!watch.pending().length) return
      try {
        const res = await pollUserPayments(userId)
        if (!alive) return
        watch.settle(res.data || []).forEach((p) => celebrate({
          title: 'Payment confirmed',
          message: `₹${Math.round(p.amount).toLocaleString('en-IN')} received${p.label ? ` · ${p.label}` : ''}. Your trip is all set.`,
        }))
      } catch { /* quiet: the next tick tries again */ }
    }
    tick()
    const id = setInterval(tick, 10000)
    return () => { alive = false; clearInterval(id) }
  }, [userId, celebrate])
  return null
}

/** Remember a UPI payment the traveller says they sent, for the watcher to confirm later. */
export function rememberPayment(userId, payment) {
  createPaymentWatch(() => window.localStorage, userId).remember(payment)
}

export function CelebrationProvider({ children }) {
  const [queue, setQueue] = useState([])
  // A page's own popup replaces the generic one its API call just raised (and any still waiting);
  // generic popups replace each other, so a save that makes three calls shows one.
  const push = useCallback((item, auto) => {
    const next = { id: `${Date.now()}-${Math.random()}`, tone: 'party', ...item, auto }
    setQueue((q) => [...q.filter((i) => !i.auto), next])
  }, [])
  const celebrate = useCallback((item) => push(item, false), [push])
  const close = useCallback(() => setQueue((q) => q.slice(1)), [])
  const current = queue[0]

  useEffect(() => {
    const onSuccess = (e) => {
      const d = e.detail || {}
      if (!d.title) return
      push({ title: d.title, message: d.message, tone: d.tone }, !d.explicit)
    }
    window.addEventListener('nj:action-success', onSuccess)
    return () => window.removeEventListener('nj:action-success', onSuccess)
  }, [push])

  return (
    <CelebrateContext.Provider value={celebrate}>
      {children}
      <PaymentWatcher celebrate={celebrate} />
      {typeof document !== 'undefined' && createPortal(
        <AnimatePresence>{current && <Popup key={current.id} item={current} onClose={close} />}</AnimatePresence>,
        document.body,
      )}
    </CelebrateContext.Provider>
  )
}
