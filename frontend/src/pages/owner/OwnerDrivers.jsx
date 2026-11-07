import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import Pagination, { usePagination } from '../../components/Pagination'
import { Link } from 'react-router-dom'
import {
  createDriver, getOwnerDrivers, getOwnerDriverById, deleteOwnerDriver, createDriverTelegramLink, updateDriverVehicle,
  getIndependentDrivers, getOwnerVehicles, getDriverChecks, recordDriverCheck, approveDriver, rejectDriver, setDriverDefaultVehicle,
} from '../../utils/api'
import VehicleFields, { DEFAULT_VEHICLE } from '../../components/owner/VehicleFields'
import { vehicleIcon, vehicleLabel } from '../../dash/vehicles'
import { categoryLabel } from '../../utils/carCategories'
import { rejectReasonError, REJECT_MAX } from '../../utils/rejectReason'
import useOwnerMe from '../../hooks/useOwnerMe'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { useCelebrate } from '../../components/celebrate/Celebration'

const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback
const carText = (v) => (v ? `${v.registrationMissing ? 'No registration yet' : v.registrationNumber} · ${categoryLabel(v.category)}${v.model ? ` · ${v.model}` : ''}` : '')
const BLANK_DRIVER = { name: '', mobile: '', email: '', licenseNumber: '', aadhaarNumber: '', defaultVehicleId: '' }

// Each check is made by hand on a free official Parivahan site, opened in a new tab.
const CHECKS = [
  { type: 'LICENCE', label: 'Driving licence', site: 'Sarathi',
    how: (d) => `Search licence ${d.licenseNumber || ''} with the date of birth: it is valid, covers cars, and the name and photo match.`,
    link: 'https://sarathi.parivahan.gov.in/sarathiservice/dlSearch.do' },
  { type: 'RC', label: 'Registration (RC)', site: 'Vahan',
    how: (_d, car) => `Search ${car && !car.registrationMissing ? car.registrationNumber : 'the usual car\'s number'}: registered to you, with fitness, insurance and permit valid.`,
    link: 'https://vahan.parivahan.gov.in/nrservices/faces/user/citizen/searchstatus.xhtml' },
]

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const capsStyle = { fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.03em' }
const wellStyle = { padding: '12px 14px', borderRadius: 12, background: 'var(--pc-wash)', border: '1px solid var(--pc-line)' }
const avatarStyle = (size) => ({
  width: size, height: size, borderRadius: size / 3.4, background: 'var(--pc-brand-soft)', color: 'var(--pc-brand-deep)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size * 0.42, fontWeight: 800, flexShrink: 0,
})
const dangerBtn = { color: 'var(--pc-bad)' }

// An unlinked driver fails silently: trips are still assigned to them, they just never get
// the alert while driving — which is the exact problem this channel exists to fix. So the
// status is shown on the driver record, and connecting is one click away from it.
function TelegramLinkPanel({ driver }) {
  const [linkUrl, setLinkUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  const generate = async () => {
    setLoading(true); setError(''); setCopied(false)
    try {
      const res = await createDriverTelegramLink(driver.driverId)
      setLinkUrl(res.data.linkUrl)
    } catch (e) {
      setError(e.response?.data?.message || 'Could not create a link. Check the bot settings.')
    } finally {
      setLoading(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(linkUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setError('Copy failed — select the link and copy it manually.')
    }
  }

  return (
    <div style={{ ...wellStyle, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <span style={capsStyle}>Telegram Alerts</span>
        <span className={`pc-pill ${driver.telegramLinked ? 'pc-pill-ok' : 'pc-pill-wait'}`}>
          {driver.telegramLinked ? 'Connected' : 'Not connected'}
        </span>
      </div>

      {!driver.telegramLinked && (
        <p style={{ fontSize: 12, color: 'var(--pc-ink-2)', marginTop: 8 }}>
          This driver will not be alerted on their phone when a trip is assigned. Send them the link below to connect.
        </p>
      )}

      {error && <div className="pc-error" role="alert" style={{ marginTop: 10, fontSize: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}

      {linkUrl ? (
        <div style={{ marginTop: 10 }}>
          <div style={{ fontSize: 11, color: 'var(--pc-ink-2)', marginBottom: 6 }}>
            One-time link, valid 24 hours. Anyone who opens it can accept trips as this driver — send it only to them.
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <input readOnly value={linkUrl} aria-label="Telegram connect link" onFocus={(e) => e.target.select()}
              className="input-field" style={{ flex: 1, minWidth: 0, fontSize: 12 }} />
            <button type="button" onClick={copy} className="pc-btn pc-btn-ghost pc-btn-sm">
              <Icon name={copied ? 'check' : 'link'} size={14} />{copied ? 'Copied' : 'Copy'}
            </button>
          </div>
        </div>
      ) : (
        <button type="button" onClick={generate} disabled={loading} className="pc-btn pc-btn-ghost pc-btn-sm" style={{ marginTop: 10 }}>
          <Icon name="send" size={14} />
          {loading ? 'Creating link...' : driver.telegramLinked ? 'Create a new link' : 'Create connect link'}
        </button>
      )}
    </div>
  )
}

// The vehicle decides which rides reach a driver: bikes and autos get City rides, only cars get
// Trips. Drivers added before vehicles existed are recorded as cars, so this is where to correct it.
function VehiclePanel({ driver, onSaved }) {
  const pickVehicle = (d) => Object.fromEntries(Object.keys(DEFAULT_VEHICLE).map((k) => [k, d[k] ?? DEFAULT_VEHICLE[k]]))
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(() => pickVehicle(driver))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const independent = driver.fleet === 'INDEPENDENT'

  const save = async () => {
    if (independent && !String(draft.vehicleNumber || '').trim()) {
      setError('An independent driver needs a number plate'); return
    }
    setSaving(true); setError('')
    try {
      const res = await updateDriverVehicle(driver.driverId, draft)
      onSaved(res.data)
      setEditing(false)
    } catch (e) {
      setError(e.response?.data?.message || e.response?.data?.error || 'Could not save the vehicle')
    } finally { setSaving(false) }
  }

  return (
    <div style={{ ...wellStyle, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <span style={capsStyle}>Vehicle</span>
        {!editing && (
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => { setDraft(pickVehicle(driver)); setEditing(true) }}>
            <Icon name="edit" size={14} />Edit
          </button>
        )}
      </div>
      {editing ? (
        <div style={{ marginTop: 12 }}>
          <VehicleFields idPrefix={`veh-${driver.driverId}`} hideTrips={independent} value={draft} onChange={(patch) => setDraft((p) => ({ ...p, ...patch }))} />
          {error && <div className="pc-error" role="alert" style={{ marginTop: 10, fontSize: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" disabled={saving} onClick={() => setEditing(false)}>Cancel</button>
            <button type="button" className="pc-btn pc-btn-sm" disabled={saving} onClick={save}><Icon name="check" size={14} />{saving ? 'Saving…' : 'Save vehicle'}</button>
          </div>
        </div>
      ) : (
        <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink)' }}>
          <Icon name={vehicleIcon(driver.vehicleType)} size={20} />
          {vehicleLabel(driver.vehicleType)}
          {driver.vehicleModel && <span style={{ color: 'var(--pc-ink-2)' }}>{driver.vehicleModel}</span>}
          <span style={{ color: 'var(--pc-ink-2)' }}>{driver.vehicleNumber || 'No number plate yet'}</span>
          {driver.vehicleType === 'CAR' && <span style={{ color: 'var(--pc-ink-2)' }}>{driver.vehicleSeats} seats · {driver.vehicleAc ? 'AC' : 'Non-AC'}</span>}
          <span style={{ display: 'flex', gap: 6 }}>
            {driver.servesCity && <span className="pc-pill pc-pill-go">City</span>}
            {driver.servesTrip && <span className="pc-pill pc-pill-ok">Trips</span>}
          </span>
        </div>
      )}
    </div>
  )
}

// The car a driver normally takes: AUTO assignment prefers it, and the assign dialog pre-selects it.
function UsualCarPanel({ driver, vehicles, onSaved }) {
  const [vehicleId, setVehicleId] = useState(driver.defaultVehicleId ? String(driver.defaultVehicleId) : '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const usable = vehicles.filter((v) => v.active || v.id === driver.defaultVehicleId)
  const changed = vehicleId !== (driver.defaultVehicleId ? String(driver.defaultVehicleId) : '')

  const save = async () => {
    setSaving(true); setError('')
    try {
      const res = await setDriverDefaultVehicle(driver.driverId, Number(vehicleId))
      onSaved(res.data)
    } catch (e) {
      setError(errorText(e, 'Could not save the usual car'))
    } finally { setSaving(false) }
  }

  return (
    <div style={{ ...wellStyle, marginBottom: 16 }}>
      <label htmlFor={`usual-car-${driver.driverId}`} style={capsStyle}>Usual car</label>
      {usable.length === 0 ? (
        <p style={{ fontSize: 12.5, color: 'var(--pc-ink-2)', marginTop: 8 }}>No cars in use yet. <Link to="/owner/vehicles">Add a car</Link> first.</p>
      ) : (
        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <select id={`usual-car-${driver.driverId}`} className="input-field" style={{ flex: 1, minWidth: 0 }} value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
            <option value="">None</option>
            {usable.map((v) => <option key={v.id} value={v.id}>{carText(v)}</option>)}
          </select>
          <button type="button" className="pc-btn pc-btn-sm" disabled={saving || !changed || !vehicleId} onClick={save}>
            <Icon name="check" size={14} />{saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      )}
      {error && <div className="pc-error" role="alert" style={{ marginTop: 10, fontSize: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
    </div>
  )
}

function CheckRow({ check, driver, car, value, onSave }) {
  const [note, setNote] = useState(value?.note || '')
  const [busy, setBusy] = useState(false)
  const save = async (result) => { setBusy(true); try { await onSave(check.type, result, note) } finally { setBusy(false) } }
  const state = value?.result
  return (
    <div style={{ padding: '12px 0', borderTop: '1px solid var(--pc-line)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
        <strong style={{ fontSize: 13.5 }}>{check.label}</strong>
        <span className={`pc-pill ${state === 'VERIFIED' ? 'pc-pill-ok' : state === 'FAILED' ? 'pc-pill-stop' : ''}`}>
          {state === 'VERIFIED' ? 'Verified' : state === 'FAILED' ? 'Failed' : 'Not checked'}
        </span>
      </div>
      <p style={{ fontSize: 12.5, color: 'var(--pc-ink-2)', margin: '6px 0' }}>{check.how(driver, car)}</p>
      {state === 'FAILED' && value?.note && <p style={{ fontSize: 12, color: 'var(--pc-bad)', margin: '0 0 6px' }}>{value.note}</p>}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <a className="pc-btn pc-btn-ghost pc-btn-sm" href={check.link} target="_blank" rel="noopener noreferrer"><Icon name="link" size={14} />Open {check.site}</a>
        <input className="input-field" style={{ flex: '1 1 160px', minWidth: 0 }} placeholder="Note (required if failed)" value={note}
          maxLength={500} aria-label={`${check.label} note`} onChange={(e) => setNote(e.target.value)} />
        <button type="button" className="pc-btn pc-btn-sm" disabled={busy} onClick={() => save('VERIFIED')}><Icon name="check" size={14} />Verified</button>
        <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !note.trim()} style={{ background: 'var(--pc-bad)', boxShadow: 'none' }}
          onClick={() => save('FAILED')}><Icon name="x" size={14} />Failed</button>
      </div>
    </div>
  )
}

// A driver I added waits here: check the licence and RC on Parivahan, then approve (which emails their login) or reject.
function ReviewPanel({ driver, car, onUpdated }) {
  const [checks, setChecks] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  useEffect(() => {
    getDriverChecks(driver.driverId)
      .then((r) => setChecks(Object.fromEntries((r.data || []).map((c) => [c.type, c]))))
      .catch(() => setChecks({}))
  }, [driver.driverId])

  const saveCheck = async (type, result, note) => {
    setError('')
    try {
      const res = await recordDriverCheck(driver.driverId, type, result, note.trim() || null)
      setChecks((c) => ({ ...c, [type]: res.data }))
    } catch (e) { setError(errorText(e, 'Could not save that check')) }
  }

  const decide = async (fn) => {
    setBusy(true); setError('')
    try {
      const res = await fn()
      onUpdated(res.data)
    } catch (e) {
      setError(errorText(e, 'Could not save that decision'))
    } finally { setBusy(false) }
  }

  const ready = CHECKS.every((c) => checks?.[c.type]?.result === 'VERIFIED')
  const reasonProblem = rejectReasonError(reason)

  if (driver.verificationStatus === 'REJECTED') {
    return (
      <div style={{ ...wellStyle, marginBottom: 16 }}>
        <span style={capsStyle}>Rejected</span>
        <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', marginTop: 6 }}>{driver.rejectionReason || 'No reason recorded.'}</p>
      </div>
    )
  }

  return (
    <div style={{ ...wellStyle, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <span style={capsStyle}>Checks before approval</span>
        <span className="pc-pill pc-pill-wait">No login yet</span>
      </div>
      <p style={{ fontSize: 12, color: 'var(--pc-ink-2)', marginTop: 6 }}>Approving emails the driver their login. Both checks must be verified first.</p>
      {!checks ? <div style={{ marginTop: 10 }}><Skeleton rows={2} /></div>
        : CHECKS.map((c) => <CheckRow key={c.type} check={c} driver={driver} car={car} value={checks[c.type]} onSave={saveCheck} />)}
      {error && <div className="pc-error" role="alert" style={{ marginTop: 10, fontSize: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--pc-line)' }}>
        <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !ready} onClick={() => decide(() => approveDriver(driver.driverId, { silent: true }))}>
          <Icon name="check" size={14} />{busy ? 'Saving…' : 'Approve and email login'}
        </button>
        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" disabled={busy} style={dangerBtn} onClick={() => setRejecting((r) => !r)}>
          <Icon name="x" size={14} />Reject
        </button>
      </div>
      {rejecting && (
        <div style={{ marginTop: 12 }}>
          <label htmlFor={`reject-${driver.driverId}`} style={labelStyle}>Reason</label>
          <textarea id={`reject-${driver.driverId}`} className="input-field" rows={3} value={reason} maxLength={REJECT_MAX}
            onChange={(e) => setReason(e.target.value)} style={{ width: '100%', resize: 'vertical' }} />
          {reason && reasonProblem && <p style={{ fontSize: 12, color: 'var(--pc-bad)', marginTop: 4 }}>{reasonProblem}</p>}
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !!reasonProblem} style={{ marginTop: 8, background: 'var(--pc-bad)', boxShadow: 'none' }}
            onClick={() => decide(() => rejectDriver(driver.driverId, reason.trim(), { silent: true }))}>
            Reject driver
          </button>
        </div>
      )}
    </div>
  )
}

function DriverDetailModal({ driver, vehicles, onClose, onUpdated }) {
  const imgUrl = (path) => {
    if (!path) return null
    if (path.startsWith('http')) return path
    return path.startsWith('/') ? path : `/${path}`
  }

  return (
    <div className="modal-overlay">
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="driver-detail-title" style={{ maxWidth: 520, overflow: 'hidden', padding: 0 }}>
        <div style={{ padding: '20px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--pc-line)' }}>
          <h3 id="driver-detail-title" style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)' }}>Driver Details</h3>
          <button type="button" onClick={onClose} aria-label="Close" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ width: 34, padding: 0 }}><Icon name="x" size={16} /></button>
        </div>
        <div style={{ padding: 24 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
            {imgUrl(driver.photo)
              ? <img src={imgUrl(driver.photo)} alt="Driver" style={{ width: 64, height: 64, borderRadius: 16, objectFit: 'cover', border: '1px solid var(--pc-line)' }} />
              : <div style={avatarStyle(64)}>{driver.name?.[0]?.toUpperCase()}</div>
            }
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 800, fontSize: 17, color: 'var(--pc-ink)' }}>{driver.name}</div>
              <div style={{ fontSize: 13, color: 'var(--pc-ink-2)', overflowWrap: 'anywhere' }}>{driver.mobile} · {driver.email}</div>
              <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <StatusPill status={driver.status} label={driver.status} />
                {driver.emailVerified && <span className="pc-pill pc-pill-ok">Verified</span>}
                {driver.telegramLinked && <span className="pc-pill pc-pill-go">Telegram</span>}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
            {[['License Number', driver.licenseNumber], ['Aadhaar Number', driver.aadhaarNumber], ['Joined', driver.createdAt ? new Date(driver.createdAt).toLocaleDateString('en-IN') : '—']].map(([k, v]) => (
              <div key={k} style={{ ...wellStyle, padding: '10px 14px', display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span style={capsStyle}>{k}</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--pc-ink)' }}>{v || '—'}</span>
              </div>
            ))}
          </div>
          {driver.fleet === 'INDEPENDENT' ? <VehiclePanel driver={driver} onSaved={onUpdated} /> : (
            <>
              {driver.verificationStatus && driver.verificationStatus !== 'APPROVED' && (
                <ReviewPanel driver={driver} car={vehicles.find((v) => v.id === driver.defaultVehicleId)} onUpdated={onUpdated} />
              )}
              <UsualCarPanel key={driver.defaultVehicleId} driver={driver} vehicles={vehicles} onSaved={onUpdated} />
            </>
          )}
          <TelegramLinkPanel driver={driver} />
          <div className="driver-image-row" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10 }}>
            {[['photo', 'user', 'Driver Photo'], ['licensePhoto', 'card', 'License'], ['aadhaarPhoto', 'shield', 'Aadhaar']].map(([key, icon, label]) => (
              <div key={key} style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }}>{label}</div>
                {imgUrl(driver[key])
                  ? <img src={imgUrl(driver[key])} alt={label} style={{ width: '100%', height: 80, objectFit: 'cover', borderRadius: 10, border: '1px solid var(--pc-line)' }} />
                  : <div style={{ height: 80, borderRadius: 10, background: 'var(--pc-wash)', color: 'var(--pc-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center' }} title={`No ${label.toLowerCase()} uploaded`}><Icon name={icon} size={24} /></div>
                }
              </div>
            ))}
          </div>
          <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={{ width: '100%', marginTop: 16 }}>Close</button>
        </div>
      </div>
    </div>
  )
}

function DeleteConfirmModal({ driver, deleting, error, onConfirm, onClose }) {
  return (
    <div className="modal-overlay">
      <div className="modal-box" role="alertdialog" aria-modal="true" aria-labelledby="delete-driver-title" style={{ maxWidth: 420, padding: 24 }}>
        <div style={{ width: 56, height: 56, borderRadius: 16, background: 'var(--pc-bad-soft)', color: 'var(--pc-bad)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}><Icon name="trash" size={26} /></div>
        <h3 id="delete-driver-title" style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)', textAlign: 'center', marginBottom: 8 }}>Delete Driver?</h3>
        <p style={{ fontSize: 14, color: 'var(--pc-ink-2)', textAlign: 'center', marginBottom: 16 }}>
          Are you sure you want to permanently delete <strong style={{ color: 'var(--pc-ink)' }}>{driver.name}</strong> ({driver.mobile})? This cannot be undone.
        </p>
        {error && <div className="pc-error" role="alert" style={{ marginBottom: 16 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
        <div style={{ display: 'flex', gap: 10 }}>
          <button type="button" onClick={onClose} disabled={deleting} className="pc-btn pc-btn-ghost" style={{ flex: 1 }}>Cancel</button>
          <button type="button" onClick={onConfirm} disabled={deleting} className="pc-btn" style={{ flex: 1, background: 'var(--pc-bad)', boxShadow: 'none' }}>
            {deleting ? <><div className="spinner" style={{ width: 16, height: 16 }} /> Deleting...</> : <><Icon name="trash" size={16} />Delete</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// `independent`: the platform's own City-ride drivers instead of my Trip fleet.
function DriverList({ independent = false, vehicles = [], hasIndependentTab = false }) {
  const [drivers, setDrivers] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [confirmTarget, setConfirmTarget] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  useEffect(() => {
    (independent ? getIndependentDrivers() : getOwnerDrivers()).then(r => setDrivers(r.data || [])).catch(() => {}).finally(() => setLoading(false))
  }, [independent])

  const title = independent ? 'Independent drivers' : 'Your drivers'
  const carById = Object.fromEntries(vehicles.map((v) => [v.id, v]))
  const pending = drivers.filter((d) => d.verificationStatus === 'UNDER_REVIEW').length

  const openDetail = async (driverId) => {
    setDetailLoading(true)
    try {
      const r = await getOwnerDriverById(driverId)
      setSelected(r.data)
    } catch { /* ignore */ }
    setDetailLoading(false)
  }

  const askDelete = (driver) => { setDeleteError(''); setConfirmTarget(driver) }

  const handleDelete = async () => {
    if (!confirmTarget) return
    setDeleting(true); setDeleteError('')
    try {
      await deleteOwnerDriver(confirmTarget.driverId)
      setDrivers(prev => prev.filter(d => d.driverId !== confirmTarget.driverId))
      setConfirmTarget(null)
    } catch (err) {
      setDeleteError(err.response?.data?.error || err.response?.data?.message || 'Failed to delete driver')
    } finally { setDeleting(false) }
  }

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(drivers, 10)

  if (loading) return <Panel span={12} title={title}><Skeleton rows={5} height={18} /></Panel>
  if (drivers.length === 0) {
    return (
      <Panel span={12} title={title}>
        <Empty icon="car" title="No drivers yet">
          {independent ? 'Approved driver applications show up here.'
            : `Add a driver for your Trips to get started.${hasIndependentTab ? ' Independent drivers are in the next tab.' : ''}`}
        </Empty>
      </Panel>
    )
  }

  return (
    <>
      {selected && (
        <DriverDetailModal
          driver={selected}
          vehicles={vehicles}
          onClose={() => setSelected(null)}
          onUpdated={(d) => { setSelected(d); setDrivers((prev) => prev.map((x) => (x.driverId === d.driverId ? { ...x, ...d } : x))) }}
        />
      )}
      {confirmTarget && <DeleteConfirmModal driver={confirmTarget} deleting={deleting} error={deleteError} onConfirm={handleDelete} onClose={() => !deleting && setConfirmTarget(null)} />}
      <Panel span={12} title={title} meta={`${drivers.length} total${pending ? ` · ${pending} waiting for your checks` : ''}`}>
        <div className="pc-rows">
          {paginatedItems.map((d, i) => (
            <div key={d.driverId} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', padding: '12px 0', borderTop: i ? '1px solid var(--pc-line)' : 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                <div style={avatarStyle(44)}>{d.name?.[0]?.toUpperCase()}</div>
                <div style={{ minWidth: 0 }}>
                  <div className="pc-row-title" style={{ fontSize: 15 }}>{d.name}</div>
                  <div style={{ fontSize: 12, color: 'var(--pc-muted)', overflowWrap: 'anywhere' }}>{d.mobile} · {d.email || '—'}</div>
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                {independent ? (
                  <span className="pc-pill" title={d.vehicleNumber || 'No number plate yet'} style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
                    <Icon name={vehicleIcon(d.vehicleType)} size={14} />{vehicleLabel(d.vehicleType)}
                    {d.servesCity ? ' · City' : ' · Off'}
                  </span>
                ) : (
                  <span className="pc-pill" style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
                    <Icon name="car" size={14} />{carById[d.defaultVehicleId] ? carText(carById[d.defaultVehicleId]) : 'No usual car'}
                  </span>
                )}
                <StatusPill status={d.status} label={d.status} />
                {d.emailVerified && <span className="pc-pill pc-pill-ok">Email</span>}
                {d.verificationStatus && d.verificationStatus !== 'APPROVED' && (
                  <span className={`pc-pill ${d.verificationStatus === 'REJECTED' ? 'pc-pill-stop' : ''}`}>
                    {d.verificationStatus === 'REJECTED' ? 'Rejected' : 'Under review'}
                  </span>
                )}
                {!independent && d.verificationStatus === 'UNDER_REVIEW'
                  ? <button type="button" onClick={() => openDetail(d.driverId)} disabled={detailLoading} className="pc-btn pc-btn-sm"><Icon name="shield" size={14} />Check and approve</button>
                  : <button type="button" onClick={() => openDetail(d.driverId)} disabled={detailLoading} className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="eye" size={14} />View Details</button>}
                <button type="button" onClick={() => askDelete(d)} title="Delete driver" className="pc-btn pc-btn-ghost pc-btn-sm" style={dangerBtn}><Icon name="trash" size={14} />Delete</button>
              </div>
            </div>
          ))}
        </div>
        <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
      </Panel>
    </>
  )
}

function UploadTile({ label, icon, file, onChange }) {
  return (
    <div>
      <span style={labelStyle}>{label}</span>
      <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6, height: 100, borderRadius: 14, cursor: 'pointer', border: `2px dashed ${file ? 'var(--pc-brand)' : 'var(--pc-line)'}`, background: file ? 'var(--pc-brand-soft)' : 'var(--pc-wash)', color: file ? 'var(--pc-brand-deep)' : 'var(--pc-muted)', transition: 'all 0.15s' }}>
        <Icon name={file ? 'check' : icon} size={22} />
        <span style={{ fontSize: 11, fontWeight: file ? 700 : 500, color: file ? 'var(--pc-brand-deep)' : 'var(--pc-ink-2)' }}>{file ? file.name.slice(0, 14) + '...' : 'Click to upload'}</span>
        <input type="file" accept="image/*" aria-label={label} style={{ display: 'none' }} onChange={onChange} />
      </label>
    </div>
  )
}

export default function OwnerDrivers() {
  const celebrate = useCelebrate()
  const me = useOwnerMe()
  const [tab, setTab] = useState('list')
  const [form, setForm] = useState(BLANK_DRIVER)
  const [files, setFiles] = useState({ photo: null, licensePhoto: null, aadhaarPhoto: null })
  const [vehicles, setVehicles] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)

  useEffect(() => { getOwnerVehicles().then((r) => setVehicles(r.data || [])).catch(() => {}) }, [])
  // How many independent drivers work with the platform owner, for the tab label.
  const [independentCount, setIndependentCount] = useState(null)
  useEffect(() => {
    if (!me?.platform) return
    getIndependentDrivers().then((r) => setIndependentCount((r.data || []).length)).catch(() => {})
  }, [me?.platform])
  const activeCars = vehicles.filter((v) => v.active)

  const set = (k, v) => setForm(p => ({ ...p, [k]: v }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true); setError('')
    try {
      // My drivers take Trips only, in my cars; City rides are for independent drivers.
      const car = activeCars.find((v) => String(v.id) === String(form.defaultVehicleId))
      const fd = new FormData()
      Object.entries(form).forEach(([k, v]) => { if (v !== '') fd.append(k, String(v)) })
      fd.append('vehicleType', 'CAR')
      fd.append('servesCity', 'false')
      fd.append('servesTrip', 'true')
      if (car) { fd.append('vehicleSeats', String(car.seats)); fd.append('vehicleAc', String(!!car.ac)) }
      Object.entries(files).forEach(([k, v]) => { if (v) fd.append(k, v) })
      const res = await createDriver(fd)
      setSuccess(res.data)
      setForm(BLANK_DRIVER)
      setFiles({ photo: null, licensePhoto: null, aadhaarPhoto: null })
      setTab('list')
      celebrate({ title: 'Driver added', message: `Check ${res.data?.name || form.name}'s licence and RC, then approve to email their login.` })
    } catch (err) {
      setError(err.response?.data?.error || Object.values(err.response?.data || {}).join(', ') || 'Failed to create driver')
    } finally { setLoading(false) }
  }

  const fieldGrid = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 14 }

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      <PageHead title="Driver Management" sub={me?.platform ? 'Your own Trip drivers, and independent drivers who work with you' : 'Your own Trip drivers: add, check and approve'}>
        <button type="button" onClick={() => setTab(tab === 'create' ? 'list' : 'create')} className={tab === 'create' ? 'pc-btn pc-btn-ghost' : 'pc-btn'}>
          <Icon name={tab === 'create' ? 'back' : 'plus'} />{tab === 'create' ? 'Driver List' : 'Add Driver'}
        </button>
      </PageHead>

      {/* Adding a driver is the Add Driver button above; the tabs only switch between lists. */}
      <div className="pc-chips" role="tablist" aria-label="Driver views" style={{ marginBottom: 18, flexWrap: 'nowrap', overflowX: 'auto' }}>
        {[['list', 'users', 'My drivers'], ...(me?.platform ? [['independent', 'id', `Independent drivers${independentCount != null ? ` (${independentCount})` : ''}`]] : [])].map(([key, icon, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className="pc-chip"
            style={{ border: 'none', cursor: 'pointer', whiteSpace: 'nowrap', flexShrink: 0, ...(tab === key ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}
          ><Icon name={icon} size={14} />{label}</button>
        ))}
      </div>

      <div className="pc-grid">
        {tab === 'list' ? (
          <>
            {success && (
              <div className="alert-success pc-span-12" role="status" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Icon name="check" size={18} />
                <span style={{ flex: 1 }}><strong>Driver added.</strong> {success.name} gets a login once you check their licence and RC and approve.</span>
                <button type="button" onClick={() => setSuccess(null)} aria-label="Dismiss" style={{ color: 'inherit', background: 'none', border: 'none', cursor: 'pointer', display: 'flex', padding: 4 }}><Icon name="x" size={16} /></button>
              </div>
            )}
            <DriverList key={success?.id} vehicles={vehicles} hasIndependentTab={!!me?.platform} />
          </>
        ) : tab === 'independent' && me?.platform ? (
          <DriverList independent />
        ) : (
          <div className="pc-span-8">
            {error && <div className="pc-error" role="alert" style={{ marginBottom: 16 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
            <form onSubmit={handleSubmit} className="pc-grid">
              <Panel span={12} title="Basic Information">
                <div style={fieldGrid}>
                  <div><label htmlFor="drv-name" style={labelStyle}>Full Name *</label><input id="drv-name" className="input-field" placeholder="Suresh Sharma" value={form.name} onChange={e => set('name', e.target.value)} required /></div>
                  <div><label htmlFor="drv-mobile" style={labelStyle}>Mobile Number *</label><input id="drv-mobile" className="input-field" placeholder="9876543210" maxLength={10} value={form.mobile} onChange={e => set('mobile', e.target.value)} required /></div>
                  <div style={{ gridColumn: '1/-1' }}><label htmlFor="drv-email" style={labelStyle}>Email Address *</label><input id="drv-email" type="email" className="input-field" placeholder="driver@example.com" value={form.email} onChange={e => set('email', e.target.value)} required /></div>
                </div>
              </Panel>
              <Panel span={12} title="Documents">
                <div style={fieldGrid}>
                  <div><label htmlFor="drv-licence" style={labelStyle}>License Number *</label><input id="drv-licence" className="input-field" placeholder="DL1420110012345" value={form.licenseNumber} onChange={e => set('licenseNumber', e.target.value)} required /></div>
                  <div><label htmlFor="drv-aadhaar" style={labelStyle}>Aadhaar Number *</label><input id="drv-aadhaar" className="input-field" placeholder="123456789012" maxLength={12} value={form.aadhaarNumber} onChange={e => set('aadhaarNumber', e.target.value)} required /></div>
                </div>
              </Panel>
              <Panel span={12} title="Usual car" meta="Trips only">
                {activeCars.length === 0 ? (
                  <p style={{ fontSize: 13, color: 'var(--pc-ink-2)' }}>
                    You have no cars in use yet. <Link to="/owner/vehicles">Add your cars</Link> so this driver can be put on Trips.
                  </p>
                ) : (
                  <div>
                    <label htmlFor="drv-car" style={labelStyle}>The car this driver normally takes</label>
                    <select id="drv-car" className="input-field" value={form.defaultVehicleId} onChange={e => set('defaultVehicleId', e.target.value)}>
                      <option value="">Choose later</option>
                      {activeCars.map((v) => <option key={v.id} value={v.id}>{carText(v)}</option>)}
                    </select>
                  </div>
                )}
              </Panel>
              <Panel span={12} title="Photo Uploads" meta="optional">
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(160px,1fr))', gap: 12 }}>
                  {[['photo', 'user', 'Driver Photo'], ['licensePhoto', 'card', 'License Photo'], ['aadhaarPhoto', 'shield', 'Aadhaar Photo']].map(([key, icon, label]) => (
                    <UploadTile key={key} label={label} icon={icon} file={files[key]} onChange={e => setFiles(p => ({ ...p, [key]: e.target.files[0] }))} />
                  ))}
                </div>
              </Panel>
              <div className="pc-span-12">
                <button type="submit" disabled={loading} className="pc-btn" style={{ width: '100%' }}>
                  {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Creating driver...</> : <><Icon name="check" />Create Driver Account</>}
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    </DashboardLayout>
  )
}
