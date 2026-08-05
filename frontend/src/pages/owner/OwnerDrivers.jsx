import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { createDriver, getOwnerDrivers, getOwnerDriverById, deleteOwnerDriver, createDriverTelegramLink } from '../../utils/api'

const navItems = [
  { path: '/owner/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '📋', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '🚗', label: 'Drivers' },
  { path: '/owner/payments', icon: '💳', label: 'Payments' },
  { path: '/owner/reviews', icon: '⭐', label: 'Reviews' },
  { path: '/owner/packages', icon: '📦', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '🎫', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '📊', label: 'Revenue' },
  { path: '/owner/profile', icon: '👤', label: 'Profile' },
]

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
    <div style={{ padding: '12px 14px', borderRadius: 12, background: '#f8fafc', border: '1px solid #e2e8f0', marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase' }}>Telegram Alerts</span>
        <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 20, background: driver.telegramLinked ? '#dcfce7' : '#fef3c7', color: driver.telegramLinked ? '#15803d' : '#b45309' }}>
          {driver.telegramLinked ? '✓ Connected' : 'Not connected'}
        </span>
      </div>

      {!driver.telegramLinked && (
        <p style={{ fontSize: 12, color: '#64748b', marginTop: 8 }}>
          This driver will not be alerted on their phone when a trip is assigned. Send them the link below to connect.
        </p>
      )}

      {error && <div className="alert-error" style={{ marginTop: 10, borderRadius: 10, fontSize: 12 }}>⚠️ {error}</div>}

      {linkUrl ? (
        <div style={{ marginTop: 10 }}>
          <div style={{ fontSize: 11, color: '#64748b', marginBottom: 6 }}>
            One-time link, valid 24 hours. Anyone who opens it can accept trips as this driver — send it only to them.
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <input readOnly value={linkUrl} onFocus={(e) => e.target.select()}
              style={{ flex: 1, padding: '8px 10px', borderRadius: 10, border: '1px solid #e2e8f0', fontSize: 12, color: '#374151', background: '#fff' }} />
            <button onClick={copy} className="btn-ghost" style={{ borderRadius: 10, fontSize: 12, padding: '8px 12px' }}>
              {copied ? '✓ Copied' : 'Copy'}
            </button>
          </div>
        </div>
      ) : (
        <button onClick={generate} disabled={loading} className="btn-ghost"
          style={{ marginTop: 10, borderRadius: 10, fontSize: 13, padding: '8px 12px', cursor: loading ? 'not-allowed' : 'pointer' }}>
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
      <div className="modal-box" style={{ maxWidth: 520, borderRadius: 20, overflow: 'hidden', padding: 0 }}>
        <div style={{ background: 'linear-gradient(135deg, #0F172A, #1e293b)', padding: '20px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: '#fff' }}>Driver Details</h3>
          <button onClick={onClose} style={{ background: 'rgba(255,255,255,0.1)', border: 'none', fontSize: 16, cursor: 'pointer', color: '#fff', width: 32, height: 32, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>✕</button>
        </div>
        <div style={{ padding: 24 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
            {imgUrl(driver.photo)
              ? <img src={imgUrl(driver.photo)} alt="Driver" style={{ width: 64, height: 64, borderRadius: 16, objectFit: 'cover', border: '2px solid #ffedd5' }} />
              : <div style={{ width: 64, height: 64, borderRadius: 16, background: 'linear-gradient(135deg,#8b5cf6,#f97316)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 900, color: '#fff' }}>{driver.name?.[0]?.toUpperCase()}</div>
            }
            <div>
              <div style={{ fontWeight: 800, fontSize: 17, color: '#0F172A' }}>{driver.name}</div>
              <div style={{ fontSize: 13, color: '#64748b' }}>{driver.mobile} • {driver.email}</div>
              <div style={{ marginTop: 4, display: 'flex', gap: 6 }}>
                <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 20, background: driver.status === 'ACTIVE' ? '#dcfce7' : '#fee2e2', color: driver.status === 'ACTIVE' ? '#15803d' : '#b91c1c' }}>{driver.status}</span>
                {driver.emailVerified && <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 20, background: '#f3e8ff', color: '#8b5cf6' }}>✓ Verified</span>}
                {driver.telegramLinked && <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 20, background: '#e0f2fe', color: '#0369a1' }}>✈ Telegram</span>}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
            {[['License Number', driver.licenseNumber], ['Aadhaar Number', driver.aadhaarNumber], ['Joined', driver.createdAt ? new Date(driver.createdAt).toLocaleDateString('en-IN') : '—']].map(([k, v]) => (
              <div key={k} style={{ padding: '10px 14px', borderRadius: 12, background: '#f8fafc', border: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase' }}>{k}</span>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{v || '—'}</span>
              </div>
            ))}
          </div>
          <TelegramLinkPanel driver={driver} />
          <div className="driver-image-row" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10 }}>
            {[['photo', '👤', 'Driver Photo'], ['licensePhoto', '📄', 'License'], ['aadhaarPhoto', '🪪', 'Aadhaar']].map(([key, icon, label]) => (
              <div key={key} style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: '#64748b', marginBottom: 6 }}>{label}</div>
                {imgUrl(driver[key])
                  ? <img src={imgUrl(driver[key])} alt={label} style={{ width: '100%', height: 80, objectFit: 'cover', borderRadius: 10, border: '1px solid #e2e8f0' }} />
                  : <div style={{ height: 80, borderRadius: 10, background: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24 }}>{icon}</div>
                }
              </div>
            ))}
          </div>
          <button onClick={onClose} className="btn-ghost" style={{ width: '100%', justifyContent: 'center', marginTop: 16, borderRadius: 12 }}>Close</button>
        </div>
      </div>
    </div>
  )
}

function DeleteConfirmModal({ driver, deleting, error, onConfirm, onClose }) {
  return (
    <div className="modal-overlay">
      <div className="modal-box" style={{ maxWidth: 420, borderRadius: 20, padding: 24 }}>
        <div style={{ width: 56, height: 56, borderRadius: 16, background: '#fee2e2', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, margin: '0 auto 16px' }}>🗑️</div>
        <h3 style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: '#0F172A', textAlign: 'center', marginBottom: 8 }}>Delete Driver?</h3>
        <p style={{ fontSize: 14, color: '#64748b', textAlign: 'center', marginBottom: 16 }}>
          Are you sure you want to permanently delete <strong style={{ color: '#0F172A' }}>{driver.name}</strong> ({driver.mobile})? This cannot be undone.
        </p>
        {error && <div className="alert-error" style={{ marginBottom: 16, borderRadius: 12, fontSize: 13 }}>⚠️ {error}</div>}
        <div style={{ display: 'flex', gap: 10 }}>
          <button onClick={onClose} disabled={deleting} className="btn-ghost" style={{ flex: 1, justifyContent: 'center', borderRadius: 12 }}>Cancel</button>
          <button onClick={onConfirm} disabled={deleting} style={{ flex: 1, justifyContent: 'center', display: 'flex', alignItems: 'center', gap: 6, padding: '12px', borderRadius: 12, fontSize: 14, fontWeight: 700, color: '#fff', border: 'none', cursor: deleting ? 'not-allowed' : 'pointer', background: 'linear-gradient(135deg,#ef4444,#b91c1c)', boxShadow: '0 4px 14px rgba(239,68,68,0.3)' }}>
            {deleting ? <><div className="spinner" style={{ width: 16, height: 16 }} /> Deleting...</> : 'Delete'}
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

  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0' }}><div className="spinner" /></div>
  if (drivers.length === 0) return <div className="empty-state"><div className="empty-icon">🚗</div><h3>No drivers yet</h3><p>Create a driver account to get started.</p></div>

  return (
    <>
      {selected && <DriverDetailModal driver={selected} onClose={() => setSelected(null)} />}
      {confirmTarget && <DeleteConfirmModal driver={confirmTarget} deleting={deleting} error={deleteError} onConfirm={handleDelete} onClose={() => !deleting && setConfirmTarget(null)} />}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {paginatedItems.map(d => (
          <div key={d.driverId} style={{ background: '#fff', borderRadius: 16, padding: 18, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', border: '1px solid #e2e8f0', boxShadow: '0 2px 8px rgba(0,0,0,0.03)', transition: 'all 0.2s' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ width: 46, height: 46, borderRadius: 14, background: 'linear-gradient(135deg,#8b5cf6,#f97316)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 900, color: '#fff', flexShrink: 0 }}>{d.name?.[0]?.toUpperCase()}</div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A' }}>{d.name}</div>
                <div style={{ fontSize: 12, color: '#64748b' }}>{d.mobile} • {d.email || '—'}</div>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 11, fontWeight: 700, padding: '4px 12px', borderRadius: 20, background: d.status === 'ACTIVE' ? '#dcfce7' : '#fee2e2', color: d.status === 'ACTIVE' ? '#15803d' : '#b91c1c' }}>{d.status}</span>
              {d.emailVerified && <span style={{ fontSize: 11, fontWeight: 700, padding: '4px 12px', borderRadius: 20, background: '#f3e8ff', color: '#8b5cf6' }}>✓ Email</span>}
              <button onClick={() => openDetail(d.driverId)} disabled={detailLoading} style={{ padding: '7px 16px', borderRadius: 20, fontSize: 12, fontWeight: 700, background: '#f8fafc', color: '#f97316', border: '1px solid #ffedd5', cursor: 'pointer', transition: 'all 0.15s' }}>View Details</button>
              <button onClick={() => askDelete(d)} title="Delete driver" style={{ padding: '7px 16px', borderRadius: 20, fontSize: 12, fontWeight: 700, background: '#fef2f2', color: '#ef4444', border: '1px solid #fecaca', cursor: 'pointer', transition: 'all 0.15s' }}>Delete</button>
            </div>
          </div>
        ))}
      </div>
      <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
    </>
  )
}

export default function OwnerDrivers() {
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
    } catch (err) {
      setError(err.response?.data?.error || Object.values(err.response?.data || {}).join(', ') || 'Failed to create driver')
    } finally { setLoading(false) }
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div className="animate-fadeIn">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#0F172A', marginBottom: 4 }}>Driver Management</h1>
            <p style={{ fontSize: 14, color: '#64748b' }}>Manage and create driver accounts</p>
          </div>
          <button onClick={() => setTab(tab === 'create' ? 'list' : 'create')} className="btn-primary" style={{ background: 'linear-gradient(135deg, #8b5cf6, #f97316)', borderRadius: 12, boxShadow: '0 4px 14px rgba(139,92,246,0.3)' }}>
            {tab === 'create' ? '← Driver List' : '+ Add Driver'}
          </button>
        </div>

        <div style={{ display: 'flex', gap: 4, marginBottom: 24, background: '#f1f5f9', borderRadius: 14, padding: 4, width: 'fit-content' }}>
          {[['list', '📋 Driver List'], ['create', '+ Create Driver']].map(([key, label]) => (
            <button key={key} onClick={() => setTab(key)} style={{ padding: '9px 20px', borderRadius: 10, fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: tab === key ? 'linear-gradient(135deg,#8b5cf6,#f97316)' : 'transparent', color: tab === key ? '#fff' : '#64748b', transition: 'all 0.2s', boxShadow: tab === key ? '0 4px 12px rgba(139,92,246,0.25)' : 'none' }}>{label}</button>
          ))}
        </div>

        {tab === 'list' ? (
          <>
            {success && <div className="alert-success" style={{ marginBottom: 16, borderRadius: 12, border: '1px solid #bbf7d0' }}>✅ <strong>Driver Created!</strong> {success.name} added successfully.<button onClick={() => setSuccess(null)} style={{ marginLeft: 12, fontSize: 12, color: '#16a34a', background: 'none', border: 'none', cursor: 'pointer' }}>✕</button></div>}
            <DriverList key={success?.id} />
          </>
        ) : (
          <div style={{ maxWidth: 680 }}>
            {error && <div className="alert-error" style={{ marginBottom: 16, borderRadius: 12 }}>⚠️ {error}</div>}
            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <div style={{ background: '#fff', borderRadius: 18, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 8px rgba(0,0,0,0.03)' }}>
                <h3 style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 18 }}>Basic Information</h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 14 }}>
                  <div><label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Full Name *</label><input className="input-field" style={{ borderRadius: 12 }} placeholder="Suresh Sharma" value={form.name} onChange={e => set('name', e.target.value)} required /></div>
                  <div><label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Mobile Number *</label><input className="input-field" style={{ borderRadius: 12 }} placeholder="9876543210" maxLength={10} value={form.mobile} onChange={e => set('mobile', e.target.value)} required /></div>
                  <div style={{ gridColumn: '1/-1' }}><label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email Address *</label><input type="email" className="input-field" style={{ borderRadius: 12 }} placeholder="driver@example.com" value={form.email} onChange={e => set('email', e.target.value)} required /></div>
                </div>
              </div>
              <div style={{ background: '#fff', borderRadius: 18, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 8px rgba(0,0,0,0.03)' }}>
                <h3 style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 18 }}>Documents</h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 14 }}>
                  <div><label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>License Number *</label><input className="input-field" style={{ borderRadius: 12 }} placeholder="DL1420110012345" value={form.licenseNumber} onChange={e => set('licenseNumber', e.target.value)} required /></div>
                  <div><label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Aadhaar Number *</label><input className="input-field" style={{ borderRadius: 12 }} placeholder="123456789012" maxLength={12} value={form.aadhaarNumber} onChange={e => set('aadhaarNumber', e.target.value)} required /></div>
                </div>
              </div>
              <div style={{ background: '#fff', borderRadius: 18, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 8px rgba(0,0,0,0.03)' }}>
                <h3 style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 18 }}>Photo Uploads</h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(160px,1fr))', gap: 12 }}>
                  {[['photo', '👤', 'Driver Photo'], ['licensePhoto', '📄', 'License Photo'], ['aadhaarPhoto', '🪪', 'Aadhaar Photo']].map(([key, icon, label]) => (
                    <div key={key}>
                      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>{label}</label>
                      <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 100, borderRadius: 14, cursor: 'pointer', border: `2px dashed ${files[key] ? '#8b5cf6' : '#cbd5e1'}`, background: files[key] ? '#f3e8ff' : '#f8fafc', transition: 'all 0.15s' }}>
                        <span style={{ fontSize: 24, marginBottom: 4 }}>{files[key] ? '✅' : icon}</span>
                        <span style={{ fontSize: 11, color: files[key] ? '#8b5cf6' : '#64748b', fontWeight: files[key] ? 600 : 400 }}>{files[key] ? files[key].name.slice(0, 14) + '...' : 'Click to upload'}</span>
                        <input type="file" accept="image/*" style={{ display: 'none' }} onChange={e => setFiles(p => ({ ...p, [key]: e.target.files[0] }))} />
                      </label>
                    </div>
                  ))}
                </div>
              </div>
              <button type="submit" disabled={loading} className="btn-primary" style={{ justifyContent: 'center', padding: '14px', fontSize: 15, background: 'linear-gradient(135deg, #8b5cf6, #f97316)', borderRadius: 14, boxShadow: '0 4px 16px rgba(139,92,246,0.3)' }}>
                {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Creating driver...</> : 'Create Driver Account'}
              </button>
            </form>
          </div>
        )}
      </div>
    </DashboardLayout>
  )
}
