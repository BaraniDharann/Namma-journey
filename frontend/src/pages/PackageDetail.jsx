import React, { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getPublicPackageById, bookPackage } from '../utils/api'

const getCategoryColor = (cat) => {
  const colors = { TEMPLE: '#f59e0b', HONEYMOON: '#ec4899', ADVENTURE: '#10b981', HILL_STATION: '#f97316', BEACH: '#06b6d4', HERITAGE: '#8b5cf6', WILDLIFE: '#84cc16', PILGRIMAGE: '#f97316', FAMILY: '#3b82f6', STATE_SPECIAL: '#ef4444' }
  return colors[cat] || '#6b7280'
}
const getCategoryEmoji = (cat) => {
  const emojis = { TEMPLE: '🛕', HONEYMOON: '💑', ADVENTURE: '🏔️', HILL_STATION: '⛰️', BEACH: '🏖️', HERITAGE: '🏛️', WILDLIFE: '🦁', PILGRIMAGE: '🙏', FAMILY: '👨‍👩‍👧‍👦', STATE_SPECIAL: '🌟' }
  return emojis[cat] || '✈️'
}

export default function PackageDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [pkg, setPkg] = useState(null)
  const [loading, setLoading] = useState(true)
  const [showBooking, setShowBooking] = useState(false)
  const [booking, setBooking] = useState({ userName: '', userEmail: '', userPhone: '', numberOfPersons: 1, travelDate: '', specialRequests: '' })
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    getPublicPackageById(id)
      .then(res => setPkg(res.data))
      .catch(() => navigate('/'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (user) {
      setBooking(b => ({ ...b, userName: user.name || '', userEmail: user.email || '', userPhone: user.mobile || '' }))
    }
  }, [user])

  const totalAmount = pkg ? pkg.pricePerPerson * booking.numberOfPersons : 0

  const handleBook = async (e) => {
    e.preventDefault()
    if (!user) { navigate('/login'); return }
    setSubmitting(true)
    setError('')
    try {
      await bookPackage(user.userId, { ...booking, packageId: pkg.id })
      setSuccess(true)
      setShowBooking(false)
    } catch (err) {
      setError(err.response?.data?.message || 'Booking failed')
    }
    setSubmitting(false)
  }

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', background: '#f8fafc' }}>
      <div style={{ textAlign: 'center' }}>
        <div className="spinner" style={{ width: 40, height: 40, margin: '0 auto 16px' }} />
        <p style={{ color: '#64748b', fontSize: 14 }}>Loading package details...</p>
      </div>
    </div>
  )
  if (!pkg) return null

  const color = getCategoryColor(pkg.category)

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc' }}>
      {/* Header */}
      <div style={{ background: `linear-gradient(135deg, ${color}22, ${color}08)`, padding: '24px 0 0' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto', padding: '0 24px' }}>
          <Link to="/" style={{ color: '#64748b', textDecoration: 'none', fontSize: 14, display: 'inline-flex', alignItems: 'center', gap: 6, marginBottom: 20, fontWeight: 500 }}>
            ← Back to Home
          </Link>
          <div style={{ display: 'flex', gap: 28, flexWrap: 'wrap', paddingBottom: 32 }}>
            {/* Image */}
            <div style={{ flex: '0 0 480px', maxWidth: '100%' }}>
              <div style={{ height: 320, borderRadius: 16, overflow: 'hidden', background: pkg.imageUrl ? `url(${pkg.imageUrl}) center/cover` : `linear-gradient(135deg, ${color}44, ${color}11)`, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
                {!pkg.imageUrl && <span style={{ fontSize: 80, opacity: 0.4 }}>{getCategoryEmoji(pkg.category)}</span>}
                <div style={{ position: 'absolute', top: 14, left: 14 }}>
                  <span style={{ padding: '5px 14px', background: color, color: '#fff', borderRadius: 20, fontSize: 12, fontWeight: 700 }}>{getCategoryEmoji(pkg.category)} {pkg.category?.replace('_', ' ')}</span>
                </div>
                <div style={{ position: 'absolute', top: 14, right: 14 }}>
                  <span style={{ padding: '5px 14px', background: 'rgba(255,255,255,0.9)', borderRadius: 20, fontSize: 12, fontWeight: 600, color: '#475569' }}>📍 {pkg.state}</span>
                </div>
              </div>
            </div>
            {/* Info */}
            <div style={{ flex: 1, minWidth: 260, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
              <h1 style={{ fontSize: 30, fontWeight: 800, color: '#0F172A', marginBottom: 8, lineHeight: 1.2, fontFamily: 'Poppins, sans-serif' }}>{pkg.name}</h1>
              <p style={{ fontSize: 14, color: '#64748b', marginBottom: 20, lineHeight: 1.7 }}>{pkg.description}</p>

              {/* Info boxes */}
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 24 }}>
                <div style={{ padding: '14px 20px', background: '#fff', borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid #f1f5f9', flex: 1, minWidth: 110 }}>
                  <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: 4 }}>Duration</div>
                  <div style={{ fontSize: 20, fontWeight: 800, color: '#0F172A' }}>{pkg.durationDays}D / {pkg.durationNights}N</div>
                </div>
                <div style={{ padding: '14px 20px', background: '#fff', borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid #f1f5f9', flex: 1, minWidth: 110 }}>
                  <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: 4 }}>Price</div>
                  <div style={{ fontSize: 20, fontWeight: 800, color: '#7c3aed' }}>₹{pkg.pricePerPerson?.toLocaleString()} <span style={{ fontSize: 12, fontWeight: 500, color: '#94a3b8' }}>/ person</span></div>
                </div>
                <div style={{ padding: '14px 20px', background: '#fff', borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid #f1f5f9', flex: 1, minWidth: 110 }}>
                  <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: 4 }}>Group Size</div>
                  <div style={{ fontSize: 20, fontWeight: 800, color: '#0F172A' }}>Up to {pkg.maxGroupSize}</div>
                </div>
              </div>

              <button onClick={() => user ? setShowBooking(true) : navigate('/login')}
                style={{ padding: '14px 36px', background: `linear-gradient(135deg, ${color}, ${color}cc)`, color: '#fff', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 15, cursor: 'pointer', alignSelf: 'flex-start' }}>
                Book This Package
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '32px 24px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 24, alignItems: 'flex-start' }}>
          <div>
            {/* Places */}
            <div style={{ background: '#fff', borderRadius: 14, padding: 24, marginBottom: 20, boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>
              <h3 style={{ fontSize: 17, fontWeight: 800, color: '#0F172A', marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>📍 Places Covered</h3>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {pkg.placesIncluded?.map((p, i) => (
                  <span key={i} style={{ padding: '8px 16px', background: `${color}12`, color: color, borderRadius: 20, fontWeight: 600, fontSize: 13, border: `1px solid ${color}25` }}>{p}</span>
                ))}
              </div>
            </div>

            {/* Highlights */}
            {pkg.highlights?.length > 0 && (
              <div style={{ background: '#fff', borderRadius: 14, padding: 24, marginBottom: 20, boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>
                <h3 style={{ fontSize: 17, fontWeight: 800, color: '#0F172A', marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>✨ Highlights</h3>
                <div style={{ display: 'grid', gap: 8 }}>
                  {pkg.highlights.map((h, i) => (
                    <div key={i} style={{ display: 'flex', gap: 10, alignItems: 'flex-start', padding: '10px 14px', background: '#f8fafc', borderRadius: 10 }}>
                      <div style={{ width: 6, height: 6, borderRadius: '50%', background: color, marginTop: 6, flexShrink: 0 }} />
                      <span style={{ color: '#475569', fontSize: 14, lineHeight: 1.6 }}>{h}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Itinerary */}
            {pkg.itinerary?.length > 0 && (
              <div style={{ background: '#fff', borderRadius: 14, padding: 24, marginBottom: 20, boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>
                <h3 style={{ fontSize: 17, fontWeight: 800, color: '#0F172A', marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>🗓️ Day-wise Itinerary</h3>
                {pkg.itinerary.map((day, i) => (
                  <div key={i} style={{ marginBottom: 20, padding: '16px 18px', background: '#f8fafc', borderRadius: 12, borderLeft: `3px solid ${color}` }}>
                    <span style={{ fontWeight: 700, color, fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Day {day.day}</span>
                    <div style={{ fontWeight: 700, color: '#0F172A', fontSize: 16, marginTop: 4, marginBottom: 4 }}>{day.title}</div>
                    {day.description && <p style={{ color: '#64748b', fontSize: 14, marginBottom: 8, lineHeight: 1.6 }}>{day.description}</p>}
                    {day.activities?.length > 0 && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                        {day.activities.map((a, j) => (
                          <span key={j} style={{ padding: '4px 12px', background: '#fff', borderRadius: 8, fontSize: 12, color: '#475569', fontWeight: 500, border: '1px solid #e2e8f0' }}>{a}</span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Sidebar */}
          <div style={{ position: 'sticky', top: 20 }}>
            <div style={{ background: '#fff', borderRadius: 14, padding: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.04)', marginBottom: 16 }}>
              <h3 style={{ fontSize: 16, fontWeight: 800, color: '#0F172A', marginBottom: 16 }}>What&apos;s Included</h3>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {[
                  { ok: pkg.foodIncluded, label: '🍽️ Food', detail: pkg.foodDetails },
                  { ok: pkg.accommodationIncluded, label: '🏨 Stay', detail: pkg.accommodationDetails },
                  { ok: pkg.transportIncluded, label: '🚗 Transport', detail: pkg.transportDetails },
                  { ok: pkg.tollFree, label: '🛣️ Toll Free' },
                  { ok: pkg.guideIncluded, label: '🗣️ Guide' },
                  { ok: pkg.sightseeingIncluded, label: '📸 Sightseeing' },
                ].map((item, i) => (
                  <span key={i} style={{ padding: '6px 14px', borderRadius: 20, fontSize: 13, fontWeight: 600, background: item.ok ? '#ecfdf5' : '#fef2f2', color: item.ok ? '#16a34a' : '#dc2626', border: `1px solid ${item.ok ? '#bbf7d0' : '#fecaca'}` }}>
                    {item.label} {item.ok ? '✓' : '✕'}
                  </span>
                ))}
              </div>
            </div>

            <div style={{ background: '#f5f3ff', borderRadius: 14, padding: 24, border: '1px solid #ddd6fe', textAlign: 'center' }}>
              <div style={{ fontSize: 12, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Starting From</div>
              <div style={{ fontSize: 36, fontWeight: 900, color: '#7c3aed', margin: '6px 0' }}>₹{pkg.pricePerPerson?.toLocaleString()}</div>
              <div style={{ fontSize: 14, color: '#94a3b8', marginBottom: 16 }}>per person</div>
              <button onClick={() => user ? setShowBooking(true) : navigate('/login')}
                style={{ width: '100%', padding: '14px 0', background: 'linear-gradient(135deg, #f97316, #ea580c)', color: '#fff', border: 'none', borderRadius: 10, fontWeight: 700, fontSize: 15, cursor: 'pointer' }}>
                Book Now
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Booking Modal */}
      {showBooking && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={() => setShowBooking(false)}>
          <div style={{ background: '#fff', borderRadius: 18, maxWidth: 500, width: '100%', padding: '32px 28px', maxHeight: '90vh', overflow: 'auto' }} onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', marginBottom: 4 }}>Book Package</h2>
            <p style={{ color: '#64748b', fontSize: 14, marginBottom: 20 }}>{pkg.name} — {pkg.durationDays}D/{pkg.durationNights}N — ₹{pkg.pricePerPerson?.toLocaleString()}/person</p>

            {error && <div style={{ padding: '10px 14px', background: '#fef2f2', color: '#dc2626', borderRadius: 10, marginBottom: 14, fontSize: 13 }}>{error}</div>}

            <form onSubmit={handleBook}>
              <div style={{ display: 'grid', gap: 14 }}>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Full Name</label>
                  <input required style={{ width: '100%', padding: '10px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 14, boxSizing: 'border-box' }} value={booking.userName} onChange={e => setBooking(b => ({ ...b, userName: e.target.value }))} />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Email</label>
                  <input required type="email" style={{ width: '100%', padding: '10px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 14, boxSizing: 'border-box' }} value={booking.userEmail} onChange={e => setBooking(b => ({ ...b, userEmail: e.target.value }))} />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Phone</label>
                  <input required style={{ width: '100%', padding: '10px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 14, boxSizing: 'border-box' }} value={booking.userPhone} onChange={e => setBooking(b => ({ ...b, userPhone: e.target.value }))} />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Number of Persons</label>
                  <input required type="number" min="1" max={pkg.maxGroupSize} style={{ width: '100%', padding: '10px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 14, boxSizing: 'border-box' }} value={booking.numberOfPersons} onChange={e => setBooking(b => ({ ...b, numberOfPersons: +e.target.value }))} />
                  <span style={{ fontSize: 11, color: '#94a3b8', marginTop: 4, display: 'block' }}>Max {pkg.maxGroupSize} persons</span>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Travel Date</label>
                  <input required type="date" style={{ width: '100%', padding: '10px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 14, boxSizing: 'border-box' }} value={booking.travelDate} onChange={e => setBooking(b => ({ ...b, travelDate: e.target.value }))} min={new Date().toISOString().split('T')[0]} />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Special Requests</label>
                  <textarea style={{ width: '100%', padding: '10px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 14, boxSizing: 'border-box', minHeight: 60 }} value={booking.specialRequests} onChange={e => setBooking(b => ({ ...b, specialRequests: e.target.value }))} placeholder="Any dietary needs, accessibility requirements..." />
                </div>
              </div>

              <div style={{ background: '#f5f3ff', borderRadius: 12, padding: '14px 18px', margin: '20px 0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', border: '1px solid #ddd6fe' }}>
                <span style={{ color: '#64748b', fontWeight: 600, fontSize: 14 }}>Total Amount</span>
                <span style={{ fontSize: 24, fontWeight: 900, color: '#7c3aed' }}>₹{totalAmount.toLocaleString()}</span>
              </div>

              <div style={{ display: 'flex', gap: 10 }}>
                <button type="button" onClick={() => setShowBooking(false)}
                  style={{ flex: 1, padding: '12px 0', background: '#f8fafc', border: '1.5px solid #e2e8f0', borderRadius: 10, fontWeight: 600, cursor: 'pointer', color: '#475569', fontSize: 14 }}>
                  Cancel
                </button>
                <button type="submit" disabled={submitting}
                  style={{ flex: 2, padding: '12px 0', background: submitting ? '#fdba74' : 'linear-gradient(135deg, #f97316, #ea580c)', color: '#fff', border: 'none', borderRadius: 10, fontWeight: 700, cursor: submitting ? 'not-allowed' : 'pointer', fontSize: 14 }}>
                  {submitting ? 'Booking...' : 'Confirm Booking'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Success */}
      {success && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
          <div style={{ background: '#fff', borderRadius: 18, maxWidth: 420, width: '100%', padding: '40px 32px', textAlign: 'center' }}>
            <div style={{ fontSize: 56, marginBottom: 16 }}>🎉</div>
            <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', marginBottom: 8, fontFamily: 'Poppins, sans-serif' }}>Booking Confirmed!</h2>
            <p style={{ color: '#64748b', fontSize: 14, marginBottom: 24, lineHeight: 1.6 }}>Your booking for <strong>{pkg.name}</strong> has been submitted. We&apos;ll confirm it shortly!</p>
            <div style={{ display: 'flex', gap: 10 }}>
              <button onClick={() => navigate('/user/package-bookings')}
                style={{ flex: 1, padding: '12px 0', background: 'linear-gradient(135deg, #f97316, #ea580c)', color: '#fff', border: 'none', borderRadius: 10, fontWeight: 700, cursor: 'pointer', fontSize: 14 }}>
                View My Bookings
              </button>
              <button onClick={() => navigate('/')}
                style={{ flex: 1, padding: '12px 0', background: '#f8fafc', border: '1.5px solid #e2e8f0', borderRadius: 10, fontWeight: 600, cursor: 'pointer', color: '#475569', fontSize: 14 }}>
                Back to Home
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
