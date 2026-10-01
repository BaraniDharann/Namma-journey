import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getDriverProfile, toggleDriverAvailability } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Skeleton } from '../../components/dash/ui'

const navItems = [
  { path: '/driver/dashboard', icon: '', label: 'Dashboard' },
  { path: '/driver/bookings', icon: '', label: 'My Trips' },
  { path: '/driver/profile', icon: '', label: 'Profile' },
]

const DOCS = [['photo', 'camera', 'Driver Photo'], ['licensePhoto', 'card', 'License'], ['aadhaarPhoto', 'shield', 'Aadhaar']]


export default function DriverProfile() {
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [toggling, setToggling] = useState(false)

  const imgUrl = (path) => {
    if (!path) return null
    if (path.startsWith('http')) return path
    return path.startsWith('/') ? path : `/${path}`
  }

  useEffect(() => {
    if (!user?.userId) return
    getDriverProfile(user.userId)
      .then(r => setProfile(r.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [user?.userId])

  const handleToggleAvailability = async () => {
    if (toggling) return
    const newStatus = profile?.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setToggling(true)
    try {
      await toggleDriverAvailability(user.userId, newStatus)
      setProfile(prev => ({ ...prev, status: newStatus }))
    } catch {
      alert('Failed to update availability')
    } finally {
      setToggling(false)
    }
  }

  const d = profile || {}
  const isActive = d.status === 'ACTIVE'
  const initial = (d.name || user?.name || 'D')[0].toUpperCase()


  return (
    <DashboardLayout navItems={navItems} role="ROLE_DRIVER">
      <PageHead title="Driver Profile" sub="Manage your availability and details" />

      {loading ? (
        <div className="pc-grid"><Panel span={12}><Skeleton rows={5} /></Panel></div>
      ) : (
        <div className="pc-grid">
          <Panel span={7} title="Profile" meta={<StatusPill status={isActive ? 'ACTIVE' : 'INACTIVE'} />}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 18 }}>
              {imgUrl(d.photo)
                ? <img src={imgUrl(d.photo)} alt="Driver" style={{ width: 72, height: 72, borderRadius: 20, objectFit: 'cover', border: '3px solid var(--pc-line)' }} />
                : <div aria-hidden="true" style={{ width: 72, height: 72, borderRadius: 20, background: 'var(--pc-rail)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 30, fontWeight: 800, color: '#fff' }}>{initial}</div>
              }
              <div style={{ minWidth: 0 }}>
                <h2 style={{ fontWeight: 800, fontSize: 22, color: 'var(--pc-ink)', margin: '0 0 6px' }}>{d.name || user?.name || 'Driver'}</h2>
                <div className="pc-chips">
                  {d.emailVerified && <span className="pc-chip"><Icon name="check" size={14} />Verified</span>}
                  <span className="pc-chip">{isActive ? 'ACTIVE' : 'INACTIVE'}</span>
                </div>
              </div>
            </div>
            <div className="pc-rows">
              {[
                ['Name', d.name || user?.name],
                ['Mobile', d.mobile || user?.mobile],
                ['Email', d.email || user?.email],
                ['License Number', d.licenseNumber],
                ['Aadhaar Number', d.aadhaarNumber],
                ['Joined', d.createdAt ? new Date(d.createdAt).toLocaleDateString('en-IN') : null],
              ].filter(([, v]) => v).map(([k, v]) => (
                <div key={k} className="pc-row" style={{ gridTemplateColumns: 'auto minmax(0, 1fr)' }}>
                  <span style={{ color: 'var(--pc-muted)', fontWeight: 700 }}>{k}</span>
                  <b style={{ textAlign: 'right', color: 'var(--pc-ink-2)', overflowWrap: 'anywhere' }}>{v}</b>
                </div>
              ))}
            </div>
          </Panel>

          <Panel span={5} title="Availability" meta={isActive ? 'online' : 'offline'}>
            <p style={{ margin: '0 0 6px', fontWeight: 800, fontSize: 16, color: isActive ? 'var(--pc-good)' : 'var(--pc-bad)' }}>
              {isActive ? 'Available for Trips' : 'Offline - Not Available'}
            </p>
            <p style={{ margin: '0 0 16px', fontSize: 13.5, color: 'var(--pc-muted)', fontWeight: 600 }}>
              {isActive ? 'You will receive new trip requests' : 'You won\'t receive any trip requests'}
            </p>
            <button
              type="button"
              className="pc-switch"
              role="switch"
              aria-checked={isActive}
              aria-label="Available for trips"
              onClick={handleToggleAvailability}
              disabled={toggling}
            >
              <span className="pc-switch-track" />
              {isActive ? 'Online' : 'Offline'}
            </button>
          </Panel>

          <Panel span={12} title="Documents" meta={`${DOCS.filter(([key]) => d[key]).length} of ${DOCS.length} uploaded`}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
              {DOCS.map(([key, icon, label]) => (
                <div key={key} style={{ padding: 14, textAlign: 'center', borderRadius: 16, background: 'var(--pc-panel)', border: `1.5px solid ${d[key] ? 'var(--pc-brand-deep)' : 'var(--pc-line)'}` }}>
                  {imgUrl(d[key])
                    ? <img src={imgUrl(d[key])} alt={label} style={{ width: '100%', height: 96, objectFit: 'cover', borderRadius: 12, marginBottom: 8 }} />
                    : <div style={{ marginBottom: 8, width: '100%', height: 96, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--pc-wash)', borderRadius: 12, border: '2px dashed var(--pc-line)', color: 'var(--pc-muted)' }}><Icon name={icon} size={28} /></div>
                  }
                  <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--pc-ink)', marginBottom: 4 }}>{label}</div>
                  <div style={{ fontSize: 11.5, fontWeight: 700, color: d[key] ? 'var(--pc-brand-deep)' : 'var(--pc-muted)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    {d[key] ? <><Icon name="check" size={13} />Uploaded</> : 'Not uploaded'}
                  </div>
                </div>
              ))}
            </div>
          </Panel>
        </div>
      )}
    </DashboardLayout>
  )
}
