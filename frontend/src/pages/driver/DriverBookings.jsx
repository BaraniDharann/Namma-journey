import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import LiveTrackingMap from '../../components/LiveTrackingMap'
import { useAuth } from '../../context/AuthContext'
import { getDriverBookings, driverBookingAction, endTrip, markCashReceived, startTrip, uploadEndTripPhoto } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr, shortPlace } from '../../dash/metrics'
import { useCelebrate } from '../../components/celebrate/Celebration'

const navItems = [
  { path: '/driver/dashboard', icon: '', label: 'Dashboard' },
  { path: '/driver/bookings', icon: '', label: 'My Trips' },
  { path: '/driver/profile', icon: '', label: 'Profile' },
]

const FILTERS = ['ALL', 'PENDING', 'CONFIRMED', 'STARTED', 'COMPLETED', 'CANCELLED']

// Shared modal chrome: the legacy overlay/box classes are restyled inside the shell; these
// only pin the stacking order and strip the box's default padding so the header bar is flush.
const overlayStyle = { position: 'fixed', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }
const boxStyle = (maxWidth) => ({ maxWidth, width: '90%', padding: 0, overflow: 'hidden' })
const modalHead = { background: 'var(--pc-rail)', padding: '18px 24px', color: '#fff' }
const modalTitle = { fontWeight: 800, fontSize: 18, color: '#fff', margin: 0, display: 'flex', alignItems: 'center', gap: 8 }
const modalSub = { fontSize: 13, color: 'rgba(255,255,255,0.72)', margin: '4px 0 0' }

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
    <div className="modal-overlay" style={overlayStyle}>
      <div className="modal-box" style={{ ...boxStyle(440), textAlign: 'center' }}>
        <div style={modalHead}>
          <h3 style={{ ...modalTitle, justifyContent: 'center' }}><Icon name="camera" />Driver Photo Verification</h3>
          <p style={modalSub}>Take a live selfie before ending the trip</p>
        </div>
        <div style={{ padding: 24 }}>
          {error && <div className="pc-error" style={{ marginBottom: 14, textAlign: 'left' }}><Icon name="alert" /><span>{error}</span></div>}
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
                <button type="button" className="pc-btn pc-btn-ghost" onClick={onClose} style={{ flex: 1 }}>Cancel</button>
                <button type="button" className="pc-btn" onClick={capture} disabled={!stream || !!error} style={{ flex: 1 }}><Icon name="camera" />Capture Photo</button>
              </>
            ) : (
              <>
                <button type="button" className="pc-btn pc-btn-ghost" onClick={retake} style={{ flex: 1 }}><Icon name="refresh" />Retake</button>
                <button type="button" className="pc-btn pc-btn-teal" onClick={submit} disabled={uploading} style={{ flex: 1 }}>
                  {uploading ? 'Uploading...' : <><Icon name="check" />Submit &amp; End Trip</>}
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
    <div className="modal-overlay" style={overlayStyle}>
      <div className="modal-box" style={{ ...boxStyle(380), textAlign: 'center' }}>
        <div style={modalHead}>
          <h3 style={{ ...modalTitle, justifyContent: 'center' }}><Icon name="qr" />Show QR to Customer</h3>
          <p style={modalSub}>Amount: <strong style={{ color: '#fff' }}>{inr(qrData.amount)}</strong></p>
        </div>
        <div style={{ padding: 24 }}>
          <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', margin: '0 0 16px', fontWeight: 600 }}>Customer scans with GPay, PhonePe, Paytm or any UPI app</p>
          {qrData.upiQrCode && (
            <img src={qrData.upiQrCode} alt="UPI QR" style={{ width: 220, height: 220, borderRadius: 16, border: '2px solid var(--pc-line)', marginBottom: 16 }} />
          )}
          <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', margin: '0 0 12px' }}>{qrData.message}</p>
          <button type="button" className="pc-btn pc-btn-ghost" onClick={onClose} style={{ width: '100%' }}>Close</button>
        </div>
      </div>
    </div>
  )
}

export default function DriverBookings() {
  const { user } = useAuth()
  const celebrate = useCelebrate()
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
        const trip = bookings.find(b => b.bookingId === bookingId)
        celebrate({
          title: 'Trip accepted',
          message: trip ? `${shortPlace(trip.fromPlace)} → ${shortPlace(trip.toPlace)}. The traveller has been told you're on the way.` : 'The traveller has been told you\'re on the way.',
        })
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

  const busy = (b, action) => actionLoading === b.bookingId + action
  const filterChip = (on) => ({
    border: 0, cursor: 'pointer', fontFamily: 'inherit',
    background: on ? 'var(--pc-ink)' : 'var(--pc-wash)',
    color: on ? '#fff' : 'var(--pc-ink-2)',
  })

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
        <div className="modal-overlay" style={overlayStyle}>
          <div className="modal-box" style={boxStyle(600)}>
            <div style={{ ...modalHead, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={modalTitle}><Icon name="nav" />Live Navigation</h3>
              <button type="button" aria-label="Close live navigation" onClick={() => setTrackingBooking(null)} style={{ background: 'rgba(255,255,255,0.12)', border: 'none', width: 32, height: 32, borderRadius: 8, cursor: 'pointer', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="x" size={16} /></button>
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
                isDriver={true}
                driverId={user.userId}
              />
            </div>
          </div>
        </div>
      )}

      <PageHead title="My Trips" sub={`${bookings.length} total assigned trips`} />

      <div className="pc-grid">
        <Panel span={12} title="Filter by status" meta={loading ? undefined : `${filtered.length} shown`}>
          <div className="pc-chips" role="group" aria-label="Filter trips by status">
            {FILTERS.map(f => (
              <button key={f} type="button" className="pc-chip" aria-pressed={filter === f}
                onClick={() => { setFilter(f); setCurrentPage(1) }}
                style={filterChip(filter === f)}>{f}</button>
            ))}
          </div>
        </Panel>

        {loading ? (
          <Panel span={12}><Skeleton rows={4} /></Panel>
        ) : filtered.length === 0 ? (
          <Panel span={12}>
            <Empty icon="car" title="No trips found">Trips assigned by owner will appear here</Empty>
          </Panel>
        ) : (
          paginatedItems.map(b => (
            <Panel key={b.bookingId} span={12} title={`${b.fromDate} – ${b.toDate}`} meta={<StatusPill status={b.status} />}>
              <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', justifyContent: 'space-between', gap: 18 }}>
                <div style={{ flex: '1 1 280px', minWidth: 0 }}>
                  <div className="pc-route">
                    <i /><div>{b.fromPlace}<small>Pickup · {b.userName}</small></div>
                    <span className="pc-route-ln" /><span />
                    <i className="is-end" /><div>{b.toPlace}<small>Drop · {b.distanceKm?.toFixed(1)} km</small></div>
                  </div>
                  <div className="pc-chips" style={{ marginTop: 14 }}>
                    <span className="pc-chip"><Icon name="user" size={15} />{b.userName}</span>
                    <span className="pc-chip"><Icon name="phone" size={15} />{b.userPhone}</span>
                    <span className="pc-chip"><Icon name="users" size={15} />{`${b.travelMembers} persons`}</span>
                    <span className="pc-chip"><Icon name="car" size={15} />{b.acType}</span>
                    {b.bookingType === 'HOUR_BASED' && (
                      <span className="pc-chip"><Icon name="clock" size={15} />{b.bookingHours}h @ {inr(b.pricePerHourAtBooking)}/hr</span>
                    )}
                  </div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 12, marginLeft: 'auto' }}>
                  <div style={{ textAlign: 'right' }}>
                    <div className="pc-figure" style={{ fontSize: 28, color: 'var(--pc-ink)' }}>{inr(b.totalAmount)}</div>
                    <small style={{ color: 'var(--pc-muted)', fontWeight: 700 }}>fare</small>
                  </div>
                  <div className="pc-chips" style={{ justifyContent: 'flex-end' }}>
                    {b.status === 'PENDING' && (
                      <>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => handleAction(b.bookingId, 'REJECT')} disabled={busy(b, 'REJECT')}>
                          {busy(b, 'REJECT') ? '...' : <><Icon name="x" size={15} />Reject</>}
                        </button>
                        <button type="button" className="pc-btn pc-btn-sm" onClick={() => handleAction(b.bookingId, 'ACCEPT')} disabled={busy(b, 'ACCEPT')}>
                          {busy(b, 'ACCEPT') ? '...' : <><Icon name="check" size={15} />Accept</>}
                        </button>
                      </>
                    )}
                    {b.status === 'CONFIRMED' && (
                      <>
                        <button type="button" className="pc-btn pc-btn-teal pc-btn-sm" onClick={() => handleStartTrip(b)} disabled={busy(b, 'START')}>
                          {busy(b, 'START') ? '...' : <><Icon name="nav" size={15} />Start trip</>}
                        </button>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => handleEndTrip(b.bookingId)} disabled={busy(b, 'END')}>
                          {busy(b, 'END') ? '...' : <><Icon name="camera" size={15} />End trip</>}
                        </button>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => handleCash(b.bookingId, b.totalAmount)} disabled={busy(b, 'CASH')}>
                          {busy(b, 'CASH') ? '...' : <><Icon name="wallet" size={15} />Cash received</>}
                        </button>
                      </>
                    )}
                    {b.status === 'STARTED' && (
                      <>
                        <button type="button" className="pc-btn pc-btn-sm" onClick={() => setTrackingBooking(b)}>
                          <Icon name="pin" size={15} />Track
                        </button>
                        <button type="button" className="pc-btn pc-btn-teal pc-btn-sm" onClick={() => handleEndTrip(b.bookingId)} disabled={busy(b, 'END')}>
                          {busy(b, 'END') ? '...' : <><Icon name="camera" size={15} />End trip</>}
                        </button>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => handleCash(b.bookingId, b.totalAmount)} disabled={busy(b, 'CASH')}>
                          {busy(b, 'CASH') ? '...' : <><Icon name="wallet" size={15} />Cash received</>}
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </div>
            </Panel>
          ))
        )}
      </div>
      {!loading && filtered.length > 0 && <div style={{ marginTop: 16 }}><Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} /></div>}
    </DashboardLayout>
  )
}
