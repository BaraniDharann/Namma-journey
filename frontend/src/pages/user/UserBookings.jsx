import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import LiveTrackingMap from '../../components/LiveTrackingMap'
import { useAuth } from '../../context/AuthContext'
import { getUserBookings, deleteBooking, initiatePayment, submitReview, getTripSummary } from '../../utils/api'

const navItems = [
  { path: '/user/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/user/bookings', icon: '📋', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '➕', label: 'New Booking' },
  { path: '/user/payments', icon: '💳', label: 'Payments' },
  { path: '/user/package-bookings', icon: '📦', label: 'My Packages' },
  { path: '/user/profile', icon: '👤', label: 'Profile' },
]

function StatusBadge({ status }) {
  const map = { PENDING: 'badge-pending', CONFIRMED: 'badge-confirmed', STARTED: 'badge-confirmed', COMPLETED: 'badge-completed', CANCELLED: 'badge-cancelled' }
  return <span className={`px-3 py-1 rounded-full text-xs font-semibold ${map[status] || 'badge-pending'}`}>{status}</span>
}

function ReviewModal({ booking, onClose, onSubmit }) {
  const [rating, setRating] = useState(5)
  const [feedback, setFeedback] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    setLoading(true)
    await onSubmit(booking.bookingId, { rating, feedback })
    setLoading(false)
    onClose()
  }

  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div className="modal-box" style={{ background: '#fff', borderRadius: 24, overflow: 'hidden', maxWidth: 420, width: '90%', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
        <div style={{ background: '#0F172A', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 2 }}>Rate Your Trip</h3>
          <p style={{ fontSize: 13, color: '#94a3b8' }}>{booking.fromPlace} → {booking.toPlace}</p>
        </div>
        <div style={{ padding: 24 }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 20, justifyContent: 'center' }}>
            {[1,2,3,4,5].map(s => (
              <button key={s} onClick={() => setRating(s)} style={{ fontSize: 32, background: 'none', border: 'none', cursor: 'pointer', transition: 'transform 0.15s', transform: s <= rating ? 'scale(1.1)' : 'scale(0.9)', opacity: s <= rating ? 1 : 0.3 }}
                onMouseEnter={e => e.target.style.transform = 'scale(1.25)'}
                onMouseLeave={e => e.target.style.transform = s <= rating ? 'scale(1.1)' : 'scale(0.9)'}>
                ⭐
              </button>
            ))}
          </div>
          <textarea className="input-field" rows={4} placeholder="Share your experience..." value={feedback}
            onChange={e => setFeedback(e.target.value)} style={{ resize: 'none', marginBottom: 20, width: '100%', padding: '12px 14px', borderRadius: 12, border: '1.5px solid #e2e8f0', fontSize: 14, outline: 'none', fontFamily: 'inherit' }} />
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={onClose} style={{ flex: 1, padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b' }}>Cancel</button>
            <button onClick={handleSubmit} disabled={loading} style={{ flex: 1, padding: '11px', borderRadius: 12, border: 'none', background: '#f97316', color: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 700 }}>
              {loading ? 'Submitting...' : 'Submit Review'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function TripSummaryModal({ booking, userId, onClose }) {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const BASE = import.meta.env.VITE_API_BASE_URL?.replace('/api', '') || ''
  const imgUrl = (path) => path ? (path.startsWith('http') ? path : `${BASE}/${path}`) : null

  useEffect(() => {
    getTripSummary(userId, booking.bookingId)
      .then(r => setSummary(r.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const d = summary?.driver

  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div className="modal-box" style={{ maxWidth: 500, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
        <div style={{ background: '#0F172A', padding: '18px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff' }}>Trip Summary</h3>
          <button onClick={onClose} style={{ background: 'rgba(255,255,255,0.1)', border: 'none', width: 32, height: 32, borderRadius: 8, fontSize: 16, cursor: 'pointer', color: '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>✕</button>
        </div>
        <div style={{ padding: 24 }}>
          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}><div className="spinner" /></div>
          ) : summary ? (
            <>
              <div style={{ padding: '14px 16px', borderRadius: 14, background: '#fff7ed', border: '1px solid #fed7aa', marginBottom: 14 }}>
                <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 4 }}>{summary.fromPlace} → {summary.toPlace}</div>
                <div style={{ fontSize: 12, color: '#64748b' }}>{summary.fromDate} – {summary.toDate} • {summary.travelMembers} member{summary.travelMembers > 1 ? 's' : ''} • {summary.acType}</div>
                <div style={{ display: 'flex', gap: 16, marginTop: 10, fontSize: 13 }}>
                  <span>📏 {summary.distanceKm?.toFixed(1)} km</span>
                  <span>⏱️ {summary.estimatedTimeFormatted}</span>
                  <span style={{ fontWeight: 700, color: '#f97316' }}>₹{summary.totalAmount?.toLocaleString()}</span>
                </div>
              </div>

              {d && (
                <div style={{ padding: '14px 16px', borderRadius: 14, background: '#F0FDF4', border: '1px solid #BBF7D0', marginBottom: 14 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: '#15803d', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 10 }}>Assigned Driver</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    {imgUrl(d.photo)
                      ? <img src={imgUrl(d.photo)} alt="Driver" style={{ width: 48, height: 48, borderRadius: 14, objectFit: 'cover', border: '2px solid #BBF7D0' }} />
                      : <div style={{ width: 48, height: 48, borderRadius: 14, background: 'linear-gradient(135deg,#f97316,#7C3AED)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 900, color: '#fff' }}>{d.name?.[0]?.toUpperCase()}</div>
                    }
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A' }}>{d.name}</div>
                      <div style={{ fontSize: 12, color: '#64748b' }}>📞 {d.mobile}</div>
                      {d.email && <div style={{ fontSize: 12, color: '#64748b' }}>✉️ {d.email}</div>}
                    </div>
                  </div>
                </div>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 14 }}>
                {[['Payment', summary.paymentMethod || '—'], ['Pay Status', summary.paymentStatus || '—'], ['Review', summary.reviewSubmitted ? '✅ Submitted' : '⏳ Pending']].map(([k, v]) => (
                  <div key={k} style={{ padding: '12px 14px', borderRadius: 12, background: '#f8fafc', border: '1px solid #e2e8f0' }}>
                    <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 3 }}>{k}</div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{v}</div>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p style={{ color: '#94a3b8', textAlign: 'center', padding: '20px 0' }}>Could not load trip summary.</p>
          )}
          <button onClick={onClose} style={{ width: '100%', padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b', textAlign: 'center' }}>Close</button>
        </div>
      </div>
    </div>
  )
}

function UpiQrModal({ result, onClose }) {
  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div className="modal-box" style={{ maxWidth: 380, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', textAlign: 'center', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
        <div style={{ background: '#0F172A', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 2 }}>Scan to Pay</h3>
          <p style={{ fontSize: 13, color: '#94a3b8' }}>Amount: <strong style={{ color: '#F59E0B' }}>₹{result.amount?.toLocaleString()}</strong></p>
        </div>
        <div style={{ padding: 24 }}>
          <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 16 }}>Scan with GPay, PhonePe, Paytm or any UPI app</p>
          {result.upiQrCode && (
            <img src={result.upiQrCode} alt="UPI QR Code" style={{ width: 220, height: 220, borderRadius: 16, border: '2px solid #e2e8f0', marginBottom: 16 }} />
          )}
          <a href={result.upiDeepLink} style={{ display: 'block', padding: '12px 0', borderRadius: 12, fontWeight: 700, fontSize: 14, background: 'linear-gradient(135deg,#f97316,#ea580c)', color: '#fff', textDecoration: 'none', marginBottom: 10 }}>📲 Open UPI App Directly</a>
          <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 12 }}>{result.message}</p>
          <button onClick={onClose} style={{ width: '100%', padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b' }}>Done</button>
        </div>
      </div>
    </div>
  )
}

function PaymentModal({ booking, onClose, onPay }) {
  const [method, setMethod] = useState('UPI')
  const [loading, setLoading] = useState(false)
  const [upiResult, setUpiResult] = useState(null)

  const handlePay = async () => {
    setLoading(true)
    const res = await onPay(booking.bookingId, { paymentMethod: method })
    if (method === 'UPI' && res?.upiQrCode) {
      setUpiResult(res)
    } else {
      onClose()
    }
    setLoading(false)
  }

  if (upiResult) return <UpiQrModal result={upiResult} onClose={onClose} />

  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div className="modal-box" style={{ maxWidth: 420, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
        <div style={{ background: '#0F172A', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 2 }}>Pay for Trip</h3>
          <p style={{ fontSize: 13, color: '#94a3b8' }}>{booking.fromPlace} → {booking.toPlace}</p>
        </div>
        <div style={{ padding: 24 }}>
          <div style={{ fontSize: 32, fontWeight: 900, color: '#f97316', marginBottom: 20, textAlign: 'center' }}>₹{booking.totalAmount?.toLocaleString()}</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 20 }}>
            {['UPI', 'CASH'].map(m => (
              <button key={m} onClick={() => setMethod(m)} style={{ padding: '14px', borderRadius: 14, fontWeight: 700, fontSize: 14, cursor: 'pointer', transition: 'all 0.15s', background: method === m ? '#fff7ed' : '#f8fafc', color: method === m ? '#f97316' : '#374151', border: method === m ? '2px solid #f97316' : '2px solid #e2e8f0' }}>
                {m === 'UPI' ? '📱 UPI' : '💵 Cash'}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={onClose} style={{ flex: 1, padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b' }}>Cancel</button>
            <button onClick={handlePay} disabled={loading} style={{ flex: 1, padding: '11px', borderRadius: 12, border: 'none', background: '#f97316', color: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 700 }}>
              {loading ? 'Processing...' : 'Pay Now'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function UserBookings() {
  const { user } = useAuth()
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [reviewBooking, setReviewBooking] = useState(null)
  const [payBooking, setPayBooking] = useState(null)
  const [summaryBooking, setSummaryBooking] = useState(null)
  const [trackingBooking, setTrackingBooking] = useState(null)

  const load = async () => {
    try {
      const res = await getUserBookings(user.userId)
      setBookings(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { load() }, [user.userId])

  const filtered = filter === 'ALL' ? bookings : bookings.filter(b => b.status === filter)
  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 6)

  const handleDelete = async (bookingId) => {
    if (!confirm('Cancel this booking?')) return
    try { await deleteBooking(user.userId, bookingId); load() } catch { /* ignore */ }
  }

  const handleReview = async (bookingId, data) => {
    try { await submitReview(user.userId, bookingId, data) } catch { /* ignore */ }
  }

  const handlePayment = async (bookingId, data) => {
    try {
      const res = await initiatePayment(user.userId, bookingId, data)
      return res.data
    } catch { return null }
  }

  const statusBorderColor = (status) => {
    const m = { PENDING: '#F59E0B', CONFIRMED: '#f97316', STARTED: '#f97316', COMPLETED: '#22c55e', CANCELLED: '#ef4444' }
    return m[status] || '#e2e8f0'
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      {reviewBooking && <ReviewModal booking={reviewBooking} onClose={() => setReviewBooking(null)} onSubmit={handleReview} />}
      {payBooking && <PaymentModal booking={payBooking} onClose={() => setPayBooking(null)} onPay={handlePayment} />}
      {summaryBooking && <TripSummaryModal booking={summaryBooking} userId={user.userId} onClose={() => setSummaryBooking(null)} />}
      {trackingBooking && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div className="modal-box" style={{ maxWidth: 600, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
            <div style={{ background: '#0F172A', padding: '18px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff' }}>Track Your Driver</h3>
              <button onClick={() => setTrackingBooking(null)} style={{ background: 'rgba(255,255,255,0.1)', border: 'none', width: 32, height: 32, borderRadius: 8, fontSize: 16, cursor: 'pointer', color: '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>X</button>
            </div>
            <div style={{ padding: 20 }}>
              <LiveTrackingMap
                bookingId={trackingBooking.bookingId}
                fromLat={trackingBooking.fromLat}
                fromLon={trackingBooking.fromLon}
                toLat={trackingBooking.toLat}
                toLon={trackingBooking.toLon}
                fromPlace={trackingBooking.fromPlace}
                toPlace={trackingBooking.toPlace}
                isDriver={false}
              />
            </div>
          </div>
        </div>
      )}

      <div className="animate-fadeIn">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px', marginBottom: 4 }}>My Bookings</h1>
            <p style={{ fontSize: 14, color: '#64748b' }}>{bookings.length} total trips</p>
          </div>
          <Link to="/user/bookings/new" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '10px 20px', borderRadius: 12, background: '#f97316', color: '#fff', fontWeight: 700, fontSize: 14, textDecoration: 'none', boxShadow: '0 4px 16px rgba(249,115,22,0.3)', transition: 'all 0.2s' }}>➕ New Booking</Link>
        </div>

        {/* Filter pills */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
          {['ALL','PENDING','CONFIRMED','COMPLETED','CANCELLED'].map(f => (
            <button key={f} onClick={() => { setFilter(f); setCurrentPage(1) }}
              style={{ padding: '8px 18px', borderRadius: 20, fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: filter === f ? '#f97316' : '#f1f5f9', color: filter === f ? '#fff' : '#64748b', transition: 'all 0.15s', boxShadow: filter === f ? '0 4px 12px rgba(249,115,22,0.25)' : 'none' }}>{f}</button>
          ))}
        </div>

        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0' }}><div className="spinner" /></div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 24px', background: '#fff', borderRadius: 20, border: '1px solid #e2e8f0' }}>
            <div style={{ fontSize: 56, marginBottom: 12 }}>🗺️</div>
            <h3 style={{ fontWeight: 700, color: '#0F172A', fontSize: 18, marginBottom: 8 }}>No bookings found</h3>
            <Link to="/user/bookings/new" style={{ display: 'inline-flex', padding: '10px 24px', borderRadius: 12, background: '#f97316', color: '#fff', fontWeight: 600, fontSize: 14, textDecoration: 'none', marginTop: 8 }}>Book Your First Trip</Link>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {paginatedItems.map(b => (
              <div key={b.bookingId} style={{ background: '#fff', borderRadius: 18, padding: 20, border: '1px solid #e2e8f0', borderLeft: `4px solid ${statusBorderColor(b.status)}`, transition: 'all 0.2s ease', boxShadow: '0 1px 4px rgba(0,0,0,0.03)' }}
                onMouseEnter={e => { e.currentTarget.style.boxShadow = '0 8px 28px rgba(0,0,0,0.08)'; e.currentTarget.style.transform = 'translateY(-1px)' }}
                onMouseLeave={e => { e.currentTarget.style.boxShadow = '0 1px 4px rgba(0,0,0,0.03)'; e.currentTarget.style.transform = 'translateY(0)' }}>
                <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                  <div style={{ flex: 1, minWidth: 200 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                      <div style={{ width: 40, height: 40, borderRadius: 12, background: '#fff7ed', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
                      <div>
                        <div style={{ fontWeight: 700, color: '#0F172A', fontSize: 15 }}>{b.fromPlace} → {b.toPlace}</div>
                        <div style={{ fontSize: 12, color: '#94a3b8' }}>{b.fromDate} – {b.toDate} • {b.travelMembers} member{b.travelMembers > 1 ? 's' : ''} • {b.acType}</div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, fontSize: 13, color: '#64748b', alignItems: 'center', marginLeft: 50 }}>
                      <span>📏 {b.distanceKm?.toFixed(1)} km</span>
                      <span>⏱️ {Math.round((b.estimatedTimeMinutes||0)/60)}h {(b.estimatedTimeMinutes||0)%60}m</span>
                      {b.bookingType === 'HOUR_BASED' && <span style={{ background: '#fff7ed', padding: '2px 10px', borderRadius: 8, fontSize: 12, fontWeight: 600, color: '#f97316', border: '1px solid #fed7aa' }}>🕐 {b.bookingHours}h @ ₹{b.pricePerHourAtBooking}/hr</span>}
                      <span style={{ fontWeight: 700, color: '#f97316', fontSize: 14 }}>₹{b.totalAmount?.toLocaleString()}</span>
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <StatusBadge status={b.status} />
                    {(b.status === 'CONFIRMED' || b.status === 'STARTED') && <button onClick={() => setTrackingBooking(b)} style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#fff7ed', color: '#f97316', border: '1px solid #fed7aa', cursor: 'pointer', transition: 'all 0.15s' }}>📍 Track</button>}
                    {b.status === 'CONFIRMED' && <button onClick={() => setPayBooking(b)} style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#F0FDF4', color: '#15803d', border: '1px solid #BBF7D0', cursor: 'pointer', transition: 'all 0.15s' }}>💳 Pay</button>}
                    {b.status === 'COMPLETED' && <button onClick={() => setSummaryBooking(b)} style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#fff7ed', color: '#f97316', border: '1px solid #fed7aa', cursor: 'pointer', transition: 'all 0.15s' }}>🗺️ Details</button>}
                    {b.status === 'COMPLETED' && <button onClick={() => setReviewBooking(b)} style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#FFFBEB', color: '#A16207', border: '1px solid #FDE68A', cursor: 'pointer', transition: 'all 0.15s' }}>⭐ Review</button>}
                    {b.status === 'PENDING' && <button onClick={() => handleDelete(b.bookingId)} style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#FEF2F2', color: '#B91C1C', border: '1px solid #FECACA', cursor: 'pointer', transition: 'all 0.15s' }}>✕ Cancel</button>}
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
