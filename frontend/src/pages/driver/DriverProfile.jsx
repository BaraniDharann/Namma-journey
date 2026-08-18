import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getDriverProfile, toggleDriverAvailability } from '../../utils/api'

const navItems = [
  { path: '/driver/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/driver/bookings', icon: '📋', label: 'My Trips' },
  { path: '/driver/profile', icon: '👤', label: 'Profile' },
]

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
      <div style={{ maxWidth: 640, margin: '0 auto' }} className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px', marginBottom: 4 }}>Driver Profile</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>Manage your availability and details</p>
        </div>

        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0' }}><div className="spinner" /></div>
        ) : (
          <>
            {/* Availability Toggle Card */}
            <div style={{
              padding: '20px 24px', marginBottom: 16, borderRadius: 18,
              background: isActive ? '#F0FDF4' : '#FEF2F2',
              border: `1.5px solid ${isActive ? '#BBF7D0' : '#FECACA'}`,
              transition: 'all 0.2s'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 16, color: isActive ? '#15803d' : '#B91C1C', marginBottom: 4 }}>
                    {isActive ? '🟢 Available for Trips' : '🔴 Offline - Not Available'}
                  </div>
                  <div style={{ fontSize: 13, color: isActive ? '#22c55e' : '#ef4444' }}>
                    {isActive ? 'You will receive new trip requests' : 'You won\'t receive any trip requests'}
                  </div>
                </div>
                <button
                  onClick={handleToggleAvailability}
                  disabled={toggling}
                  style={{
                    position: 'relative',
                    width: 56, height: 30, borderRadius: 15,
                    background: isActive ? '#22c55e' : '#cbd5e1',
                    border: 'none', cursor: toggling ? 'wait' : 'pointer',
                    transition: 'background 0.3s', flexShrink: 0
                  }}
                >
                  <div style={{
                    position: 'absolute',
                    top: 3, left: isActive ? 29 : 3,
                    width: 24, height: 24, borderRadius: '50%',
                    background: '#fff',
                    transition: 'left 0.3s',
                    boxShadow: '0 1px 4px rgba(0,0,0,0.2)'
                  }} />
                </button>
              </div>
            </div>

            {/* Profile Info Card */}
            <div style={{ background: '#fff', borderRadius: 20, padding: 24, border: '1px solid #e2e8f0', marginBottom: 16, boxShadow: '0 2px 12px rgba(0,0,0,0.03)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
                {imgUrl(d.photo)
                  ? <img src={imgUrl(d.photo)} alt="Driver" style={{ width: 72, height: 72, borderRadius: 20, objectFit: 'cover', border: '3px solid #3b82f6' }} />
                  : <div style={{ width: 72, height: 72, borderRadius: 20, background: 'linear-gradient(135deg,#3b82f6,#2563eb)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 30, fontWeight: 900, color: '#fff' }}>{initial}</div>
                }
                <div>
                  <h2 style={{ fontWeight: 800, fontSize: 22, color: '#0F172A', marginBottom: 4 }}>{d.name || user?.name || 'Driver'}</h2>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    {d.emailVerified && <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 8, background: '#eff6ff', color: '#3b82f6', border: '1px solid #bfdbfe' }}>✓ Verified</span>}
                    <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 8, background: isActive ? '#F0FDF4' : '#FEF2F2', color: isActive ? '#15803d' : '#B91C1C', border: `1px solid ${isActive ? '#BBF7D0' : '#FECACA'}` }}>{isActive ? 'ACTIVE' : 'INACTIVE'}</span>
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {[
                  ['Name', d.name || user?.name],
                  ['Mobile', d.mobile || user?.mobile],
                  ['Email', d.email || user?.email],
                  ['License Number', d.licenseNumber],
                  ['Aadhaar Number', d.aadhaarNumber],
                  ['Joined', d.createdAt ? new Date(d.createdAt).toLocaleDateString('en-IN') : null],
                ].filter(([, v]) => v).map(([k, v]) => (
                  <div key={k} style={{ padding: '12px 16px', borderRadius: 14, background: '#f8fafc', border: '1px solid #e2e8f0' }}>
                    <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>{k}</div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: '#374151' }}>{v}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Document grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
              {[['photo', '📸', 'Driver Photo'], ['licensePhoto', '📄', 'License'], ['aadhaarPhoto', '🪪', 'Aadhaar']].map(([key, icon, label]) => (
                <div key={key} style={{ padding: 16, textAlign: 'center', borderRadius: 18, background: '#fff', border: `1.5px solid ${d[key] ? '#3b82f6' : '#e2e8f0'}`, boxShadow: '0 2px 8px rgba(0,0,0,0.03)' }}>
                  {imgUrl(d[key])
                    ? <img src={imgUrl(d[key])} alt={label} style={{ width: '100%', height: 80, objectFit: 'cover', borderRadius: 12, marginBottom: 8 }} />
                    : <div style={{ fontSize: 32, marginBottom: 8, width: '100%', height: 80, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f8fafc', borderRadius: 12, border: '2px dashed #e2e8f0' }}>{icon}</div>
                  }
                  <div style={{ fontWeight: 700, fontSize: 13, color: '#0F172A', marginBottom: 4 }}>{label}</div>
                  <div style={{ fontSize: 11, fontWeight: 600, color: d[key] ? '#3b82f6' : '#94a3b8' }}>
                    {d[key] ? '✓ Uploaded' : '-- Not uploaded'}
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </DashboardLayout>
  )
}
