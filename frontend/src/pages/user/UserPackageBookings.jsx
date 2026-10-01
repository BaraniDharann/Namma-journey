import React, { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getUserPackageBookings, cancelPackageBooking } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'

const STATUSES = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']

const navItems = [
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
]

export default function UserPackageBookings() {
  const { user } = useAuth()
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')

  useEffect(() => { if (user) fetchBookings() }, [user])

  const fetchBookings = async () => {
    setLoading(true)
    try {
      const res = await getUserPackageBookings(user.userId)
      setBookings(res.data || [])
    } catch { setBookings([]) }
    setLoading(false)
  }

  const handleCancel = async (id) => {
    const reason = prompt('Reason for cancellation:')
    if (!reason) return
    try { await cancelPackageBooking(user.userId, id, reason); fetchBookings() } catch { /* already surfaced by the api error toast */ }
  }

  const filtered = filter === 'ALL' ? bookings : bookings.filter(b => b.status === filter)
  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 6)

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <PageHead title="My Package Bookings" sub="Track all your travel package bookings">
        <Link to="/" className="pc-btn pc-btn-ghost"><Icon name="package" />Explore packages</Link>
      </PageHead>

      <div className="pc-grid">
        <Panel span={12} title="Filter by status" meta={!loading && `${filtered.length} shown`}>
          <div className="pc-chips" role="group" aria-label="Filter package bookings by status">
            {STATUSES.map(s => (
              <button key={s} type="button" aria-pressed={filter === s} onClick={() => { setFilter(s); setCurrentPage(1) }}
                className={`pc-btn pc-btn-sm ${filter === s ? '' : 'pc-btn-ghost'}`}>{s}</button>
            ))}
          </div>
        </Panel>

        {loading ? (
          <Panel span={12}><Skeleton rows={4} /></Panel>
        ) : filtered.length === 0 ? (
          <Panel span={12}>
            <Empty icon="package" title="No package bookings yet" action={<Link to="/" className="pc-btn pc-btn-sm"><Icon name="search" size={15} />Explore packages</Link>}>
              Book a travel package to get started.
            </Empty>
          </Panel>
        ) : (
          paginatedItems.map(b => (
            <Panel key={b.id} span={6} title={b.packageName} meta={<StatusPill status={b.status} />}>
              <div className="pc-chips">
                <span className="pc-chip"><Icon name="package" size={15} />{b.packageCategory?.replace('_', ' ')}</span>
                <span className="pc-chip"><Icon name="clock" size={15} />{b.durationDays}D / {b.durationNights}N</span>
                <span className="pc-chip"><Icon name="users" size={15} />{b.numberOfPersons} persons</span>
                <span className="pc-chip"><Icon name="calendar" size={15} />{b.travelDate}</span>
              </div>
              {b.specialRequests && <p style={{ fontSize: 12.5, color: 'var(--pc-ink-2)', margin: '12px 0 0', fontStyle: 'italic', padding: '8px 12px', background: 'var(--pc-wash)', borderRadius: 10 }}>{b.specialRequests}</p>}
              {b.cancellationReason && <p style={{ fontSize: 12.5, color: 'var(--pc-bad)', margin: '10px 0 0', padding: '8px 12px', background: 'var(--pc-bad-soft)', borderRadius: 10, fontWeight: 700 }}>Reason: {b.cancellationReason}</p>}
              {b.confirmedAt && <p style={{ fontSize: 12.5, color: 'var(--pc-good)', margin: '10px 0 0', fontWeight: 700 }}>Confirmed: {new Date(b.confirmedAt).toLocaleString()}</p>}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap', marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--pc-line)' }}>
                <div>
                  <div className="pc-figure" style={{ fontSize: 26, color: 'var(--pc-brand-deep)' }}>{inr(b.totalAmount)}</div>
                  <div style={{ fontSize: 12, color: 'var(--pc-muted)', marginTop: 4, fontWeight: 600 }}>{inr(b.pricePerPerson)} × {b.numberOfPersons} · Booked: {new Date(b.bookingDate).toLocaleDateString()}</div>
                </div>
                {(b.status === 'PENDING' || b.status === 'CONFIRMED') && (
                  <button type="button" onClick={() => handleCancel(b.id)} className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }}><Icon name="x" size={15} />Cancel Booking</button>
                )}
              </div>
            </Panel>
          ))
        )}
      </div>
      {!loading && filtered.length > 0 && <div style={{ marginTop: 16 }}><Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} /></div>}
    </DashboardLayout>
  )
}
