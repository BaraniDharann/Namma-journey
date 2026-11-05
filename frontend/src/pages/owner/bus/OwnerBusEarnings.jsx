import React, { useCallback, useEffect, useMemo, useState } from 'react'
import DashboardLayout from '../../../components/DashboardLayout'
import Icon from '../../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton, SummaryStrip } from '../../../components/dash/ui'
import { BusPill, ownerNavItems } from '../../../components/bus/busUi'
import { getOwnerBusPayouts } from '../../../utils/busApi'
import { errorText, inr } from '../../../utils/busText'
import { PAYOUT_STATUS, payoutTotals, round2, whenText } from '../../../utils/busBooking'
import '../../../styles/bus.css'


/** What each completed bus trip earned the owner, and whether it has been paid out. */
export default function OwnerBusEarnings() {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try { setRows((await getOwnerBusPayouts()).data || []) } catch (e) { setError(errorText(e, 'Could not load your bus earnings.')); setRows([]) }
  }, [])
  useEffect(() => { load() }, [load])

  const sorted = useMemo(() => [...(rows || [])].sort((a, b) => String(b.travelDate).localeCompare(String(a.travelDate))), [rows])
  const totals = useMemo(() => payoutTotals(rows), [rows])

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <PageHead title="Bus earnings" sub="One payout per completed trip, the morning after: fares travelled plus cancellation charges kept, less the platform commission" />

      <div className="pc-grid">
        <SummaryStrip
          loading={rows == null}
          items={[
            { icon: 'clock', label: 'Due to you', value: inr(totals.DUE.net), sub: `${totals.DUE.count} trip${totals.DUE.count === 1 ? '' : 's'}` },
            { icon: 'check', label: 'Paid to you', value: inr(totals.PAID.net), sub: `${totals.PAID.count} trip${totals.PAID.count === 1 ? '' : 's'}` },
            { icon: 'rupee', label: 'Commission', value: inr(round2(totals.DUE.commission + totals.PAID.commission)), sub: 'on every trip so far' },
          ]}
        />

        {rows == null ? (
          <Panel span={12} title="Payouts"><Skeleton rows={5} height={18} /></Panel>
        ) : error ? (
          <Panel span={12}><div className="pc-error" role="alert"><Icon name="alert" size={16} /><span style={{ flex: 1 }}>{error}</span><button type="button" className="pc-btn pc-btn-sm" onClick={load}>Retry</button></div></Panel>
        ) : rows.length === 0 ? (
          <Panel span={12}><Empty icon="rupee" title="No payouts yet">A payout appears the morning after each completed trip.</Empty></Panel>
        ) : (
          <Panel span={12} title="Payouts" meta={`${rows.length}`}>
            <div className="bus-table-wrap">
              <table className="bus-table">
                <thead>
                  <tr>
                    <th>Trip</th><th className="num">Fares travelled</th><th className="num">Charges kept</th><th className="num">Gross</th>
                    <th className="num">Commission</th><th className="num">Net</th><th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {sorted.map((p) => (
                    <tr key={p.id}>
                      <td><b>{whenText(p.travelDate)}</b><small style={{ display: 'block', color: 'var(--pc-muted)' }}>{p.routeName} · {p.busName}</small></td>
                      <td className="num">{inr(p.faresTravelled)}</td>
                      <td className="num">{inr(p.chargesKept)}</td>
                      <td className="num">{inr(p.gross)}</td>
                      <td className="num">{inr(p.commission)}<small style={{ display: 'block', color: 'var(--pc-muted)' }}>{p.commissionPercent}%</small></td>
                      <td className="num"><b>{inr(p.net)}</b></td>
                      <td>
                        <BusPill table={PAYOUT_STATUS} status={p.status} />
                        {p.status === 'PAID' && <small style={{ display: 'block', color: 'var(--pc-muted)', marginTop: 3 }}>UTR {p.utr}{p.paidAt ? ` · ${whenText(p.paidAt)}` : ''}</small>}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  {['DUE', 'PAID'].map((s) => (
                    <tr key={s}>
                      <td>Total {s === 'DUE' ? 'due' : 'paid'} ({totals[s].count})</td>
                      <td className="num">{inr(totals[s].faresTravelled)}</td>
                      <td className="num">{inr(totals[s].chargesKept)}</td>
                      <td className="num">{inr(totals[s].gross)}</td>
                      <td className="num">{inr(totals[s].commission)}</td>
                      <td className="num">{inr(totals[s].net)}</td>
                      <td />
                    </tr>
                  ))}
                </tfoot>
              </table>
            </div>
          </Panel>
        )}
      </div>
    </DashboardLayout>
  )
}

