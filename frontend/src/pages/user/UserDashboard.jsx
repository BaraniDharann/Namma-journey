import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getUserBookings, getUserPayments } from '../../utils/api'
import { MobileNumberPrompt } from '../../components/MobileNumberGate'
import RoadTripStory from '../../components/journey/RoadTripStory'
import Icon from '../../components/dash/Icon'
import { Panel, PanelLink, PageHead, SummaryStrip, StatusPill, Empty, Skeleton, ErrorNote } from '../../components/dash/ui'
import AnalyticsCard from '../../components/dash/AnalyticsCard'
import { inr, shortPlace, spendByMonth, tripLengthSegments, totalKm, topDestinations, daysUntil, localDate } from '../../dash/metrics'

const navItems = [
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
]

const firstName = (n) => String(n || '').trim().split(/\s+/)[0]
const niceDate = (s) => localDate(s)?.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }) || s

export default function UserDashboard() {
  const { user } = useAuth()
  const [bookings, setBookings] = useState([])
  const [payments, setPayments] = useState([])
  const [state, setState] = useState({ loading: true, ok: true })

  const load = useCallback(() => {
    setState((s) => ({ ...s, loading: true }))
    Promise.all([
      getUserBookings(user.userId).then((r) => r.data || [], () => null),
      getUserPayments(user.userId).then((r) => r.data || [], () => []),
    ]).then(([b, p]) => {
      setBookings(b || [])
      setPayments(p)
      setState({ loading: false, ok: b !== null })
    })
  }, [user.userId])
  useEffect(() => { load() }, [load])

  const m = useMemo(() => {
    const now = new Date()
    const upcoming = bookings
      .filter((b) => ['PENDING', 'CONFIRMED', 'STARTED'].includes(b.status))
      .map((b) => ({ b, days: daysUntil(b.fromDate, now) }))
      .filter(({ b, days }) => b.status === 'STARTED' || (days != null && days >= 0))
      .sort((x, y) => (x.b.status === 'STARTED' ? -1 : y.b.status === 'STARTED' ? 1 : x.days - y.days))[0]
    return {
      upcoming,
      spend: spendByMonth(bookings, now),
      lengths: tripLengthSegments(bookings),
      km: totalKm(bookings),
      places: topDestinations(bookings),
      done: bookings.filter((b) => b.status === 'COMPLETED').length,
      spent: payments.reduce((s, p) => s + (Number(p.amount) || 0), 0),
      recent: [...bookings].sort((a, b) => String(b.fromDate || '').localeCompare(String(a.fromDate || ''))).slice(0, 5),
    }
  }, [bookings, payments])

  const name = firstName(user?.name)
  const next = m.upcoming?.b
  const sub = state.loading ? 'Loading your trips…'
    : next ? (next.status === 'STARTED' ? 'You are on the road right now' : m.upcoming.days === 0 ? 'Your next trip is today' : `Your next trip is in ${m.upcoming.days} day${m.upcoming.days === 1 ? '' : 's'}`)
      : 'Plan your next trip in a couple of minutes'

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      {/* Renders nothing once a number is on file. */}
      <MobileNumberPrompt />

      <PageHead title={name ? `Welcome back, ${name}` : 'Welcome back!'} sub={sub}>
        <Link to="/user/bookings/new" className="pc-btn"><Icon name="plus" />New Booking</Link>
      </PageHead>

      <RoadTripStory />

      <div className="pc-grid">
        <Panel span={7} title={next ? `${next.status === 'STARTED' ? 'On the road' : 'Upcoming'} · ${niceDate(next.fromDate)}` : 'Upcoming trip'} meta={next && <StatusPill status={next.status} label={next.status === 'PENDING' ? 'Finding a driver' : next.status === 'CONFIRMED' ? 'Driver assigned' : undefined} />}>
          {state.loading ? <Skeleton rows={4} /> : !state.ok ? <ErrorNote onRetry={load} /> : !next ? (
            <Empty icon="route" title="No trip planned" action={<Link to="/user/bookings/new" className="pc-btn pc-btn-sm"><Icon name="plus" size={15} />Book a trip</Link>}>
              Pick a destination and we&apos;ll match you with a driver.
            </Empty>
          ) : (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
                <div className="pc-route">
                  <i /><div>{shortPlace(next.fromPlace)}<small>Pickup</small></div>
                  <span className="pc-route-ln" /><span />
                  <i className="is-end" /><div>{shortPlace(next.toPlace)}<small>{next.distanceKm ? `${Math.round(next.distanceKm)} km` : 'Drop'}{next.travelDays ? ` · ${next.travelDays} day${next.travelDays === 1 ? '' : 's'}` : ''}</small></div>
                </div>
                {next.status !== 'STARTED' && (
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                    <span className="pc-figure" style={{ fontSize: 46, color: 'var(--pc-brand-deep)' }}>{m.upcoming.days}</span>
                    <span style={{ fontWeight: 800, color: 'var(--pc-ink-2)' }}>day{m.upcoming.days === 1 ? '' : 's'} to go</span>
                  </div>
                )}
              </div>
              <div className="pc-chips" style={{ marginTop: 16 }}>
                <span className="pc-chip"><Icon name="users" size={15} />{next.travelMembers || 1} traveller{(next.travelMembers || 1) === 1 ? '' : 's'}</span>
                <span className="pc-chip"><Icon name="car" size={15} />{next.acType === 'AC' ? 'AC car' : 'Non-AC car'}</span>
                <span className="pc-chip"><Icon name="wallet" size={15} />{inr(next.totalAmount)}</span>
                <Link to="/user/bookings" className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="route" size={15} />{next.status === 'STARTED' ? 'Track live' : 'View booking'}</Link>
              </div>
            </>
          )}
        </Panel>

        <AnalyticsCard
          span={5}
          title="Your travel"
          loading={state.loading}
          datasets={[
            { id: 'spend', label: 'Spent', kind: 'series', format: inr, data: m.spend },
            { id: 'length', label: 'Trip length', kind: 'parts', data: m.lengths.map(([label, value]) => ({ label, value })) },
            { id: 'places', label: 'Destinations', kind: 'parts', data: m.places.map(([label, value]) => ({ label, value })) },
          ]}
        />

        <SummaryStrip
          loading={state.loading}
          items={[
            { icon: 'route', label: 'Trips taken', value: m.done.toLocaleString('en-IN'), sub: `${bookings.length} booked in total` },
            { icon: 'map', label: 'Kilometres travelled', value: `${m.km.toLocaleString('en-IN')} km`, sub: 'across trips not cancelled' },
            { icon: 'wallet', label: 'Total paid', value: inr(m.spent), sub: `${payments.length} payment${payments.length === 1 ? '' : 's'}` },
          ]}
        />

        <Panel span={12} title="Recent trips" action={<PanelLink to="/user/bookings">View all</PanelLink>}>
          {state.loading ? <Skeleton rows={4} /> : m.recent.length === 0 ? (
            <Empty icon="calendar" title="No bookings yet">Your trips will appear here.</Empty>
          ) : (
            <div className="pc-rows">
              {m.recent.map((b) => (
                <div key={b.id} className="pc-row" style={{ gridTemplateColumns: 'minmax(0,1fr) auto' }}>
                  <div style={{ minWidth: 0 }}>
                    <div className="pc-row-title">{shortPlace(b.fromPlace)} → {shortPlace(b.toPlace)}</div>
                    <small>{niceDate(b.fromDate)} · {inr(b.totalAmount)}</small>
                  </div>
                  <StatusPill status={b.status} />
                </div>
              ))}
            </div>
          )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
