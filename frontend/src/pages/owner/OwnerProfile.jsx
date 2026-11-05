import React from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import useOwnerMe from '../../hooks/useOwnerMe'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../components/dash/ui'
import FleetTypePanel from '../../components/bus/FleetTypePanel'

function Rows({ rows }) {
  return (
    <div className="pc-rows">
      {rows.map(([icon, k, v]) => (
        <div key={k} className="pc-row" style={{ gridTemplateColumns: 'auto minmax(0, 1fr)' }}>
          <span style={{ color: 'var(--pc-muted)', display: 'inline-flex' }}><Icon name={icon} size={18} /></span>
          <div>
            <small style={{ marginTop: 0, textTransform: 'uppercase', letterSpacing: '.05em' }}>{k}</small>
            <div className="pc-row-title" style={{ overflowWrap: 'anywhere' }}>{v || '—'}</div>
          </div>
        </div>
      ))}
    </div>
  )
}

export default function OwnerProfile() {
  const me = useOwnerMe()

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      <PageHead title="Owner Profile" sub="Your travels, as travellers and NammaJourney see it" />

      <div className="pc-grid">
        <Panel span={6}>
          {!me ? <Skeleton rows={5} height={18} /> : (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
                <span className="pc-empty-ic" style={{ width: 60, height: 60, borderRadius: 18, margin: 0 }} aria-hidden="true">
                  <Icon name="shield" size={26} />
                </span>
                <div>
                  <h2 style={{ fontSize: 18, margin: 0, color: 'var(--pc-ink)' }}>{me.travelsName}</h2>
                  <div className="pc-chips" style={{ marginTop: 8 }}>
                    <span className="pc-pill pc-pill-ok">{me.ownerCode}</span>
                    {me.platform && <span className="pc-pill pc-pill-go">Platform</span>}
                  </div>
                </div>
              </div>
              <Rows rows={[
                ['user', 'Contact', me.contactName],
                ['mail', 'Email', me.email],
                ['phone', 'Phone', me.phone],
                ['map', 'Cities', (me.cities || []).join(', ')],
                ['wheel', 'Trip assignment', me.assignmentMode === 'MANUAL' ? 'By hand' : 'Automatic'],
              ]} />
            </>
          )}
        </Panel>
        <Panel span={6} title="Payouts" meta="masked for safety">
          {!me ? <Skeleton rows={4} height={18} /> : (
            <Rows rows={[
              ['user', 'Account holder', me.bankAccountHolder],
              ['card', 'Account', me.bankAccountMasked],
              ['id', 'IFSC', me.bankIfsc],
              ['wallet', 'Bank', me.bankName],
              ['qr', 'UPI ID', me.upiId],
            ]} />
          )}
        </Panel>
        <FleetTypePanel span={6} />
      </div>
    </DashboardLayout>
  )
}
