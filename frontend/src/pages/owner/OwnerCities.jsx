import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import PlaceAutocomplete from '../../components/PlaceAutocomplete'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton } from '../../components/dash/ui'
import { getCities, createCity, updateCity } from '../../utils/api'

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
const EMPTY = { id: null, name: '', centerName: '', centerLat: null, centerLon: null, radiusKm: '30', commissionPercent: '10', active: true }

/** The places City rides are served. A pickup outside every active city can only be booked as a Trip. */
export default function OwnerCities() {
  const [cities, setCities] = useState(null)
  const [draft, setDraft] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    getCities().then((r) => setCities(r.data || [])).catch(() => setCities([]))
  }, [])

  const upsert = (city) => setCities((list) => {
    const rest = (list || []).filter((c) => c.id !== city.id)
    return [...rest, city].sort((a, b) => a.name.localeCompare(b.name))
  })

  const radius = Number(draft?.radiusKm)
  const commission = Number(draft?.commissionPercent)
  const valid = draft && draft.name.trim() && draft.centerLat != null && radius >= 1 && radius <= 100
    && draft.commissionPercent !== '' && commission >= 0 && commission <= 50

  const save = async () => {
    setSaving(true); setError('')
    const body = { name: draft.name.trim(), centerLat: draft.centerLat, centerLon: draft.centerLon, radiusKm: radius, commissionPercent: commission, active: draft.active }
    try {
      const r = draft.id ? await updateCity(draft.id, body) : await createCity(body)
      upsert(r.data)
      setDraft(null)
    } catch (e) {
      setError(e.response?.data?.message || 'Could not save the city')
    }
    setSaving(false)
  }

  // The switch saves straight away: turning a city off is the quick way to stop City rides there.
  const toggle = async (city) => {
    setError('')
    try {
      const r = await updateCity(city.id, { ...city, active: !city.active })
      upsert(r.data)
    } catch (e) {
      setError(e.response?.data?.message || 'Could not update the city')
    }
  }

  const edit = (c) => {
    setError('')
    setDraft({ id: c.id, name: c.name, centerName: `${c.centerLat.toFixed(4)}, ${c.centerLon.toFixed(4)}`, centerLat: c.centerLat, centerLon: c.centerLon, radiusKm: String(c.radiusKm), commissionPercent: String(c.commissionPercent ?? 10), active: c.active })
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Cities" sub="City rides are only offered where a pickup falls inside one of these. Anywhere else is a Trip.">
        {!draft && <button type="button" className="pc-btn" onClick={() => { setError(''); setDraft({ ...EMPTY }) }}><Icon name="plus" size={16} />Add city</button>}
      </PageHead>

      {error && <div className="pc-error" role="alert" style={{ marginBottom: 12 }}><Icon name="alert" size={16} /><span>{error}</span></div>}

      {draft && (
        <Panel title={draft.id ? 'Edit city' : 'Add a city'} style={{ marginBottom: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 14 }}>
            <div>
              <label style={labelStyle} htmlFor="city-name">Name</label>
              <input id="city-name" className="input-field" maxLength={80} value={draft.name} style={{ width: '100%' }}
                onChange={(e) => setDraft((d) => ({ ...d, name: e.target.value }))} placeholder="Bengaluru" />
            </div>
            <div>
              <label style={labelStyle}>Centre</label>
              <PlaceAutocomplete value={draft.centerName} placeholder="Search the middle of the city"
                onChange={({ name, lat, lon }) => setDraft((d) => ({ ...d, centerName: name, centerLat: lat, centerLon: lon }))} />
            </div>
            <div>
              <label style={labelStyle} htmlFor="city-radius">Radius (km, 1 to 100)</label>
              <input id="city-radius" className="input-field" type="number" min="1" max="100" step="1" inputMode="decimal"
                value={draft.radiusKm} style={{ width: '100%' }} onChange={(e) => setDraft((d) => ({ ...d, radiusKm: e.target.value }))} />
            </div>
            <div>
              <label style={labelStyle} htmlFor="city-commission">Commission (%, 0 to 50)</label>
              <input id="city-commission" className="input-field" type="number" min="0" max="50" step="0.5" inputMode="decimal"
                value={draft.commissionPercent} style={{ width: '100%' }} onChange={(e) => setDraft((d) => ({ ...d, commissionPercent: e.target.value }))} />
              <small style={{ color: 'var(--pc-muted)', fontSize: 12 }}>Kept from each ride by an independent driver here.</small>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 16, flexWrap: 'wrap' }}>
            <button type="button" className="pc-btn pc-btn-ghost" disabled={saving} onClick={() => setDraft(null)}>Cancel</button>
            <button type="button" className="pc-btn" disabled={saving || !valid} onClick={save}>
              <Icon name="check" size={14} />{saving ? 'Saving…' : 'Save city'}
            </button>
          </div>
        </Panel>
      )}

      <Panel title="Your cities" meta={cities ? `${cities.filter((c) => c.active).length} active` : undefined}>
        {!cities ? <Skeleton rows={3} /> : cities.length === 0 ? (
          <Empty icon="map" title="No cities yet">Add the first one to start taking City rides.</Empty>
        ) : (
          <div style={{ display: 'grid', gap: 10 }}>
            {cities.map((c) => (
              <div key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', padding: '12px 14px', borderRadius: 12, background: 'var(--pc-wash)', border: '1px solid var(--pc-line)' }}>
                <div>
                  <b style={{ fontSize: 15 }}>{c.name}</b>
                  <div style={{ fontSize: 13, color: 'var(--pc-muted)' }}>{c.radiusKm} km around {c.centerLat.toFixed(4)}, {c.centerLon.toFixed(4)} · {Number(c.commissionPercent ?? 10)}% commission</div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <button type="button" className="pc-switch" role="switch" aria-checked={c.active} onClick={() => toggle(c)}>
                    <span className="pc-switch-track" />{c.active ? 'Active' : 'Off'}
                  </button>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => edit(c)}><Icon name="edit" size={14} />Edit</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Panel>
    </DashboardLayout>
  )
}
