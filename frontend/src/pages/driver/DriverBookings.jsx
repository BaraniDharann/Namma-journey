import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import LiveTrackingMap from '../../components/LiveTrackingMap'
import { useAuth } from '../../context/AuthContext'
import { getDriverBookings, driverBookingAction, endTrip, markCashReceived, startTrip, uploadEndTripPhoto } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'

const navItems = [
  { path: '/driver/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/driver/bookings', icon: '📋', label: 'My Trips' },
  { path: '/driver/profile', icon: '👤', label: 'Profile' },
]

function StatusBadge({ status }) {
  const map = { PENDING: 'badge-pending', CONFIRMED: 'badge-confirmed', STARTED: 'badge-confirmed', COMPLETED: 'badge-completed', CANCELLED: 'badge-cancelled' }
  return <span className={`px-3 py-1 rounded-full text-xs font-semibold ${map[status] || 'badge-pending'}`}>{status}</span>
}

function CameraModal({ bookingId, driverId, onPhotoCaptured, onClose }) {
  const videoRef = React.useRef(null)
  const canvasRef = React.useRef(null)
  const [stream, setStream] = React.useState(null)
  const [captured, setCaptured] = React.useState(null)
  const [uploading, setUploading] = React.useState(false)
  const [error, setError] = React.useState('')

  React.useEffect(() => {
    let mediaStream = null
    const startCamera = async () => {
      try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } }
        })
        setStream(mediaStream)
        if (videoRef.current) {
          videoRef.current.srcObject = mediaStream
        }
      } catch {
        setError('Camera access denied. Please allow camera permission to take your photo.')
      }
    }
    startCamera()
    return () => {
      if (mediaStream) mediaStream.getTracks().forEach(t => t.stop())
    }
  }, [])

  const capture = () => {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (!video || !canvas) return
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0)
    setCaptured(canvas.toDataURL('image/jpeg', 0.85))
  }

  const retake = () => setCaptured(null)

  const submit = async () => {
    if (!captured) return
    setUploading(true)
    try {
      const blob = await fetch(captured).then(r => r.blob())
      const file = new File([blob], `driver_${driverId}_trip_${bookingId}.jpg`, { type: 'image/jpeg' })
      await uploadEndTripPhoto(driverId, bookingId, file)
      if (stream) stream.getTracks().forEach(t => t.stop())
      onPhotoCaptured()
    } catch {
      setError('Failed to upload photo. Please try again.')
    }
    setUploading(false)
  }

  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div className="modal-box" style={{ maxWidth: 440, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', textAlign: 'center', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
        <div style={{ background: '#0F172A', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 2 }}>📸 Driver Photo Verification</h3>
          <p style={{ fontSize: 13, color: '#94a3b8' }}>Take a live selfie before ending the trip</p>
        </div>
        <div style={{ padding: 24 }}>
          {error && <div style={{ marginBottom: 14, fontSize: 13, padding: '10px 14px', borderRadius: 12, background: '#FEF2F2', border: '1px solid #FECACA', color: '#B91C1C' }}>⚠️ {error}</div>}
          <div style={{ position: 'relative', borderRadius: 16, overflow: 'hidden', background: '#000', marginBottom: 16, aspectRatio: '4/3' }}>
            {!captured ? (
              <video ref={videoRef} autoPlay playsInline muted style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
            ) : (
              <img src={captured} alt="Captured" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            )}
            <canvas ref={canvasRef} style={{ display: 'none' }} />
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            {!captured ? (
              <>
                <button onClick={onClose} style={{ flex: 1, padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b' }}>Cancel</button>
                <button onClick={capture} disabled={!stream || !!error} style={{ flex: 1, padding: '11px', borderRadius: 12, border: 'none', background: '#3b82f6', color: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 700, opacity: (!stream || !!error) ? 0.5 : 1 }}>📸 Capture Photo</button>
              </>
            ) : (
              <>
                <button onClick={retake} style={{ flex: 1, padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b' }}>🔄 Retake</button>
                <button onClick={submit} disabled={uploading} style={{ flex: 1, padding: '11px', borderRadius: 12, border: 'none', background: '#3b82f6', color: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 700, opacity: uploading ? 0.7 : 1 }}>
                  {uploading ? 'Uploading...' : '✓ Submit & End Trip'}
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

function QrModal({ qrData, onClose }) {
  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div className="modal-box" style={{ maxWidth: 380, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', textAlign: 'center', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
        <div style={{ background: '#0F172A', padding: '20px 24px' }}>
          <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff', marginBottom: 2 }}>Show QR to Customer</h3>
          <p style={{ fontSize: 13, color: '#94a3b8' }}>Amount: <strong style={{ color: '#F59E0B' }}>₹{qrData.amount?.toLocaleString()}</strong></p>
        </div>
        <div style={{ padding: 24 }}>
          <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 16 }}>Customer scans with GPay, PhonePe, Paytm or any UPI app</p>
          {qrData.upiQrCode && (
            <img src={qrData.upiQrCode} alt="UPI QR" style={{ width: 220, height: 220, borderRadius: 16, border: '2px solid #e2e8f0', marginBottom: 16 }} />
          )}
          <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 12 }}>{qrData.message}</p>
          <button onClick={onClose} style={{ width: '100%', padding: '11px', borderRadius: 12, border: '1.5px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 14, fontWeight: 600, color: '#64748b' }}>Close</button>
        </div>
      </div>
    </div>
  )
}

export default function DriverBookings() {
  const { user } = useAuth()
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [actionLoading, setActionLoading] = useState(null)
  const [qrData, setQrData] = useState(null)
  const [trackingBooking, setTrackingBooking] = useState(null)
  const [cameraBookingId, setCameraBookingId] = useState(null)

  const load = async () => {
    try {
      const res = await getDriverBookings(user.userId)
      setBookings(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { load() }, [user.userId])

  const filtered = filter === 'ALL' ? bookings : bookings.filter(b => b.status === filter)
  const { paginatedItems, currentPage, totalPages, setCurrentPage } = usePagination(filtered, 6)

  const handleAction = async (bookingId, action) => {
    setActionLoading(bookingId + action)
    try {
      await driverBookingAction(user.userId, bookingId, action)
      if (action === 'ACCEPT') {
        setBookings(prev => prev.map(b => b.bookingId === bookingId ? { ...b, status: 'CONFIRMED' } : b))
      } else {
        // Rejecting unassigns the trip — it goes to another driver, not to CANCELLED, and it
        // leaves this driver's list entirely. Showing a status the booking never enters was
        // misleading, so drop the card and reconcile with the server.
        setBookings(prev => prev.filter(b => b.bookingId !== bookingId))
        load()
      }
    } catch { /* ignore */ }
    setActionLoading(null)
  }

  const handleEndTrip = (bookingId) => {
    setCameraBookingId(bookingId)
  }

  const handlePhotoCapturedAndEndTrip = async () => {
    const bookingId = cameraBookingId
    setCameraBookingId(null)
    setActionLoading(bookingId + 'END')
    try {
      const res = await endTrip(user.userId, bookingId)
      if (res.data?.upiQrCode) setQrData(res.data)
      load()
    } catch { /* ignore */ }
    setActionLoading(null)
  }

  const handleStartTrip = async (booking) => {
    setActionLoading(booking.bookingId + 'START')
    try {
      await startTrip(user.userId, booking.bookingId)
      setTrackingBooking({ ...booking, status: 'STARTED' })
      load()
    } catch { /* ignore */ }
    setActionLoading(null)
  }

  const handleCash = async (bookingId, amount) => {
    setActionLoading(bookingId + 'CASH')
    try {
      await markCashReceived(user.userId, bookingId, { amountReceived: amount })
      load()
    } catch { /* ignore */ }
    setActionLoading(null)
  }

  const statusBorderColor = (status) => {
    const m = { PENDING: '#F59E0B', CONFIRMED: '#3b82f6', STARTED: '#3b82f6', COMPLETED: '#22c55e', CANCELLED: '#ef4444' }
    return m[status] || '#e2e8f0'
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_DRIVER">
      {cameraBookingId && (
        <CameraModal
          bookingId={cameraBookingId}
          driverId={user.userId}
          onPhotoCaptured={handlePhotoCapturedAndEndTrip}
          onClose={() => setCameraBookingId(null)}
        />
      )}
      {qrData && <QrModal qrData={qrData} onClose={() => setQrData(null)} />}
      {trackingBooking && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div className="modal-box" style={{ maxWidth: 600, width: '90%', background: '#fff', borderRadius: 24, overflow: 'hidden', boxShadow: '0 24px 64px rgba(0,0,0,0.15)' }}>
            <div style={{ background: '#0F172A', padding: '18px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 18, color: '#fff' }}>Live Navigation</h3>
              <button onClick={() => setTrackingBooking(null)} style={{ background: 'rgba(255,255,255,0.1)', border: 'none', width: 32, height: 32, borderRadius: 8, fontSize: 16, cursor: 'pointer', color: '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>✕</button>
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
                isDriver={true}
                driverId={user.userId}
              />
            </div>
          </div>
        </div>
      )}
      <div className="animate-fadeIn">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px', marginBottom: 4 }}>My Trips</h1>
            <p style={{ fontSize: 14, color: '#64748b' }}>{bookings.length} total assigned trips</p>
          </div>
        </div>

        {/* Filter pills */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
          {['ALL','PENDING','CONFIRMED','STARTED','COMPLETED','CANCELLED'].map(f => (
            <button key={f} onClick={() => { setFilter(f); setCurrentPage(1) }}
              style={{ padding: '8px 18px', borderRadius: 20, fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: filter === f ? '#3b82f6' : '#f1f5f9', color: filter === f ? '#fff' : '#64748b', transition: 'all 0.15s', boxShadow: filter === f ? '0 4px 12px rgba(59,130,246,0.25)' : 'none' }}>{f}</button>
          ))}
        </div>

        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0' }}><div className="spinner" /></div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 24px', background: '#fff', borderRadius: 20, border: '1px solid #e2e8f0' }}>
            <div style={{ fontSize: 56, marginBottom: 12 }}>🚗</div>
            <h3 style={{ fontWeight: 700, color: '#0F172A', fontSize: 18, marginBottom: 8 }}>No trips found</h3>
            <p style={{ color: '#94a3b8', fontSize: 14 }}>Trips assigned by owner will appear here</p>
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
                      <div style={{ width: 40, height: 40, borderRadius: 12, background: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
                      <div>
                        <div style={{ fontWeight: 700, color: '#0F172A', fontSize: 15 }}>{b.fromPlace} → {b.toPlace}</div>
                        <div style={{ fontSize: 12, color: '#94a3b8' }}>{b.fromDate} – {b.toDate}</div>
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))', gap: 8, marginBottom: 10, marginLeft: 50 }}>
                      {[['Customer', b.userName], ['Phone', b.userPhone], ['Members', `${b.travelMembers} persons`], ['Vehicle', b.acType]].map(([k,v]) => (
                        <div key={k} style={{ padding: '8px 10px', borderRadius: 10, background: '#f8fafc', border: '1px solid #f1f5f9' }}>
                          <div style={{ fontSize: 10, color: '#94a3b8', marginBottom: 2, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.5px' }}>{k}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{v}</div>
                        </div>
                      ))}
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, fontSize: 13, color: '#64748b', alignItems: 'center', marginLeft: 50 }}>
                      <span>📏 {b.distanceKm?.toFixed(1)} km</span>
                      {b.bookingType === 'HOUR_BASED' && <span style={{ background: '#fff7ed', padding: '2px 10px', borderRadius: 8, fontSize: 12, fontWeight: 600, color: '#f97316', border: '1px solid #fed7aa' }}>🕐 {b.bookingHours}h @ ₹{b.pricePerHourAtBooking}/hr</span>}
                      <span style={{ fontWeight: 700, color: '#3b82f6', fontSize: 14 }}>₹{b.totalAmount?.toLocaleString()}</span>
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <StatusBadge status={b.status} />
                    {b.status === 'PENDING' && (
                      <>
                        <button onClick={() => handleAction(b.bookingId, 'ACCEPT')} disabled={actionLoading === b.bookingId+'ACCEPT'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#eff6ff', color: '#3b82f6', border: '1px solid #bfdbfe', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'ACCEPT' ? '...' : '✓ Accept'}
                        </button>
                        <button onClick={() => handleAction(b.bookingId, 'REJECT')} disabled={actionLoading === b.bookingId+'REJECT'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#FEF2F2', color: '#B91C1C', border: '1px solid #FECACA', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'REJECT' ? '...' : '✕ Reject'}
                        </button>
                      </>
                    )}
                    {b.status === 'CONFIRMED' && (
                      <>
                        <button onClick={() => handleStartTrip(b)} disabled={actionLoading === b.bookingId+'START'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#eff6ff', color: '#3b82f6', border: '1px solid #bfdbfe', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'START' ? '...' : '📍 Start Trip'}
                        </button>
                        <button onClick={() => handleEndTrip(b.bookingId)} disabled={actionLoading === b.bookingId+'END'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#fff7ed', color: '#f97316', border: '1px solid #fed7aa', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'END' ? '...' : '🏁 End Trip'}
                        </button>
                        <button onClick={() => handleCash(b.bookingId, b.totalAmount)} disabled={actionLoading === b.bookingId+'CASH'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#F0FDF4', color: '#15803d', border: '1px solid #BBF7D0', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'CASH' ? '...' : '💵 Cash Received'}
                        </button>
                      </>
                    )}
                    {b.status === 'STARTED' && (
                      <>
                        <button onClick={() => setTrackingBooking(b)}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#fff7ed', color: '#f97316', border: '1px solid #fed7aa', cursor: 'pointer', transition: 'all 0.15s' }}>
                          📍 Track
                        </button>
                        <button onClick={() => handleEndTrip(b.bookingId)} disabled={actionLoading === b.bookingId+'END'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#eff6ff', color: '#3b82f6', border: '1px solid #bfdbfe', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'END' ? '...' : '🏁 End Trip'}
                        </button>
                        <button onClick={() => handleCash(b.bookingId, b.totalAmount)} disabled={actionLoading === b.bookingId+'CASH'}
                          style={{ padding: '7px 14px', borderRadius: 10, fontSize: 12, fontWeight: 700, background: '#F0FDF4', color: '#15803d', border: '1px solid #BBF7D0', cursor: 'pointer', transition: 'all 0.15s' }}>
                          {actionLoading === b.bookingId+'CASH' ? '...' : '💵 Cash Received'}
                        </button>
                      </>
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
