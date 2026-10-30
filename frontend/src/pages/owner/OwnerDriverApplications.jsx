import React, { useCallback, useEffect, useRef, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton } from '../../components/dash/ui'
import { getDriverApplications, approveDriver, rejectDriver } from '../../utils/api'
import { vehicleIcon, vehicleLabel } from '../../dash/vehicles'
import { rejectReasonError, REJECT_MAX } from '../../utils/rejectReason'

const navItems = [
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

const TABS = [
  ['UNDER_REVIEW', 'Waiting'],
  ['REJECTED', 'Rejected'],
  ['APPROVED', 'Approved'],
]

const DOCS = [
  ['photo', 'Driver photo'],
  ['licensePhoto', 'License'],
  ['aadhaarPhoto', 'Aadhaar'],
  ['rcPhoto', 'RC book'],
]

const capsStyle = { fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.03em' }

const imgUrl = (path) => {
  if (!path) return null
  if (path.startsWith('http')) return path
  return path.startsWith('/') ? path : `/${path}`
}

const errorText = (e, fallback) => e.response?.data?.message || e.response?.data?.error || fallback

const FOCUSABLE = 'button:not([disabled]), textarea, input, [href], [tabindex]:not([tabindex="-1"])'

/**
 * Shared modal behaviour: focus moves inside on open and returns to whatever opened it on close,
 * Escape closes, and Tab stays within the dialog.
 */
function useDialog(onClose) {
  const ref = useRef(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    const opener = document.activeElement
    const node = ref.current
    const first = node?.querySelector('[data-autofocus]') || node?.querySelector(FOCUSABLE)
    first?.focus()
    const onKey = (e) => {
      if (e.key === 'Escape') { e.stopPropagation(); closeRef.current() }
      if (e.key === 'Tab' && node) {
        const items = [...node.querySelectorAll(FOCUSABLE)]
        if (!items.length) return
        const a = items[0]; const z = items[items.length - 1]
        if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus() }
        else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus() }
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      if (opener && document.contains(opener)) opener.focus()
    }
  }, [])
  return ref
}

function DocumentViewer({ doc, onClose }) {
  const ref = useDialog(onClose)
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div ref={ref} className="modal-box" role="dialog" aria-modal="true" aria-label={`${doc.label} — ${doc.name}`}
        onClick={(e) => e.stopPropagation()} style={{ maxWidth: 820, padding: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginBottom: 12 }}>
          <strong style={{ color: 'var(--pc-ink)' }}>{doc.label} — {doc.name}</strong>
          <button type="button" data-autofocus onClick={onClose} aria-label="Close" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ width: 34, padding: 0 }}><Icon name="x" size={16} /></button>
        </div>
        <img src={doc.src} alt={`${doc.label} of ${doc.name}`} style={{ display: 'block', maxWidth: '100%', maxHeight: '75vh', margin: '0 auto', borderRadius: 10 }} />
      </div>
    </div>
  )
}

function RejectDialog({ driver, busy, error, onSubmit, onClose }) {
  const ref = useDialog(() => { if (!busy) onClose() })
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const problem = rejectReasonError(reason)
  const len = reason.trim().length
  return (
    <div className="modal-overlay">
      <div ref={ref} className="modal-box" role="dialog" aria-modal="true" aria-labelledby="reject-title" style={{ maxWidth: 460, padding: 24 }}>
        <h3 id="reject-title" style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)', marginBottom: 6 }}>Reject {driver.name}?</h3>
        <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', marginBottom: 14 }}>The driver sees this reason and can fix the problem and send the application again.</p>
        <label htmlFor="reject-reason" style={{ display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }}>Reason</label>
        <textarea id="reject-reason" data-autofocus className="input-field" rows={4} value={reason}
          onChange={(e) => { setReason(e.target.value); setTouched(true) }}
          aria-invalid={touched && !!problem} aria-describedby="reject-hint" style={{ width: '100%', resize: 'vertical' }} />
        <div id="reject-hint" style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginTop: 6, fontSize: 12, color: touched && problem ? 'var(--pc-bad)' : 'var(--pc-muted)' }}>
          <span role={touched && problem ? 'alert' : undefined}>{touched && problem ? problem : 'Say what is wrong, e.g. "License photo is blurry".'}</span>
          <span>{len}/{REJECT_MAX}</span>
        </div>
        {error && <div className="pc-error" role="alert" style={{ marginTop: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
        <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
          <button type="button" className="pc-btn pc-btn-ghost" style={{ flex: 1 }} disabled={busy} onClick={onClose}>Cancel</button>
          <button type="button" className="pc-btn" style={{ flex: 1, background: 'var(--pc-bad)', boxShadow: 'none' }} disabled={busy || !!problem} onClick={() => onSubmit(reason.trim())}>
            {busy ? 'Rejecting…' : 'Reject application'}
          </button>
        </div>
      </div>
    </div>
  )
}

function ApplicationCard({ app, tab, busy, onApprove, onReject, onView }) {
  const docs = DOCS.map(([key, label]) => ({ key, label, src: imgUrl(app[key]) }))
  const submitted = app.submittedAt ? new Date(app.submittedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
  return (
    <article style={{ padding: '16px 0', borderTop: '1px solid var(--pc-line)' }} aria-label={`Application from ${app.name}`}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 0 }}>
          <div className="pc-row-title" style={{ fontSize: 16 }}>{app.name}</div>
          <div style={{ fontSize: 12, color: 'var(--pc-muted)', overflowWrap: 'anywhere' }}>{app.mobile} · {app.email || '—'}</div>
          <div style={{ fontSize: 12, color: 'var(--pc-muted)' }}>Sent {submitted}</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, flexWrap: 'wrap' }}>
          <span className="pc-pill" style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <Icon name={vehicleIcon(app.vehicleType)} size={14} />{vehicleLabel(app.vehicleType)}
          </span>
          {app.vehicleNumber && <span className="pc-pill">{app.vehicleNumber}</span>}
          {tab === 'APPROVED' && <span className="pc-pill pc-pill-ok">Approved</span>}
          {tab === 'REJECTED' && <span className="pc-pill pc-pill-stop">Rejected</span>}
          {tab === 'UNDER_REVIEW' && (app.autoApproveInMinutes != null
            ? <span className="pc-pill" title="Approved automatically if its checks pass, unless you reject it">
                <Icon name="clock" size={13} /> Auto-approves {app.autoApproveInMinutes > 0 ? `in ${app.autoApproveInMinutes} min` : 'now'}
              </span>
            : <span className="pc-pill" title="Held, failed an automatic check, or re-uploaded after a rejection">Needs your decision</span>)}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', margin: '12px 0', fontSize: 13, color: 'var(--pc-ink)' }}>
        <span><span style={capsStyle}>License </span><strong>{app.licenseNumber || '—'}</strong></span>
        <span><span style={capsStyle}>Aadhaar </span><strong>{app.aadhaarNumber || '—'}</strong></span>
        {app.vehicleModel && <span><span style={capsStyle}>Model </span><strong>{app.vehicleModel}</strong></span>}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(120px,1fr))', gap: 10 }}>
        {docs.map((d) => (
          <div key={d.key} style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }}>{d.label}</div>
            {d.src ? (
              <button type="button" onClick={() => onView({ ...d, name: app.name })} aria-label={`View ${d.label} of ${app.name} full size`}
                style={{ display: 'block', width: '100%', padding: 0, border: 0, background: 'none', cursor: 'zoom-in' }}>
                <img src={d.src} alt={`${d.label} of ${app.name}`} style={{ width: '100%', height: 90, objectFit: 'cover', borderRadius: 10, border: '1px solid var(--pc-line)' }} />
              </button>
            ) : (
              <div style={{ height: 90, borderRadius: 10, background: 'var(--pc-wash)', color: 'var(--pc-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12 }}>Not uploaded</div>
            )}
          </div>
        ))}
      </div>

      {tab === 'REJECTED' && app.rejectionReason && (
        <p style={{ marginTop: 12, fontSize: 13, color: 'var(--pc-ink-2)' }}><strong>Reason given:</strong> {app.rejectionReason}</p>
      )}

      {tab === 'UNDER_REVIEW' && (
        <div style={{ display: 'flex', gap: 10, marginTop: 14, flexWrap: 'wrap' }}>
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy} onClick={() => onApprove(app)}>
            <Icon name="check" size={14} />{busy ? 'Working…' : 'Approve'}
          </button>
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }} disabled={busy} onClick={() => onReject(app)}>
            <Icon name="x" size={14} />Reject
          </button>
        </div>
      )}
    </article>
  )
}

export default function OwnerDriverApplications() {
  const [tab, setTab] = useState('UNDER_REVIEW')
  const [apps, setApps] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [waiting, setWaiting] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [notice, setNotice] = useState(null) // { tone: 'ok' | 'error', text }
  const [viewing, setViewing] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [rejectError, setRejectError] = useState('')
  const tabRef = useRef(tab)
  tabRef.current = tab

  const load = useCallback(async (which) => {
    setLoadError('')
    try {
      const r = await getDriverApplications(which)
      if (tabRef.current !== which) return // the owner moved on; don't paint a stale tab
      const list = r.data || []
      setApps(list)
      if (which === 'UNDER_REVIEW') setWaiting(list.length)
    } catch (e) {
      if (tabRef.current !== which) return
      setApps([])
      setLoadError(errorText(e, 'Could not load applications.'))
    }
    if (which !== 'UNDER_REVIEW') {
      try {
        const r = await getDriverApplications('UNDER_REVIEW')
        setWaiting((r.data || []).length)
      } catch { /* the count is a convenience; the list already reports errors */ }
    }
  }, [])

  useEffect(() => { setApps(null); load(tab) }, [tab, load])

  const decide = async (app, action, onDone) => {
    setBusyId(app.driverId); setNotice(null)
    try {
      await action()
      setNotice({ tone: 'ok', text: onDone })
      setRejecting(null)
    } catch (e) {
      const text = errorText(e, 'Could not save that decision.')
      // 409: someone else decided first. Show it and fall through to a refresh so the card is current.
      if (rejecting) setRejectError(text)
      if (e.response?.status === 409) setRejecting(null)
      setNotice({ tone: 'error', text })
    } finally {
      setBusyId(null)
      await load(tabRef.current)
    }
  }

  const approve = (app) => decide(app, () => approveDriver(app.driverId), `${app.name} is approved and can go online.`)
  const reject = (reason) => {
    setRejectError('')
    return decide(rejecting, () => rejectDriver(rejecting.driverId, reason), `${rejecting.name} was rejected. They can fix it and apply again.`)
  }

  const emptyText = { UNDER_REVIEW: 'No applications are waiting.', REJECTED: 'No rejected applications.', APPROVED: 'No approved applications yet.' }[tab]

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Driver Applications" sub={waiting == null ? 'Review drivers who signed up on their own' : `${waiting} waiting for review`} />

      <div className="pc-chips" role="tablist" aria-label="Application status" style={{ marginBottom: 18 }}>
        {TABS.map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tab === key} className="pc-chip"
            onClick={() => { setNotice(null); setTab(key) }}
            style={{ border: 'none', cursor: 'pointer', ...(tab === key ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}>
            {label}{key === 'UNDER_REVIEW' && waiting != null ? ` (${waiting})` : ''}
          </button>
        ))}
      </div>

      {notice && (
        <div className={notice.tone === 'ok' ? 'alert-success' : 'pc-error'} role={notice.tone === 'ok' ? 'status' : 'alert'} style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
          <Icon name={notice.tone === 'ok' ? 'check' : 'alert'} size={16} /><span>{notice.text}</span>
        </div>
      )}

      <div className="pc-grid">
        {apps == null ? (
          <Panel span={12} title="Applications"><Skeleton rows={4} height={18} /></Panel>
        ) : loadError ? (
          <Panel span={12}><div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{loadError}</span></div></Panel>
        ) : apps.length === 0 ? (
          <Panel span={12}><Empty icon="id" title={emptyText}>{tab === 'UNDER_REVIEW' ? 'New sign-ups appear here, oldest first.' : ''}</Empty></Panel>
        ) : (
          <Panel span={12} title={TABS.find(([k]) => k === tab)[1]} meta={`${apps.length}`}>
            {apps.map((a) => (
              <ApplicationCard key={a.driverId} app={a} tab={tab} busy={busyId === a.driverId}
                onApprove={approve} onReject={(x) => { setRejectError(''); setRejecting(x) }} onView={setViewing} />
            ))}
          </Panel>
        )}
      </div>

      {viewing && <DocumentViewer doc={viewing} onClose={() => setViewing(null)} />}
      {rejecting && <RejectDialog driver={rejecting} busy={busyId === rejecting.driverId} error={rejectError} onSubmit={reject} onClose={() => setRejecting(null)} />}
    </DashboardLayout>
  )
}
