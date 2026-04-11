import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { getPendingPayments, verifyPayment } from '../../utils/api'

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

export default function OwnerPayments() {
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [verifying, setVerifying] = useState(null)
  const [verified, setVerified] = useState([])

  const load = async () => {
    try {
      const res = await getPendingPayments()
      setPayments(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  const handleVerify = async (paymentId) => {
    setVerifying(paymentId)
    try {
      await verifyPayment(paymentId)
      setVerified(p => [...p, paymentId])
      load()
    } catch { /* ignore */ }
    setVerifying(null)
  }

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(payments, 8)

  const totalPending = payments.reduce((s, p) => s + (p.amount || 0), 0)

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#0F172A', marginBottom: 4 }}>Payments</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>Verify and manage payments</p>
        </div>
        <div className="stats-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(180px,1fr))', gap: 16, marginBottom: 24 }}>
          {[
            { label: 'Pending Payments', value: payments.length, icon: '⏳', color: '#F59E0B' },
            { label: 'Pending Amount', value: `₹${totalPending.toLocaleString()}`, icon: '💰', color: '#f97316' },
            { label: 'Verified Today', value: verified.length, icon: '✅', color: '#15803d' },
          ].map((s, i) => (
            <div key={i} style={{ background: '#fff', borderRadius: 18, padding: '20px 18px', border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
              <div style={{ width: 44, height: 44, borderRadius: 14, background: s.color + '15', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, marginBottom: 14 }}>{s.icon}</div>
              <div style={{ fontSize: 24, fontWeight: 900, color: '#0F172A' }}>{loading ? '...' : s.value}</div>
              <div style={{ fontSize: 13, color: '#64748b', marginTop: 4 }}>{s.label}</div>
            </div>
          ))}
        </div>
        <div style={{ background: '#fff', borderRadius: 18, overflow: 'hidden', border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
          <div style={{ padding: '18px 20px 14px', borderBottom: '1px solid #f1f5f9' }}><h2 style={{ fontWeight: 700, fontSize: 16, color: '#0F172A' }}>Pending Verifications</h2></div>
          {loading ? <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}><div className="spinner" /></div>
            : payments.length === 0 ? <div className="empty-state"><div className="empty-icon">✅</div><h3>All payments verified!</h3><p>No pending payments</p></div>
            : <div style={{ overflowX: 'auto' }}>
                <table className="data-table">
                  <thead><tr><th>Payment ID</th><th>Booking ID</th><th>Amount</th><th>Method</th><th>Date</th><th>Action</th></tr></thead>
                  <tbody>
                    {paginatedItems.map(p => (
                      <tr key={p.paymentId}>
                        <td style={{ fontFamily: 'monospace', fontSize: 12, color: '#475569' }}>{String(p.paymentId).slice(0,12)}...</td>
                        <td style={{ fontFamily: 'monospace', fontSize: 12, color: '#475569' }}>{String(p.bookingId).slice(0,12)}...</td>
                        <td style={{ fontWeight: 700, color: '#f97316' }}>₹{p.amount?.toLocaleString()}</td>
                        <td><span style={{ padding: '4px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600, background: p.paymentMethod === 'UPI' ? '#f3e8ff' : '#f0fdf4', color: p.paymentMethod === 'UPI' ? '#8b5cf6' : '#16a34a' }}>{p.paymentMethod === 'UPI' ? '📱' : '💵'} {p.paymentMethod}</span></td>
                        <td style={{ fontSize: 13, color: '#475569' }}>{p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}</td>
                        <td><button onClick={() => handleVerify(p.paymentId)} disabled={verifying === p.paymentId} style={{ padding: '7px 16px', borderRadius: 20, fontSize: 12, fontWeight: 700, background: '#dcfce7', color: '#15803d', border: '1px solid #86efac', cursor: 'pointer', transition: 'all 0.15s' }}>{verifying === p.paymentId ? '...' : '✓ Verify'}</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
              </div>}
        </div>
      </div>
    </DashboardLayout>
  )
}
