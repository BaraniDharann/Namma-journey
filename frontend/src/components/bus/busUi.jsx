import React from 'react'
import Icon from '../dash/Icon'

// Shared bits for the owner and admin bus screens, in the same look as the other owner pages.

// The owner rail. DashboardLayout adds the bus items (and drops car items) by fleet type.
export const ownerNavItems = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/driver-applications', icon: '', label: 'Applications' },
  { path: '/owner/cities', icon: '', label: 'Cities' },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/payouts', icon: '', label: 'Payouts' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
]

export const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
export const capsStyle = { fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.03em' }
export const wellStyle = { padding: '12px 14px', borderRadius: 12, background: 'var(--pc-wash)', border: '1px solid var(--pc-line)' }
export const fieldGrid = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 14 }
export const mutedStyle = { fontSize: 12, color: 'var(--pc-muted)' }
export const dangerBtn = { background: 'var(--pc-bad)', boxShadow: 'none' }

/** Status as label + dot, from one of the status tables in utils/busText. */
export function BusPill({ table, status }) {
  const [tone, text] = table[status] || ['wait', status || '—']
  return <span className={`pc-pill pc-pill-${tone}`}>{text}</span>
}

/** Success or error line above a form. */
export function Notice({ notice, onClose }) {
  if (!notice) return null
  const ok = notice.tone === 'ok'
  return (
    <div className={ok ? 'alert-success' : 'pc-error'} role={ok ? 'status' : 'alert'}
      style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
      <Icon name={ok ? 'check' : 'alert'} size={16} /><span style={{ flex: 1 }}>{notice.text}</span>
      {onClose && (
        <button type="button" onClick={onClose} aria-label="Dismiss" style={{ color: 'inherit', background: 'none', border: 'none', cursor: 'pointer', display: 'flex', padding: 4 }}>
          <Icon name="x" size={14} />
        </button>
      )}
    </div>
  )
}

/** Wizard steps as tabs; a step is reachable once `enabled(index)` says so. */
export function Steps({ steps, step, onStep, enabled = () => true }) {
  return (
    <div className="pc-chips" role="tablist" aria-label="Steps" style={{ marginBottom: 18 }}>
      {steps.map((label, i) => (
        <button key={label} type="button" role="tab" aria-selected={step === i} className="pc-chip" disabled={!enabled(i)}
          onClick={() => onStep(i)}
          style={{ border: 'none', cursor: enabled(i) ? 'pointer' : 'not-allowed', opacity: enabled(i) ? 1 : 0.5, ...(step === i ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}>
          {i + 1}. {label}
        </button>
      ))}
    </div>
  )
}

/** Tabs for list filters, styled like the other owner and admin pages. */
export function Tabs({ tabs, value, onChange, label }) {
  return (
    <div className="pc-chips" role="tablist" aria-label={label} style={{ marginBottom: 18 }}>
      {tabs.map(([key, text]) => (
        <button key={key} type="button" role="tab" aria-selected={value === key} className="pc-chip" onClick={() => onChange(key)}
          style={{ border: 'none', cursor: 'pointer', ...(value === key ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}>
          {text}
        </button>
      ))}
    </div>
  )
}
