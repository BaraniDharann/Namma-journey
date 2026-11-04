import React, { useEffect, useId, useRef, useState } from 'react'
import Icon from '../dash/Icon'
import { searchBusPlaces } from '../../utils/busApi'

/**
 * Town field for the customer's bus search: types ahead against the public town list and keeps
 * the picked town ({id, name}) shown in the box. Unlike the owner's PlacePicker it never adds
 * towns. Arrow keys move through the list, Enter picks.
 */
export default function BusPlaceField({ id, label, value, onChange, exclude, className = '', placeholder = 'Type a town' }) {
  const [q, setQ] = useState(value?.name || '')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const box = useRef(null)
  const listId = useId()

  // A town picked from outside (a restored search) replaces the text. Clearing the value while
  // typing keeps the text; the form remounts the field when it needs a blank one (swap).
  useEffect(() => { if (value?.name) setQ(value.name) }, [value?.id, value?.name])

  useEffect(() => {
    const term = q.trim()
    if (!open || term.length < 2 || term === value?.name) { setResults([]); return undefined }
    let live = true
    const t = setTimeout(() => {
      searchBusPlaces(term)
        .then((res) => { if (live) { setResults((res.data || []).filter((p) => p.id !== exclude)); setActive(0) } })
        .catch(() => { if (live) setResults([]) })
    }, 220)
    return () => { live = false; clearTimeout(t) }
  }, [q, open, exclude, value?.name])

  useEffect(() => {
    const close = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const pick = (place) => { onChange(place); setQ(place.name); setResults([]); setOpen(false) }

  const onKey = (e) => {
    if (!results.length) return
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => (a + 1) % results.length) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => (a - 1 + results.length) % results.length) }
    else if (e.key === 'Enter') { e.preventDefault(); pick(results[active]) }
    else if (e.key === 'Escape') setOpen(false)
  }

  const showList = open && results.length > 0
  return (
    <div ref={box} className={`bus-place ${className}`}>
      <label htmlFor={id}>{label}</label>
      <input id={id} className="input-field" value={q} placeholder={placeholder} autoComplete="off"
        role="combobox" aria-expanded={showList} aria-controls={listId} aria-autocomplete="list"
        aria-activedescendant={showList ? `${listId}-${active}` : undefined}
        onChange={(e) => { setQ(e.target.value); setOpen(true); if (value && e.target.value !== value.name) onChange(null) }}
        onFocus={(e) => { setOpen(true); e.target.select() }} onKeyDown={onKey} />
      {showList && (
        <ul id={listId} role="listbox" className="bus-place-list">
          {results.map((p, i) => (
            <li key={p.id}>
              <button id={`${listId}-${i}`} type="button" role="option" aria-selected={i === active} onMouseDown={(e) => e.preventDefault()} onClick={() => pick(p)}>
                <Icon name="pin" size={14} /><span><b>{p.name}</b>{p.district ? <small> · {p.district}</small> : null}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
