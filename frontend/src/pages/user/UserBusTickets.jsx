import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton } from '../../components/dash/ui'
import { BusPill, Tabs } from '../../components/bus/busUi'
import { getBusTickets } from '../../utils/busApi'
import { errorText, inr } from '../../utils/busText'
import { clockText } from '../../utils/busSearch'
import { TICKET_STATUS, ticketTab, ticketsForTab, whenText } from '../../utils/busBooking'
import { userNavItems } from '../../utils/userMenu'
import '../../styles/bus.css'

const TABS = [['UPCOMING', 'Upcoming'], ['COMPLETED', 'Completed'], ['CANCELLED', 'Cancelled']]

/** The traveller's bus tickets, by Upcoming / Completed / Cancelled. */
export default function UserBusTickets() {
  const [tab, setTab] = useState('UPCOMING')
  const [tickets, setTickets] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try { setTickets((await getBusTickets()).data || []) } catch (e) { setError(errorText(e, 'Could not load your bus tickets.')); setTickets([]) }
  }, [])
  useEffect(() => { load() }, [load])

  const counts = useMemo(() => {
    const c = { UPCOMING: 0, COMPLETED: 0, CANCELLED: 0 }
    for (const t of tickets || []) { const k = ticketTab(t); if (k) c[k]++ }
    return c
  }, [tickets])
  const rows = useMemo(() => ticketsForTab(tickets, tab), [tickets, tab])

  return (
    <DashboardLayout navItems={userNavItems} role="ROLE_USER">
      <PageHead title="Bus tickets" sub={tickets ? `${counts.UPCOMING} upcoming journey${counts.UPCOMING === 1 ? '' : 's'}` : 'Loading…'}>
        <Link to="/?mode=bus" className="pc-btn"><Icon name="bus" />Book a bus</Link>
      </PageHead>
      <Tabs tabs={TABS.map(([k, label]) => [k, tickets ? `${label} (${counts[k]})` : label])} value={tab} onChange={setTab} label="Ticket status" />

      <div className="pc-grid">
        {tickets == null ? (
          <Panel span={12}><Skeleton rows={4} height={18} /></Panel>
        ) : error ? (
          <Panel span={12}>
            <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span style={{ flex: 1 }}>{error}</span><button type="button" className="pc-btn pc-btn-sm" onClick={load}>Retry</button></div>
          </Panel>
        ) : rows.length === 0 ? (
          <Panel span={12}>
            <Empty icon="ticket" title={tab === 'UPCOMING' ? 'No journeys coming up' : 'Nothing here yet'}
              action={tab === 'UPCOMING' ? <Link to="/?mode=bus" className="pc-btn pc-btn-sm"><Icon name="search" size={15} />Search buses</Link> : null}>
              {tab === 'UPCOMING' ? 'Book a seat on any operator’s bus and your ticket shows up here.' : ''}
            </Empty>
          </Panel>
        ) : (
          <Panel span={12} title={TABS.find(([k]) => k === tab)[1]} meta={`${rows.length}`}>
            {rows.map((t) => {
              const live = (t.passengers || []).filter((p) => p.status !== 'CANCELLED')
              return (
                <Link key={t.id} to={`/user/bus-tickets/${t.id}`} aria-label={`Open ticket ${t.ticketNo}`}
                  style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: 12, padding: '14px 0', borderTop: '1px solid var(--pc-line)', color: 'inherit', textDecoration: 'none' }}>
                  <div style={{ minWidth: 0 }}>
                    <div className="pc-row-title" style={{ fontSize: 16 }}>{t.fromPlace} → {t.toPlace}</div>
                    <div style={{ fontSize: 13, color: 'var(--pc-ink-2)', fontWeight: 600, marginTop: 3 }}>
                      {whenText(t.travelDate)} · boards {clockText(t.boardingPoint?.at)} at {t.boardingPoint?.name}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 600, marginTop: 2 }}>
                      {t.travelsName} · {t.ticketNo} · Seat{live.length === 1 ? '' : 's'} {(live.length ? live : t.passengers || []).map((p) => p.seatLabel).join(', ')}
                    </div>
                  </div>
                  <div style={{ display: 'grid', justifyItems: 'end', gap: 6, alignContent: 'center' }}>
                    <BusPill table={TICKET_STATUS} status={t.status} />
                    <b style={{ fontVariantNumeric: 'tabular-nums' }}>{inr(t.totalAmount)}</b>
                  </div>
                </Link>
              )
            })}
          </Panel>
        )}
      </div>
    </DashboardLayout>
  )
}
