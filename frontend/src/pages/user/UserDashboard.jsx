import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getUserBookings, getUserPayments } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'

const navItems = [
  { path: '/user/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/user/bookings', icon: '📋', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '➕', label: 'New Booking' },
  { path: '/user/payments', icon: '💳', label: 'Payments' },
  { path: '/user/package-bookings', icon: '📦', label: 'My Packages' },
  { path: '/user/profile', icon: '👤', label: 'Profile' },
]

const destinations = [
  { img: '/images/indian-hindu-temple-singapore.jpg', name: 'Temple Tours' },
  { img: '/images/palace-king-mahal-kingdom-shiva.jpg', name: 'Palace Visits' },
  { img: '/images/beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg', name: 'Garden Escapes' },
  { img: '/images/city-view-from-mountain-hill (1).jpg', name: 'Hill Stations' },
]

function StatusBadge({ status }) {
  const cls = { PENDING: 'badge-pending', CONFIRMED: 'badge-confirmed', COMPLETED: 'badge-completed', CANCELLED: 'badge-cancelled' }
  return <span className={`px-3 py-1 rounded-full text-xs font-semibold ${cls[status] || 'badge-pending'}`}>{status}</span>
}

function AnimatedNumber({ value, loading }) {
  if (loading) return <span>...</span>
  return <span className="number-pop">{value}</span>
}

export default function UserDashboard() {
  const { user } = useAuth()
  const [bookings, setBookings] = useState([])
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const load = async () => {
      try {
        const [b, p] = await Promise.all([getUserBookings(user.userId), getUserPayments(user.userId)])
        setBookings(b.data || [])
        setPayments(p.data || [])
      } catch { /* ignore */ }
      setLoading(false)
    }
    load()
  }, [user.userId])

  const stats = [
    { label: 'Total Trips', value: bookings.length, icon: '🗺️', color: '#f97316' },
    { label: 'Completed', value: bookings.filter(b => b.status === 'COMPLETED').length, icon: '✅', color: '#22c55e' },
    { label: 'Pending', value: bookings.filter(b => b.status === 'PENDING').length, icon: '⏳', color: '#fbbf24' },
    { label: 'Total Spent', value: `₹${payments.reduce((s, p) => s + (p.amount || 0), 0).toLocaleString()}`, icon: '💰', color: '#60a5fa' },
  ]

  const { currentPage, totalPages, paginatedItems: recent, setCurrentPage } = usePagination(bookings, 5)

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <div className="animate-fadeIn">
        <div className="dashboard-hero" style={{ backgroundImage: 'url(/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg)' }}>
          <div className="hero-float-img" style={{ position: 'absolute', top: 20, right: 20, zIndex: 2 }}>
            <img src="/images/GPS navigator-cuate.png" alt="" style={{ width: 120, height: 120, objectFit: 'contain', opacity: 0.9, filter: 'drop-shadow(0 4px 12px rgba(0,0,0,0.3))' }} className="animate-float" />
          </div>
          <div className="floating-decor" style={{ width: 80, height: 80, background: '#f97316', top: 10, left: '30%', animationDelay: '1s' }} />
          <div className="floating-decor" style={{ width: 50, height: 50, background: '#60a5fa', top: 30, right: '20%', animationDelay: '2s' }} />
          <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
              <div className="animate-fadeInLeft">
                <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 28, color: '#fff', marginBottom: 6 }}>Welcome back! 👋</h1>
                <p style={{ fontSize: 15, color: 'rgba(255,255,255,0.8)' }}>Here&apos;s your travel overview</p>
              </div>
              <Link to="/user/bookings/new" className="btn-primary animate-fadeInRight" style={{ background: 'rgba(249,115,22,0.9)', backdropFilter: 'blur(8px)', boxShadow: '0 8px 24px rgba(249,115,22,0.3)' }}>➕ New Booking</Link>
            </div>
          </div>
        </div>

        <div className="stagger-children stats-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16, marginBottom: 28 }}>
          {stats.map((s, i) => (
            <div key={i} className="stat-card-enhanced" style={{ '--accent-color': s.color }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                <div style={{ width: 48, height: 48, borderRadius: 14, background: `linear-gradient(135deg, ${s.color}20, ${s.color}10)`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22, boxShadow: `0 4px 12px ${s.color}15` }}>{s.icon}</div>
                <div style={{ width: 10, height: 10, borderRadius: '50%', background: s.color, boxShadow: `0 0 8px ${s.color}60`, animation: 'pulse 2s ease-in-out infinite' }} />
              </div>
              <div style={{ fontSize: 28, fontWeight: 900, color: '#0f172a', marginBottom: 4 }}>
                <AnimatedNumber value={s.value} loading={loading} />
              </div>
              <div style={{ fontSize: 13, color: '#64748b', fontWeight: 500 }}>{s.label}</div>
            </div>
          ))}
        </div>

        <div className="dashboard-grid-main" style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 20 }}>
          <div className="glass-card animate-slideUp" style={{ overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '20px 20px 16px' }}>
              <h2 style={{ fontWeight: 700, fontSize: 16, color: '#0f172a' }}>Recent Bookings</h2>
              <Link to="/user/bookings" style={{ fontSize: 13, color: '#f97316', fontWeight: 600, textDecoration: 'none', transition: 'all 0.2s' }}>View all →</Link>
            </div>
            {loading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}><div className="spinner" /></div>
            ) : recent.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon animate-float">🗺️</div>
                <h3>No bookings yet</h3>
                <p>Start your first journey today</p>
                <Link to="/user/bookings/new" className="btn-primary" style={{ marginTop: 16, fontSize: 14 }}>Book Your First Trip</Link>
              </div>
            ) : (
              <>
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table animated-table">
                  <thead><tr><th>Route</th><th>Date</th><th>Amount</th><th>Status</th></tr></thead>
                  <tbody>
                    {recent.map(b => (
                      <tr key={b.id}>
                        <td>
                          <div style={{ fontWeight: 600, color: '#0f172a', fontSize: 14 }}>{b.fromPlace}</div>
                          <div style={{ fontSize: 12, color: '#94a3b8' }}>→ {b.toPlace}</div>
                        </td>
                        <td style={{ fontSize: 13 }}>{b.fromDate}</td>
                        <td style={{ fontWeight: 700, color: '#f97316' }}>₹{b.totalAmount?.toLocaleString()}</td>
                        <td><StatusBadge status={b.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div style={{ padding: '12px 20px' }}><Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} /></div>
              </>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="glass-card animate-fadeInRight" style={{ padding: 18 }}>
              <h2 style={{ fontWeight: 700, fontSize: 15, color: '#0f172a', marginBottom: 14 }}>Popular Destinations</h2>
              <div className="image-grid">
                {destinations.map((d, i) => (
                  <Link to="/user/bookings/new" key={i} className="image-showcase" style={{ height: 90 }}>
                    <img src={d.img} alt={d.name} loading="lazy" />
                    <div className="overlay">
                      <span style={{ color: '#fff', fontSize: 12, fontWeight: 700 }}>{d.name}</span>
                    </div>
                  </Link>
                ))}
              </div>
            </div>

            <div className="glass-card" style={{ padding: 20, animationDelay: '0.2s' }} >
              <h2 style={{ fontWeight: 700, fontSize: 15, color: '#0f172a', marginBottom: 14 }}>Quick Actions</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }} className="stagger-children">
                {[
                  { to: '/user/bookings/new', icon: '🚗', label: 'Book a Trip', color: '#f97316' },
                  { to: '/user/bookings', icon: '📋', label: 'View Bookings', color: '#3b82f6' },
                  { to: '/user/payments', icon: '💳', label: 'Payment History', color: '#22c55e' },
                  { to: '/user/profile', icon: '👤', label: 'Edit Profile', color: '#8b5cf6' },
                ].map(a => (
                  <Link key={a.to} to={a.to} className="quick-action-item" style={{ '--action-color': a.color }}>
                    <div style={{ width: 38, height: 38, borderRadius: 10, background: `linear-gradient(135deg, ${a.color}20, ${a.color}10)`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 17 }}>{a.icon}</div>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{a.label}</span>
                    <span style={{ marginLeft: 'auto', fontSize: 14, color: '#94a3b8', transition: 'transform 0.3s ease' }}>→</span>
                  </Link>
                ))}
              </div>
            </div>

            <div className="promo-shimmer" style={{ borderRadius: 20, position: 'relative', overflow: 'hidden' }}>
              <img src="/images/indian-hindu-temple-singapore.jpg" alt="" style={{ width: '100%', height: 140, objectFit: 'cover' }} />
              <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(135deg, rgba(249,115,22,0.9), rgba(234,88,12,0.75))', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: 20 }}>
                <div style={{ position: 'absolute', right: 12, top: 12, fontSize: 48, opacity: 0.2 }} className="animate-float">🛕</div>
                <h3 style={{ fontWeight: 800, color: '#fff', marginBottom: 6, fontSize: 17 }}>Temple Special</h3>
                <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.9)', marginBottom: 12 }}>Get 10% off on temple trips this month!</p>
                <Link to="/user/bookings/new" style={{ fontSize: 13, fontWeight: 700, color: '#fff', textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 4 }}>Book Now <span style={{ transition: 'transform 0.3s' }}>→</span></Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
