import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useAuth } from '../../context/AuthContext'
import { pollUserPayments } from '../../utils/api'
import { createPaymentWatch } from './paymentWatch'

/**
 * One celebration popup for the whole app: a confetti burst, a tick that draws itself and the
 * Namma Journey car driving across a little road. Any page calls
 *
 *   const celebrate = useCelebrate()
 *   celebrate({ title: 'Trip booked', message: 'Coimbatore → Ooty', actionLabel: 'View trip', onAction })
 *
 * `tone: 'calm'` drops the confetti for moments that are good news but not yet final
 * (a payment sent but not yet confirmed).
 */

const CelebrateContext = createContext(() => {})
export const useCelebrate = () => useContext(CelebrateContext)

const EASE = [0.16, 1, 0.3, 1]
const CONFETTI = ['#f97316', '#2a78d6', '#1baf7a', '#eda100', '#e87ba4']

function Confetti() {
  const pieces = useMemo(() => Array.from({ length: 34 }, (_, i) => {
    const a = (i / 34) * Math.PI * 2 + (i % 3) * 0.2
    const d = 110 + (i % 6) * 22
    return {
      key: i,
      style: {
        background: CONFETTI[i % CONFETTI.length],
        '--x': `${Math.cos(a) * d}px`,
        '--y': `${Math.sin(a) * d - 70}px`,
        '--r': `${(i * 53) % 540}deg`,
        animationDelay: `${(i % 4) * 30}ms`,
        width: i % 3 ? 8 : 6,
        height: i % 3 ? 12 : 6,
        borderRadius: i % 3 ? 2 : 6,
      },
    }
  }), [])
  return <div className="cel-confetti" aria-hidden="true">{pieces.map((p) => <i key={p.key} style={p.style} />)}</div>
}

function MiniCar() {
  return (
    <svg className="cel-car" width="86" height="34" viewBox="0 0 86 34" aria-hidden="true">
      <path d="M4 25 V14 Q4 6 12 6 L52 5 Q58 5 62 9 L72 18 Q82 19 82 24 V25 Q82 28 79 28 H7 Q4 28 4 25 Z" fill="#f97316" />
      <path d="M4 22 H82 V25 Q82 28 79 28 H7 Q4 28 4 25 Z" fill="#c2410c" />
      <rect x="11" y="9" width="17" height="8" rx="2" fill="#d6ebf7" />
      <rect x="31" y="9" width="17" height="8" rx="2" fill="#d6ebf7" />
      <path d="M51 9 H57 Q59 9 61 11 L66 17 H51 Z" fill="#d6ebf7" />
      <rect x="15" y="1" width="30" height="4" rx="1.5" fill="#0f766e" />
      <g className="cel-wheel" style={{ transformOrigin: '20px 28px' }}><circle cx="20" cy="28" r="6" fill="#111827" /><circle cx="20" cy="28" r="2.6" fill="#e5e7eb" /></g>
      <g className="cel-wheel" style={{ transformOrigin: '66px 28px' }}><circle cx="66" cy="28" r="6" fill="#111827" /><circle cx="66" cy="28" r="2.6" fill="#e5e7eb" /></g>
      <rect x="78" y="18" width="4" height="3" rx="1" fill="#fef9c3" />
    </svg>
  )
}

function Popup({ item, onClose }) {
  const reduced = useReducedMotion()
  const btnRef = useRef(null)
  const party = item.tone !== 'calm'

  useEffect(() => {
    btnRef.current?.focus()
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    // Small confirmations (a saved profile) step aside by themselves; big moments wait for a click.
    const t = item.autoClose ? setTimeout(onClose, item.autoClose) : null
    return () => { document.removeEventListener('keydown', onKey); clearTimeout(t) }
  }, [onClose, item.autoClose])

  const act = () => {
    onClose()
    item.onAction?.()
  }

  return (
    <motion.div
      className="cel-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.25 }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <motion.div
        className={`cel-box${party ? ' is-party' : ' is-calm'}${reduced ? ' is-still' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cel-title"
        aria-describedby="cel-msg"
        initial={{ opacity: 0, y: 26, scale: 0.92 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 12, scale: 0.96 }}
        transition={{ duration: 0.5, ease: EASE }}
      >
        {party && !reduced && <Confetti />}
        <div className={`cel-badge${party ? '' : ' is-calm'}`} aria-hidden="true">
          {party ? (
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path className="cel-tick" d="M5 12l5 5 9-10" /></svg>
          ) : (
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
          )}
        </div>
        <h2 id="cel-title">{item.title}</h2>
        {item.message && <p id="cel-msg">{item.message}</p>}
        <div className="cel-road" aria-hidden="true">
          <MiniCar />
        </div>
        <div className="cel-actions">
          {item.onAction && item.actionLabel && (
            <button type="button" className="cel-btn" onClick={act} ref={btnRef}>{item.actionLabel}</button>
          )}
          <button type="button" className={item.onAction ? 'cel-btn cel-btn-ghost' : 'cel-btn'} onClick={onClose} ref={item.onAction ? undefined : btnRef}>
            {item.closeLabel || (item.onAction ? 'Close' : 'Great')}
          </button>
        </div>
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
  const celebrate = useCallback((item) => {
    setQueue((q) => [...q, { id: `${Date.now()}-${Math.random()}`, tone: 'party', ...item }])
  }, [])
  const close = useCallback(() => setQueue((q) => q.slice(1)), [])
  const current = queue[0]

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
