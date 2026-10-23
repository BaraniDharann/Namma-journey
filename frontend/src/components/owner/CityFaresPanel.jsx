import React, { useEffect, useState } from 'react'
import Icon from '../dash/Icon'
import { Panel, Skeleton } from '../dash/ui'
import { getCityFares, setCityFare } from '../../utils/api'
import { inr } from '../../dash/metrics'
import { VEHICLES, VEHICLE_ORDER } from '../../dash/vehicles'

const FIELDS = [
  ['baseFare', 'Base fare'],
  ['perKm', 'Per km'],
  ['perMinute', 'Per minute'],
  ['minimumFare', 'Minimum'],
]

/** What a City ride costs in each vehicle: max(minimum, base + per km + per minute). Trips keep their own rates. */
export default function CityFaresPanel({ span = 12 }) {
  const [fares, setFares] = useState(null)
  const [drafts, setDrafts] = useState({})
  const [saving, setSaving] = useState(null)
  const [saved, setSaved] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getCityFares()
      .then((r) => {
        const byType = Object.fromEntries((r.data || []).map((f) => [f.vehicleType, f]))
        setFares(byType)
        setDrafts(Object.fromEntries(Object.entries(byType).map(([t, f]) => [t, Object.fromEntries(FIELDS.map(([k]) => [k, String(f[k] ?? '')]))])))
      })
      .catch(() => setFares({}))
  }, [])

  const edit = (type, key, value) => setDrafts((d) => ({ ...d, [type]: { ...d[type], [key]: value } }))

  const save = async (type) => {
    setSaving(type); setError(''); setSaved(null)
    try {
      const body = Object.fromEntries(FIELDS.map(([k]) => [k, Number(drafts[type]?.[k])]))
      const r = await setCityFare(type, body)
      setFares((f) => ({ ...f, [type]: r.data }))
      setSaved(type)
      setTimeout(() => setSaved((s) => (s === type ? null : s)), 3000)
    } catch (e) {
      setError(e.response?.data?.message || 'Could not save the fare')
    }
    setSaving(null)
  }

  // An example ride, so the owner sees what the numbers mean for a traveller.
  const sample = (type) => {
    const d = drafts[type]
    if (!d) return null
    const n = (k) => Number(d[k]) || 0
    return Math.max(n('minimumFare'), n('baseFare') + n('perKm') * 5 + n('perMinute') * 15)
  }

  return (
    <Panel span={span} title="City ride fares" meta="5 km · 15 min example">
      {error && <div className="pc-error" role="alert" style={{ marginBottom: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
      {!fares ? <Skeleton rows={3} /> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: 14 }}>
          {VEHICLE_ORDER.map((type) => (
            <div key={type} style={{ padding: 14, borderRadius: 14, background: 'var(--pc-wash)', border: '1px solid var(--pc-line)' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 10 }}>
                <b style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 15 }}><Icon name={VEHICLES[type].icon} size={20} />{VEHICLES[type].label}</b>
                <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)' }}>e.g. {inr(sample(type))}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                {FIELDS.map(([key, text]) => (
                  <label key={key} style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-ink-2)' }}>
                    {text} (₹)
                    <input className="input-field" type="number" min="0" step="0.5" inputMode="decimal"
                      value={drafts[type]?.[key] ?? ''} onChange={(e) => edit(type, key, e.target.value)}
                      style={{ marginTop: 4, width: '100%' }} />
                  </label>
                ))}
              </div>
              <button type="button" className={saved === type ? 'pc-btn pc-btn-teal pc-btn-sm' : 'pc-btn pc-btn-sm'} style={{ marginTop: 10, width: '100%' }}
                disabled={!!saving || FIELDS.some(([k]) => drafts[type]?.[k] === '')} onClick={() => save(type)}>
                {saving === type ? 'Saving…' : saved === type ? <><Icon name="check" size={14} />Saved</> : `Save ${VEHICLES[type].label.toLowerCase()} fare`}
              </button>
            </div>
          ))}
        </div>
      )}
    </Panel>
  )
}
