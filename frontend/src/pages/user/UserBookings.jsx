import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import LiveTrackingMap from '../../components/LiveTrackingMap'
import { useAuth } from '../../context/AuthContext'
import { getUserBookings, deleteBooking, initiatePayment, submitReview, getTripSummary } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr, shortPlace } from '../../dash/metrics'
import { useCelebrate, rememberPayment } from '../../components/celebrate/Celebration'

const navItems = [
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
]

// Shared modal chrome: the shell restyles .modal-overlay / .modal-box; these add layout only.
const overlayStyle = { position: 'fixed', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }
const boxStyle = (maxWidth) => ({ maxWidth, width: '100%', background: 'var(--pc-panel)', overflow: 'hidden', maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' })
const headStyle = { background: 'var(--pc-rail)', padding: '18px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }
const headTitle = { fontWeight: 700, fontSize: 18, color: 'var(--pc-rail-ink)', margin: 0 }
const headSub = { fontSize: 13, color: 'var(--pc-rail-ink-2)', margin: '2px 0 0' }
const closeBtn = { background: 'rgba(255,255,255,0.1)', border: 'none', width: 32, height: 32, borderRadius: 8, cursor: 'pointer', color: 'var(--pc-rail-ink)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }
const fullBtn = { width: '100%' }

const hm = (mins) => `${Math.round((mins || 0) / 60)}h ${(mins || 0) % 60}m`

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
    <div className="modal-overlay" style={overlayStyle}>
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="review-title" style={boxStyle(420)}>
        <div style={headStyle}>
          <div>
            <h3 id="review-title" style={headTitle}>Rate Your Trip</h3>
            <p style={headSub}>{booking.fromPlace} → {booking.toPlace}</p>
          </div>
        </div>
        <div style={{ padding: 24 }}>
          <div role="radiogroup" aria-label="Rating" style={{ display: 'flex', gap: 6, marginBottom: 20, justifyContent: 'center' }}>
            {[1, 2, 3, 4, 5].map(s => (
              <button key={s} type="button" role="radio" aria-checked={s === rating} aria-label={`${s} star${s > 1 ? 's' : ''}`}
                onClick={() => setRating(s)}
                style={{ background: 'none', border: 'none', padding: 4, cursor: 'pointer', transition: 'transform 0.15s', transform: s <= rating ? 'scale(1.1)' : 'scale(0.95)', color: s <= rating ? 'var(--pc-s4)' : 'var(--pc-line)', lineHeight: 0 }}>
                <Icon name="star" size={32} style={{ fill: s <= rating ? 'currentColor' : 'none' }} />
              </button>
            ))}
          </div>
          <textarea className="input-field" rows={4} placeholder="Share your experience..." aria-label="Feedback" value={feedback}
            onChange={e => setFeedback(e.target.value)} style={{ resize: 'none', marginBottom: 20, width: '100%', padding: '12px 14px', fontSize: 14, outline: 'none' }} />
          <div style={{ display: 'flex', gap: 10 }}>
            <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={{ flex: 1 }}>Cancel</button>
            <button type="button" onClick={handleSubmit} disabled={loading} className="pc-btn" style={{ flex: 1 }}>
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
    <div className="modal-overlay" style={overlayStyle}>
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="summary-title" style={boxStyle(500)}>
        <div style={headStyle}>
          <h3 id="summary-title" style={headTitle}>Trip Summary</h3>
          <button type="button" onClick={onClose} aria-label="Close" style={closeBtn}><Icon name="x" size={16} /></button>
        </div>
        <div style={{ padding: 24 }}>
          {loading ? (
            <Skeleton rows={4} />
          ) : summary ? (
            <>
              <div style={{ padding: '14px 16px', borderRadius: 14, background: 'var(--pc-wash)', marginBottom: 14 }}>
                <div className="pc-route">
                  <i /><div style={{ overflowWrap: 'anywhere' }}>{summary.fromPlace}<small>{summary.fromDate}</small></div>
                  <span className="pc-route-ln" /><span />
                  <i className="is-end" /><div style={{ overflowWrap: 'anywhere' }}>{summary.toPlace}<small>{summary.toDate}</small></div>
                </div>
                <div className="pc-chips" style={{ marginTop: 12 }}>
                  <span className="pc-chip" style={{ background: 'var(--pc-panel)' }}><Icon name="users" size={15} />{summary.travelMembers} member{summary.travelMembers > 1 ? 's' : ''}</span>
                  <span className="pc-chip" style={{ background: 'var(--pc-panel)' }}><Icon name="car" size={15} />{summary.acType}</span>
                  <span className="pc-chip" style={{ background: 'var(--pc-panel)' }}><Icon name="route" size={15} />{summary.distanceKm?.toFixed(1)} km</span>
                  <span className="pc-chip" style={{ background: 'var(--pc-panel)' }}><Icon name="clock" size={15} />{summary.estimatedTimeFormatted}</span>
                  <span className="pc-chip" style={{ background: 'var(--pc-panel)', color: 'var(--pc-brand-deep)' }}><Icon name="wallet" size={15} />{inr(summary.totalAmount)}</span>
                </div>
              </div>

              {d && (
                <div style={{ padding: '14px 16px', borderRadius: 14, border: '1px solid var(--pc-line)', marginBottom: 14 }}>
                  <div style={{ fontSize: 11, fontWeight: 800, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Assigned Driver</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    {imgUrl(d.photo)
                      ? <img src={imgUrl(d.photo)} alt="Driver" style={{ width: 48, height: 48, borderRadius: 14, objectFit: 'cover' }} />
                      : <div aria-hidden="true" style={{ width: 48, height: 48, borderRadius: 14, background: 'var(--pc-brand-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 800, color: 'var(--pc-brand-deep)' }}>{d.name?.[0]?.toUpperCase()}</div>
                    }
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--pc-ink)' }}>{d.name}</div>
                      <div style={{ fontSize: 12, color: 'var(--pc-ink-2)', display: 'flex', alignItems: 'center', gap: 6 }}><Icon name="phone" size={13} />{d.mobile}</div>
                      {d.email && <div style={{ fontSize: 12, color: 'var(--pc-ink-2)', display: 'flex', alignItems: 'center', gap: 6, overflowWrap: 'anywhere' }}><Icon name="mail" size={13} />{d.email}</div>}
                    </div>
                  </div>
                </div>
              )}

              <div className="pc-minis" style={{ marginBottom: 14 }}>
                {[['Payment', summary.paymentMethod || '—'], ['Pay Status', summary.paymentStatus || '—'], ['Review', summary.reviewSubmitted ? 'Submitted' : 'Pending']].map(([k, v]) => (
                  <div key={k}><b>{v}</b>{k}</div>
                ))}
              </div>
            </>
          ) : (
            <p style={{ color: 'var(--pc-muted)', textAlign: 'center', padding: '20px 0' }}>Could not load trip summary.</p>
          )}
          <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={fullBtn}>Close</button>
        </div>
      </div>
    </div>
  )
}

function UpiQrModal({ result, onClose, onPaid }) {
  return (
    <div className="modal-overlay" style={overlayStyle}>
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="upi-title" style={{ ...boxStyle(380), textAlign: 'center' }}>
        <div style={{ ...headStyle, display: 'block' }}>
          <h3 id="upi-title" style={headTitle}>Scan to Pay</h3>
          <p style={headSub}>Amount: <strong style={{ color: 'var(--pc-rail-ink)' }}>{inr(result.amount)}</strong></p>
        </div>
        <div style={{ padding: 24 }}>
          <p style={{ fontSize: 12, color: 'var(--pc-muted)', marginBottom: 16 }}>Scan with GPay, PhonePe, Paytm or any UPI app</p>
          {result.upiQrCode && (
            <img src={result.upiQrCode} alt="UPI QR Code" style={{ width: 220, height: 220, borderRadius: 16, border: '2px solid var(--pc-line)', marginBottom: 16 }} />
          )}
          {onPaid && result.paymentId && result.paymentMethod !== 'CASH' && (
            <button type="button" onClick={() => onPaid(result)} className="pc-btn" style={{ ...fullBtn, marginBottom: 10 }}>I&apos;ve paid</button>
          )}
          <a href={result.upiDeepLink} className="pc-btn" style={{ ...fullBtn, marginBottom: 10 }}><Icon name="phone" size={16} />Open UPI App Directly</a>
          <p style={{ fontSize: 12, color: 'var(--pc-muted)', marginBottom: 12 }}>{result.message}</p>
          <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={fullBtn}>Done</button>
        </div>
      </div>
    </div>
  )
}

function PaymentModal({ booking, onClose, onPay, onPaid }) {
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

  if (upiResult) {
    const paid = onPaid ? (result) => { onClose(); onPaid(booking, result) } : undefined
    return <UpiQrModal result={upiResult} onClose={onClose} onPaid={paid} />
  }

  return (
    <div className="modal-overlay" style={overlayStyle}>
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="pay-title" style={boxStyle(420)}>
        <div style={headStyle}>
          <div>
            <h3 id="pay-title" style={headTitle}>Pay for Trip</h3>
            <p style={headSub}>{booking.fromPlace} → {booking.toPlace}</p>
          </div>
        </div>
        <div style={{ padding: 24 }}>
          <div className="pc-figure" style={{ fontSize: 36, color: 'var(--pc-brand-deep)', marginBottom: 20, textAlign: 'center' }}>{inr(booking.totalAmount)}</div>
          <div role="radiogroup" aria-label="Payment method" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 20 }}>
            {['UPI', 'CASH'].map(m => (
              <button key={m} type="button" role="radio" aria-checked={method === m} onClick={() => setMethod(m)}
                style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '14px', borderRadius: 14, fontWeight: 800, fontSize: 14, cursor: 'pointer', transition: 'all 0.15s', background: method === m ? 'var(--pc-brand-soft)' : 'var(--pc-wash)', color: method === m ? 'var(--pc-brand-deep)' : 'var(--pc-ink-2)', border: method === m ? '2px solid var(--pc-brand)' : '2px solid var(--pc-line)' }}>
                <Icon name={m === 'UPI' ? 'qr' : 'wallet'} size={17} />{m === 'UPI' ? 'UPI' : 'Cash'}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={{ flex: 1 }}>Cancel</button>
            <button type="button" onClick={handlePay} disabled={loading} className="pc-btn" style={{ flex: 1 }}>
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
  const celebrate = useCelebrate()
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
    try {
      await submitReview(user.userId, bookingId, data)
      celebrate({ title: 'Thanks for the review', message: 'Your feedback helps other travellers and our drivers.', autoClose: 3200 })
    } catch { /* ignore */ }
  }

  // "I've paid" only records the claim locally (no API call); the app-wide watcher confirms it once verified.
  const handlePaid = (booking, result) => {
    rememberPayment(user.userId, {
      paymentId: result.paymentId,
      amount: result.amount,
      label: `${shortPlace(booking.fromPlace)} → ${shortPlace(booking.toPlace)}`,
    })
    celebrate({
      tone: 'calm',
      title: 'Payment sent',
      message: `₹${Math.round(result.amount || 0).toLocaleString('en-IN')} sent by UPI. You'll get a confirmation here as soon as it's verified.`,
    })
  }

  const handlePayment = async (bookingId, data) => {
    try {
      const res = await initiatePayment(user.userId, bookingId, data)
      return res.data
    } catch { return null }
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      {reviewBooking && <ReviewModal booking={reviewBooking} onClose={() => setReviewBooking(null)} onSubmit={handleReview} />}
      {payBooking && <PaymentModal booking={payBooking} onClose={() => setPayBooking(null)} onPay={handlePayment} onPaid={handlePaid} />}
      {summaryBooking && <TripSummaryModal booking={summaryBooking} userId={user.userId} onClose={() => setSummaryBooking(null)} />}
      {trackingBooking && (
        <div className="modal-overlay" style={overlayStyle}>
          <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="track-title" style={boxStyle(600)}>
            <div style={headStyle}>
              <h3 id="track-title" style={headTitle}>Track Your Driver</h3>
              <button type="button" onClick={() => setTrackingBooking(null)} aria-label="Close" style={closeBtn}><Icon name="x" size={16} /></button>
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
                travelMembers={trackingBooking.travelMembers}
                isDriver={false}
              />
            </div>
          </div>
        </div>
      )}

      <PageHead title="My Bookings" sub={`${bookings.length} total trips`}>
        <Link to="/user/bookings/new" className="pc-btn"><Icon name="plus" />New Booking</Link>
      </PageHead>

      <div className="pc-grid">
        <Panel span={12} title="Filter by status" meta={!loading && `${filtered.length} shown`}>
          <div className="pc-chips" role="group" aria-label="Filter bookings by status">
            {['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'].map(f => (
              <button key={f} type="button" aria-pressed={filter === f} onClick={() => { setFilter(f); setCurrentPage(1) }}
                className={`pc-btn pc-btn-sm ${filter === f ? '' : 'pc-btn-ghost'}`}>{f}</button>
            ))}
          </div>
        </Panel>

        {loading ? (
          <Panel span={12}><Skeleton rows={4} /></Panel>
        ) : filtered.length === 0 ? (
          <Panel span={12}>
            <Empty icon="map" title="No bookings found" action={<Link to="/user/bookings/new" className="pc-btn pc-btn-sm"><Icon name="plus" size={15} />Book Your First Trip</Link>}>
              Trips you book will show up here.
            </Empty>
          </Panel>
        ) : (
          paginatedItems.map(b => (
            <Panel key={b.bookingId} span={6} title={`${b.fromDate} – ${b.toDate}`} meta={<StatusPill status={b.status} />}>
              <div>
                <div className="pc-route">
                  <i /><div style={{ overflowWrap: 'anywhere' }}>{b.fromPlace}<small>Pickup</small></div>
                  <span className="pc-route-ln" /><span />
                  <i className="is-end" /><div style={{ overflowWrap: 'anywhere' }}>{b.toPlace}<small>Drop</small></div>
                </div>
                <div className="pc-chips" style={{ marginTop: 14 }}>
                  <span className="pc-chip"><Icon name="users" size={15} />{b.travelMembers} member{b.travelMembers > 1 ? 's' : ''}</span>
                  <span className="pc-chip"><Icon name="car" size={15} />{b.acType}</span>
                  <span className="pc-chip"><Icon name="route" size={15} />{b.distanceKm?.toFixed(1)} km</span>
                  <span className="pc-chip"><Icon name="clock" size={15} />{hm(b.estimatedTimeMinutes)}</span>
                  {b.bookingType === 'HOUR_BASED' && <span className="pc-chip"><Icon name="clock" size={15} />{b.bookingHours}h @ {inr(b.pricePerHourAtBooking)}/hr</span>}
                  <span className="pc-chip" style={{ color: 'var(--pc-brand-deep)' }}><Icon name="wallet" size={15} />{inr(b.totalAmount)}</span>
                </div>
                {['CONFIRMED', 'STARTED', 'COMPLETED', 'PENDING'].includes(b.status) && (
                  <div className="pc-chips" style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--pc-line)' }}>
                    {(b.status === 'CONFIRMED' || b.status === 'STARTED') && <button type="button" onClick={() => setTrackingBooking(b)} className="pc-btn pc-btn-teal pc-btn-sm"><Icon name="pin" size={15} />Track</button>}
                    {b.status === 'CONFIRMED' && <button type="button" onClick={() => setPayBooking(b)} className="pc-btn pc-btn-sm"><Icon name="card" size={15} />Pay</button>}
                    {b.status === 'COMPLETED' && <button type="button" onClick={() => setSummaryBooking(b)} className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="eye" size={15} />Details</button>}
                    {b.status === 'COMPLETED' && <button type="button" onClick={() => setReviewBooking(b)} className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="star" size={15} />Review</button>}
                    {b.status === 'PENDING' && <button type="button" onClick={() => handleDelete(b.bookingId)} className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }}><Icon name="x" size={15} />Cancel</button>}
                  </div>
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
