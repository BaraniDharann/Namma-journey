import React from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'

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

export default function OwnerProfile() {
  const { user } = useAuth()

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div style={{ maxWidth: 600, margin: '0 auto' }} className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#0F172A' }}>Owner Profile</h1>
        </div>
        <div style={{ background: '#fff', borderRadius: 20, padding: 32, border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.05)', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 18, marginBottom: 28 }}>
            <div style={{ width: 72, height: 72, borderRadius: 20, background: 'linear-gradient(135deg,#8b5cf6,#f97316)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32, fontWeight: 900, color: '#fff', boxShadow: '0 8px 24px rgba(139,92,246,0.3)' }}>👑</div>
            <div>
              <h2 style={{ fontWeight: 800, fontSize: 22, color: '#0F172A', marginBottom: 2 }}>Platform Owner</h2>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
                <span style={{ fontSize: 12, fontWeight: 700, padding: '4px 12px', borderRadius: 20, background: '#dcfce7', color: '#15803d' }}>✓ Admin</span>
                <span style={{ fontSize: 12, color: '#94a3b8' }}>ROLE_OWNER</span>
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {[
              ['Email', user?.email || '—'],
              ['Role', user?.role || 'ROLE_OWNER'],
            ].map(([k, v]) => (
              <div key={k} style={{ padding: '14px 16px', borderRadius: 14, background: '#f8fafc', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>{k}</div>
                <div style={{ fontSize: 15, fontWeight: 600, color: '#0F172A' }}>{v}</div>
              </div>
            ))}
          </div>
        </div>

      </div>
    </DashboardLayout>
  )
}
