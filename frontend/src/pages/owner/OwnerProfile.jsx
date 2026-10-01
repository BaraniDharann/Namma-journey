import React from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead } from '../../components/dash/ui'

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

export default function OwnerProfile() {
  const { user } = useAuth()

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Owner Profile" sub="The account that runs this platform" />

      <div className="pc-grid">
        <Panel span={6}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
            <span className="pc-empty-ic" style={{ width: 60, height: 60, borderRadius: 18, margin: 0 }} aria-hidden="true">
              <Icon name="shield" size={26} />
            </span>
            <div>
              <h2 style={{ fontSize: 18, margin: 0, color: 'var(--pc-ink)' }}>Platform Owner</h2>
              <div className="pc-chips" style={{ marginTop: 8 }}>
                <span className="pc-pill pc-pill-ok">Admin</span>
                <span style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 700 }}>ROLE_OWNER</span>
              </div>
            </div>
          </div>
          <div className="pc-rows">
            {[
              ['mail', 'Email', user?.email || '—'],
              ['user', 'Role', user?.role || 'ROLE_OWNER'],
            ].map(([icon, k, v]) => (
              <div key={k} className="pc-row" style={{ gridTemplateColumns: 'auto minmax(0, 1fr)' }}>
                <span style={{ color: 'var(--pc-muted)', display: 'inline-flex' }}><Icon name={icon} size={18} /></span>
                <div>
                  <small style={{ marginTop: 0, textTransform: 'uppercase', letterSpacing: '.05em' }}>{k}</small>
                  <div className="pc-row-title">{v}</div>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </DashboardLayout>
  )
}
