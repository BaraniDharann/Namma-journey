import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { getAllReviews } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, SummaryStrip, Empty, Skeleton } from '../../components/dash/ui'
import { HBarList } from '../../components/dash/charts'

const navItems = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
]

const starOn = { fill: 'currentColor', color: 'var(--pc-s4)' }
const starOff = { color: 'var(--pc-line)' }

function Stars({ value, size = 14 }) {
  return (
    <span role="img" aria-label={`${value} out of 5 stars`} style={{ display: 'inline-flex', gap: 1 }}>
      {[1, 2, 3, 4, 5].map(s => <Icon key={s} name="star" size={size} style={s <= value ? starOn : starOff} />)}
    </span>
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
  const thisMonth = reviews.filter(r => new Date(r.createdAt).getMonth() === new Date().getMonth()).length
  const fiveStar = reviews.filter(r => r.rating === 5).length

  const chipStyle = (on) => ({ border: 'none', cursor: 'pointer', ...(on ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) })

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Customer Reviews" sub={`${reviews.length} total reviews`} />

      <div className="pc-grid">
        <SummaryStrip
          loading={loading}
          items={[
            { icon: 'star', label: 'Average Rating', value: avgRating, sub: `from ${reviews.length} review${reviews.length === 1 ? '' : 's'}` },
            { icon: 'sparkle', label: '5 Star Reviews', value: fiveStar, sub: reviews.length ? `${Math.round((fiveStar / reviews.length) * 100)}% of all reviews` : 'No reviews yet' },
            { icon: 'mail', label: 'Total Reviews', value: reviews.length, sub: 'all time' },
            { icon: 'calendar', label: 'This Month', value: thisMonth, sub: 'new reviews' },
          ]}
        />

        <Panel span={4} title="Overall rating">
          {loading ? <Skeleton rows={3} /> : (
            <div style={{ textAlign: 'center', padding: '8px 0' }}>
              <div className="pc-figure" style={{ fontSize: 52, color: 'var(--pc-ink)', marginBottom: 8 }}>{avgRating}</div>
              <Stars value={Math.round(avgRating)} size={20} />
              <div style={{ fontSize: 13, color: 'var(--pc-muted)', marginTop: 6 }}>{reviews.length} reviews</div>
            </div>
          )}
        </Panel>

        <Panel span={8} title="Rating breakdown" meta="number of reviews">
          {loading ? <Skeleton rows={5} /> : (
            <HBarList rows={dist.map(d => [`${d.rating} star${d.rating === 1 ? '' : 's'}`, d.count])} slot="s4" caption="Reviews by rating" />
          )}
        </Panel>

        <Panel
          span={12}
          title="Reviews"
          meta={loading ? null : `${filtered.length} shown`}
        >
          <div className="pc-chips" role="group" aria-label="Filter by rating" style={{ marginBottom: 16 }}>
            <button type="button" aria-pressed={filter === 0} onClick={() => { setFilter(0); setCurrentPage(1) }} className="pc-chip" style={chipStyle(filter === 0)}>All</button>
            {[5,4,3,2,1].map(n => (
              <button key={n} type="button" aria-pressed={filter === n} aria-label={`${n} star${n === 1 ? '' : 's'}`} onClick={() => { setFilter(n); setCurrentPage(1) }} className="pc-chip" style={chipStyle(filter === n)}>
                {n}<Icon name="star" size={13} style={starOn} />
              </button>
            ))}
          </div>

          {loading ? <Skeleton rows={6} />
            : filtered.length === 0 ? <Empty icon="star" title="No reviews found">{filter ? 'No reviews with this rating yet.' : 'Reviews will appear here once travellers rate their trips.'}</Empty>
              : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(min(300px,100%),1fr))', gap: 14 }}>
                  {paginatedItems.map(r => (
                    <article key={r.id} style={{ background: 'var(--pc-wash)', borderRadius: 16, padding: 18 }}>
                      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10, marginBottom: 12 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                          <div style={{ width: 38, height: 38, borderRadius: '50%', background: 'var(--pc-brand-soft)', color: 'var(--pc-brand-deep)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 15, flexShrink: 0 }}>{r.userName?.[0]?.toUpperCase()}</div>
                          <div style={{ minWidth: 0 }}>
                            <div className="pc-row-title" style={{ fontSize: 14 }}>{r.userName}</div>
                            <div style={{ fontSize: 12, color: 'var(--pc-muted)' }}>Driver: {r.driverName}</div>
                          </div>
                        </div>
                        <Stars value={r.rating} />
                      </div>
                      <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', lineHeight: 1.6 }}>{r.feedback}</p>
                      <div style={{ fontSize: 11, color: 'var(--pc-muted)', marginTop: 10 }}>{r.createdAt ? new Date(r.createdAt).toLocaleDateString() : ''}</div>
                    </article>
                  ))}
                </div>
              )}
          {filtered.length > 0 && <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
