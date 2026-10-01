import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getUserPayments } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, SummaryStrip, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'

const navItems = [
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
]

export default function UserPayments() {
  const { user } = useAuth()
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getUserPayments(user.userId).then(r => setPayments(r.data || [])).catch(() => {}).finally(() => setLoading(false))
  }, [user.userId])

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(payments, 8)

  const total = payments.filter(p => p.status === 'VERIFIED').reduce((s, p) => s + (p.amount || 0), 0)
  const pending = payments.filter(p => p.status === 'PENDING').length

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <PageHead title="Payments" sub="Your payment history" />

      <div className="pc-grid">
        <SummaryStrip
          loading={loading}
          items={[
            { icon: 'wallet', label: 'Total Paid', value: inr(total), sub: 'verified payments' },
            { icon: 'chart', label: 'Transactions', value: payments.length, sub: 'all methods' },
            { icon: 'clock', label: 'Pending', value: pending, sub: 'awaiting verification' },
          ]}
        />

        <Panel span={12} pad={false} title="Transaction History" meta={!loading && payments.length > 0 && `${payments.length} total`}>
          {loading ? <div style={{ padding: '0 20px 18px' }}><Skeleton rows={4} /></div>
            : payments.length === 0 ? (
              <Empty icon="card" title="No payments yet">Your transactions will appear here</Empty>
            )
            : <div style={{ overflowX: 'auto' }}>
                <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      {['Payment ID', 'Amount', 'Method', 'Status', 'Date'].map(h => (
                        <th key={h} style={{ textAlign: 'left', textTransform: 'uppercase' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedItems.map(p => (
                      <tr key={p.paymentId}>
                        <td style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--pc-muted)' }}>{String(p.paymentId).slice(0,12)}...</td>
                        <td style={{ fontWeight: 800, color: 'var(--pc-ink)' }}>{inr(p.amount)}</td>
                        <td>
                          <span className="pc-chip"><Icon name={p.paymentMethod === 'UPI' ? 'qr' : 'wallet'} size={14} />{p.paymentMethod}</span>
                        </td>
                        <td>
                          <StatusPill status={p.status === 'VERIFIED' || p.status === 'PENDING' ? p.status : 'FAILED'} label={p.status === 'VERIFIED' || p.status === 'PENDING' ? undefined : p.status} />
                        </td>
                        <td>{p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div style={{ padding: '12px 20px' }}>
                  <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
                </div>
              </div>}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
