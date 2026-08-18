import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { getOwnerBookings, assignDriver, getDriverTripPhoto } from '../../utils/api'

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

function StatusBadge({ status }) {
  const map = { PENDING: 'badge-pending', CONFIRMED: 'badge-confirmed', COMPLETED: 'badge-completed', CANCELLED: 'badge-cancelled' }
  return <span className={`px-3 py-1 rounded-full text-xs font-semibold ${map[status] || 'badge-pending'}`}>{status}</span>
}

function AssignModal({ booking, onClose, onAssign }) {
  const [driverId, setDriverId] = useState('')
  const [loading, setLoading] = useState(false)
  const handleAssign = async () => {
    if (!driverId) return
    setLoading(true)
    await onAssign(booking.id, Number(driverId))
    setLoading(false)
    onClose()
  }
  return (
    <div className="modal-overlay">
      <div className="modal-box" style={{ borderRadius: 20, overflow: 'hidden', padding: 0 }}>
        <div style={{ background: 'linear-gradient(135deg, #0F172A, #1e293b)', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 4 }}>Assign Driver</h3>
          <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.6)' }}>{booking.fromPlace} → {booking.toPlace}</p>
        </div>
        <div style={{ padding: 24 }}>
          <div style={{ marginBottom: 20 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Driver ID</label>
            <input className="input-field" placeholder="Enter driver ID" value={driverId} onChange={e => setDriverId(e.target.value)} type="number" style={{ borderRadius: 12 }} />
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={onClose} className="btn-ghost" style={{ flex: 1 }}>Cancel</button>
            <button onClick={handleAssign} disabled={loading || !driverId} className="btn-primary" style={{ flex: 1, justifyContent: 'center', background: 'linear-gradient(135deg, #8b5cf6, #f97316)' }}>
              {loading ? 'Assigning...' : 'Assign Driver'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function DriverPhotoModal({ bookingId, onClose }) {
  const [photo, setPhoto] = React.useState(null)
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState('')

  React.useEffect(() => {
    getDriverTripPhoto(bookingId)
      .then(res => setPhoto(res.data))
      .catch(() => setError('No driver photo available for this trip'))
      .finally(() => setLoading(false))
  }, [bookingId])

  return (
    <div className="modal-overlay">
      <div className="modal-box" style={{ maxWidth: 420, textAlign: 'center', borderRadius: 20, overflow: 'hidden', padding: 0 }}>
        <div style={{ background: 'linear-gradient(135deg, #0F172A, #1e293b)', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 4 }}>Driver Trip Photo</h3>
          <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.6)' }}>Review driver&apos;s appearance & professionalism</p>
        </div>
        <div style={{ padding: 24 }}>
          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}><div className="spinner" /></div>
          ) : error ? (
            <div style={{ padding: '24px', background: '#fef2f2', borderRadius: 12, color: '#b91c1c', fontSize: 14, marginBottom: 16 }}>{error}</div>
          ) : (
            <div style={{ marginBottom: 16 }}>
              <img
                src={photo.photoPath}
                alt="Driver end-trip photo"
                style={{ width: '100%', maxHeight: 360, objectFit: 'cover', borderRadius: 14, border: '2px solid #e2e8f0', marginBottom: 10 }}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#64748b', padding: '0 4px' }}>
                <span>Driver ID: #{photo.driverId}</span>
                <span>{new Date(photo.capturedAt).toLocaleString()}</span>
              </div>
            </div>
          )}
          <button onClick={onClose} className="btn-ghost" style={{ width: '100%', justifyContent: 'center' }}>Close</button>
        </div>
      </div>
    </div>
  )
}

export default function OwnerBookings() {
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [assignBooking, setAssignBooking] = useState(null)
  const [search, setSearch] = useState('')
  const [photoBookingId, setPhotoBookingId] = useState(null)

  const load = async () => {
    try {
      const res = await getOwnerBookings()
      setBookings(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  const filtered = bookings
    .filter(b => filter === 'ALL' || b.status === filter)
    .filter(b => !search || b.userName?.toLowerCase().includes(search.toLowerCase()) || b.fromPlace?.toLowerCase().includes(search.toLowerCase()) || b.toPlace?.toLowerCase().includes(search.toLowerCase()))

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 10)

  useEffect(() => { setCurrentPage(1) }, [search])

  const handleAssign = async (bookingId, driverId) => {
    try { await assignDriver(bookingId, driverId); load() } catch { /* ignore */ }
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      {assignBooking && <AssignModal booking={assignBooking} onClose={() => setAssignBooking(null)} onAssign={handleAssign} />}
      {photoBookingId && <DriverPhotoModal bookingId={photoBookingId} onClose={() => setPhotoBookingId(null)} />}

      <div className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#0F172A', marginBottom: 4 }}>All Bookings</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>{bookings.length} total bookings</p>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 20 }}>
          <div style={{ flex: 1, minWidth: 200, position: 'relative' }}>
            <span style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', fontSize: 14, color: '#94a3b8' }}>🔍</span>
            <input className="input-field" style={{ width: '100%', paddingLeft: 38, borderRadius: 12, border: '1px solid #e2e8f0', background: '#f8fafc' }} placeholder="Search by customer, route..." value={search} onChange={e => setSearch(e.target.value)} />
          </div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {['ALL','PENDING','CONFIRMED','COMPLETED','CANCELLED'].map(f => (
              <button key={f} onClick={() => { setFilter(f); setCurrentPage(1) }} style={{ padding: '8px 16px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none', background: filter === f ? 'linear-gradient(135deg,#8b5cf6,#f97316)' : '#f1f5f9', color: filter === f ? '#fff' : '#64748b', whiteSpace: 'nowrap', transition: 'all 0.2s', boxShadow: filter === f ? '0 4px 12px rgba(139,92,246,0.3)' : 'none' }}>{f}</button>
            ))}
          </div>
        </div>
        {loading ? <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0' }}><div className="spinner" /></div>
          : filtered.length === 0 ? <div className="empty-state"><div className="empty-icon">📋</div><h3>No bookings found</h3></div>
          : <div style={{ background: '#fff', borderRadius: 18, overflow: 'hidden', border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table">
                  <thead><tr><th>Customer</th><th>Route</th><th>Date</th><th>Type</th><th>Amount</th><th>Driver</th><th>Photo</th><th>Status</th><th>Action</th></tr></thead>
                  <tbody>
                    {paginatedItems.map(b => (
                      // GET /owner/bookings returns the raw TravelBooking entity (id), while the
                      // assign-driver response is a TravelBookingResponse (bookingId). Use either.
                      <tr key={b.id || b.bookingId}>
                        <td><div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{b.userName}</div><div style={{ fontSize: 12, color: '#94a3b8' }}>{b.userPhone}</div></td>
                        <td><div style={{ fontSize: 13 }}>{b.fromPlace}</div><div style={{ fontSize: 12, color: '#94a3b8' }}>→ {b.toPlace}</div></td>
                        <td style={{ fontSize: 13 }}>{b.fromDate}</td>
                        <td style={{ fontSize: 12 }}>{b.bookingType === 'HOUR_BASED' ? <span style={{ background: '#FEF3C7', padding: '3px 10px', borderRadius: 20, fontWeight: 600, color: '#92400E', border: '1px solid #FDE68A', whiteSpace: 'nowrap', fontSize: 11 }}>🕐 {b.bookingHours}h</span> : <span style={{ color: '#64748b', fontSize: 11, background: '#f1f5f9', padding: '3px 10px', borderRadius: 20 }}>📏 KM</span>}</td>
                        <td style={{ fontWeight: 700, color: '#f97316' }}>₹{b.totalAmount?.toLocaleString()}</td>
                        <td style={{ fontSize: 13 }}>{b.driverId ? `#${b.driverId}` : <span style={{ color: '#94a3b8' }}>Unassigned</span>}</td>
                        <td>{(b.status === 'STARTED' || b.status === 'COMPLETED') && b.driverId ? (
                          <button onClick={() => setPhotoBookingId(b.bookingId || b.id)} style={{ padding: '5px 12px', borderRadius: 20, fontSize: 11, fontWeight: 700, background: '#f3e8ff', color: '#8b5cf6', border: '1px solid #d8b4fe', cursor: 'pointer', whiteSpace: 'nowrap' }}>📸 View</button>
                        ) : <span style={{ color: '#d1d5db', fontSize: 12 }}>—</span>}</td>
                        <td><StatusBadge status={b.status} /></td>
                        <td>{b.status === 'PENDING' && !b.driverId && (
                          <button onClick={() => setAssignBooking(b)} style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 700, background: 'linear-gradient(135deg, #f9731620, #f9731610)', color: '#f97316', border: '1px solid #fed7aa', cursor: 'pointer' }}>Assign</button>
                        )}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
              </div>
            </div>}
      </div>
    </DashboardLayout>
  )
}
