import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { getPayouts, recordPayout, getPendingDues, confirmDues, rejectDues } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, SummaryStrip, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'
import { useCelebrate } from '../../components/celebrate/Celebration'

const navItems = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/driver-applications', icon: '', label: 'Applications' },
  { path: '/owner/cities', icon: '', label: 'Cities' },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/payouts', icon: '', label: 'Payouts' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
]

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }

/** Opens the owner's UPI app with the payee and amount filled in; the UTR comes back from it. */
const upiLink = (row, amount) =>
  `upi://pay?pa=${encodeURIComponent(row.upiId)}&pn=${encodeURIComponent(row.name || 'Driver')}` +
  `&am=${Number(amount).toFixed(2)}&cu=INR&tn=${encodeURIComponent('Namma Journey payout')}`

function TransferModal({ row, onClose, onSaved }) {
  const owed = Number(row.balance)
  const type = owed >= 0 ? 'PAYOUT' : 'COLLECTION'
  const [amount, setAmount] = useState(Math.abs(owed).toFixed(2))
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const value = Number(amount)
  const valid = value > 0 && value <= Math.abs(owed) && /^[A-Za-z0-9]{6,64}$/.test(reference.trim())

  const save = async () => {
    setSaving(true)
    try {
      await recordPayout(row.driverId, { type, amount: value.toFixed(2), reference: reference.trim(), note })
      onSaved(type, value)
    } catch { /* the API layer toasted the reason */ }
    setSaving(false)
  }

  return (
    <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,.45)', display: 'grid', placeItems: 'center', padding: 16, zIndex: 50 }}>
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="transfer-title" style={{ width: '100%', maxWidth: 440, background: 'var(--pc-paper, #fff)', borderRadius: 20, padding: 24 }}>
        <h3 id="transfer-title" style={{ margin: '0 0 4px', fontSize: 18 }}>{type === 'PAYOUT' ? `Pay ${row.name}` : `Collect from ${row.name}`}</h3>
        <p style={{ margin: '0 0 18px', fontSize: 13, color: 'var(--pc-muted)' }}>
          {type === 'PAYOUT'
            ? <>Send the money from your UPI app to <strong style={{ color: 'var(--pc-ink)' }}>{row.upiId}</strong>, then record the UTR here.</>
            : <>{row.name} owes {inr(-owed)} in commission on cash rides. Record it once it reaches you.</>}
        </p>
        <label style={labelStyle} htmlFor="transfer-amount">Amount (₹)</label>
        <input id="transfer-amount" className="input-field" type="number" min="0.01" step="0.01" inputMode="decimal"
          value={amount} onChange={(e) => setAmount(e.target.value)} style={{ width: '100%', marginBottom: 12 }} />
        {type === 'PAYOUT' && value > 0 && (
          <a href={upiLink(row, value)} className="pc-btn pc-btn-ghost pc-btn-sm" style={{ marginBottom: 14 }}><Icon name="phone" size={14} />Open UPI app with {inr(value)}</a>
        )}
        <label style={labelStyle} htmlFor="transfer-utr">UPI reference (UTR)</label>
        <input id="transfer-utr" className="input-field" maxLength={64} value={reference} placeholder="e.g. 431289765012"
          onChange={(e) => setReference(e.target.value)} style={{ width: '100%', marginBottom: 12 }} />
        <label style={labelStyle} htmlFor="transfer-note">Note (optional)</label>
        <input id="transfer-note" className="input-field" maxLength={255} value={note} placeholder="e.g. Week of 20 Oct"
          onChange={(e) => setNote(e.target.value)} style={{ width: '100%', marginBottom: 18 }} />
        <div style={{ display: 'flex', gap: 10 }}>
          <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={{ flex: 1 }}>Cancel</button>
          <button type="button" onClick={save} disabled={!valid || saving} className="pc-btn" style={{ flex: 1 }}>
            {saving ? 'Saving…' : type === 'PAYOUT' ? 'Record payout' : 'Record collection'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function OwnerPayouts() {
  const celebrate = useCelebrate()
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [active, setActive] = useState(null)

  const [pending, setPending] = useState([])
  const [deciding, setDeciding] = useState(null)

  const load = async () => {
    try {
      const [res, due] = await Promise.all([getPayouts(), getPendingDues().catch(() => ({ data: [] }))])
      setRows(res.data || [])
      setPending(due.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  const decide = async (p, received) => {
    if (!received && !confirm(`Mark ${inr(p.amount)} from ${p.driverName} (UTR ${p.utr}) as not received? They will be asked to check the UTR.`)) return
    setDeciding(p.id)
    try {
      if (received) {
        await confirmDues(p.id)
        celebrate({ title: 'Payment confirmed', message: `${inr(p.amount)} from ${p.driverName} recorded.`, autoClose: 3200 })
      } else {
        await rejectDues(p.id)
      }
      load()
    } catch { /* the API layer toasted the reason */ }
    setDeciding(null)
  }

  useEffect(() => { load() }, [])

  const owedToDrivers = rows.reduce((s, r) => s + Math.max(0, Number(r.balance)), 0)
  const owedByDrivers = rows.reduce((s, r) => s + Math.max(0, -Number(r.balance)), 0)

  const saved = (type, amount) => {
    const row = active
    setActive(null)
    celebrate({ title: type === 'PAYOUT' ? 'Payout recorded' : 'Collection recorded', message: `${inr(amount)} ${type === 'PAYOUT' ? 'to' : 'from'} ${row.name}.`, autoClose: 3200 })
    load()
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      {active && <TransferModal row={active} onClose={() => setActive(null)} onSaved={saved} />}
      <PageHead title="Payouts" sub="What you owe independent drivers for rides paid to you, and the commission they owe on cash rides." />

      <div className="pc-grid">
        <SummaryStrip
          loading={loading}
          items={[
            { icon: 'wallet', label: 'You owe drivers', value: inr(owedToDrivers), sub: 'online rides, less commission' },
            { icon: 'rupee', label: 'Drivers owe you', value: inr(owedByDrivers), sub: 'commission on cash rides' },
            { icon: 'users', label: 'Independent drivers', value: rows.length.toLocaleString('en-IN'), sub: `${rows.filter(r => !r.upiId).length} without a UPI ID` },
          ]}
        />

        {pending.length > 0 && (
          <Panel span={12} pad={false} title="Payments from drivers to confirm" meta={`${pending.length} waiting`}>
            <p style={{ margin: '0 20px 12px', fontSize: 13, color: 'var(--pc-muted)' }}>
              Drivers paid their commission to your UPI and sent the UTR. Find each one in your bank app before confirming:
              confirming records it and lowers what they owe.
            </p>
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table">
                <thead><tr><th>Driver</th><th style={{ textAlign: 'right' }}>Amount</th><th>UTR</th><th>Sent</th><th>Action</th></tr></thead>
                <tbody>
                  {pending.map(p => (
                    <tr key={p.id}>
                      <td><strong>{p.driverName}</strong><div style={{ fontSize: 12, color: 'var(--pc-muted)' }}>{p.driverMobile} · owes {inr(p.driverOwes)}</div></td>
                      <td style={{ textAlign: 'right', fontWeight: 800 }}>{inr(p.amount)}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{p.utr}</td>
                      <td style={{ whiteSpace: 'nowrap', fontSize: 12.5 }}>{new Date(p.submittedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}</td>
                      <td>
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                          <button type="button" className="pc-btn pc-btn-teal pc-btn-sm" disabled={deciding === p.id} onClick={() => decide(p, true)}><Icon name="check" size={14} />Received</button>
                          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" disabled={deciding === p.id} onClick={() => decide(p, false)} style={{ color: 'var(--pc-bad)' }}><Icon name="x" size={14} />Not received</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>
        )}

        <Panel span={12} pad={false} title="Balances" meta={loading ? null : `${rows.length} drivers`}>
          {loading ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={5} /></div>
            : rows.length === 0 ? <Empty icon="users" title="No independent drivers yet">Drivers who sign up themselves appear here once approved.</Empty>
              : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="data-table">
                    <thead><tr><th>Driver</th><th>Payout UPI ID</th><th style={{ textAlign: 'right' }}>Balance</th><th>Action</th></tr></thead>
                    <tbody>
                      {rows.map(r => {
                        const b = Number(r.balance)
                        return (
                          <tr key={r.driverId}>
                            <td><strong>{r.name}</strong><div style={{ fontSize: 12, color: 'var(--pc-muted)' }}>{r.mobile}</div></td>
                            <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{r.upiId || <span style={{ color: 'var(--pc-muted)', fontFamily: 'inherit' }}>Not added yet</span>}</td>
                            <td style={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 800, color: b > 0 ? 'var(--pc-ink)' : b < 0 ? 'var(--pc-bad)' : 'var(--pc-muted)' }}>
                              {b < 0 ? `owes ${inr(-b)}` : inr(b)}
                            </td>
                            <td>
                              {b > 0 && (r.upiId
                                ? <button type="button" className="pc-btn pc-btn-teal pc-btn-sm" onClick={() => setActive(r)}><Icon name="wallet" size={14} />Pay</button>
                                : <span style={{ fontSize: 12, color: 'var(--pc-muted)' }}>Waiting for UPI ID</span>)}
                              {b < 0 && <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setActive(r)}><Icon name="check" size={14} />Record collection</button>}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
