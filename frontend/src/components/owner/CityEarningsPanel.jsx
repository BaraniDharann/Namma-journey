import React, { useEffect, useState } from 'react'
import { getCityEarnings } from '../../utils/api'
import { Panel, Skeleton } from '../dash/ui'
import { inr } from '../../dash/metrics'

const KIND = {
  RIDE_EARNING: 'City ride paid online',
  CASH_COMMISSION: 'Commission on a cash ride',
  PAYOUT: 'Paid to you',
  COLLECTION: 'You paid commission',
}

/**
 * City rides done in my City cars (V35): what the platform owes me (online rides, less commission)
 * or I owe it (commission on cash rides my drivers collected). Hidden until there is something to show.
 */
export default function CityEarningsPanel({ hasCityCars }) {
  const [view, setView] = useState(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    getCityEarnings().then((r) => setView(r.data)).catch(() => setFailed(true))
  }, [])

  if (failed) return null
  if (view && !view.recent?.length && !hasCityCars) return null

  const balance = Number(view?.balance || 0)
  return (
    <Panel span={12} title="City rides in your cars" meta={view ? (balance < 0 ? `you owe ${inr(-balance)}` : `owed to you ${inr(balance)}`) : null}>
      {!view ? <Skeleton rows={3} /> : !view.recent.length ? (
        <div style={{ fontSize: 13, color: 'var(--pc-muted)' }}>
          No City rides yet. When your City car drivers finish rides, the money shows here: online rides are paid to you
          less the city&rsquo;s commission; on cash rides your driver keeps the fare and you owe the commission.
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead><tr><th>When</th><th>What</th><th style={{ textAlign: 'right' }}>Fare</th><th style={{ textAlign: 'right' }}>Amount</th></tr></thead>
            <tbody>
              {view.recent.map((e) => {
                const a = Number(e.amount)
                return (
                  <tr key={e.id}>
                    <td style={{ whiteSpace: 'nowrap', fontSize: 12.5 }}>{new Date(e.createdAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}</td>
                    <td>{KIND[e.type] || e.type}{e.reference ? <span style={{ fontSize: 12, color: 'var(--pc-muted)' }}> · UTR {e.reference}</span> : null}</td>
                    <td style={{ textAlign: 'right' }}>{e.fare != null ? inr(e.fare) : '—'}</td>
                    <td style={{ textAlign: 'right', fontWeight: 800, color: a < 0 ? 'var(--pc-bad)' : 'var(--pc-ink)' }}>{a < 0 ? `−${inr(-a)}` : inr(a)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}
