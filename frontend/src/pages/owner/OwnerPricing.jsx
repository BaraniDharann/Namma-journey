import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import CityFaresPanel from '../../components/owner/CityFaresPanel'
import useOwnerMe from '../../hooks/useOwnerMe'
import { getTripRates, setTripRate, setAssignmentMode } from '../../utils/api'
import { CAR_CATEGORIES, estimateTripPrice } from '../../utils/carCategories'
import { inr } from '../../dash/metrics'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Skeleton, ErrorNote } from '../../components/dash/ui'

const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback
const MODES = [
  ['AUTO', 'Automatic', 'Each new Trip goes to your first free driver with a free car of the booked type.'],
  ['MANUAL', 'By hand', 'New Trips wait in All Bookings with a "needs a driver" badge until you assign one.'],
]

/** What my Trips cost per car type, and how they reach my drivers. City fares only for the platform. */
export default function OwnerPricing() {
  const me = useOwnerMe()
  const [drafts, setDrafts] = useState(null)
  const [loadError, setLoadError] = useState(false)
  const [saving, setSaving] = useState(null)
  const [saved, setSaved] = useState(null)
  const [error, setError] = useState('')
  const [modeChoice, setMode] = useState(null)
  const [modeSaving, setModeSaving] = useState(false)
  const [modeError, setModeError] = useState('')

  const load = () => {
    setLoadError(false)
    getTripRates()
      .then((r) => {
        const byCat = Object.fromEntries((r.data || []).map((x) => [x.category, x]))
        setDrafts(Object.fromEntries(CAR_CATEGORIES.map(({ value }) => [value, {
          perKm: byCat[value]?.perKm != null ? String(byCat[value].perKm) : '',
          perHour: byCat[value]?.perHour != null ? String(byCat[value].perHour) : '',
          priced: !!byCat[value],
        }])))
      })
      .catch(() => setLoadError(true))
  }
  useEffect(load, [])
  const mode = modeChoice ?? (me ? me.assignmentMode || 'AUTO' : null)

  const edit = (cat, key, value) => setDrafts((d) => ({ ...d, [cat]: { ...d[cat], [key]: value } }))

  const save = async (cat) => {
    // The server needs both prices; say which one is missing instead of a vague error.
    const label = CAR_CATEGORIES.find((c) => c.value === cat)?.label || 'this car type'
    if (drafts[cat].perKm === '' || drafts[cat].perHour === '') {
      setSaved(null)
      setError(`Enter both the per-km and the per-hour price for ${label}.`)
      return
    }
    setSaving(cat); setError(''); setSaved(null)
    try {
      const r = await setTripRate(cat, { perKm: Number(drafts[cat].perKm), perHour: Number(drafts[cat].perHour) })
      setDrafts((d) => ({ ...d, [cat]: { perKm: String(r.data.perKm), perHour: String(r.data.perHour), priced: true } }))
      setSaved(cat)
      setTimeout(() => setSaved((s) => (s === cat ? null : s)), 3000)
    } catch (e) {
      setError(errorText(e, 'Could not save the price'))
    }
    setSaving(null)
  }

  const chooseMode = async (next) => {
    if (next === mode) return
    const previous = mode
    setMode(next); setModeSaving(true); setModeError('')
    try {
      await setAssignmentMode(next)
    } catch (e) {
      setMode(previous)
      setModeError(errorText(e, 'Could not change how Trips are assigned'))
    }
    setModeSaving(false)
  }

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      <PageHead title="Pricing" sub="Your Trip prices per car type, and how Trips reach your drivers" />

      <div className="pc-grid">
        <Panel span={12} title="Trip prices" meta="100 km or 8 h example">
          <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', marginBottom: 14 }}>
            Travellers can only book the car types you have priced. Distance trips use the per-km price; hourly trips use the per-hour price.
            <br /><b>Fill both per-km and per-hour, then Save.</b>
          </p>
          {error && <div className="pc-error" role="alert" style={{ marginBottom: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
          {loadError ? <ErrorNote onRetry={load} /> : !drafts ? <Skeleton rows={3} /> : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: 14 }}>
              {CAR_CATEGORIES.map(({ value, label, seats }) => {
                const d = drafts[value]
                const incomplete = d.perKm === '' || d.perHour === ''
                const blank = d.perKm === '' && d.perHour === ''
                return (
                  <div key={value} style={{ padding: 14, borderRadius: 14, background: 'var(--pc-wash)', border: '1px solid var(--pc-line)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 10 }}>
                      <b style={{ fontSize: 15 }}>{label} <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--pc-muted)' }}>~{seats} seats</span></b>
                      {d.priced ? <span className="pc-pill pc-pill-ok">Offered</span> : <span className="pc-pill">Not offered</span>}
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                      {[['perKm', 'Per km'], ['perHour', 'Per hour']].map(([key, text]) => (
                        <label key={key} style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-ink-2)' }}>
                          {text} (₹)
                          <input className="input-field" type="number" min="1" step="0.5" inputMode="decimal"
                            value={d[key]} onChange={(e) => edit(value, key, e.target.value)}
                            style={{ marginTop: 4, width: '100%' }} />
                        </label>
                      ))}
                    </div>
                    {!incomplete && (
                      <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', marginTop: 8 }}>
                        e.g. {inr(estimateTripPrice({ perKm: d.perKm }, { km: 100 }))} · {inr(estimateTripPrice({ perHour: d.perHour }, { hours: 8 }))}
                      </div>
                    )}
                    <button type="button" className={saved === value ? 'pc-btn pc-btn-teal pc-btn-sm' : 'pc-btn pc-btn-sm'} style={{ marginTop: 10, width: '100%' }}
                      disabled={!!saving || blank} onClick={() => save(value)}>
                      {saving === value ? 'Saving…' : saved === value ? <><Icon name="check" size={14} />Saved</> : `Save ${label} price`}
                    </button>
                  </div>
                )
              })}
            </div>
          )}
        </Panel>

        <Panel span={12} title="Assigning Trips">
          {modeError && <div className="pc-error" role="alert" style={{ marginBottom: 12 }}><Icon name="alert" size={16} /><span>{modeError}</span></div>}
          {!mode ? <Skeleton rows={2} /> : (
            <div role="radiogroup" aria-label="How Trips are assigned" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
              {MODES.map(([value, title, text]) => {
                const on = mode === value
                return (
                  <button key={value} type="button" role="radio" aria-checked={on} disabled={modeSaving} onClick={() => chooseMode(value)}
                    style={{
                      textAlign: 'left', padding: 14, borderRadius: 14, cursor: 'pointer', fontFamily: 'inherit',
                      border: on ? '2px solid var(--pc-brand)' : '2px solid var(--pc-line)', background: on ? 'var(--pc-brand-soft)' : 'var(--pc-panel)',
                    }}>
                    <b style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 15, color: on ? 'var(--pc-brand-deep)' : 'var(--pc-ink)' }}>
                      {on && <Icon name="check" size={16} />}{title}
                    </b>
                    <span style={{ display: 'block', marginTop: 6, fontSize: 12.5, color: 'var(--pc-ink-2)' }}>{text}</span>
                  </button>
                )
              })}
            </div>
          )}
        </Panel>

        {me?.platform && <CityFaresPanel span={12} />}
      </div>
    </DashboardLayout>
  )
}
