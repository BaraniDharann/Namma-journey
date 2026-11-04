import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import Icon from '../../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton, ErrorNote } from '../../../components/dash/ui'
import { BusPill, ownerNavItems, mutedStyle } from '../../../components/bus/busUi'
import { getBusRoutes } from '../../../utils/busApi'
import { ROUTE_STATUS } from '../../../utils/busText'
import { runDaysText } from '../../../utils/busFares'

/** The owner's bus routes: which bus runs where, on which days. */
export default function OwnerBusRoutes() {
  const [routes, setRoutes] = useState(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    setFailed(false)
    try { setRoutes((await getBusRoutes()).data || []) } catch { setFailed(true); setRoutes([]) }
  }, [])
  useEffect(() => { load() }, [load])

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <PageHead title="Bus routes" sub="Stops, boarding points, fares and the days each bus runs">
        <Link to="/owner/bus-routes/new" className="pc-btn"><Icon name="plus" />New route</Link>
      </PageHead>

      <div className="pc-grid">
        <Panel span={12} title="Routes" meta={routes ? `${routes.length}` : null}>
          {routes == null ? <Skeleton rows={4} height={18} />
            : failed ? <ErrorNote onRetry={load}>Could not load your routes.</ErrorNote>
              : routes.length === 0 ? (
                <Empty icon="route" title="No routes yet" action={<Link to="/owner/bus-routes/new" className="pc-btn pc-btn-sm"><Icon name="plus" size={14} />Create a route</Link>}>
                  A route needs an approved bus. Trips are created for the next 60 days once it is published.
                </Empty>
              ) : routes.map((r) => {
                const stops = r.stops || []
                return (
                  <Link key={r.id} to={`/owner/bus-routes/${r.id}`} aria-label={`Open route ${r.name}`}
                    style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', padding: '14px 0', borderTop: '1px solid var(--pc-line)', color: 'inherit', textDecoration: 'none' }}>
                    <div style={{ minWidth: 0 }}>
                      <div className="pc-row-title" style={{ fontSize: 16 }}>{r.name}</div>
                      <div style={mutedStyle}>
                        {r.busName || `Bus #${r.busId}`} · {runDaysText(r.runDays)}
                        {stops.length > 1 ? ` · ${stops.map((s) => s.placeName).join(' → ')}` : ''}
                      </div>
                    </div>
                    <BusPill table={ROUTE_STATUS} status={r.status} />
                  </Link>
                )
              })}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
