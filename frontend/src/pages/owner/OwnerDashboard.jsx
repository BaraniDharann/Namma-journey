import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { getOwnerBookings, getPendingPayments, getAllReviews, getMonthlyRevenue } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'

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

function AnimatedNumber({ value, loading }) {
  if (loading) return <span>...</span>
  return <span className="number-pop">{value}</span>
}

export default function OwnerDashboard() {
  const [bookings, setBookings] = useState([])
  const [payments, setPayments] = useState([])
  const [reviews, setReviews] = useState([])
  const [revenue, setRevenue] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const now = new Date()
    Promise.all([
      getOwnerBookings().catch(() => ({ data: [] })),
      getPendingPayments().catch(() => ({ data: [] })),
      getAllReviews().catch(() => ({ data: [] })),
      getMonthlyRevenue(now.getFullYear(), now.getMonth() + 1).catch(() => ({ data: null })),
    ]).then(([b, p, r, rev]) => {
      setBookings(b.data || [])
      setPayments(p.data || [])
      setReviews(r.data || [])
      setRevenue(rev.data)
    }).finally(() => setLoading(false))
  }, [])

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(bookings, 5)

  const totalRevenue = revenue?.totalRevenue || bookings.filter(b => b.status === 'COMPLETED').reduce((s, b) => s + (b.totalAmount || 0), 0)
  const avgRating = reviews.length ? (reviews.reduce((s, r) => s + r.rating, 0) / reviews.length).toFixed(1) : '—'

  const stats = [
    { label: 'Total Bookings', value: bookings.length, icon: '📋', color: '#f97316', sub: `${bookings.filter(b => b.status === 'PENDING').length} pending` },
    { label: 'Monthly Revenue', value: `₹${Number(totalRevenue || 0).toLocaleString()}`, icon: '💰', color: '#22c55e', sub: 'This month' },
    { label: 'Pending Payments', value: payments.length, icon: '⏳', color: '#fbbf24', sub: 'Awaiting verification' },
    { label: 'Avg Rating', value: avgRating, icon: '⭐', color: '#a855f7', sub: `${reviews.length} reviews` },
  ]

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div className="animate-fadeIn">
        <div className="dashboard-hero" style={{ backgroundImage: 'url(/images/indian-city-buildings-scene.jpg)', minHeight: 175 }}>
          <div className="floating-decor" style={{ width: 70, height: 70, background: '#8b5cf6', top: 10, left: '35%', animationDelay: '0.8s' }} />
          <div className="floating-decor" style={{ width: 50, height: 50, background: '#f97316', top: 30, right: '25%', animationDelay: '1.8s' }} />
          <div className="hero-float-img" style={{ position: 'absolute', top: 16, right: 20, zIndex: 2 }}>
            <img src="/images/tourist-presenting-something.jpg" alt="" style={{ width: 100, height: 100, objectFit: 'cover', borderRadius: '50%', border: '3px solid rgba(255,255,255,0.3)', boxShadow: '0 8px 24px rgba(0,0,0,0.3)' }} className="animate-float" />
          </div>
          <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
              <div className="animate-fadeInLeft">
                <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 28, color: '#fff', marginBottom: 6 }}>Owner Dashboard 👑</h1>
                <p style={{ fontSize: 15, color: 'rgba(255,255,255,0.8)' }}>Platform overview & management</p>
              </div>
              <Link to="/owner/drivers" className="btn-primary animate-fadeInRight" style={{ background: 'rgba(139,92,246,0.9)', backdropFilter: 'blur(8px)', boxShadow: '0 8px 24px rgba(139,92,246,0.3)' }}>➕ Add Driver</Link>
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
              <div style={{ fontSize: 26, fontWeight: 900, color: '#0f172a', marginBottom: 2 }}>
                <AnimatedNumber value={s.value} loading={loading} />
              </div>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{s.label}</div>
              <div style={{ fontSize: 12, color: s.color, marginTop: 2 }}>{s.sub}</div>
            </div>
          ))}
        </div>

        <div className="dashboard-grid-main" style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 20 }}>
          <div className="glass-card animate-slideUp" style={{ overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '20px 20px 14px' }}>
              <h2 style={{ fontWeight: 700, fontSize: 16, color: '#0f172a' }}>Recent Bookings</h2>
              <Link to="/owner/bookings" style={{ fontSize: 13, color: '#f97316', fontWeight: 600, textDecoration: 'none' }}>View all →</Link>
            </div>
            {loading ? <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}><div className="spinner" /></div>
              : bookings.length === 0 ? <div className="empty-state"><div className="empty-icon animate-float">📋</div><p>No bookings yet</p></div>
              : <>
                <div style={{ overflowX: 'auto' }}>
                  <table className="data-table animated-table">
                    <thead><tr><th>Customer</th><th>Route</th><th>Amount</th><th>Status</th></tr></thead>
                    <tbody>
                      {paginatedItems.map(b => (
                        <tr key={b.id}>
                          <td><div style={{ fontWeight: 600, fontSize: 13, color: '#0f172a' }}>{b.userName}</div><div style={{ fontSize: 12, color: '#94a3b8' }}>{b.userPhone}</div></td>
                          <td><div style={{ fontSize: 13 }}>{b.fromPlace}</div><div style={{ fontSize: 12, color: '#94a3b8' }}>→ {b.toPlace}</div></td>
                          <td style={{ fontWeight: 700, color: '#f97316' }}>₹{b.totalAmount?.toLocaleString()}</td>
                          <td><StatusBadge status={b.status} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div style={{ padding: '12px 20px' }}><Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} /></div>
              </>}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="glass-card animate-fadeInRight" style={{ padding: 18 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <h3 style={{ fontWeight: 700, fontSize: 14, color: '#0f172a' }}>Pending Payments</h3>
                <Link to="/owner/payments" style={{ fontSize: 12, color: '#f97316', fontWeight: 600, textDecoration: 'none' }}>View →</Link>
              </div>
              {payments.slice(0, 3).map((p, i) => (
                <div key={p.paymentId} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid #f1f5f9', animation: `fadeInUp 0.3s ease ${i * 0.1}s forwards`, opacity: 0 }}>
                  <div><div style={{ fontWeight: 600, fontSize: 13, color: '#0f172a' }}>₹{p.amount?.toLocaleString()}</div><div style={{ fontSize: 11, color: '#94a3b8' }}>{p.paymentMethod}</div></div>
                  <span className="badge badge-pending">PENDING</span>
                </div>
              ))}
              {payments.length === 0 && <p style={{ fontSize: 13, color: '#94a3b8', textAlign: 'center', padding: '8px 0' }}>No pending payments</p>}
            </div>

            <div className="glass-card" style={{ padding: 18, animationDelay: '0.15s' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <h3 style={{ fontWeight: 700, fontSize: 14, color: '#0f172a' }}>Latest Reviews</h3>
                <Link to="/owner/reviews" style={{ fontSize: 12, color: '#f97316', fontWeight: 600, textDecoration: 'none' }}>View →</Link>
              </div>
              {reviews.slice(0, 3).map((r, i) => (
                <div key={r.id} style={{ padding: '10px 0', borderBottom: '1px solid #f1f5f9', animation: `fadeInUp 0.3s ease ${i * 0.1}s forwards`, opacity: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 2 }}>
                    <span style={{ fontWeight: 600, fontSize: 13, color: '#0f172a' }}>{r.userName}</span>
                    <span style={{ fontSize: 12 }}>{'⭐'.repeat(r.rating)}</span>
                  </div>
                  <p style={{ fontSize: 12, color: '#64748b' }}>{r.feedback?.slice(0, 55)}...</p>
                </div>
              ))}
              {reviews.length === 0 && <p style={{ fontSize: 13, color: '#94a3b8', textAlign: 'center', padding: '8px 0' }}>No reviews yet</p>}
            </div>

            <div className="glass-card" style={{ padding: 18 }}>
              <h3 style={{ fontWeight: 700, fontSize: 14, color: '#0f172a', marginBottom: 10 }}>Quick Actions</h3>
              <div className="stagger-children">
                {navItems.slice(1).map(item => (
                  <Link key={item.path} to={item.path} className="quick-action-item" style={{ '--action-color': '#8b5cf6', marginBottom: 4 }}>
                    <span style={{ fontSize: 16 }}>{item.icon}</span>
                    <span style={{ fontSize: 13, fontWeight: 500, color: '#374151' }}>{item.label}</span>
                    <span style={{ marginLeft: 'auto', color: '#94a3b8', fontSize: 13 }}>→</span>
                  </Link>
                ))}
              </div>
            </div>

            <div className="promo-shimmer" style={{ borderRadius: 20, overflow: 'hidden', position: 'relative' }}>
              <img src="/images/palace-king-mahal-kingdom-shiva.jpg" alt="" style={{ width: '100%', height: 130, objectFit: 'cover' }} />
              <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(135deg, rgba(139,92,246,0.85), rgba(139,92,246,0.6))', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: 20 }}>
                <h3 style={{ fontWeight: 800, color: '#fff', fontSize: 16, marginBottom: 4 }}>Expand Your Fleet</h3>
                <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.85)', marginBottom: 10 }}>Add more drivers to serve more destinations</p>
                <Link to="/owner/drivers" style={{ fontSize: 13, fontWeight: 700, color: '#fff', textDecoration: 'none' }}>Manage Drivers →</Link>
              </div>
            </div>
          </div>
        </div>

        <div style={{ marginTop: 24, display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }} className="stagger-children owner-image-row">
          {[
            { img: '/images/indian-hindu-temple-singapore.jpg', label: 'Temple Routes' },
            { img: '/images/beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg', label: 'Garden Tours' },
            { img: '/images/prasart-phimai-ancient-stone-thailand.jpg', label: 'Heritage' },
            { img: '/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg', label: 'Adventure' },
          ].map((d, i) => (
            <div key={i} className="image-showcase" style={{ height: 110, borderRadius: 16 }}>
              <img src={d.img} alt={d.label} loading="lazy" />
              <div className="overlay">
                <span style={{ color: '#fff', fontSize: 12, fontWeight: 700 }}>{d.label}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </DashboardLayout>
  )
}
