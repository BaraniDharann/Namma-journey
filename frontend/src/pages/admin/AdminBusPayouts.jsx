import React, { useCallback, useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton } from '../../components/dash/ui'
import { BusPill, Tabs, mutedStyle } from '../../components/bus/busUi'
import { adminBusPayouts, adminMarkBusPayoutPaid } from '../../utils/busApi'
import { inr } from '../../utils/busText'
import { PAYOUT_STATUS, payoutTotals, whenText } from '../../utils/busBooking'
import { adminNavItems, errorText, when } from './adminNav'
import '../../styles/bus.css'

const TABS = [['DUE', 'Due'], ['PAID', 'Paid']]

/** A value with a copy button, for pasting into the bank's transfer screen. */
function Copy({ label, value }) {
  if (!value) return null
  const copy = async () => {
    try { await navigator.clipboard.writeText(String(value)); toast.success(`${label} copied`, { id: 'bus-copy' }) } catch { toast.error('Copy is blocked in this browser') }
  }
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, padding: '2px 0' }}>
      <span style={{ ...mutedStyle, minWidth: 64 }}>{label}</span>
      <span className="bus-copy"><b style={{ fontVariantNumeric: 'tabular-nums' }}>{value}</b>
        <button type="button" onClick={copy} aria-label={`Copy ${label}`} title={`Copy ${label}`}><Icon name="copy" size={13} /></button>
      </span>
    </div>
  )
}

function MarkPaid({ payout, onClose, onDone }) {
  const [utr, setUtr] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const save = async (e) => {
    e.preventDefault()
    if (!utr.trim()) { setError('Enter the UTR from the bank transfer.'); return }
    setBusy(true); setError('')
    try { onDone((await adminMarkBusPayoutPaid(payout.id, utr.trim())).data) } catch (err) { setError(errorText(err, 'Could not mark this payout as paid.')); setBusy(false) }
  }
  return (
    <div className="bus-modal-scrim" onClick={(e) => { if (e.target === e.currentTarget && !busy) onClose() }}>
      <form className="bus-modal" role="dialog" aria-modal="true" aria-labelledby="paid-title" onSubmit={save}>
        <h2 id="paid-title">Mark {inr(payout.net)} as paid</h2>
        <p style={{ ...mutedStyle, fontSize: 13, margin: '0 0 12px' }}>{payout.travelsName} ({payout.ownerCode}) · trip of {whenText(payout.travelDate)}</p>
        <label htmlFor="utr" style={{ display: 'block', fontSize: 13, fontWeight: 700, marginBottom: 6 }}>UTR / transaction reference</label>
        <input id="utr" className="input-field" autoFocus value={utr} onChange={(e) => setUtr(e.target.value)} maxLength={64} />
        {error && <div className="pc-error" role="alert" style={{ marginTop: 10 }}>{error}</div>}
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
          <button type="button" className="pc-btn pc-btn-ghost" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="pc-btn" disabled={busy || !utr.trim()}>{busy ? 'Saving…' : 'Mark paid'}</button>
        </div>
      </form>
    </div>
  )
}

/** Bus payouts owed to owners (one per completed trip), with their bank details to pay from. */
export default function AdminBusPayouts() {
  const [tab, setTab] = useState('DUE')
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [paying, setPaying] = useState(null)

  const load = useCallback(async (status) => {
    setError('')
    try { setRows((await adminBusPayouts(status)).data || []) } catch (e) { setError(errorText(e, 'Could not load payouts.')); setRows([]) }
  }, [])
  useEffect(() => { setRows(null); load(tab) }, [tab, load])

  const total = payoutTotals(rows)[tab]

  return (
    <DashboardLayout navItems={adminNavItems} role="ROLE_ADMIN">
      <PageHead title="Bus payouts" sub={rows ? `${total.count} payout${total.count === 1 ? '' : 's'} · ${inr(total.net)} ${tab === 'DUE' ? 'to pay' : 'paid'}` : 'Owner earnings from completed bus trips'} />
      <Tabs tabs={TABS} value={tab} onChange={setTab} label="Payout status" />

      <div className="pc-grid">
        {rows == null ? (
          <Panel span={12}><Skeleton rows={4} height={18} /></Panel>
        ) : error ? (
          <Panel span={12}><div className="pc-error" role="alert"><Icon name="alert" size={16} /><span style={{ flex: 1 }}>{error}</span><button type="button" className="pc-btn pc-btn-sm" onClick={() => load(tab)}>Retry</button></div></Panel>
        ) : rows.length === 0 ? (
          <Panel span={12}><Empty icon="wallet" title={tab === 'DUE' ? 'Nothing to pay' : 'No payouts yet'}>{tab === 'DUE' ? 'Payouts appear the morning after a trip completes.' : ''}</Empty></Panel>
        ) : (
          <Panel span={12} title={tab === 'DUE' ? 'Due' : 'Paid'} meta={inr(total.net)}>
            {rows.map((p) => (
              <div key={p.id} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 14, padding: '16px 0', borderTop: '1px solid var(--pc-line)' }}>
                <div style={{ minWidth: 0 }}>
                  <div className="pc-row-title" style={{ fontSize: 15.5 }}>{p.travelsName} <span style={mutedStyle}>({p.ownerCode})</span></div>
                  <div style={mutedStyle}>{whenText(p.travelDate)} · {p.routeName} · {p.busName}</div>
                  <div style={{ fontSize: 13, marginTop: 6, color: 'var(--pc-ink-2)' }}>
                    Gross {inr(p.gross)} − commission {inr(p.commission)} ({p.commissionPercent}%)
                  </div>
                  <div style={{ font: '700 20px var(--pc-fd)', marginTop: 4 }}>{inr(p.net)}</div>
                </div>
                <div>
                  {p.upiId || p.bankAccountNumber ? (
                    <>
                      <Copy label="UPI" value={p.upiId} />
                      <Copy label="Holder" value={p.bankAccountHolder} />
                      <Copy label="Account" value={p.bankAccountNumber} />
                      <Copy label="IFSC" value={p.bankIfsc} />
                      {p.bankName && <div style={{ fontSize: 13, padding: '2px 0' }}><span style={{ ...mutedStyle, display: 'inline-block', minWidth: 64 }}>Bank</span><b>{p.bankName}</b></div>}
                    </>
                  ) : (
                    <div className="pc-error" style={{ background: 'var(--pc-warn-soft)', color: 'var(--pc-warn)' }}><Icon name="alert" size={15} />No UPI or bank details on file for this owner.</div>
                  )}
                </div>
                <div style={{ display: 'grid', justifyItems: 'end', alignContent: 'start', gap: 8 }}>
                  <BusPill table={PAYOUT_STATUS} status={p.status} />
                  {p.status === 'DUE'
                    ? <button type="button" className="pc-btn pc-btn-sm" onClick={() => setPaying(p)}><Icon name="check" size={15} />Mark paid</button>
                    : <span style={{ ...mutedStyle, textAlign: 'right' }}>UTR <b>{p.utr}</b><br />{when(p.paidAt)}</span>}
                </div>
              </div>
            ))}
          </Panel>
        )}
      </div>

      {paying && (
        <MarkPaid payout={paying} onClose={() => setPaying(null)}
          onDone={() => { setPaying(null); toast.success('Payout marked as paid'); load(tab) }} />
      )}
    </DashboardLayout>
  )
}
