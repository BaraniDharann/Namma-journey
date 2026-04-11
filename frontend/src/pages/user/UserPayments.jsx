import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { useAuth } from '../../context/AuthContext'
import { getUserPayments } from '../../utils/api'
import Pagination, { usePagination } from '../../components/Pagination'

const navItems = [
  { path: '/user/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/user/bookings', icon: '📋', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '➕', label: 'New Booking' },
  { path: '/user/payments', icon: '💳', label: 'Payments' },
  { path: '/user/package-bookings', icon: '📦', label: 'My Packages' },
  { path: '/user/profile', icon: '👤', label: 'Profile' },
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

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <div className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 900, fontSize: 26, color: '#0F172A', letterSpacing: '-0.5px', marginBottom: 4 }}>Payments</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>Your payment history</p>
        </div>

        {/* Stats */}
        <div className="stats-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(180px,1fr))', gap: 16, marginBottom: 24 }}>
          {[
            { label: 'Total Paid', value: `₹${total.toLocaleString()}`, icon: '💰', color: '#22c55e' },
            { label: 'Transactions', value: payments.length, icon: '📊', color: '#f97316' },
            { label: 'Pending', value: payments.filter(p => p.status === 'PENDING').length, icon: '⏳', color: '#F59E0B' },
          ].map((s, i) => (
            <div key={i} style={{ background: '#fff', borderRadius: 18, padding: '22px 20px', border: '1px solid #e2e8f0', borderLeft: `4px solid ${s.color}`, transition: 'all 0.25s ease', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}
              onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-3px)'; e.currentTarget.style.boxShadow = `0 8px 24px ${s.color}18` }}
              onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.04)' }}>
              <div style={{ width: 44, height: 44, borderRadius: 12, background: `${s.color}12`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, marginBottom: 14 }}>{s.icon}</div>
              <div style={{ fontSize: 24, fontWeight: 900, color: '#0F172A' }}>{loading ? '...' : s.value}</div>
              <div style={{ fontSize: 13, color: '#64748b', marginTop: 4, fontWeight: 500 }}>{s.label}</div>
            </div>
          ))}
        </div>

        {/* Transaction table */}
        <div style={{ background: '#fff', borderRadius: 20, border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 2px 12px rgba(0,0,0,0.03)' }}>
          <div style={{ padding: '20px 24px 16px' }}>
            <h2 style={{ fontWeight: 800, fontSize: 17, color: '#0F172A', letterSpacing: '-0.3px' }}>Transaction History</h2>
          </div>
          {loading ? <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}><div className="spinner" /></div>
            : payments.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '48px 24px' }}>
                <div style={{ fontSize: 48, marginBottom: 12 }}>💳</div>
                <h3 style={{ fontWeight: 700, color: '#0F172A', fontSize: 16, marginBottom: 4 }}>No payments yet</h3>
                <p style={{ color: '#94a3b8', fontSize: 13 }}>Your transactions will appear here</p>
              </div>
            )
            : <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid #f1f5f9' }}>
                      {['Payment ID', 'Amount', 'Method', 'Status', 'Date'].map(h => (
                        <th key={h} style={{ padding: '12px 20px', fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', textAlign: 'left' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedItems.map(p => (
                      <tr key={p.paymentId} style={{ borderBottom: '1px solid #f8fafc', transition: 'background 0.15s' }}
                        onMouseEnter={e => e.currentTarget.style.background = '#FAFAFE'}
                        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                        <td style={{ padding: '14px 20px', fontFamily: 'monospace', fontSize: 12, color: '#64748b' }}>{String(p.paymentId).slice(0,12)}...</td>
                        <td style={{ padding: '14px 20px', fontWeight: 700, color: '#f97316', fontSize: 14 }}>₹{p.amount?.toLocaleString()}</td>
                        <td style={{ padding: '14px 20px' }}>
                          <span style={{ padding: '4px 12px', borderRadius: 8, fontSize: 12, fontWeight: 600, background: '#fff7ed', color: '#f97316' }}>{p.paymentMethod === 'UPI' ? '📱' : '💵'} {p.paymentMethod}</span>
                        </td>
                        <td style={{ padding: '14px 20px' }}>
                          <span className={`badge ${p.status === 'VERIFIED' ? 'badge-confirmed' : p.status === 'PENDING' ? 'badge-pending' : 'badge-cancelled'}`}>{p.status}</span>
                        </td>
                        <td style={{ padding: '14px 20px', fontSize: 13, color: '#64748b' }}>{p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div style={{ padding: '12px 20px' }}>
                  <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
                </div>
              </div>}
        </div>
      </div>
    </DashboardLayout>
  )
}
