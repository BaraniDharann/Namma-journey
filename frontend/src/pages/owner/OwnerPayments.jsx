import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import Pagination, { usePagination } from '../../components/Pagination'
import { getPendingPayments, getPendingCityPayments, verifyPayment } from '../../utils/api'
import useOwnerMe from '../../hooks/useOwnerMe'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, SummaryStrip, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'
import { useCelebrate } from '../../components/celebrate/Celebration'

const mono = { fontFamily: 'monospace', fontSize: 12, color: 'var(--pc-ink-2)' }

export default function OwnerPayments() {
  const celebrate = useCelebrate()
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [verifying, setVerifying] = useState(null)
  const [verified, setVerified] = useState([])
  // The platform owner also confirms City-ride UPI payments; other owners only see their Trips.
  const me = useOwnerMe()
  const [scope, setScope] = useState('trips')

  const load = async () => {
    try {
      const res = scope === 'city' ? await getPendingCityPayments() : await getPendingPayments()
      setPayments(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { setLoading(true); load() }, [scope]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleVerify = async (paymentId) => {
    setVerifying(paymentId)
    try {
      await verifyPayment(paymentId)
      setVerified(p => [...p, paymentId])
      load()
      const amount = payments.find(p => p.paymentId === paymentId)?.amount || 0
      celebrate({ title: 'Payment verified', message: `₹${Math.round(amount).toLocaleString('en-IN')} confirmed. The traveller has been notified.` })
    } catch { /* ignore */ }
    setVerifying(null)
  }

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(payments, 8)

  const totalPending = payments.reduce((s, p) => s + (p.amount || 0), 0)

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      <PageHead title="Payments" sub="Verify and manage payments" />
      {me?.platform && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <button type="button" className={`pc-btn pc-btn-sm ${scope === 'trips' ? 'pc-btn-teal' : ''}`} onClick={() => setScope('trips')}>My Trips</button>
          <button type="button" className={`pc-btn pc-btn-sm ${scope === 'city' ? 'pc-btn-teal' : ''}`} onClick={() => setScope('city')}>City rides</button>
        </div>
      )}

      <div className="pc-grid">
        <SummaryStrip
          loading={loading}
          items={[
            { icon: 'clock', label: 'Pending Payments', value: payments.length.toLocaleString('en-IN'), sub: payments.length ? 'waiting for you to verify' : 'nothing waiting' },
            { icon: 'wallet', label: 'Pending Amount', value: inr(totalPending), sub: 'across pending payments' },
            { icon: 'check', label: 'Verified Today', value: verified.length.toLocaleString('en-IN'), sub: 'this session' },
          ]}
        />

        <Panel span={12} pad={false} title="Pending Verifications" meta={loading ? null : `${payments.length} pending`}>
          {loading ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={5} /></div>
            : payments.length === 0 ? <Empty icon="check" title="All payments verified!">No pending payments</Empty>
              : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="data-table">
                    <thead><tr><th>Payment ID</th><th>Booking ID</th><th style={{ textAlign: 'right' }}>Amount</th><th>Method</th><th>Date</th><th>Action</th></tr></thead>
                    <tbody>
                      {paginatedItems.map(p => (
                        <tr key={p.paymentId}>
                          <td style={mono} title={String(p.paymentId)}>{String(p.paymentId).slice(0, 12)}...</td>
                          <td style={mono} title={String(p.bookingId)}>{String(p.bookingId).slice(0, 12)}...</td>
                          <td style={{ textAlign: 'right', fontWeight: 800, color: 'var(--pc-ink)', whiteSpace: 'nowrap' }}>{inr(p.amount)}</td>
                          <td><span className="pc-chip" style={{ whiteSpace: 'nowrap' }}><Icon name={p.paymentMethod === 'UPI' ? 'qr' : 'rupee'} size={13} />{p.paymentMethod}</span></td>
                          <td style={{ whiteSpace: 'nowrap' }}>{p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}</td>
                          <td>
                            <button type="button" onClick={() => handleVerify(p.paymentId)} disabled={verifying === p.paymentId} className="pc-btn pc-btn-teal pc-btn-sm">
                              <Icon name="check" size={14} />{verifying === p.paymentId ? 'Verifying...' : 'Verify'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
                </div>
              )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
