import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import BusTabs from '../../../components/bus/BusTabs'
import { Panel, PageHead, Empty, Skeleton, ErrorNote } from '../../../components/dash/ui'
import { BusPill, ownerNavItems, labelStyle, mutedStyle } from '../../../components/bus/busUi'
import { getBusTrips } from '../../../utils/busApi'
import { TRIP_STATUS, addDays, dateText, inr, isoDate, timeText } from '../../../utils/busText'

/** Trips in a date range: how full each one is and what it has collected. */
export default function OwnerBusTrips() {
  const [range, setRange] = useState(() => ({ from: isoDate(), to: addDays(isoDate(), 7) }))
  const [trips, setTrips] = useState(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    setTrips(null); setFailed(false)
    try { setTrips((await getBusTrips(range.from, range.to)).data || []) } catch { setFailed(true); setTrips([]) }
  }, [range])
  useEffect(() => { load() }, [load])

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <PageHead title="Bus trips" sub="Each date a published route runs. Open one for its seat chart and passenger list" />
      <BusTabs />

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 18 }}>
        <div>
          <label htmlFor="trip-from" style={labelStyle}>From</label>
          <input id="trip-from" type="date" className="input-field" value={range.from} onChange={(e) => e.target.value && setRange((r) => ({ ...r, from: e.target.value }))} />
        </div>
        <div>
          <label htmlFor="trip-to" style={labelStyle}>To</label>
          <input id="trip-to" type="date" className="input-field" min={range.from} value={range.to} onChange={(e) => e.target.value && setRange((r) => ({ ...r, to: e.target.value }))} />
        </div>
      </div>

      <div className="pc-grid">
        <Panel span={12} pad={false} title="Trips" meta={trips ? `${trips.length}` : null}>
          {trips == null ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={5} /></div>
            : failed ? <div style={{ padding: '0 20px 20px' }}><ErrorNote onRetry={load}>Could not load trips.</ErrorNote></div>
              : trips.length === 0 ? <Empty icon="bus" title="No trips in these dates">Trips appear here once a route is published.</Empty>
                : (
                  <div style={{ overflowX: 'auto' }}>
                    <table className="data-table">
                      <thead><tr><th>Date</th><th>Route</th><th>Bus</th><th>Departs</th><th style={{ textAlign: 'right' }}>Seats sold</th><th style={{ textAlign: 'right' }}>Collected</th><th>Status</th></tr></thead>
                      <tbody>
                        {trips.map((t) => (
                          <tr key={t.id}>
                            <td style={{ whiteSpace: 'nowrap' }}><Link to={`/owner/bus-trips/${t.id}`}><strong>{dateText(t.travelDate)}</strong></Link></td>
                            <td>{t.routeName}</td>
                            <td>{t.busName}<div style={mutedStyle}>{t.registrationNumber}</div></td>
                            <td style={{ whiteSpace: 'nowrap' }}>{timeText(t.departureAt)}</td>
                            <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{t.seatsSold} / {t.seatsTotal}</td>
                            <td style={{ textAlign: 'right', fontWeight: 800 }}>{inr(t.amountCollected)}</td>
                            <td><BusPill table={TRIP_STATUS} status={t.status} /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
