import React, { useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { updateUserProfile } from '../../utils/api'

const navItems = [
  { path: '/user/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/user/bookings', icon: '📋', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '➕', label: 'New Booking' },
  { path: '/user/payments', icon: '💳', label: 'Payments' },
  { path: '/user/package-bookings', icon: '📦', label: 'My Packages' },
  { path: '/user/profile', icon: '👤', label: 'Profile' },
]

export default function UserProfile() {
  const { user, updateUser } = useAuth()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(user?.name || '')
  const [mobile, setMobile] = useState(user?.mobile || '')
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState(null)

  const initial = user?.name?.[0]?.toUpperCase() || 'U'

  const handleSave = async () => {
    setSaving(true)
    setMsg(null)
    try {
      const res = await updateUserProfile(user.userId, { name, phone: mobile })
      updateUser({ name: res.data.name, mobile: res.data.mobile })
      setMsg({ type: 'success', text: 'Profile updated successfully!' })
      setEditing(false)
    } catch (err) {
      setMsg({ type: 'error', text: err.response?.data?.message || 'Failed to update profile' })
    } finally {
      setSaving(false)
    }
  }

  const handleCancel = () => {
    setName(user?.name || '')
    setMobile(user?.mobile || '')
    setEditing(false)
    setMsg(null)
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <div style={{ maxWidth: 600, margin: '0 auto' }} className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px' }}>My Profile</h1>
        </div>

        {msg && (
          <div style={{
            padding: '12px 16px', borderRadius: 12, marginBottom: 16, fontSize: 13, fontWeight: 600,
            background: msg.type === 'success' ? '#F0FDF4' : '#FEF2F2',
            color: msg.type === 'success' ? '#15803d' : '#B91C1C',
            border: `1px solid ${msg.type === 'success' ? '#BBF7D0' : '#FECACA'}`
          }}>
            {msg.text}
          </div>
        )}

        <div style={{ background: '#fff', borderRadius: 24, overflow: 'hidden', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
          <div style={{ padding: 28 }}>
            {/* Avatar + info */}
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, marginBottom: 28 }}>
              <div style={{ width: 80, height: 80, borderRadius: 22, background: 'linear-gradient(135deg,#f97316,#ea580c)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 34, fontWeight: 900, color: '#fff', border: '4px solid #fff', boxShadow: '0 4px 16px rgba(249,115,22,0.3)', flexShrink: 0 }}>{initial}</div>
              <div style={{ flex: 1, paddingBottom: 4 }}>
                <h2 style={{ fontWeight: 800, fontSize: 22, color: '#0F172A', letterSpacing: '-0.3px' }}>{user?.name || 'Traveller'}</h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
                  <span style={{ padding: '3px 10px', borderRadius: 8, fontSize: 11, fontWeight: 700, background: '#F0FDF4', color: '#15803d', border: '1px solid #BBF7D0' }}>Verified</span>
                  <span style={{ fontSize: 12, color: '#94a3b8' }}>ROLE_USER</span>
                </div>
              </div>
              {!editing && (
                <button
                  onClick={() => setEditing(true)}
                  style={{
                    padding: '8px 18px', borderRadius: 10, border: 'none',
                    background: '#fff7ed', cursor: 'pointer', fontSize: 13, fontWeight: 700, color: '#f97316',
                    transition: 'all 0.2s', flexShrink: 0
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = '#ffedd5'}
                  onMouseLeave={e => e.currentTarget.style.background = '#fff7ed'}
                >
                  Edit Profile
                </button>
              )}
            </div>

            {/* Fields */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {/* Name */}
              <div style={{ padding: '14px 16px', borderRadius: 14, background: '#FAFAFE', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 6 }}>Name</div>
                {editing ? (
                  <input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    style={{
                      width: '100%', fontSize: 15, fontWeight: 600, color: '#374151',
                      border: '1.5px solid #fed7aa', borderRadius: 10, padding: '8px 12px',
                      outline: 'none', background: '#fff', transition: 'border 0.2s'
                    }}
                    onFocus={e => e.target.style.borderColor = '#f97316'}
                    onBlur={e => e.target.style.borderColor = '#fed7aa'}
                  />
                ) : (
                  <div style={{ fontSize: 15, fontWeight: 600, color: '#374151' }}>{user?.name || '—'}</div>
                )}
              </div>

              {/* Mobile */}
              <div style={{ padding: '14px 16px', borderRadius: 14, background: '#FAFAFE', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 6 }}>Mobile</div>
                {editing ? (
                  <input
                    value={mobile}
                    onChange={(e) => setMobile(e.target.value)}
                    placeholder="Enter mobile number"
                    style={{
                      width: '100%', fontSize: 15, fontWeight: 600, color: '#374151',
                      border: '1.5px solid #fed7aa', borderRadius: 10, padding: '8px 12px',
                      outline: 'none', background: '#fff', transition: 'border 0.2s'
                    }}
                    onFocus={e => e.target.style.borderColor = '#f97316'}
                    onBlur={e => e.target.style.borderColor = '#fed7aa'}
                  />
                ) : (
                  <div style={{ fontSize: 15, fontWeight: 600, color: '#374151' }}>{user?.mobile || '—'}</div>
                )}
              </div>

              {/* Email (read-only) */}
              <div style={{ padding: '14px 16px', borderRadius: 14, background: '#FAFAFE', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 6 }}>Email</div>
                <div style={{ fontSize: 15, fontWeight: 600, color: '#374151' }}>{user?.email || '—'}</div>
              </div>
            </div>

            {editing && (
              <div style={{ display: 'flex', gap: 10, marginTop: 22, justifyContent: 'flex-end' }}>
                <button
                  onClick={handleCancel}
                  style={{
                    padding: '10px 22px', borderRadius: 12, border: '1.5px solid #e2e8f0',
                    background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b',
                    transition: 'all 0.2s'
                  }}
                >
                  Cancel
                </button>
                <button
                  onClick={handleSave}
                  disabled={saving}
                  style={{
                    padding: '10px 22px', borderRadius: 12, border: 'none',
                    background: saving ? '#fdba74' : '#f97316', cursor: saving ? 'not-allowed' : 'pointer',
                    fontSize: 14, fontWeight: 700, color: '#fff', transition: 'all 0.2s',
                    boxShadow: '0 4px 12px rgba(249,115,22,0.25)'
                  }}
                >
                  {saving ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
