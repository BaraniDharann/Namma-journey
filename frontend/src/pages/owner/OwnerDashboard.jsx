import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import {
  getOwnerBookings, getPendingPayments, getAllReviews, getMonthlyRevenue, getMonthlyRevenueSeries, getOwnerDrivers,
} from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PanelLink, PageHead, SummaryStrip, TaskList, StatusPill, Empty, Skeleton, ErrorNote } from '../../components/dash/ui'
import { Gauge, Heatmap } from '../../components/dash/charts'
import AnalyticsCard from '../../components/dash/AnalyticsCard'
import {
  inr, shortPlace, statusSegments, topRoutes, bookingHeatmap, fleetBusy, waitingForDriver, monthSeries, pctChange,
  WEEKDAYS, TIME_BANDS,
} from '../../dash/metrics'

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

const greeting = (d = new Date()) => (d.getHours() < 12 ? 'Good morning' : d.getHours() < 17 ? 'Good afternoon' : 'Good evening')
const longDate = (d = new Date()) => d.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })

/** Load one call per card, so one failure only empties its own card. */
function useOwnerData() {
  const [state, setState] = useState({ loading: true })
  const load = useCallback(() => {
    const now = new Date()
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1)
    const settle = (p) => p.then((r) => ({ ok: true, data: r.data }), () => ({ ok: false, data: null }))
    setState((s) => ({ ...s, loading: true }))
    Promise.all([
      settle(getOwnerBookings()),
      settle(getPendingPayments()),
      settle(getAllReviews()),
      settle(getOwnerDrivers()),
      settle(getMonthlyRevenue(now.getFullYear(), now.getMonth() + 1, { silent: true })),
      settle(getMonthlyRevenue(prev.getFullYear(), prev.getMonth() + 1, { silent: true })),
      settle(getMonthlyRevenueSeries(now.getFullYear(), { silent: true })),
    ]).then(([bookings, payments, reviews, drivers, rev, revPrev, series]) => {
      const list = (r) => (Array.isArray(r.data) ? r.data : r.data?.content || [])
      setState({
        loading: false,
        bookings: list(bookings), bookingsOk: bookings.ok,
        payments: list(payments),
        reviews: list(reviews),
        drivers: list(drivers), driversOk: drivers.ok,
        revenue: rev.data, revenuePrev: revPrev.data,
        series: series.data, seriesOk: series.ok,
      })
    })
  }, [])
  useEffect(() => { load() }, [load])
  return [state, load]
}

export default function OwnerDashboard() {
  const { user } = useAuth()
  const [d, reload] = useOwnerData()
  const bookings = d.bookings || []
  const drivers = d.drivers || []
  const payments = d.payments || []
  const reviews = d.reviews || []

  const m = useMemo(() => {
    const months = monthSeries(d.series, new Date())
    const thisMonth = d.revenue?.totalRevenue ?? months[months.length - 1]?.revenue ?? 0
    const lastMonth = d.revenuePrev?.totalRevenue ?? months[months.length - 2]?.revenue ?? 0
    const fleet = fleetBusy(bookings, drivers)
    const avg = reviews.length ? reviews.reduce((s, r) => s + (Number(r.rating) || 0), 0) / reviews.length : null
    return {
      months,
      thisMonth,
      lastMonth,
      change: pctChange(thisMonth, lastMonth),
      fleet,
      waiting: waitingForDriver(bookings),
      pending: bookings.filter((b) => b.status === 'PENDING').length,
      onRoad: bookings.filter((b) => b.status === 'STARTED').length,
      paymentsDue: payments.reduce((s, p) => s + (Number(p.amount) || 0), 0),
      notSetUp: drivers.filter((x) => x.firstLogin || !x.telegramLinked).length,
      avg,
      status: statusSegments(bookings),
      routes: topRoutes(bookings),
      heat: bookingHeatmap(bookings),
      recent: [...bookings].sort((a, b) => String(b.bookingDate || '').localeCompare(String(a.bookingDate || ''))).slice(0, 6),
    }
  }, [d.series, d.revenue, d.revenuePrev, bookings, drivers, payments, reviews])

  const tasks = [
    { icon: 'calendar', count: m.waiting, label: m.waiting === 1 ? 'booking waiting for a driver' : 'bookings waiting for a driver', cta: 'Assign', to: '/owner/bookings' },
    { icon: 'wallet', count: payments.length, label: `${payments.length === 1 ? 'payment' : 'payments'} of ${inr(m.paymentsDue)} to verify`, cta: 'Verify', to: '/owner/payments' },
    { icon: 'alert', count: m.notSetUp, label: m.notSetUp === 1 ? 'driver still setting up' : 'drivers still setting up', cta: 'Review', to: '/owner/drivers' },
  ]
  const openTasks = tasks.filter((t) => t.count > 0).length

  const change = !m.thisMonth ? `${inr(m.lastMonth)} last month` : m.change == null ? 'no figures for last month'
    : <><span className={m.change >= 0 ? 'pc-up' : 'pc-down'}>{m.change >= 0 ? '▲' : '▼'} {Math.abs(m.change).toFixed(1)}%</span> vs last month</>

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title={user?.name ? `${greeting()}, ${String(user.name).trim().split(/\s+/)[0]}` : greeting()} sub={`${longDate()} · ${m.fleet.busy} of ${m.fleet.total} drivers out on a trip`}>
        <Link to="/owner/revenue" className="pc-btn pc-btn-ghost"><Icon name="chart" />Revenue report</Link>
        <Link to="/owner/drivers" className="pc-btn"><Icon name="plus" />Add driver</Link>
      </PageHead>

      <div className="pc-grid">
        <section className="pc-today" aria-label="Needs you today">
          <div>
            <h2>{d.loading ? 'Checking today…' : openTasks ? `${openTasks} thing${openTasks === 1 ? '' : 's'} need${openTasks === 1 ? 's' : ''} you today` : "You're all caught up"}</h2>
            <p>{m.pending} pending booking{m.pending === 1 ? '' : 's'}, {m.onRoad} trip{m.onRoad === 1 ? '' : 's'} on the road right now.</p>
          </div>
          {d.loading ? <Skeleton rows={2} /> : <TaskList tasks={tasks} tone="hero" />}
        </section>

        <SummaryStrip
          loading={d.loading}
          items={[
            { icon: 'wallet', label: 'Revenue this month', value: inr(m.thisMonth), sub: change },
            { icon: 'calendar', label: 'Bookings', value: bookings.length.toLocaleString('en-IN'), sub: `${m.pending} pending · ${m.onRoad} on the road` },
            { icon: 'car', label: 'Fleet busy', value: `${Math.round(m.fleet.pct * 100)}%`, sub: `${m.fleet.busy} of ${m.fleet.total} drivers` },
            { icon: 'star', label: 'Rating', value: m.avg == null ? '—' : m.avg.toFixed(1), sub: reviews.length ? `${reviews.length} review${reviews.length === 1 ? '' : 's'}` : 'No reviews yet' },
          ]}
        />

        <AnalyticsCard
          span={8}
          title="Business analytics"
          loading={d.loading}
          datasets={[
            { id: 'revenue', label: 'Revenue', kind: 'series', format: inr, data: m.months.map((x) => ({ label: x.label, value: x.revenue, note: `${x.trips} trips` })) },
            { id: 'trips', label: 'Trips', kind: 'series', format: (v) => `${Math.round(v)} trips`, data: m.months.map((x) => ({ label: x.label, value: x.trips })) },
            { id: 'status', label: 'Booking status', kind: 'parts', data: m.status.map(([label, value]) => ({ label, value })) },
            { id: 'routes', label: 'Top routes', kind: 'parts', data: m.routes.map(([label, value]) => ({ label, value })) },
          ]}
        />

        <Panel span={4} title="Fleet right now" meta={<span className="pc-pill pc-pill-live">Live</span>}>
          {d.loading ? <Skeleton rows={3} /> : !d.driversOk ? <ErrorNote onRetry={reload} /> : m.fleet.total === 0
            ? <Empty icon="car" title="No drivers yet" action={<Link to="/owner/drivers" className="pc-btn pc-btn-sm">Add a driver</Link>} />
            : <Gauge pct={m.fleet.pct} value={`${m.fleet.busy}/${m.fleet.total}`} sub="drivers on a trip" caption="Fleet busy" />}
        </Panel>

        <Panel span={12} title="When people book" meta="booking times, by weekday">
          {d.loading ? <Skeleton rows={4} /> : (
            <Heatmap matrix={m.heat} rows={WEEKDAYS} cols={TIME_BANDS} note="darker means busier" caption="When people book" />
          )}
        </Panel>

        <Panel span={12} pad={false} title="Recent bookings" action={<PanelLink to="/owner/bookings">All bookings</PanelLink>}>
          {d.loading ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={4} /></div>
            : !d.bookingsOk ? <div style={{ padding: '0 20px 20px' }}><ErrorNote onRetry={reload} /></div>
              : m.recent.length === 0 ? <Empty icon="calendar" title="No bookings yet">New bookings will show up here as soon as travellers make them.</Empty>
                : (
                  <div style={{ overflowX: 'auto' }}>
                    <table className="data-table">
                      <thead><tr><th>Customer</th><th>Route</th><th>Date</th><th style={{ textAlign: 'right' }}>Amount</th><th>Status</th></tr></thead>
                      <tbody>
                        {m.recent.map((b) => (
                          <tr key={b.id}>
                            <td><div className="pc-row-title">{b.userName || '—'}</div><small style={{ color: 'var(--pc-muted)' }}>{b.userPhone}</small></td>
                            <td><div className="pc-row-title">{shortPlace(b.fromPlace)} → {shortPlace(b.toPlace)}</div></td>
                            <td>{b.fromDate}</td>
                            <td style={{ textAlign: 'right', fontWeight: 800, color: 'var(--pc-ink)' }}>{inr(b.totalAmount)}</td>
                            <td><StatusPill status={b.status} /></td>
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
