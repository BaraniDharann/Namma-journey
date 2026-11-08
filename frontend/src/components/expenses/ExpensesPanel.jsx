import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Panel, Empty, Skeleton, ErrorNote, SummaryStrip } from '../dash/ui'
import Icon from '../dash/Icon'
import { addExpense, deleteExpense, getExpenses, getProfitSummary, EXPENSE_CATEGORIES, categoryText } from '../../utils/expenseApi'
import { inr, isoDate, errorText } from '../../utils/busText'

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const mutedStyle = { fontSize: 12, color: 'var(--pc-muted)' }

const monthStart = () => `${isoDate().slice(0, 8)}01`

/**
 * Running costs for one kind of vehicle (CAR or BUS): profit per vehicle for a period, a quick
 * "add expense" form, and the log. `vehicles` are [{id, label}] the owner can pick from; `trips`
 * (bus only, optional) are [{id, label, busId}] an expense can be tied to.
 */
export default function ExpensesPanel({ kind, vehicles, trips = [], onChange }) {
  const noun = kind === 'BUS' ? 'bus' : 'car'
  const [range, setRange] = useState(() => ({ from: monthStart(), to: isoDate() }))
  const [summary, setSummary] = useState(null)
  const [list, setList] = useState(null)
  const [failed, setFailed] = useState(false)
  const [form, setForm] = useState(() => ({ vehicleId: '', busTripId: '', spentOn: isoDate(), category: 'FUEL', amount: '', note: '' }))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState('')

  const load = useCallback(async () => {
    setFailed(false)
    try {
      const [s, l] = await Promise.all([getProfitSummary(range.from, range.to, kind), getExpenses(range.from, range.to, kind)])
      setSummary(s.data); setList(l.data || [])
    } catch { setFailed(true); setSummary(null); setList([]) }
  }, [range, kind])
  useEffect(() => { load() }, [load])

  // Pick the only vehicle for the owner, so a one-car owner never has to choose.
  useEffect(() => {
    if (vehicles?.length === 1 && !form.vehicleId) setForm((f) => ({ ...f, vehicleId: String(vehicles[0].id) }))
  }, [vehicles, form.vehicleId])

  const tripChoices = useMemo(() => trips.filter((t) => String(t.busId) === form.vehicleId), [trips, form.vehicleId])

  const submit = async (e) => {
    e.preventDefault()
    setError(''); setSaved('')
    if (!form.vehicleId) { setError(`Choose a ${noun}`); return }
    const amount = Number(form.amount)
    if (!(amount > 0)) { setError('Enter an amount above zero'); return }
    setSaving(true)
    try {
      await addExpense({
        vehicleKind: kind, vehicleId: Number(form.vehicleId), busTripId: form.busTripId ? Number(form.busTripId) : null,
        spentOn: form.spentOn, category: form.category, amount, note: form.note,
      })
      setSaved(`${inr(amount)} ${categoryText(form.category).toLowerCase()} added`)
      setForm((f) => ({ ...f, amount: '', note: '', busTripId: '' }))
      await load()
      onChange?.()
    } catch (err) {
      setError(errorText(err, 'Could not save. Check your connection and try again.'))
    } finally { setSaving(false) }
  }

  const remove = async (id) => {
    if (!window.confirm('Delete this expense?')) return
    try { await deleteExpense(id); await load(); onChange?.() } catch (err) { setError(errorText(err, 'Could not delete it.')) }
  }

  const hasVehicles = vehicles && vehicles.length > 0

  return (
    <div className="pc-grid">
      <SummaryStrip loading={!summary && !failed} items={[
        { icon: 'wallet', label: 'Revenue', value: inr(summary?.revenue), sub: kind === 'BUS' ? 'Fares on trips in these dates' : 'Payments received in these dates' },
        { icon: 'rupee', tint: 'pink', label: 'Expenses', value: inr(summary?.expenses), sub: `${list?.length || 0} ${list?.length === 1 ? 'entry' : 'entries'}` },
        { icon: 'chart', label: 'Profit', value: inr(summary?.profit), sub: 'Revenue − expenses', tint: Number(summary?.profit) < 0 ? 'pink' : 'green' },
      ]} />

      <Panel span={12} title={`Add a ${noun} expense`}>
        {!hasVehicles ? (
          <p style={mutedStyle}>Add a {noun} first, then record what you spend on it here.</p>
        ) : (
          <form onSubmit={submit} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(160px,1fr))', gap: 14, alignItems: 'end' }}>
            <div>
              <label htmlFor={`${kind}-veh`} style={labelStyle}>{kind === 'BUS' ? 'Bus' : 'Car'}</label>
              <select id={`${kind}-veh`} className="input-field" value={form.vehicleId} onChange={(e) => setForm((f) => ({ ...f, vehicleId: e.target.value, busTripId: '' }))}>
                <option value="">Choose…</option>
                {vehicles.map((v) => <option key={v.id} value={v.id}>{v.label}</option>)}
              </select>
            </div>
            {kind === 'BUS' && tripChoices.length > 0 && (
              <div>
                <label htmlFor="exp-trip" style={labelStyle}>For trip (optional)</label>
                <select id="exp-trip" className="input-field" value={form.busTripId} onChange={(e) => setForm((f) => ({ ...f, busTripId: e.target.value }))}>
                  <option value="">Not for one trip</option>
                  {tripChoices.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
                </select>
              </div>
            )}
            <div>
              <label htmlFor={`${kind}-cat`} style={labelStyle}>Spent on</label>
              <select id={`${kind}-cat`} className="input-field" value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}>
                {EXPENSE_CATEGORIES.map(([k, t]) => <option key={k} value={k}>{t}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor={`${kind}-amt`} style={labelStyle}>Amount (₹)</label>
              <input id={`${kind}-amt`} className="input-field" type="number" inputMode="decimal" min="1" step="0.01" placeholder="e.g. 2500"
                value={form.amount} onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))} />
            </div>
            <div>
              <label htmlFor={`${kind}-date`} style={labelStyle}>Date</label>
              <input id={`${kind}-date`} className="input-field" type="date" max={isoDate()} value={form.spentOn}
                onChange={(e) => e.target.value && setForm((f) => ({ ...f, spentOn: e.target.value }))} />
            </div>
            <div>
              <label htmlFor={`${kind}-note`} style={labelStyle}>Note (optional)</label>
              <input id={`${kind}-note`} className="input-field" maxLength={200} placeholder="e.g. Diesel, Salem bunk"
                value={form.note} onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))} />
            </div>
            <div>
              <button type="submit" className="pc-btn" disabled={saving} style={{ width: '100%', justifyContent: 'center' }}>
                <Icon name="plus" size={16} /> {saving ? 'Saving…' : 'Add expense'}
              </button>
            </div>
          </form>
        )}
        {error && <p className="pc-error" role="alert" style={{ marginTop: 12 }}>{error}</p>}
        {saved && <p className="alert-success" role="status" style={{ marginTop: 12 }}>{saved}</p>}
      </Panel>

      <div className="pc-span-12" style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div>
          <label htmlFor={`${kind}-from`} style={labelStyle}>From</label>
          <input id={`${kind}-from`} type="date" className="input-field" value={range.from} max={range.to}
            onChange={(e) => e.target.value && setRange((r) => ({ ...r, from: e.target.value }))} />
        </div>
        <div>
          <label htmlFor={`${kind}-to`} style={labelStyle}>To</label>
          <input id={`${kind}-to`} type="date" className="input-field" value={range.to} min={range.from} max={isoDate()}
            onChange={(e) => e.target.value && setRange((r) => ({ ...r, to: e.target.value }))} />
        </div>
      </div>

      <Panel span={12} pad={false} title={`Profit by ${noun}`}>
        {failed ? <div style={{ padding: '0 20px 20px' }}><ErrorNote onRetry={load}>Could not load the figures.</ErrorNote></div>
          : !summary ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={3} /></div>
            : summary.vehicles.length === 0 ? <Empty icon={kind === 'BUS' ? 'bus' : 'car'} title={`No ${noun}s yet`}>Profit per {noun} shows here once you add one.</Empty>
              : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="data-table">
                    <thead><tr><th>{kind === 'BUS' ? 'Bus' : 'Car'}</th><th style={{ textAlign: 'right' }}>Revenue</th><th style={{ textAlign: 'right' }}>Expenses</th><th style={{ textAlign: 'right' }}>Profit</th><th>Biggest cost</th></tr></thead>
                    <tbody>
                      {summary.vehicles.map((v) => {
                        const top = Object.entries(v.byCategory || {}).sort((a, b) => Number(b[1]) - Number(a[1]))[0]
                        return (
                          <tr key={`${v.vehicleKind}-${v.vehicleId}`}>
                            <td><strong>{v.label}</strong></td>
                            <td style={{ textAlign: 'right' }}>{inr(v.revenue)}</td>
                            <td style={{ textAlign: 'right' }}>{inr(v.expenses)}</td>
                            <td style={{ textAlign: 'right', fontWeight: 800, color: Number(v.profit) < 0 ? 'var(--pc-bad)' : undefined }}>{inr(v.profit)}</td>
                            <td style={mutedStyle}>{top ? `${categoryText(top[0])} ${inr(top[1])}` : '—'}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
      </Panel>

      <Panel span={12} pad={false} title="Expense log" meta={list ? `${list.length}` : null}>
        {list == null ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={3} /></div>
          : list.length === 0 ? <Empty icon="rupee" title="No expenses in these dates">Fuel, tolls, driver pay and repairs you add show up here.</Empty>
            : (
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table">
                  <thead><tr><th>Date</th><th>{kind === 'BUS' ? 'Bus' : 'Car'}</th><th>Spent on</th><th style={{ textAlign: 'right' }}>Amount</th><th>Note</th><th aria-label="Actions" /></tr></thead>
                  <tbody>
                    {list.map((x) => (
                      <tr key={x.id}>
                        <td style={{ whiteSpace: 'nowrap' }}>{new Date(`${x.spentOn}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}</td>
                        <td>{x.vehicleLabel}{x.busTripId && <div style={mutedStyle}>For a trip</div>}</td>
                        <td>{categoryText(x.category)}</td>
                        <td style={{ textAlign: 'right', fontWeight: 800 }}>{inr(x.amount)}</td>
                        <td style={mutedStyle}>{x.note || '—'}</td>
                        <td style={{ textAlign: 'right' }}>
                          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => remove(x.id)} aria-label={`Delete ${categoryText(x.category)} ${inr(x.amount)}`}>
                            <Icon name="trash" size={14} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
      </Panel>
    </div>
  )
}
