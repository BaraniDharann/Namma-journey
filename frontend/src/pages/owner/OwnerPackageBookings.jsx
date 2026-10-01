import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'
import { getOwnerPackageBookings, confirmPackageBooking, cancelOwnerPackageBooking, completePackageBooking } from '../../utils/api'

const STATUSES = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']

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

export default function OwnerPackageBookings() {
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [actionLoading, setActionLoading] = useState(null)

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(bookings, 6)

  useEffect(() => { fetchBookings() }, [filter])

  const fetchBookings = async () => {
    setLoading(true)
    try {
      const res = await getOwnerPackageBookings(filter === 'ALL' ? null : filter)
      setBookings(res.data || [])
    } catch { setBookings([]) }
    setLoading(false)
  }

  const handleConfirm = async (id) => {
    setActionLoading(id)
    try { await confirmPackageBooking(id); fetchBookings() } catch { /* already surfaced by the api error toast */ }
    setActionLoading(null)
  }

  const handleCancel = async (id) => {
    const reason = prompt('Cancellation reason:')
    if (!reason) return
    setActionLoading(id)
    try { await cancelOwnerPackageBooking(id, reason); fetchBookings() } catch { /* already surfaced by the api error toast */ }
    setActionLoading(null)
  }

  const handleComplete = async (id) => {
    setActionLoading(id)
    try { await completePackageBooking(id); fetchBookings() } catch { /* already surfaced by the api error toast */ }
    setActionLoading(null)
  }

  const label = (s) => (s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase())

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Package Bookings" sub="Manage all customer package bookings" />

      <div className="pc-grid">
        <Panel
          span={12}
          title={filter === 'ALL' ? 'All package bookings' : `${label(filter)} package bookings`}
          meta={loading ? null : `${bookings.length} found`}
        >
          {/* Filters */}
          <div className="pc-chips" role="group" aria-label="Filter by status" style={{ marginBottom: 16 }}>
            {STATUSES.map(s => (
              <button
                key={s}
                type="button"
                aria-pressed={filter === s}
                onClick={() => { setFilter(s); setCurrentPage(1) }}
                className={filter === s ? 'pc-btn pc-btn-sm' : 'pc-btn pc-btn-ghost pc-btn-sm'}
              >{label(s)}</button>
            ))}
          </div>

          {loading ? (
            <Skeleton rows={5} />
          ) : bookings.length === 0 ? (
            <Empty icon="ticket" title="No package bookings found">
              Bookings for your travel packages will show up here.
            </Empty>
          ) : (
            <div className="pc-rows">
              {paginatedItems.map(b => (
                <div key={b.id} className="pc-row" style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'flex-start', padding: '16px 0' }}>
                  <div style={{ flex: '1 1 260px', minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10, flexWrap: 'wrap' }}>
                      <h3 style={{ fontSize: 15, fontWeight: 700, color: 'var(--pc-ink)', margin: 0 }}>{b.packageName}</h3>
                      <StatusPill status={b.status} />
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 8, fontSize: 13, color: 'var(--pc-ink-2)' }}>
                      <Detail icon="user"><strong style={{ color: 'var(--pc-ink)' }}>{b.userName}</strong></Detail>
                      <Detail icon="mail">{b.userEmail}</Detail>
                      <Detail icon="phone">{b.userPhone}</Detail>
                      <Detail icon="users">{b.numberOfPersons} persons</Detail>
                      <Detail icon="calendar">Travel: {b.travelDate}</Detail>
                      <Detail icon="clock">Booked: {new Date(b.bookingDate).toLocaleDateString()}</Detail>
                      <Detail icon="route">{b.durationDays}D / {b.durationNights}N</Detail>
                      <div style={{ fontWeight: 800, color: 'var(--pc-ink)', fontSize: 16 }}>{inr(b.totalAmount)}</div>
                    </div>
                    {b.specialRequests && <p className="pc-chip" style={{ marginTop: 10, borderRadius: 10, fontStyle: 'italic', fontWeight: 600 }}>Note: {b.specialRequests}</p>}
                    {b.cancellationReason && <p className="pc-pill pc-pill-stop" style={{ marginTop: 8, borderRadius: 10, whiteSpace: 'normal' }}>Cancelled: {b.cancellationReason}</p>}
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                    {b.status === 'PENDING' && (
                      <>
                        <button type="button" className="pc-btn pc-btn-sm" onClick={() => handleConfirm(b.id)} disabled={actionLoading === b.id}><Icon name="check" size={15} />Confirm</button>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => handleCancel(b.id)} disabled={actionLoading === b.id}><Icon name="x" size={15} />Cancel</button>
                      </>
                    )}
                    {b.status === 'CONFIRMED' && (
                      <>
                        <button type="button" className="pc-btn pc-btn-teal pc-btn-sm" onClick={() => handleComplete(b.id)} disabled={actionLoading === b.id}><Icon name="check" size={15} />Mark Complete</button>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => handleCancel(b.id)} disabled={actionLoading === b.id}><Icon name="x" size={15} />Cancel</button>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
          {bookings.length > 0 && <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />}
        </Panel>
      </div>
    </DashboardLayout>
  )
}

function Detail({ icon, children }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 7, minWidth: 0, overflowWrap: 'anywhere' }}>
      <span style={{ color: 'var(--pc-muted)', display: 'inline-flex' }}><Icon name={icon} size={15} /></span>
      <span style={{ minWidth: 0 }}>{children}</span>
    </div>
  )
}
