import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import BusTabs from '../../../components/bus/BusTabs'
import Icon from '../../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton, ErrorNote } from '../../../components/dash/ui'
import { BusPill, ownerNavItems, mutedStyle } from '../../../components/bus/busUi'
import { getBuses } from '../../../utils/busApi'
import { BUS_STATUS, docName, expiringDocs, daysUntil } from '../../../utils/busText'

/** The owner's buses with where each stands: draft or in service. */
export default function OwnerBuses() {
  const [buses, setBuses] = useState(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    setFailed(false)
    try { setBuses((await getBuses()).data || []) } catch { setFailed(true); setBuses([]) }
  }, [])
  useEffect(() => { load() }, [load])

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <PageHead title="Buses" sub="Add a bus, draw its seats, upload its papers and put it in service">
        <Link to="/owner/buses/new" className="pc-btn"><Icon name="plus" />Add bus</Link>
      </PageHead>
      <BusTabs />

      <div className="pc-grid">
        <Panel span={12} title="Your buses" meta={buses ? `${buses.length}` : null}>
          {buses == null ? <Skeleton rows={4} height={18} />
            : failed ? <ErrorNote onRetry={load}>Could not load your buses.</ErrorNote>
              : buses.length === 0 ? (
                <Empty icon="bus" title="No buses yet" action={<Link to="/owner/buses/new" className="pc-btn pc-btn-sm"><Icon name="plus" size={14} />Add your first bus</Link>}>
                  Each bus is checked by Namma Journeys before its seats go on sale.
                </Empty>
              ) : buses.map((b) => {
                const soon = expiringDocs(b.documents)
                return (
                  <Link key={b.id} to={`/owner/buses/${b.id}`} aria-label={`Open ${b.name || b.registrationNumber}`}
                    style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', padding: '14px 0', borderTop: '1px solid var(--pc-line)', color: 'inherit', textDecoration: 'none' }}>
                    <div style={{ minWidth: 0 }}>
                      <div className="pc-row-title" style={{ fontSize: 16 }}>{b.name || 'Unnamed bus'} · {b.registrationNumber}</div>
                      <div style={mutedStyle}>{b.typeLabel || (b.ac ? 'A/C' : 'Non-A/C')} · {(b.seats || []).length} seats</div>
                      {b.status === 'REJECTED' && b.rejectionReason && (
                        <div style={{ fontSize: 12.5, color: 'var(--pc-bad)', marginTop: 4 }}>Rejected: {b.rejectionReason}</div>
                      )}
                      {soon.map((d) => {
                        const days = daysUntil(d.expiresOn)
                        return (
                          <div key={d.type} style={{ fontSize: 12.5, color: 'var(--pc-warn)', marginTop: 4, display: 'flex', gap: 6, alignItems: 'center' }}>
                            <Icon name="alert" size={14} />
                            {docName(d.type)} {days < 0 ? 'has expired' : days === 0 ? 'expires today' : `expires in ${days} day${days === 1 ? '' : 's'}`}
                          </div>
                        )
                      })}
                    </div>
                    <BusPill table={BUS_STATUS} status={b.status} />
                  </Link>
                )
              })}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
