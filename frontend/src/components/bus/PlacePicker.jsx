import React, { useEffect, useRef, useState } from 'react'
import Icon from '../dash/Icon'
import { addBusPlace, searchBusPlaces } from '../../utils/busApi'
import { errorText } from '../../utils/busText'

/**
 * Town search for bus stops. Types ahead against the shared town list; when the town is missing
 * the owner can add it (name + district) and it is picked straight away.
 */
export default function PlacePicker({ id, onPick, exclude = [], placeholder = 'Search a town' }) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const [district, setDistrict] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const box = useRef(null)

  useEffect(() => {
    const term = q.trim()
    if (term.length < 2) { setResults([]); return undefined }
    let live = true
    const t = setTimeout(() => {
      searchBusPlaces(term).then((res) => { if (live) setResults(res.data || []) }).catch(() => { if (live) setResults([]) })
    }, 250)
    return () => { live = false; clearTimeout(t) }
  }, [q])

  useEffect(() => {
    const close = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const pick = (place) => { onPick(place); setQ(''); setResults([]); setOpen(false); setAdding(false); setDistrict('') }

  const add = async () => {
    setBusy(true); setError('')
    try { pick((await addBusPlace(q.trim(), district.trim())).data) } catch (e) { setError(errorText(e, 'Could not add this town.')) } finally { setBusy(false) }
  }

  const shown = results.filter((p) => !exclude.includes(p.id))
  const exact = results.some((p) => p.name.toLowerCase() === q.trim().toLowerCase())

  return (
    <div ref={box} style={{ position: 'relative' }}>
      <input id={id} className="input-field" value={q} placeholder={placeholder} autoComplete="off"
        role="combobox" aria-expanded={open} aria-autocomplete="list"
        onChange={(e) => { setQ(e.target.value); setOpen(true); setAdding(false) }} onFocus={() => setOpen(true)} />
      {open && q.trim().length >= 2 && (
        <div role="listbox" style={{ position: 'absolute', zIndex: 20, top: 'calc(100% + 4px)', left: 0, right: 0, background: 'var(--pc-panel)', border: '1px solid var(--pc-line)', borderRadius: 12, boxShadow: 'var(--pc-shadow-hi)', padding: 6, maxHeight: 280, overflowY: 'auto' }}>
          {shown.map((p) => (
            <button key={p.id} type="button" role="option" aria-selected="false" onClick={() => pick(p)}
              style={{ display: 'flex', width: '100%', textAlign: 'left', gap: 8, alignItems: 'center', padding: '8px 10px', border: 'none', background: 'none', borderRadius: 8, cursor: 'pointer', color: 'var(--pc-ink)' }}>
              <Icon name="pin" size={14} /><span><b>{p.name}</b>{p.district ? <small style={{ color: 'var(--pc-muted)' }}> · {p.district}</small> : null}</span>
            </button>
          ))}
          {!exact && !adding && (
            <button type="button" onClick={() => setAdding(true)}
              style={{ display: 'flex', width: '100%', gap: 8, alignItems: 'center', padding: '8px 10px', border: 'none', background: 'none', cursor: 'pointer', color: 'var(--pc-brand-deep)', fontWeight: 700 }}>
              <Icon name="plus" size={14} />Add town “{q.trim()}”
            </button>
          )}
          {adding && (
            <div style={{ padding: 8, display: 'grid', gap: 8 }}>
              <input className="input-field" aria-label="District" placeholder="District" value={district} onChange={(e) => setDistrict(e.target.value)} />
              {error && <span style={{ color: 'var(--pc-bad)', fontSize: 12 }}>{error}</span>}
              <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !district.trim()} onClick={add}>{busy ? 'Adding…' : `Add ${q.trim()}`}</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
