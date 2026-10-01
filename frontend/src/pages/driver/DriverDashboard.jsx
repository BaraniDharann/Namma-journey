import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import DashboardLayout from '../../components/DashboardLayout'
import LiveTrackingMap from '../../components/LiveTrackingMap'
import { useAuth } from '../../context/AuthContext'
import { getDriverBookings, getDriverProfile, toggleDriverAvailability, driverBookingAction } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PanelLink, PageHead, StatusPill, Empty, Skeleton, ErrorNote } from '../../components/dash/ui'
import { Gauge } from '../../components/dash/charts'
import AnalyticsCard from '../../components/dash/AnalyticsCard'
import { useCelebrate } from '../../components/celebrate/Celebration'
import { inr, shortPlace, todayStats, earningsThisWeek, completionRate, nextTrip, typeMix, daysUntil, statusSegments } from '../../dash/metrics'

const navItems = [
  { path: '/driver/dashboard', icon: '', label: 'Dashboard' },
  { path: '/driver/bookings', icon: '', label: 'My Trips' },
  { path: '/driver/profile', icon: '', label: 'Profile' },
]

const firstName = (n) => String(n || '').trim().split(/\s+/)[0] || 'there'
const whenLabel = (b) => {
  const days = daysUntil(b.fromDate)
  if (days === 0) return 'Today'
  if (days === 1) return 'Tomorrow'
  return b.fromDate
}
const mapsUrl = (b) =>
  b.toLat != null && b.toLon != null
    ? `https://www.google.com/maps/dir/?api=1&origin=${b.fromLat},${b.fromLon}&destination=${b.toLat},${b.toLon}&travelmode=driving`
    : `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(b.toPlace || '')}`

export default function DriverDashboard() {
  const { user } = useAuth()
  const celebrate = useCelebrate()
  const [bookings, setBookings] = useState([])
  const [state, setState] = useState({ loading: true, ok: true })
  const [status, setStatus] = useState(null)
  const [toggling, setToggling] = useState(false)
  const [acting, setActing] = useState(null)

  const load = useCallback(() => {
    setState((s) => ({ ...s, loading: true }))
    getDriverBookings(user.userId)
      .then((r) => { setBookings(r.data || []); setState({ loading: false, ok: true }) })
      .catch(() => setState({ loading: false, ok: false }))
  }, [user.userId])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    getDriverProfile(user.userId).then((r) => setStatus(r.data?.status || 'ACTIVE')).catch(() => setStatus('ACTIVE'))
  }, [user.userId])

  const online = status === 'ACTIVE'
  const toggleOnline = async () => {
    if (toggling || status == null) return
    const next = online ? 'INACTIVE' : 'ACTIVE'
    setToggling(true)
    try {
      await toggleDriverAvailability(user.userId, next)
      setStatus(next)
      toast.success(next === 'ACTIVE' ? "You're online. New trips can reach you." : "You're offline. No new trips will be sent.")
    } catch { /* surfaced by the api toast */ }
    setToggling(false)
  }

  const act = async (b, action) => {
    setActing(b.bookingId + action)
    try {
      await driverBookingAction(user.userId, b.bookingId, action)
      if (action === 'ACCEPT') {
        celebrate({
          title: 'Trip accepted',
          message: `${shortPlace(b.fromPlace)} → ${shortPlace(b.toPlace)}. The traveller has been told you're on the way.`,
        })
      } else {
        toast.success('Trip declined')
      }
      load()
    } catch { /* surfaced by the api toast */ }
    setActing(null)
  }

  const m = useMemo(() => {
    const now = new Date()
    return {
      today: todayStats(bookings, now),
      week: earningsThisWeek(bookings, now),
      rate: completionRate(bookings),
      next: nextTrip(bookings, now),
      mix: typeMix(bookings),
      requests: bookings.filter((b) => b.status === 'PENDING'),
      started: bookings.find((b) => b.status === 'STARTED'),
      done: bookings.filter((b) => b.status === 'COMPLETED').length,
      lifetime: bookings.filter((b) => b.status === 'COMPLETED').reduce((s, b) => s + (Number(b.totalAmount) || 0), 0),
    }
  }, [bookings])

  const sub = state.loading ? 'Loading your trips…'
    : `${m.today.trips} trip${m.today.trips === 1 ? '' : 's'} done today${m.next ? ` · next: ${whenLabel(m.next)}` : ''}`

  return (
    <DashboardLayout navItems={navItems} role="ROLE_DRIVER">
      <PageHead title={`Hi ${firstName(user?.name)}, you're ${online ? 'online' : 'offline'}`} sub={sub}>
        <button
          type="button"
          className="pc-switch"
          role="switch"
          aria-checked={online}
          onClick={toggleOnline}
          disabled={toggling || status == null}
        >
          <span className="pc-switch-track" />
          {online ? 'Online' : 'Offline'}
        </button>
      </PageHead>

      <div className="pc-grid">
        <Panel span={7} title={m.next ? `${m.next.status === 'STARTED' ? 'Trip in progress' : 'Next trip'} · ${whenLabel(m.next)}` : 'Next trip'} meta={m.next && <StatusPill status={m.next.status} />}>
          {state.loading ? <Skeleton rows={4} /> : !state.ok ? <ErrorNote onRetry={load} /> : !m.next ? (
            <Empty icon="route" title="No trip lined up">{online ? 'Stay online and new trips will appear here.' : 'Go online to start receiving trips.'}</Empty>
          ) : (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
                <div className="pc-route">
                  <i /><div>{shortPlace(m.next.fromPlace)}<small>Pickup · {m.next.userName || 'Traveller'} · {m.next.travelMembers || 1} passenger{(m.next.travelMembers || 1) === 1 ? '' : 's'}</small></div>
                  <span className="pc-route-ln" /><span />
                  <i className="is-end" /><div>{shortPlace(m.next.toPlace)}<small>Drop{m.next.distanceKm ? ` · ${Math.round(m.next.distanceKm)} km` : ''}{m.next.estimatedTimeMinutes ? ` · ~${Math.round(m.next.estimatedTimeMinutes)} min` : ''}</small></div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div className="pc-figure" style={{ fontSize: 34 }}>{inr(m.next.totalAmount)}</div>
                  <small style={{ color: 'var(--pc-muted)', fontWeight: 700 }}>fare</small>
                </div>
              </div>
              <div className="pc-chips" style={{ marginTop: 16 }}>
                <a className="pc-btn" href={mapsUrl(m.next)} target="_blank" rel="noreferrer"><Icon name="nav" />Navigate</a>
                {m.next.userPhone && <a className="pc-btn pc-btn-ghost" href={`tel:${m.next.userPhone}`}><Icon name="phone" />Call {firstName(m.next.userName)}</a>}
                <Link className="pc-btn pc-btn-ghost" to="/driver/bookings"><Icon name="route" />Open in My Trips</Link>
              </div>
            </>
          )}
        </Panel>

        <Panel span={5} title="Today" meta="resets at midnight">
          {state.loading ? <Skeleton rows={3} /> : (
            <>
              <div className="pc-figure" style={{ fontSize: 38 }}>{inr(m.today.earned)}</div>
              <p style={{ margin: '6px 0 0', color: 'var(--pc-muted)', fontWeight: 700, fontSize: 13 }}>earned from completed trips today</p>
              <div className="pc-minis" style={{ marginTop: 14 }}>
                <div><b>{m.today.trips}</b>trips</div>
                <div><b>{Math.round(m.today.km)} km</b>driven</div>
                <div><b>{inr(m.lifetime)}</b>all time</div>
              </div>
            </>
          )}
        </Panel>

        {m.started && (
          <Panel span={12} title="Live trip map" meta={<span className="pc-pill pc-pill-live">Sharing location</span>}>
            <LiveTrackingMap
              bookingId={m.started.bookingId}
              fromLat={m.started.fromLat}
              fromLon={m.started.fromLon}
              toLat={m.started.toLat}
              toLon={m.started.toLon}
              fromPlace={m.started.fromPlace}
              toPlace={m.started.toPlace}
              travelMembers={m.started.travelMembers}
              isDriver
              driverId={user.userId}
            />
          </Panel>
        )}

        <AnalyticsCard
          span={9}
          title="Your driving analytics"
          loading={state.loading}
          datasets={[
            { id: 'week', label: 'Earnings this week', kind: 'series', format: inr, data: m.week },
            { id: 'mix', label: 'Trip mix', kind: 'parts', data: m.mix.map(([label, value]) => ({ label, value })) },
            { id: 'status', label: 'Trips by status', kind: 'parts', data: statusSegments(bookings).map(([label, value]) => ({ label, value })) },
          ]}
        />

        <Panel span={3} title="Completion" meta="all trips">
          {state.loading ? <Skeleton rows={3} /> : m.done === 0 && m.rate === 0
            ? <Empty icon="check" title="No trips yet">Your completion rate appears after your first trip.</Empty>
            : <Gauge pct={m.rate} value={`${Math.round(m.rate * 100)}%`} sub="of assigned trips done" caption="Completion rate" />}
        </Panel>

        <Panel span={12} title="New requests" meta={m.requests.length ? `${m.requests.length} waiting` : undefined} action={<PanelLink to="/driver/bookings">All trips</PanelLink>}>
          {state.loading ? <Skeleton rows={3} /> : m.requests.length === 0 ? (
            <Empty icon="bell" title="No new requests">We&apos;ll notify you when an owner assigns you a trip.</Empty>
          ) : (
            <div className="pc-rows">
              {m.requests.slice(0, 5).map((b) => (
                <div key={b.bookingId} className="pc-row">
                  <div style={{ minWidth: 0 }}>
                    <div className="pc-row-title">{shortPlace(b.fromPlace)} → {shortPlace(b.toPlace)}</div>
                    <small>{whenLabel(b)} · {b.travelMembers || 1} passenger{(b.travelMembers || 1) === 1 ? '' : 's'}{b.distanceKm ? ` · ${Math.round(b.distanceKm)} km` : ''}{b.acType ? ` · ${b.acType === 'AC' ? 'AC' : 'Non-AC'}` : ''}</small>
                  </div>
                  <b>{inr(b.totalAmount)}</b>
                  <div className="pc-chips">
                    <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" disabled={!!acting} onClick={() => act(b, 'REJECT')}>Decline</button>
                    <button type="button" className="pc-btn pc-btn-sm" disabled={!!acting} onClick={() => act(b, 'ACCEPT')}><Icon name="check" size={15} />Accept</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
