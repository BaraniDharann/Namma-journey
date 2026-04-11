import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { getAllReviews } from '../../utils/api'

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

function Stars({ rating }) {
  return (
    <div className="flex gap-0.5">
      {[1,2,3,4,5].map(s => (
        <span key={s} className={s <= rating ? 'star' : 'star-empty'}>★</span>
      ))}
    </div>
  )
}

export default function OwnerReviews() {
  const [reviews, setReviews] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState(0)

  useEffect(() => {
    getAllReviews().then(r => setReviews(r.data || [])).catch(() => {}).finally(() => setLoading(false))
  }, [])

  const filtered = filter === 0 ? reviews : reviews.filter(r => r.rating === filter)
  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 9)
  const avgRating = reviews.length ? (reviews.reduce((s, r) => s + r.rating, 0) / reviews.length).toFixed(1) : 0
  const dist = [5,4,3,2,1].map(n => ({ rating: n, count: reviews.filter(r => r.rating === n).length }))

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#0F172A', marginBottom: 4 }}>Customer Reviews</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>{reviews.length} total reviews</p>
        </div>
        <div className="review-stats-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(280px,1fr))', gap: 20, marginBottom: 24 }}>
          <div style={{ background: '#fff', borderRadius: 18, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
            <div style={{ textAlign: 'center', marginBottom: 20 }}>
              <div style={{ fontSize: 52, fontWeight: 900, background: 'linear-gradient(135deg, #f97316, #8b5cf6)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>{avgRating}</div>
              <div style={{ display: 'flex', justifyContent: 'center', gap: 2, marginBottom: 6 }}>
                {[1,2,3,4,5].map(s => <span key={s} style={{ fontSize: 18, color: s <= Math.round(avgRating) ? '#F59E0B' : '#e2e8f0' }}>★</span>)}
              </div>
              <div style={{ fontSize: 13, color: '#64748b' }}>{reviews.length} reviews</div>
            </div>
            {dist.map(d => (
              <div key={d.rating} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                <span style={{ fontSize: 12, color: '#64748b', width: 12, fontWeight: 600 }}>{d.rating}</span>
                <span style={{ fontSize: 12, color: '#F59E0B' }}>★</span>
                <div style={{ flex: 1, height: 8, borderRadius: 4, background: '#f1f5f9', overflow: 'hidden' }}>
                  <div style={{ height: '100%', borderRadius: 4, width: reviews.length ? `${(d.count/reviews.length)*100}%` : '0%', background: 'linear-gradient(90deg,#f97316,#8b5cf6)', transition: 'width 0.5s' }} />
                </div>
                <span style={{ fontSize: 11, color: '#94a3b8', width: 20, textAlign: 'right', fontWeight: 600 }}>{d.count}</span>
              </div>
            ))}
          </div>
          <div className="review-mini-stats" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, alignContent: 'start' }}>
            {[
              { label: '5 Star Reviews', value: reviews.filter(r => r.rating === 5).length, icon: '🌟', color: '#F59E0B' },
              { label: 'Total Reviews', value: reviews.length, icon: '💬', color: '#f97316' },
              { label: 'Average Rating', value: avgRating + '★', icon: '⭐', color: '#8b5cf6' },
              { label: 'This Month', value: reviews.filter(r => new Date(r.createdAt).getMonth() === new Date().getMonth()).length, icon: '📅', color: '#15803d' },
            ].map((s, i) => (
              <div key={i} style={{ background: '#fff', borderRadius: 16, padding: 18, border: '1px solid #e2e8f0', boxShadow: '0 2px 8px rgba(0,0,0,0.03)' }}>
                <div style={{ fontSize: 22, marginBottom: 8 }}>{s.icon}</div>
                <div style={{ fontSize: 20, fontWeight: 900, color: '#0F172A' }}>{loading ? '...' : s.value}</div>
                <div style={{ fontSize: 12, color: '#64748b', marginTop: 2 }}>{s.label}</div>
              </div>
            ))}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 20, flexWrap: 'wrap' }}>
          <button onClick={() => { setFilter(0); setCurrentPage(1) }} style={{ padding: '8px 16px', borderRadius: 20, fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: filter === 0 ? 'linear-gradient(135deg,#8b5cf6,#f97316)' : '#f1f5f9', color: filter === 0 ? '#fff' : '#64748b', boxShadow: filter === 0 ? '0 4px 12px rgba(139,92,246,0.3)' : 'none', transition: 'all 0.2s' }}>All</button>
          {[5,4,3,2,1].map(n => (
            <button key={n} onClick={() => { setFilter(n); setCurrentPage(1) }} style={{ padding: '8px 16px', borderRadius: 20, fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: filter === n ? 'linear-gradient(135deg,#8b5cf6,#f97316)' : '#f1f5f9', color: filter === n ? '#fff' : '#64748b', boxShadow: filter === n ? '0 4px 12px rgba(139,92,246,0.3)' : 'none', transition: 'all 0.2s' }}>{'⭐'.repeat(n)}</button>
          ))}
        </div>
        {loading ? <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}><div className="spinner" /></div>
          : filtered.length === 0 ? <div className="empty-state"><div className="empty-icon">⭐</div><h3>No reviews found</h3></div>
          : <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(300px,1fr))', gap: 16 }}>
              {paginatedItems.map(r => (
                <div key={r.id} style={{ background: '#fff', borderRadius: 18, padding: 22, border: '1px solid #e2e8f0', boxShadow: '0 2px 8px rgba(0,0,0,0.03)', transition: 'all 0.2s' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{ width: 40, height: 40, borderRadius: '50%', background: 'linear-gradient(135deg,#f97316,#8b5cf6)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, color: '#fff', fontSize: 15 }}>{r.userName?.[0]?.toUpperCase()}</div>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{r.userName}</div>
                        <div style={{ fontSize: 12, color: '#94a3b8' }}>Driver: {r.driverName}</div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 1 }}>{[1,2,3,4,5].map(s => <span key={s} style={{ fontSize: 14, color: s <= r.rating ? '#F59E0B' : '#e2e8f0' }}>★</span>)}</div>
                  </div>
                  <p style={{ fontSize: 13, color: '#374151', lineHeight: 1.6 }}>{r.feedback}</p>
                  <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 10 }}>{r.createdAt ? new Date(r.createdAt).toLocaleDateString() : ''}</div>
                </div>
              ))}
            </div>}
        {filtered.length > 0 && <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />}
      </div>
    </DashboardLayout>
  )
}
