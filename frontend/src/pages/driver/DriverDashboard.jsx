import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import LiveTrackingMap from '../../components/LiveTrackingMap'
import { useAuth } from '../../context/AuthContext'
import { getDriverBookings } from '../../utils/api'
import { Link } from 'react-router-dom'
import Pagination, { usePagination } from '../../components/Pagination'

const navItems = [
  { path: '/driver/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/driver/bookings', icon: '📋', label: 'My Trips' },
  { path: '/driver/profile', icon: '👤', label: 'Profile' },
]

function AnimatedNumber({ value, loading }) {
  if (loading) return <span>...</span>
  return <span className="number-pop">{value}</span>
}

export default function DriverDashboard() {
  const { user } = useAuth()
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getDriverBookings(user.userId).then(r => setBookings(r.data || [])).catch(() => {}).finally(() => setLoading(false))
  }, [user.userId])

  const pending = bookings.filter(b => b.status === 'PENDING')
  const started = bookings.filter(b => b.status === 'STARTED')
  const confirmed = bookings.filter(b => b.status === 'CONFIRMED')
  const completed = bookings.filter(b => b.status === 'COMPLETED')
  const earnings = completed.reduce((s, b) => s + (b.totalAmount || 0), 0)

  const { paginatedItems: paginatedPending, currentPage: currentPageP, totalPages: totalPagesP, setCurrentPage: setCurrentPageP } = usePagination(pending, 4)
  const { paginatedItems: paginatedConfirmed, currentPage: currentPageC, totalPages: totalPagesC, setCurrentPage: setCurrentPageC } = usePagination(confirmed, 4)

  const stats = [
    { label: 'New Requests', value: pending.length, icon: '🔔', color: '#fbbf24' },
    { label: 'Active Trips', value: confirmed.length + started.length, icon: '🚗', color: '#60a5fa' },
    { label: 'Completed', value: completed.length, icon: '✅', color: '#22c55e' },
    { label: 'Total Earnings', value: `₹${earnings.toLocaleString()}`, icon: '💰', color: '#f97316' },
  ]

  return (
    <DashboardLayout navItems={navItems} role="ROLE_DRIVER">
      <div className="animate-fadeIn">
        <div className="dashboard-hero" style={{ backgroundImage: 'url(/images/city-view-from-mountain-hill (1).jpg)', minHeight: 170 }}>
          <div className="hero-float-img" style={{ position: 'absolute', top: 16, right: 20, zIndex: 2 }}>
            <img src="/images/car image.jpeg" alt="" style={{ width: 130, height: 80, objectFit: 'cover', borderRadius: 14, boxShadow: '0 8px 24px rgba(0,0,0,0.3)', border: '2px solid rgba(255,255,255,0.3)' }} className="animate-float" />
          </div>
          <div className="floating-decor" style={{ width: 60, height: 60, background: '#3b82f6', top: 10, left: '40%', animationDelay: '0.5s' }} />
          <div className="floating-decor" style={{ width: 40, height: 40, background: '#22c55e', top: 40, left: '60%', animationDelay: '1.5s' }} />
          <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
              <div className="animate-fadeInLeft">
                <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#fff', marginBottom: 6 }}>Driver Dashboard 🚗</h1>
                <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.8)' }}>Manage your trips and earnings</p>
              </div>
              <div className="glow-online animate-fadeInRight" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 16px', background: 'rgba(34,197,94,0.2)', backdropFilter: 'blur(8px)', border: '1px solid rgba(34,197,94,0.4)', color: '#4ade80', fontSize: 13, fontWeight: 700 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#4ade80', boxShadow: '0 0 8px #4ade80' }} /> Online
              </div>
            </div>
          </div>
        </div>

        <div className="stagger-children stats-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 16, marginBottom: 28 }}>
          {stats.map((s, i) => (
            <div key={i} className="stat-card-enhanced" style={{ '--accent-color': s.color }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                <div style={{ width: 48, height: 48, borderRadius: 14, background: `linear-gradient(135deg, ${s.color}20, ${s.color}10)`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22, boxShadow: `0 4px 12px ${s.color}15` }}>{s.icon}</div>
                <div style={{ width: 10, height: 10, borderRadius: '50%', background: s.color, boxShadow: `0 0 8px ${s.color}60`, animation: 'pulse 2s ease-in-out infinite' }} />
              </div>
              <div style={{ fontSize: 26, fontWeight: 900, color: '#0f172a', marginBottom: 4 }}>
                <AnimatedNumber value={s.value} loading={loading} />
              </div>
              <div style={{ fontSize: 13, color: '#64748b', fontWeight: 500 }}>{s.label}</div>
            </div>
          ))}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20 }}>
          <div className="glass-card animate-slideUp" style={{ overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 20px 12px' }}>
              <h2 style={{ fontWeight: 700, fontSize: 15, color: '#0f172a' }}>New Trip Requests</h2>
              <span className="badge badge-pending" style={{ animation: pending.length > 0 ? 'pulse 2s ease-in-out infinite' : 'none' }}>{pending.length}</span>
            </div>
            {loading ? <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}><div className="spinner" /></div>
              : pending.length === 0 ? <div className="empty-state" style={{ padding: '32px 20px' }}><div style={{ fontSize: 32, marginBottom: 8 }} className="animate-float">🎉</div><p style={{ color: '#64748b', fontSize: 13 }}>No pending requests</p></div>
              : <div className="stagger-children">
                  {paginatedPending.map(b => (
                    <div key={b.id} style={{ padding: '14px 20px', borderTop: '1px solid #f1f5f9', transition: 'all 0.2s ease', cursor: 'pointer' }}
                      onMouseEnter={e => { e.currentTarget.style.background = '#fffbeb'; e.currentTarget.style.paddingLeft = '24px' }}
                      onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.paddingLeft = '20px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 14, color: '#0f172a' }}>{b.fromPlace} → {b.toPlace}</div>
                          <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{b.fromDate} • {b.travelMembers} members • {b.acType}</div>
                        </div>
                        <div style={{ fontWeight: 700, color: '#f97316', fontSize: 15 }}>₹{b.totalAmount?.toLocaleString()}</div>
                      </div>
                    </div>
                  ))}
                </div>}
            <Pagination currentPage={currentPageP} totalPages={totalPagesP} onPageChange={setCurrentPageP} />
            <div style={{ padding: 14 }}>
              <Link to="/driver/bookings" className="btn-primary" style={{ width: '100%', justifyContent: 'center', fontSize: 13, padding: '10px', borderRadius: 12 }}>View All Trips →</Link>
            </div>
          </div>

          <div className="glass-card animate-slideUp" style={{ overflow: 'hidden', animationDelay: '0.1s' }}>
            <div style={{ padding: '18px 20px 12px' }}>
              <h2 style={{ fontWeight: 700, fontSize: 15, color: '#0f172a' }}>Active Trips</h2>
            </div>
            {confirmed.length === 0 ? <div className="empty-state" style={{ padding: '32px 20px' }}><div style={{ fontSize: 32, marginBottom: 8 }} className="animate-float">🗺️</div><p style={{ color: '#64748b', fontSize: 13 }}>No active trips</p></div>
              : <div className="stagger-children">
                  {paginatedConfirmed.map(b => (
                    <div key={b.id} style={{ padding: '14px 20px', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', gap: 12, transition: 'all 0.2s ease' }}
                      onMouseEnter={e => { e.currentTarget.style.background = '#eff6ff'; e.currentTarget.style.transform = 'translateX(4px)' }}
                      onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.transform = 'translateX(0)' }}>
                      <div style={{ width: 42, height: 42, borderRadius: 12, background: 'linear-gradient(135deg, #eff6ff, #dbeafe)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, flexShrink: 0, boxShadow: '0 2px 8px rgba(59,130,246,0.15)' }}>🚗</div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 14, color: '#0f172a' }}>{b.fromPlace} → {b.toPlace}</div>
                        <div style={{ fontSize: 12, color: '#94a3b8' }}>{b.userName} • {b.userPhone}</div>
                      </div>
                      <div style={{ fontWeight: 700, color: '#3b82f6', fontSize: 14 }}>₹{b.totalAmount?.toLocaleString()}</div>
                    </div>
                  ))}
                </div>}
            <Pagination currentPage={currentPageC} totalPages={totalPagesC} onPageChange={setCurrentPageC} />

            <div style={{ margin: 16 }}>
              {started.length > 0 ? (
                <LiveTrackingMap
                  bookingId={started[0].bookingId}
                  fromLat={started[0].fromLat}
                  fromLon={started[0].fromLon}
                  toLat={started[0].toLat}
                  toLon={started[0].toLon}
                  fromPlace={started[0].fromPlace}
                  toPlace={started[0].toPlace}
                  isDriver={true}
                  driverId={user.userId}
                />
              ) : (
                <div style={{ borderRadius: 16, overflow: 'hidden', position: 'relative' }}>
                  <img src="/images/GPS navigator-cuate.png" alt="GPS" style={{ width: '100%', height: 140, objectFit: 'contain', background: 'linear-gradient(135deg,#f0f9ff,#e0f2fe)', padding: 12 }} className="animate-float" />
                  <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, background: 'linear-gradient(to top, rgba(15,23,42,0.8), transparent)', padding: '20px 16px 12px', textAlign: 'center' }}>
                    <div style={{ fontWeight: 700, fontSize: 14, color: '#fff' }}>Live Map View</div>
                    <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.7)' }}>Start a trip to activate GPS tracking</div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        <div style={{ marginTop: 24, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }} className="stagger-children driver-image-row">
          {[
            { img: '/images/indian-city-buildings-scene.jpg', label: 'City Routes' },
            { img: '/images/prasart-phimai-ancient-stone-thailand.jpg', label: 'Heritage Sites' },
            { img: '/images/palace-king-mahal-kingdom-shiva.jpg', label: 'Palace Trips' },
          ].map((d, i) => (
            <div key={i} className="image-showcase" style={{ height: 120, borderRadius: 16 }}>
              <img src={d.img} alt={d.label} loading="lazy" />
              <div className="overlay">
                <span style={{ color: '#fff', fontSize: 13, fontWeight: 700 }}>{d.label}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </DashboardLayout>
  )
}
