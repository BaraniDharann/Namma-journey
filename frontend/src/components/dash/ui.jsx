import React, { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import Icon from './Icon'

/** A dashboard card. `span` is out of 12 columns on desktop; everything stacks on phones. */
export function Panel({ title, meta, action, span = 12, className = '', children, pad = true, ...rest }) {
  return (
    <section className={`pc-panel pc-span-${span} ${pad ? '' : 'pc-nopad'} ${className}`} {...rest}>
      {(title || meta || action) && (
        <header className="pc-panel-hd">
          {title && <h2>{title}</h2>}
          <div className="pc-panel-meta">
            {meta && <span>{meta}</span>}
            {action}
          </div>
        </header>
      )}
      {children}
    </section>
  )
}

export function PanelLink({ to, children }) {
  return <Link to={to} className="pc-link">{children}<Icon name="arrow" size={14} /></Link>
}

/** Page heading row: title, one-line context, and the page's primary actions. */
export function PageHead({ title, sub, children }) {
  return (
    <div className="pc-head">
      <div>
        <h1>{title}</h1>
        {sub && <p>{sub}</p>}
      </div>
      {children && <div className="pc-head-actions">{children}</div>}
    </div>
  )
}

/** Default chip tint for a summary figure, by its icon. */
const STRIP_TINT = { wallet: 'green', calendar: 'blue', car: 'teal', star: 'amber', route: 'violet', map: 'pink', users: 'violet', clock: 'amber', check: 'green', chart: 'blue', qr: 'violet', rupee: 'green' }

/** One strip of headline figures with hairline dividers, instead of identical tiles. */
export function SummaryStrip({ items, loading }) {
  return (
    <section className="pc-panel pc-span-12 pc-strip">
      {items.map((it) => (
        <div key={it.label}>
          <div className="pc-strip-l"><span className={`pc-tic pc-tint-${it.tint || STRIP_TINT[it.icon] || 'brand'}`}><Icon name={it.icon} size={20} /></span>{it.label}</div>
          <div className="pc-strip-v">{loading ? <span className="pc-skel" style={{ width: 90 }} /> : it.value}</div>
          <div className="pc-strip-s">{it.sub}</div>
        </div>
      ))}
    </section>
  )
}

/** "Needs you now": counts that link to the page that resolves them. */
export function TaskList({ tasks, tone = 'light' }) {
  const live = tasks.filter((t) => t.count > 0)
  if (!live.length) {
    return (
      <div className={`pc-tasks pc-tasks-${tone}`}>
        <div className="pc-task pc-task-done"><Icon name="check" /><span>All caught up. Nothing needs you right now.</span></div>
      </div>
    )
  }
  return (
    <div className={`pc-tasks pc-tasks-${tone}`}>
      {live.map((t) => (
        <Link key={t.label} to={t.to} className="pc-task">
          <Icon name={t.icon} />
          <b>{t.count}</b>
          <span>{t.label}</span>
          <em>{t.cta}<Icon name="arrow" size={14} /></em>
        </Link>
      ))}
    </div>
  )
}

const PILL = {
  PENDING: ['wait', 'Pending'],
  CONFIRMED: ['go', 'Confirmed'],
  STARTED: ['live', 'On the road'],
  COMPLETED: ['ok', 'Completed'],
  CANCELLED: ['stop', 'Cancelled'],
  VERIFIED: ['ok', 'Verified'],
  SUCCESS: ['ok', 'Paid'],
  PAID: ['ok', 'Paid'],
  FAILED: ['stop', 'Failed'],
  ACTIVE: ['ok', 'Active'],
  INACTIVE: ['stop', 'Inactive'],
}

/** Status shown as label + dot, never colour alone. */
export function StatusPill({ status, label }) {
  const [tone, text] = PILL[String(status || '').toUpperCase()] || ['wait', status || '—']
  return <span className={`pc-pill pc-pill-${tone}`}>{label || text}</span>
}

export function Empty({ icon = 'sparkle', title, children, action }) {
  return (
    <div className="pc-empty">
      <span className="pc-empty-ic"><Icon name={icon} size={22} /></span>
      {title && <h3>{title}</h3>}
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}

export function Skeleton({ rows = 3, height = 14 }) {
  return (
    <div className="pc-skel-stack">
      {Array.from({ length: rows }, (_, i) => (
        <span key={i} className="pc-skel" style={{ height, width: `${92 - i * 14}%` }} />
      ))}
    </div>
  )
}

export function ErrorNote({ children = "Couldn't load this right now." , onRetry }) {
  return (
    <div className="pc-error">
      <Icon name="alert" />
      <span>{children}</span>
      {onRetry && <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={onRetry}>Try again</button>}
    </div>
  )
}

/**
 * One floating tooltip for every chart: marks carry `data-tip="value|context"` and this layer
 * follows the pointer (and keyboard focus) over them.
 */
export function TipLayer() {
  const ref = useRef(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const show = (target, x, y) => {
      const [a, b] = target.dataset.tip.split('|')
      el.innerHTML = ''
      el.append(document.createTextNode(a))
      if (b) {
        const s = document.createElement('small')
        s.textContent = b
        el.append(s)
      }
      const w = el.offsetWidth
      el.style.left = Math.min(window.innerWidth - w - 8, x + 14) + 'px'
      el.style.top = Math.max(8, y - 44) + 'px'
      el.style.opacity = '1'
    }
    const hide = () => { el.style.opacity = '0' }
    const move = (e) => {
      const t = e.target.closest?.('[data-tip]')
      if (t) show(t, e.clientX, e.clientY)
      else hide()
    }
    const focus = (e) => {
      const t = e.target.closest?.('[data-tip]')
      if (!t) return
      const r = t.getBoundingClientRect()
      show(t, r.left + r.width / 2, r.top)
    }
    document.addEventListener('pointermove', move)
    document.addEventListener('focusin', focus)
    document.addEventListener('focusout', hide)
    document.addEventListener('scroll', hide, true)
    return () => {
      document.removeEventListener('pointermove', move)
      document.removeEventListener('focusin', focus)
      document.removeEventListener('focusout', hide)
      document.removeEventListener('scroll', hide, true)
    }
  }, [])
  return <div ref={ref} className="pc-tip" role="status" aria-live="off" />
}
