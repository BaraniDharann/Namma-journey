import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { getOwnerPackageBookings, confirmPackageBooking, cancelOwnerPackageBooking, completePackageBooking } from '../../utils/api'

const STATUSES = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const statusColors = { PENDING: '#F59E0B', CONFIRMED: '#10b981', COMPLETED: '#f97316', CANCELLED: '#ef4444' }

const navItems = [
  { path: '/owner/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '📋', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '🚗', label: 'Drivers' },
  { path: '/owner/payments', icon: '💳', label: 'Payments' },
  { path: '/owner/reviews', icon: '⭐', label: 'Reviews' },
  { path: '/owner/packages', icon: '📦', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '🎫', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '📊', label: 'Revenue' },
  { path: '/owner/profile', icon: '👤', label: 'Profile' },
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

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins', fontSize: 26, fontWeight: 900, color: '#0F172A' }}>Package Bookings</h1>
          <p style={{ color: '#64748b', marginTop: 4, fontSize: 14 }}>Manage all customer package bookings</p>
        </div>

        {/* Filters */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 20, flexWrap: 'wrap' }}>
          {STATUSES.map(s => (
            <button key={s} onClick={() => { setFilter(s); setCurrentPage(1) }} style={{ padding: '8px 20px', borderRadius: 20, border: 'none', fontWeight: 600, fontSize: 13, cursor: 'pointer', background: filter === s ? 'linear-gradient(135deg, #8b5cf6, #f97316)' : '#f1f5f9', color: filter === s ? '#fff' : '#64748b', transition: 'all 0.2s', boxShadow: filter === s ? '0 4px 12px rgba(139,92,246,0.3)' : 'none' }}>{s}</button>
          ))}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 60 }}><div className="spinner" /></div>
        ) : bookings.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 60, background: '#f8fafc', borderRadius: 20 }}>
            <p style={{ fontSize: 48 }}>📋</p>
            <p style={{ color: '#64748b', marginTop: 8 }}>No package bookings found</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {paginatedItems.map(b => (
              <div key={b.id} style={{ background: '#fff', borderRadius: 18, padding: 22, boxShadow: '0 2px 12px rgba(0,0,0,0.04)', border: '1px solid #e2e8f0', transition: 'all 0.2s' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
                  <div style={{ flex: 1, minWidth: 200 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                      <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A' }}>{b.packageName}</h3>
                      <span style={{ padding: '4px 12px', borderRadius: 20, fontSize: 11, fontWeight: 700, color: '#fff', background: statusColors[b.status] }}>{b.status}</span>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 8, fontSize: 13, color: '#64748b' }}>
                      <div>👤 <strong style={{ color: '#0F172A' }}>{b.userName}</strong></div>
                      <div>📧 {b.userEmail}</div>
                      <div>📱 {b.userPhone}</div>
                      <div>👥 {b.numberOfPersons} persons</div>
                      <div>📅 Travel: {b.travelDate}</div>
                      <div>🕐 Booked: {new Date(b.bookingDate).toLocaleDateString()}</div>
                      <div>⏱️ {b.durationDays}D / {b.durationNights}N</div>
                      <div style={{ fontWeight: 800, color: '#8b5cf6', fontSize: 16 }}>₹{b.totalAmount?.toLocaleString()}</div>
                    </div>
                    {b.specialRequests && <p style={{ fontSize: 12, color: '#94a3b8', marginTop: 8, fontStyle: 'italic', background: '#f8fafc', padding: '6px 10px', borderRadius: 8 }}>Note: {b.specialRequests}</p>}
                    {b.cancellationReason && <p style={{ fontSize: 12, color: '#ef4444', marginTop: 6, background: '#fef2f2', padding: '6px 10px', borderRadius: 8 }}>Cancelled: {b.cancellationReason}</p>}
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                    {b.status === 'PENDING' && (
                      <>
                        <button onClick={() => handleConfirm(b.id)} disabled={actionLoading === b.id} style={{ padding: '9px 20px', background: 'linear-gradient(135deg, #10b981, #059669)', color: '#fff', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 13, cursor: 'pointer', boxShadow: '0 4px 12px rgba(16,185,129,0.3)' }}>Confirm</button>
                        <button onClick={() => handleCancel(b.id)} disabled={actionLoading === b.id} style={{ padding: '9px 20px', background: '#fee2e2', color: '#dc2626', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 13, cursor: 'pointer' }}>Cancel</button>
                      </>
                    )}
                    {b.status === 'CONFIRMED' && (
                      <>
                        <button onClick={() => handleComplete(b.id)} disabled={actionLoading === b.id} style={{ padding: '9px 20px', background: 'linear-gradient(135deg, #f97316, #8b5cf6)', color: '#fff', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 13, cursor: 'pointer', boxShadow: '0 4px 12px rgba(249,115,22,0.3)' }}>Mark Complete</button>
                        <button onClick={() => handleCancel(b.id)} disabled={actionLoading === b.id} style={{ padding: '9px 20px', background: '#fee2e2', color: '#dc2626', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 13, cursor: 'pointer' }}>Cancel</button>
                      </>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        {bookings.length > 0 && <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />}
      </div>
    </DashboardLayout>
  )
}
