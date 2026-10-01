import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { createDriver, getOwnerDrivers, getOwnerDriverById, deleteOwnerDriver, createDriverTelegramLink } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { useCelebrate } from '../../components/celebrate/Celebration'

const navItems = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
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

function DriverDetailModal({ driver, onClose }) {
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

function DriverList() {
  const [drivers, setDrivers] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [confirmTarget, setConfirmTarget] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  useEffect(() => {
    getOwnerDrivers().then(r => setDrivers(r.data || [])).catch(() => {}).finally(() => setLoading(false))
  }, [])

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

  if (loading) return <Panel span={12} title="Drivers"><Skeleton rows={5} height={18} /></Panel>
  if (drivers.length === 0) return <Panel span={12}><Empty icon="car" title="No drivers yet">Create a driver account to get started.</Empty></Panel>

  return (
    <>
      {selected && <DriverDetailModal driver={selected} onClose={() => setSelected(null)} />}
      {confirmTarget && <DeleteConfirmModal driver={confirmTarget} deleting={deleting} error={deleteError} onConfirm={handleDelete} onClose={() => !deleting && setConfirmTarget(null)} />}
      <Panel span={12} title="Drivers" meta={`${drivers.length} total`}>
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
                <StatusPill status={d.status} label={d.status} />
                {d.emailVerified && <span className="pc-pill pc-pill-ok">Email</span>}
                <button type="button" onClick={() => openDetail(d.driverId)} disabled={detailLoading} className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="eye" size={14} />View Details</button>
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
  const [tab, setTab] = useState('list')
  const [form, setForm] = useState({ name: '', mobile: '', email: '', licenseNumber: '', aadhaarNumber: '' })
  const [files, setFiles] = useState({ photo: null, licensePhoto: null, aadhaarPhoto: null })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)

  const set = (k, v) => setForm(p => ({ ...p, [k]: v }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true); setError('')
    try {
      const fd = new FormData()
      Object.entries(form).forEach(([k, v]) => fd.append(k, v))
      Object.entries(files).forEach(([k, v]) => { if (v) fd.append(k, v) })
      const res = await createDriver(fd)
      setSuccess(res.data)
      setForm({ name: '', mobile: '', email: '', licenseNumber: '', aadhaarNumber: '' })
      setFiles({ photo: null, licensePhoto: null, aadhaarPhoto: null })
      setTab('list')
      celebrate({ title: 'Driver added', message: `${res.data?.name || form.name} is now on your team.` })
    } catch (err) {
      setError(err.response?.data?.error || Object.values(err.response?.data || {}).join(', ') || 'Failed to create driver')
    } finally { setLoading(false) }
  }

  const fieldGrid = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 14 }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Driver Management" sub="Manage and create driver accounts">
        <button type="button" onClick={() => setTab(tab === 'create' ? 'list' : 'create')} className={tab === 'create' ? 'pc-btn pc-btn-ghost' : 'pc-btn'}>
          <Icon name={tab === 'create' ? 'back' : 'plus'} />{tab === 'create' ? 'Driver List' : 'Add Driver'}
        </button>
      </PageHead>

      <div className="pc-chips" role="tablist" aria-label="Driver views" style={{ marginBottom: 18 }}>
        {[['list', 'users', 'Driver List'], ['create', 'plus', 'Create Driver']].map(([key, icon, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className="pc-chip"
            style={{ border: 'none', cursor: 'pointer', ...(tab === key ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}
          ><Icon name={icon} size={14} />{label}</button>
        ))}
      </div>

      <div className="pc-grid">
        {tab === 'list' ? (
          <>
            {success && (
              <div className="alert-success pc-span-12" role="status" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Icon name="check" size={18} />
                <span style={{ flex: 1 }}><strong>Driver Created!</strong> {success.name} added successfully.</span>
                <button type="button" onClick={() => setSuccess(null)} aria-label="Dismiss" style={{ color: 'inherit', background: 'none', border: 'none', cursor: 'pointer', display: 'flex', padding: 4 }}><Icon name="x" size={16} /></button>
              </div>
            )}
            <DriverList key={success?.id} />
          </>
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
