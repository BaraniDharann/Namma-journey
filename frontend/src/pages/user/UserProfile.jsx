import React, { useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { updateUserProfile } from '../../utils/api'
import { needsMobileNumber } from '../../components/MobileNumberGate'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill } from '../../components/dash/ui'
import { useCelebrate } from '../../components/celebrate/Celebration'

const navItems = [
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
]

export default function UserProfile() {
  const { user, updateUser } = useAuth()
  const celebrate = useCelebrate()
  // Travellers are sent here specifically to supply a missing number (booking is closed without
  // one), so open straight into the form rather than making them hunt for the Edit button.
  const [editing, setEditing] = useState(() => needsMobileNumber(user))
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
      celebrate({ title: 'Profile saved', message: 'Your name and mobile number are up to date.', autoClose: 2600 })
    } catch (err) {
      // The API's error envelope is {"error": "..."} (GlobalExceptionHandler); reading only
      // `message` swallowed every reason the server gave — "that number is already registered",
      // "enter a valid 10-digit number" — and showed the generic fallback instead.
      const reason = err.response?.data?.error || err.response?.data?.message
      setMsg({ type: 'error', text: reason || 'Failed to update profile' })
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

  const fieldBox = { padding: '14px 16px', borderRadius: 14, background: 'var(--pc-wash)' }
  const fieldLabel = { display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, fontWeight: 800, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }
  const fieldValue = { fontSize: 15, fontWeight: 700, color: 'var(--pc-ink)', overflowWrap: 'anywhere' }
  const inputStyle = { width: '100%', padding: '8px 12px', outline: 'none' }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <PageHead title="My Profile" sub="Your name and contact details">
        {!editing && (
          <button type="button" onClick={() => setEditing(true)} className="pc-btn pc-btn-ghost">
            <Icon name="edit" />Edit Profile
          </button>
        )}
      </PageHead>

      <div className="pc-grid">
        {(needsMobileNumber(user) || msg) && (
          <div className="pc-span-12" style={{ display: 'grid', gap: 12, maxWidth: 760 }}>
            {needsMobileNumber(user) && (
              <div style={{
                padding: '14px 16px', borderRadius: 14, fontSize: 13.5, lineHeight: 1.6,
                background: 'var(--pc-brand-soft)', color: 'var(--pc-brand-deep)', display: 'flex', gap: 10, alignItems: 'flex-start',
              }}>
                <Icon name="phone" size={18} style={{ flexShrink: 0, marginTop: 2 }} />
                <span><strong>Your mobile number is missing.</strong> Trip booking stays closed until you
                add one — the driver assigned to your trip needs it to reach you.</span>
              </div>
            )}

            {msg && (
              <div role={msg.type === 'error' ? 'alert' : 'status'} className={msg.type === 'success' ? 'alert-success' : 'alert-error'} style={{
                padding: '12px 16px', fontSize: 13.5, display: 'flex', gap: 8, alignItems: 'center',
              }}>
                <Icon name={msg.type === 'success' ? 'check' : 'alert'} size={16} />
                {msg.text}
              </div>
            )}
          </div>
        )}

        <Panel span={8} title="Account details" meta={<StatusPill status="VERIFIED" />}>
          {/* Avatar + info */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
            <div aria-hidden="true" style={{ width: 64, height: 64, borderRadius: 20, background: 'var(--pc-brand)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 800, color: '#fff', flexShrink: 0 }}>{initial}</div>
            <div style={{ minWidth: 0 }}>
              <h3 style={{ fontWeight: 700, fontSize: 20, color: 'var(--pc-ink)', margin: 0, overflowWrap: 'anywhere' }}>{user?.name || 'Traveller'}</h3>
              <div style={{ fontSize: 12.5, color: 'var(--pc-muted)', marginTop: 4, fontWeight: 600 }}>ROLE_USER</div>
            </div>
          </div>

          {/* Fields */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {/* Name */}
            <div style={fieldBox}>
              <label htmlFor="profile-name" style={fieldLabel}><Icon name="user" size={13} />Name</label>
              {editing ? (
                <input
                  id="profile-name"
                  className="input-field"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  style={inputStyle}
                />
              ) : (
                <div style={fieldValue}>{user?.name || '—'}</div>
              )}
            </div>

            {/* Mobile */}
            <div style={fieldBox}>
              <label htmlFor="profile-mobile" style={fieldLabel}><Icon name="phone" size={13} />Mobile</label>
              {editing ? (
                <input
                  id="profile-mobile"
                  className="input-field"
                  type="tel"
                  value={mobile}
                  onChange={(e) => setMobile(e.target.value)}
                  placeholder="Enter mobile number"
                  style={inputStyle}
                />
              ) : (
                <div style={fieldValue}>{user?.mobile || '—'}</div>
              )}
            </div>

            {/* Email (read-only) */}
            <div style={fieldBox}>
              <div style={fieldLabel}><Icon name="mail" size={13} />Email</div>
              <div style={fieldValue}>{user?.email || '—'}</div>
            </div>
          </div>

          {editing && (
            <div style={{ display: 'flex', gap: 10, marginTop: 20, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
              <button type="button" onClick={handleCancel} className="pc-btn pc-btn-ghost">
                Cancel
              </button>
              <button type="button" onClick={handleSave} disabled={saving} className="pc-btn">
                <Icon name="check" />{saving ? 'Saving...' : 'Save changes'}
              </button>
            </div>
          )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
