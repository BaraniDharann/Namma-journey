import React, { useEffect, useState } from 'react'
import { getDriverEarnings, setDriverPayoutUpi } from '../../utils/api'
import Icon from '../dash/Icon'
import { Panel, Skeleton } from '../dash/ui'
import { inr } from '../../dash/metrics'

const LINE = {
  RIDE_EARNING: 'Ride paid online',
  CASH_COMMISSION: 'Commission on a cash ride',
  PAYOUT: 'Paid to you',
  COLLECTION: 'You paid the owner',
}

const UPI = /^[a-zA-Z0-9._-]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}$/

/**
 * An independent driver's money with the platform: what they are owed (or owe), recent
 * entries, and the UPI ID they are paid at. The UPI ID is only ever shown masked here.
 */
export default function EarningsPanel({ driverId }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(false)
  const [upi, setUpi] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    getDriverEarnings(driverId)
      .then(r => setData(r.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [driverId])

  const save = async () => {
    setSaving(true)
    try {
      const r = await setDriverPayoutUpi(driverId, upi.trim())
      setData(r.data)
      setEditing(false)
      setUpi('')
    } catch { /* the API layer toasted the reason */ }
    setSaving(false)
  }

  const balance = Number(data?.balance || 0)

  return (
    <Panel span={12} title="Earnings" meta={loading ? null : balance >= 0 ? 'owed to you' : 'you owe'}>
      {loading ? <Skeleton rows={3} /> : (
        <div style={{ display: 'grid', gap: 18 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-end', justifyContent: 'space-between' }}>
            <div>
              <div className="pc-figure" style={{ fontSize: 34, color: balance < 0 ? 'var(--pc-bad)' : 'var(--pc-brand-deep)' }}>{inr(Math.abs(balance))}</div>
              <small style={{ color: 'var(--pc-muted)', fontWeight: 700 }}>
                {balance > 0 ? 'The owner pays this to your UPI ID' : balance < 0 ? 'Commission on cash rides, payable to the owner' : 'All settled'}
              </small>
            </div>

            <div style={{ minWidth: 240 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }}>Payout UPI ID</div>
              {editing || !data?.upiSet ? (
                <div style={{ display: 'flex', gap: 8 }}>
                  <input className="input-field" aria-label="Payout UPI ID" placeholder="name@okbank" value={upi}
                    onChange={(e) => setUpi(e.target.value)} style={{ flex: 1, minWidth: 0 }} />
                  <button type="button" className="pc-btn pc-btn-sm" disabled={saving || !UPI.test(upi.trim())} onClick={save}>
                    {saving ? 'Saving…' : 'Save'}
                  </button>
                </div>
              ) : (
                <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                  <span style={{ fontFamily: 'monospace', fontSize: 14 }}>{data.upiMasked}</span>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setEditing(true)}><Icon name="edit" size={14} />Change</button>
                </div>
              )}
              <small style={{ color: 'var(--pc-muted)', fontSize: 11.5 }}>Stored encrypted. Only the owner sees it in full, to pay you.</small>
            </div>
          </div>

          {data?.recent?.length > 0 && (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table">
                <thead><tr><th>Date</th><th>What</th><th style={{ textAlign: 'right' }}>Fare</th><th style={{ textAlign: 'right' }}>Commission</th><th style={{ textAlign: 'right' }}>Amount</th></tr></thead>
                <tbody>
                  {data.recent.map(e => (
                    <tr key={e.id}>
                      <td style={{ whiteSpace: 'nowrap' }}>{new Date(e.createdAt).toLocaleDateString()}</td>
                      <td>{LINE[e.type] || e.type}{e.reference && <div style={{ fontSize: 11.5, color: 'var(--pc-muted)' }}>UTR {e.reference}</div>}</td>
                      <td style={{ textAlign: 'right' }}>{e.fare != null ? inr(e.fare) : '—'}</td>
                      <td style={{ textAlign: 'right' }}>{e.commission != null ? `${inr(e.commission)} (${Number(e.commissionPercent)}%)` : '—'}</td>
                      <td style={{ textAlign: 'right', fontWeight: 800, color: Number(e.amount) < 0 ? 'var(--pc-bad)' : 'var(--pc-ink)' }}>
                        {Number(e.amount) < 0 ? '−' : '+'}{inr(Math.abs(Number(e.amount)))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Panel>
  )
}
