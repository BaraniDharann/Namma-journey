import React, { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import PlaceAutocomplete from '../../components/PlaceAutocomplete'
import RouteMap from '../../components/RouteMap'
import { useAuth } from '../../context/AuthContext'
import { createBooking, getCurrentPricing } from '../../utils/api'

const navItems = [
  { path: '/user/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/user/bookings', icon: '📋', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '➕', label: 'New Booking' },
  { path: '/user/payments', icon: '💳', label: 'Payments' },
  { path: '/user/package-bookings', icon: '📦', label: 'My Packages' },
  { path: '/user/profile', icon: '👤', label: 'Profile' },
]

const temples = ['Tirupati Balaji', 'Shirdi Sai Baba', 'Vaishno Devi', 'Kedarnath', 'Badrinath', 'Somnath', 'Rameshwaram', 'Kashi Vishwanath', 'Mahakaleshwar', 'Jagannath Puri']

export default function NewBooking() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [form, setForm] = useState({
    fromPlace: searchParams.get('from') || '', toPlace: searchParams.get('to') || '',
    fromLat: null, fromLon: null, toLat: null, toLon: null,
    fromDate: searchParams.get('date') || '', toDate: '', travelMembers: Number(searchParams.get('members')) || 1, acType: 'AC',
    bookingType: 'DISTANCE_BASED', bookingHours: 2
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)
  const [pricing, setPricing] = useState({ pricePerKm: 10, pricePerHour: 150 })

  React.useEffect(() => {
    getCurrentPricing().then(res => {
      if (res.data) setPricing(res.data)
    }).catch(() => {})
  }, [])

  const set = (k, v) => setForm(p => ({ ...p, [k]: v }))

  const handleFromChange = ({ name, lat, lon }) => {
    setForm(p => ({ ...p, fromPlace: name, fromLat: lat, fromLon: lon }))
  }

  const handleToChange = ({ name, lat, lon }) => {
    setForm(p => ({ ...p, toPlace: name, toLat: lat, toLon: lon }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.fromLat || !form.toLat) {
      setError('Please select both locations from the dropdown suggestions for accurate distance calculation.')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await createBooking(user.userId, {
        userName: user.name,
        userPhone: user.mobile || '',
        fromPlace: form.fromPlace,
        toPlace: form.toPlace,
        fromLat: form.fromLat,
        fromLon: form.fromLon,
        toLat: form.toLat,
        toLon: form.toLon,
        fromDate: form.fromDate,
        toDate: form.toDate,
        travelMembers: Number(form.travelMembers),
        acType: form.acType,
        bookingType: form.bookingType,
        bookingHours: form.bookingType === 'HOUR_BASED' ? Number(form.bookingHours) : null,
      })
      setSuccess(res.data)
    } catch (err) {
      setError(err.response?.data?.message || Object.values(err.response?.data || {}).join(', ') || 'Booking failed')
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <DashboardLayout navItems={navItems} role="ROLE_USER">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 400 }}>
          <div style={{ textAlign: 'center', maxWidth: 440 }}>
            <div style={{ width: 80, height: 80, borderRadius: '50%', background: 'linear-gradient(135deg, #fff7ed, #ffedd5)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 40, margin: '0 auto 16px' }}>🎉</div>
            <h2 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: '1.8rem', color: '#0F172A', marginBottom: 8, letterSpacing: '-0.5px' }}>Booking Confirmed!</h2>
            <div style={{ background: '#fff', borderRadius: 18, padding: 24, marginBottom: 20, textAlign: 'left', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
              {[['From', success.fromPlace], ['To', success.toPlace], ['Type', success.bookingType === 'HOUR_BASED' ? `Hour Based (${success.bookingHours}h)` : 'Distance Based'], ['Distance', `${success.distanceKm?.toFixed(1)} km`]].map(([k,v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid #f1f5f9' }}>
                  <span style={{ fontSize: 13, color: '#64748b' }}>{k}</span>
                  <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{v}</span>
                </div>
              ))}
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: 14 }}>
                <span style={{ fontSize: 13, color: '#64748b' }}>Amount</span>
                <span style={{ fontSize: 24, fontWeight: 900, color: '#f97316' }}>₹{success.totalAmount?.toLocaleString()}</span>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 10 }}>
              <button onClick={() => navigate('/user/bookings')} style={{ flex: 1, padding: '12px 20px', borderRadius: 12, border: 'none', background: '#f97316', color: '#fff', fontWeight: 700, fontSize: 14, cursor: 'pointer', transition: 'all 0.2s' }}>View Bookings</button>
              <button onClick={() => { setSuccess(null); setForm({ fromPlace:'', toPlace:'', fromLat:null, fromLon:null, toLat:null, toLon:null, fromDate:'', toDate:'', travelMembers:1, acType:'AC', bookingType:'DISTANCE_BASED', bookingHours:2 }) }}
                style={{ flex: 1, padding: '12px 20px', borderRadius: 12, border: '2px solid #ffedd5', background: '#fff', color: '#f97316', fontWeight: 700, fontSize: 14, cursor: 'pointer', transition: 'all 0.2s' }}>New Booking</button>
            </div>
          </div>
        </div>
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <div style={{ maxWidth: 680, margin: '0 auto' }} className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px', marginBottom: 4 }}>Book a Trip</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>Fill in the details to book your journey</p>
        </div>

        {error && <div className="alert-error" style={{ marginBottom: 16, padding: '12px 16px', borderRadius: 12, background: '#FEF2F2', border: '1px solid #FECACA', color: '#B91C1C', fontSize: 13, fontWeight: 600 }}>⚠️ {error}</div>}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Route Details */}
          <div style={{ background: '#fff', borderRadius: 20, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.03)' }}>
            <h3 style={{ fontWeight: 800, fontSize: 15, color: '#0F172A', marginBottom: 18, display: 'flex', alignItems: 'center', gap: 8, letterSpacing: '-0.2px' }}>
              <span style={{ width: 32, height: 32, borderRadius: 10, background: '#fff7ed', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 15 }}>📍</span> Route Details
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>From (Pickup Location) *</label>
                <PlaceAutocomplete
                  value={form.fromPlace}
                  onChange={handleFromChange}
                  placeholder="Search your city, town or area..."
                  required
                />
                {form.fromLat && (
                  <div style={{ fontSize: 11, color: '#22c55e', marginTop: 4, fontWeight: 600 }}>✓ Location selected</div>
                )}
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>To (Destination) *</label>
                <PlaceAutocomplete
                  value={form.toPlace}
                  onChange={handleToChange}
                  placeholder="Search temple, city or destination..."
                  required
                />
                {form.toLat && (
                  <div style={{ fontSize: 11, color: '#22c55e', marginTop: 4, fontWeight: 600 }}>✓ Location selected</div>
                )}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
                  {temples.slice(0, 5).map(t => (
                    <button key={t} type="button" onClick={() => handleToChange({ name: t, lat: null, lon: null })}
                      style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none', background: form.toPlace === t ? '#f97316' : '#F1F5F9', color: form.toPlace === t ? '#fff' : '#64748b', transition: 'all 0.15s' }}>
                      🛕 {t}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {form.fromLat && form.toLat && (
            <div style={{ background: '#fff', borderRadius: 20, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.03)' }}>
              <h3 style={{ fontWeight: 800, fontSize: 15, color: '#0F172A', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ width: 32, height: 32, borderRadius: 10, background: '#fff7ed', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 15 }}>🗺️</span> Route Preview
              </h3>
              <RouteMap
                fromLat={form.fromLat} fromLon={form.fromLon}
                toLat={form.toLat} toLon={form.toLon}
                fromPlace={form.fromPlace} toPlace={form.toPlace}
              />
            </div>
          )}

          {/* Booking Type */}
          <div style={{ background: '#fff', borderRadius: 20, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.03)' }}>
            <h3 style={{ fontWeight: 800, fontSize: 15, color: '#0F172A', marginBottom: 18, display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ width: 32, height: 32, borderRadius: 10, background: '#fff7ed', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 15 }}>🕐</span> Booking Type
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 14 }}>
              {[{ key: 'DISTANCE_BASED', label: '📏 Distance Based', desc: 'Pay per kilometer' }, { key: 'HOUR_BASED', label: '🕐 Hour Based', desc: 'Pay per hour' }].map(type => (
                <button key={type.key} type="button" onClick={() => set('bookingType', type.key)}
                  style={{ padding: '16px', borderRadius: 14, cursor: 'pointer', border: form.bookingType === type.key ? '2px solid #f97316' : '2px solid #e2e8f0', background: form.bookingType === type.key ? '#fff7ed' : '#fff', transition: 'all 0.2s', textAlign: 'left' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, color: form.bookingType === type.key ? '#f97316' : '#374151', marginBottom: 2 }}>{type.label}</div>
                  <div style={{ fontSize: 12, color: '#94a3b8' }}>{type.desc}</div>
                </button>
              ))}
            </div>
            {form.bookingType === 'HOUR_BASED' && (
              <div style={{ background: '#FAFAFE', borderRadius: 14, padding: 18, border: '1px solid #ffedd5' }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>Select Hours *</label>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
                  {[1, 2, 3, 4, 5, 6, 8, 10, 12, 24].map(h => (
                    <button key={h} type="button" onClick={() => set('bookingHours', h)}
                      style={{ padding: '8px 16px', borderRadius: 10, fontSize: 13, fontWeight: 700, cursor: 'pointer', border: 'none', background: form.bookingHours === h ? 'linear-gradient(135deg,#f97316,#ea580c)' : '#f1f5f9', color: form.bookingHours === h ? '#fff' : '#374151', transition: 'all 0.15s', minWidth: 52 }}>
                      {h}h
                    </button>
                  ))}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: '#fff', borderRadius: 12, border: '1px solid #e2e8f0' }}>
                  <div>
                    <div style={{ fontSize: 12, color: '#94a3b8' }}>Rate: ₹{pricing.pricePerHour}/hour</div>
                    <div style={{ fontSize: 11, color: '#94a3b8' }}>Duration: {form.bookingHours} hour{form.bookingHours > 1 ? 's' : ''}</div>
                  </div>
                  <div style={{ fontSize: 24, fontWeight: 900, color: '#f97316' }}>₹{(form.bookingHours * pricing.pricePerHour).toLocaleString()}</div>
                </div>
              </div>
            )}
          </div>

          {/* Travel Details */}
          <div style={{ background: '#fff', borderRadius: 20, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.03)' }}>
            <h3 style={{ fontWeight: 800, fontSize: 15, color: '#0F172A', marginBottom: 18, display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ width: 32, height: 32, borderRadius: 10, background: '#fff7ed', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 15 }}>📅</span> Travel Details
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 14, marginBottom: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>From Date *</label>
                <input type="date" className="input-field" value={form.fromDate} min={new Date().toISOString().split('T')[0]} onChange={e => set('fromDate', e.target.value)} required style={{ width: '100%', padding: '10px 14px', borderRadius: 12, border: '1.5px solid #e2e8f0', fontSize: 14, color: '#374151', outline: 'none', transition: 'border 0.2s' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>To Date *</label>
                <input type="date" className="input-field" value={form.toDate} min={form.fromDate || new Date().toISOString().split('T')[0]} onChange={e => set('toDate', e.target.value)} required style={{ width: '100%', padding: '10px 14px', borderRadius: 12, border: '1.5px solid #e2e8f0', fontSize: 14, color: '#374151', outline: 'none', transition: 'border 0.2s' }} />
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Travel Members *</label>
                <select className="input-field" value={form.travelMembers} onChange={e => set('travelMembers', e.target.value)} style={{ width: '100%', padding: '10px 14px', borderRadius: 12, border: '1.5px solid #e2e8f0', fontSize: 14, color: '#374151', outline: 'none', background: '#fff' }}>
                  {Array.from({ length: 20 }, (_, i) => i + 1).map(n => <option key={n} value={n}>{n} Person{n > 1 ? 's' : ''}</option>)}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Vehicle Type *</label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  {['AC', 'NON_AC'].map(type => (
                    <button key={type} type="button" onClick={() => set('acType', type)}
                      style={{ padding: '11px', borderRadius: 12, fontSize: 13, fontWeight: 700, cursor: 'pointer', border: 'none', transition: 'all 0.15s', background: form.acType === type ? 'linear-gradient(135deg,#f97316,#ea580c)' : '#f1f5f9', color: form.acType === type ? '#fff' : '#374151' }}>
                      {type === 'AC' ? '❄️ AC' : '🌬️ Non-AC'}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <button type="submit" disabled={loading} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '14px', fontSize: 15, fontWeight: 700, borderRadius: 14, border: 'none', background: loading ? '#fdba74' : 'linear-gradient(135deg,#f97316,#ea580c)', color: '#fff', cursor: loading ? 'not-allowed' : 'pointer', transition: 'all 0.2s', boxShadow: '0 4px 20px rgba(249,115,22,0.3)' }}>
            {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Calculating route & price...</> : '🚀 Confirm Booking'}
          </button>
        </form>
      </div>
    </DashboardLayout>
  )
}
