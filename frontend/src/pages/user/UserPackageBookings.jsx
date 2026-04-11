import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getUserPackageBookings, cancelPackageBooking } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'

const STATUSES = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const statusColors = { PENDING: '#F59E0B', CONFIRMED: '#10b981', COMPLETED: '#f97316', CANCELLED: '#ef4444' }
const statusBg = { PENDING: '#FEF3C7', CONFIRMED: '#DCFCE7', COMPLETED: '#fff7ed', CANCELLED: '#FEF2F2' }

const navItems = [
  { path: '/user/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/user/bookings', icon: '📋', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '➕', label: 'New Booking' },
  { path: '/user/payments', icon: '💳', label: 'Payments' },
  { path: '/user/package-bookings', icon: '📦', label: 'My Packages' },
  { path: '/user/profile', icon: '👤', label: 'Profile' },
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
    try { await cancelPackageBooking(user.userId, id, reason); fetchBookings() } catch {}
  }

  const filtered = filter === 'ALL' ? bookings : bookings.filter(b => b.status === filter)
  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 6)

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }} className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px', marginBottom: 4 }}>My Package Bookings</h1>
          <p style={{ color: '#64748b', fontSize: 14 }}>Track all your travel package bookings</p>
        </div>

        {/* Filter pills */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
          {STATUSES.map(s => (
            <button key={s} onClick={() => { setFilter(s); setCurrentPage(1) }}
              style={{ padding: '8px 18px', borderRadius: 20, border: 'none', fontWeight: 600, fontSize: 13, cursor: 'pointer', background: filter === s ? '#8b5cf6' : '#f1f5f9', color: filter === s ? '#fff' : '#64748b', transition: 'all 0.15s', boxShadow: filter === s ? '0 4px 12px rgba(139,92,246,0.25)' : 'none' }}>{s}</button>
          ))}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 60 }}><div className="spinner" /></div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 60, background: '#fff', borderRadius: 20, border: '1px solid #e2e8f0' }}>
            <p style={{ fontSize: 56, marginBottom: 8 }}>🎒</p>
            <h3 style={{ fontWeight: 700, color: '#0F172A', fontSize: 18, marginBottom: 6 }}>No package bookings yet</h3>
            <p style={{ color: '#64748b', fontSize: 14 }}>
              <a href="/" style={{ color: '#8b5cf6', textDecoration: 'none', fontWeight: 600 }}>Explore packages</a> to get started
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {paginatedItems.map(b => (
              <div key={b.id} style={{ background: '#fff', borderRadius: 18, padding: 22, boxShadow: '0 1px 4px rgba(0,0,0,0.03)', border: '1px solid #e2e8f0', borderLeft: `4px solid ${statusColors[b.status]}`, transition: 'all 0.2s ease' }}
                onMouseEnter={e => { e.currentTarget.style.boxShadow = '0 8px 28px rgba(0,0,0,0.08)'; e.currentTarget.style.transform = 'translateY(-1px)' }}
                onMouseLeave={e => { e.currentTarget.style.boxShadow = '0 1px 4px rgba(0,0,0,0.03)'; e.currentTarget.style.transform = 'translateY(0)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                      <h3 style={{ fontSize: 17, fontWeight: 800, color: '#0F172A', letterSpacing: '-0.2px' }}>{b.packageName}</h3>
                      <span style={{ padding: '4px 14px', borderRadius: 20, fontSize: 11, fontWeight: 700, color: statusColors[b.status], background: statusBg[b.status] }}>{b.status}</span>
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, fontSize: 13, color: '#64748b' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>📦 {b.packageCategory?.replace('_', ' ')}</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>⏱️ {b.durationDays}D / {b.durationNights}N</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>👥 {b.numberOfPersons} persons</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>📅 {b.travelDate}</span>
                    </div>
                    {b.specialRequests && <p style={{ fontSize: 12, color: '#94a3b8', marginTop: 10, fontStyle: 'italic', padding: '6px 12px', background: '#f8fafc', borderRadius: 8, display: 'inline-block' }}>{b.specialRequests}</p>}
                    {b.cancellationReason && <p style={{ fontSize: 12, color: '#ef4444', marginTop: 8, padding: '6px 12px', background: '#FEF2F2', borderRadius: 8, display: 'inline-block' }}>Reason: {b.cancellationReason}</p>}
                    {b.confirmedAt && <p style={{ fontSize: 12, color: '#10b981', marginTop: 6 }}>Confirmed: {new Date(b.confirmedAt).toLocaleString()}</p>}
                  </div>
                  <div style={{ textAlign: 'right', minWidth: 140 }}>
                    <div style={{ fontSize: 24, fontWeight: 900, color: '#8b5cf6', letterSpacing: '-0.5px' }}>₹{b.totalAmount?.toLocaleString()}</div>
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>₹{b.pricePerPerson?.toLocaleString()} × {b.numberOfPersons}</div>
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>Booked: {new Date(b.bookingDate).toLocaleDateString()}</div>
                    {(b.status === 'PENDING' || b.status === 'CONFIRMED') && (
                      <button onClick={() => handleCancel(b.id)} style={{ marginTop: 12, padding: '7px 18px', background: '#FEF2F2', color: '#DC2626', border: '1px solid #FECACA', borderRadius: 10, fontSize: 12, fontWeight: 700, cursor: 'pointer', transition: 'all 0.15s' }}>Cancel Booking</button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        {!loading && filtered.length > 0 && <div style={{ marginTop: 16 }}><Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} /></div>}
      </div>
    </DashboardLayout>
  )
}
