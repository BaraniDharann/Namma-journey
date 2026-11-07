import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import { Panel, PageHead, Empty, Skeleton, ErrorNote, SummaryStrip } from '../../../components/dash/ui'
import Icon from '../../../components/dash/Icon'
import { BusPill, ownerNavItems, mutedStyle } from '../../../components/bus/busUi'
import BusTabs from '../../../components/bus/BusTabs'
import { getBusDay } from '../../../utils/expenseApi'
import { TRIP_STATUS, addDays, dateText, inr, isoDate } from '../../../utils/busText'

const clock = (iso) => (iso ? new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : '—')

/** How full a trip is, as a bar. */
function SeatBar({ sold, total }) {
  const pct = total > 0 ? Math.round((sold / total) * 100) : 0
  return (
    <div aria-label={`${sold} of ${total} seats sold`} style={{ height: 8, borderRadius: 99, background: 'var(--pc-wash-2)', overflow: 'hidden' }}>
      <div style={{ width: `${pct}%`, height: '100%', borderRadius: 99, background: pct >= 90 ? 'var(--pc-good, #16a34a)' : 'var(--pc-brand)' }} />
    </div>
  )
}

/** The bus home: each of the day's departures with seats, booking close, revenue, expense and profit. */
export default function OwnerBusToday() {
  const [date, setDate] = useState(isoDate())
  const [day, setDay] = useState(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    setDay(null); setFailed(false)
    try { setDay((await getBusDay(date)).data) } catch { setFailed(true) }
  }, [date])
  useEffect(() => { load() }, [load])

  const isToday = date === isoDate()
  const when = isToday ? 'today' : dateText(date)

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <PageHead title="Bus" sub={`Every bus departing ${when}: seats sold, when booking closes, and what each trip earned`}>
        <div style={{ display: 'flex', gap: 6 }}>
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setDate((d) => addDays(d, -1))} aria-label="Previous day"><Icon name="back" size={16} /></button>
          <input type="date" className="input-field" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} aria-label="Day" style={{ width: 160 }} />
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setDate((d) => addDays(d, 1))} aria-label="Next day"><Icon name="arrow" size={16} /></button>
        </div>
      </PageHead>
      <BusTabs />

      <div className="pc-grid">
        <SummaryStrip loading={!day && !failed} items={[
          { icon: 'route', label: 'Departures', value: day?.trips ?? 0, sub: isToday ? 'Today' : dateText(date) },
          { icon: 'users', label: 'Seats sold', value: day ? `${day.seatsSold} / ${day.seatsTotal}` : '—', sub: day && day.seatsTotal ? `${Math.round((day.seatsSold / day.seatsTotal) * 100)}% full` : 'No seats on sale' },
          { icon: 'wallet', label: 'Revenue', value: inr(day?.revenue), sub: 'Fares, before GST' },
          { icon: 'chart', label: 'Profit', value: inr(day?.profit), sub: `After ${inr(day?.expenses)} trip expenses`, tint: Number(day?.profit) < 0 ? 'pink' : 'green' },
        ]} />

        <Panel span={12} pad={false} title={`Departures ${when}`} meta={day ? `${day.trips}` : null}>
          {failed ? <div style={{ padding: '0 20px 20px' }}><ErrorNote onRetry={load}>Could not load the day.</ErrorNote></div>
            : !day ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={4} /></div>
              : day.list.length === 0 ? (
                <Empty icon="bus" title={`No buses run ${when}`}
                  action={<Link to="/owner/bus-routes" className="pc-btn pc-btn-sm">Set up a route</Link>}>
                  A route makes trips on the days it runs. Publish one and its trips appear here.
                </Empty>
              ) : (
                <ul style={{ listStyle: 'none', margin: 0, padding: '0 12px 12px', display: 'grid', gap: 10 }}>
                  {day.list.map(({ trip: t, seatsOpen, bookingClosesAt, bookingOpen, expenses, profit }) => (
                    <li key={t.id}>
                      <Link to={`/owner/bus-trips/${t.id}`} className="pc-bus-day" style={{ display: 'grid', gap: 10, padding: 16, borderRadius: 14, border: '1px solid var(--pc-line)', background: 'var(--pc-panel)', color: 'inherit', textDecoration: 'none' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', alignItems: 'baseline' }}>
                          <div>
                            <strong style={{ fontSize: 16 }}>{t.routeName}</strong>
                            <div style={mutedStyle}>{t.busName} · {t.registrationNumber} · departs {clock(t.departureAt)}</div>
                          </div>
                          <BusPill table={TRIP_STATUS} status={t.status} />
                        </div>
                        <SeatBar sold={t.seatsSold} total={t.seatsTotal} />
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))', gap: 10, fontSize: 13 }}>
                          <div><div style={mutedStyle}>Seats</div><strong>{t.seatsSold} sold · {seatsOpen} open</strong></div>
                          <div><div style={mutedStyle}>Booking</div><strong>{bookingOpen ? `Open till ${clock(bookingClosesAt)}` : 'Closed'}</strong></div>
                          <div><div style={mutedStyle}>Revenue</div><strong>{inr(t.amountCollected)}</strong></div>
                          <div><div style={mutedStyle}>Expense</div><strong>{inr(expenses)}</strong></div>
                          <div><div style={mutedStyle}>Profit</div><strong style={{ color: Number(profit) < 0 ? 'var(--pc-bad)' : undefined }}>{inr(profit)}</strong></div>
                        </div>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
        </Panel>

        <Panel span={12} title="Running costs">
          <p style={{ ...mutedStyle, fontSize: 13, margin: 0 }}>
            Add fuel, toll and driver pay for a trip on the <Link to="/owner/bus-expenses">Expenses</Link> tab; profit here updates straight away.
          </p>
        </Panel>
      </div>
    </DashboardLayout>
  )
}
