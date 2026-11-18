import React, { useEffect, useMemo, useState } from 'react'
import PlaceAutocomplete from '../PlaceAutocomplete'
import { getPlaceStates, getPlaces } from '../../utils/api'

const OTHER = '__other__'

/**
 * Where a driver works: State -> District -> Town. Riders in that town are matched with them, and
 * picking a town that had no City-ride zone creates one. States with listed towns (Tamil Nadu,
 * Puducherry, Karnataka for now) offer dropdowns; a town that is not listed, or any town in another
 * state, is found on the map search so we know where it is.
 *
 * onChange gets { placeId } for a listed town, { state, district, town, lat, lon } for one from the
 * map, or null while the choice is incomplete.
 */
export default function HomeAreaPicker({ initial, onChange, idPrefix = 'home' }) {
  const [states, setStates] = useState({ listed: [], all: [] })
  const [state, setState] = useState(initial?.state || '')
  const [places, setPlaces] = useState([])
  const [loadingPlaces, setLoadingPlaces] = useState(false)
  const [district, setDistrict] = useState(initial?.district || '')
  const [placeId, setPlaceId] = useState(initial?.placeId ? String(initial.placeId) : '')
  const [custom, setCustom] = useState({ district: '', town: '', lat: null, lon: null })

  useEffect(() => {
    getPlaceStates().then((r) => {
      const data = r.data || {}
      setStates({ listed: data.listed || [], all: data.all || [] })
      // Most drivers are in Tamil Nadu; start there unless something else was chosen.
      if (!initial?.state && (data.listed || []).includes('Tamil Nadu')) setState('Tamil Nadu')
    }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps -- once, on mount
  }, [])

  const listed = states.listed.includes(state)

  useEffect(() => {
    if (!state || !listed) return undefined // places are only read for a listed state
    let stale = false
    setLoadingPlaces(true)
    getPlaces(state)
      .then((r) => { if (!stale) setPlaces(r.data || []) })
      .catch(() => { if (!stale) setPlaces([]) })
      .finally(() => { if (!stale) setLoadingPlaces(false) })
    return () => { stale = true }
  }, [state, listed])

  const districts = useMemo(() => [...new Set(places.map((p) => p.district))].sort(), [places])
  const towns = useMemo(() => places.filter((p) => p.district === district), [places, district])
  const otherTown = !listed || placeId === OTHER

  // Report the choice upward whenever it becomes complete (or stops being).
  useEffect(() => {
    if (!state) { onChange(null); return }
    if (!otherTown) {
      onChange(placeId ? { placeId: Number(placeId) } : null)
      return
    }
    const d = listed ? district : custom.district.trim()
    const ready = d && custom.town.trim() && custom.lat != null && custom.lon != null
    onChange(ready ? { state, district: d, town: custom.town.trim(), lat: custom.lat, lon: custom.lon } : null)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- onChange is the parent's setter
  }, [state, district, placeId, custom, listed, otherTown])

  const lbl = { display: 'block', fontSize: 13, fontWeight: 700, marginBottom: 6, color: 'var(--pc-ink-2, #374151)' }
  const field = { width: '100%', padding: '10px 14px', outline: 'none' }
  const others = states.all.filter((s) => !states.listed.includes(s))

  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <div>
        <label htmlFor={`${idPrefix}-state`} style={lbl}>State</label>
        <select id={`${idPrefix}-state`} className="input-field" style={field} value={state}
          onChange={(e) => { setState(e.target.value); setDistrict(''); setPlaceId(''); setCustom({ district: '', town: '', lat: null, lon: null }) }}>
          <option value="">Choose your state</option>
          {states.listed.length > 0 && (
            <optgroup label="Towns listed">
              {states.listed.map((s) => <option key={s} value={s}>{s}</option>)}
            </optgroup>
          )}
          <optgroup label="Other states">
            {others.map((s) => <option key={s} value={s}>{s}</option>)}
          </optgroup>
        </select>
      </div>

      {state && listed && (
        <>
          <div>
            <label htmlFor={`${idPrefix}-district`} style={lbl}>District</label>
            <select id={`${idPrefix}-district`} className="input-field" style={field} value={district} disabled={loadingPlaces}
              onChange={(e) => { setDistrict(e.target.value); setPlaceId('') }}>
              <option value="">{loadingPlaces ? 'Loading…' : 'Choose your district'}</option>
              {districts.map((d) => <option key={d} value={d}>{d}</option>)}
            </select>
          </div>
          {district && (
            <div>
              <label htmlFor={`${idPrefix}-town`} style={lbl}>Town you drive in</label>
              <select id={`${idPrefix}-town`} className="input-field" style={field} value={placeId}
                onChange={(e) => setPlaceId(e.target.value)}>
                <option value="">Choose your town</option>
                {towns.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                <option value={OTHER}>My town is not listed</option>
              </select>
            </div>
          )}
        </>
      )}

      {state && !listed && (
        <div>
          <label htmlFor={`${idPrefix}-custom-district`} style={lbl}>District</label>
          <input id={`${idPrefix}-custom-district`} className="input-field" style={field} maxLength={80} value={custom.district}
            placeholder="Your district" onChange={(e) => setCustom({ ...custom, district: e.target.value })} />
        </div>
      )}

      {state && otherTown && (listed ? district : true) && (
        <div>
          <span style={lbl}>Find your town on the map</span>
          <PlaceAutocomplete
            value={custom.town}
            placeholder="Type your town's name"
            onChange={({ name, lat, lon }) => setCustom((c) => ({ ...c, town: (name || '').split(',')[0].trim(), lat, lon }))}
          />
          <p style={{ margin: '6px 0 0', fontSize: 12, color: 'var(--pc-muted, #6b7280)' }}>
            Pick it from the suggestions. City rides start in a new town once its first driver is approved.
          </p>
        </div>
      )}
    </div>
  )
}
