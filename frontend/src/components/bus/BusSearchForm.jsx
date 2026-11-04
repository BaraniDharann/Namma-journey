import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Icon from '../dash/Icon'
import BusPlaceField from './BusPlaceField'
import { addDays, isoDate } from '../../utils/busText'
import '../../styles/bus.css'

/** The search URL; the town names ride along so the results page can show them without a lookup. */
export function busSearchPath({ from, to, date }) {
  const p = new URLSearchParams({ from: String(from.id), to: String(to.id), date, fromName: from.name, toName: to.name })
  return `/bus/search?${p}`
}

/**
 * From / To / date for a bus search, with a swap button and Today / Tomorrow chips. Goes to
 * /bus/search; `onSearch` replaces that (the results page re-searches in place).
 */
export default function BusSearchForm({ initial, onSearch, idPrefix = 'bus' }) {
  const navigate = useNavigate()
  const today = isoDate()
  const [from, setFrom] = useState(initial?.from || null)
  const [to, setTo] = useState(initial?.to || null)
  const [date, setDate] = useState(initial?.date && initial.date >= today ? initial.date : today)
  const [swaps, setSwaps] = useState(0)
  const [error, setError] = useState('')

  const swap = () => { setFrom(to); setTo(from); setSwaps((n) => n + 1) }

  const submit = (e) => {
    e.preventDefault()
    if (!from || !to) { setError('Pick both towns from the list.'); return }
    if (from.id === to.id) { setError('From and To must be different towns.'); return }
    if (!date || date < today) { setError('Pick today or a later date.'); return }
    setError('')
    const query = { from, to, date }
    if (onSearch) onSearch(query)
    else navigate(busSearchPath(query))
  }

  const tomorrow = addDays(today, 1)
  return (
    <form className="bus-form" onSubmit={submit} aria-label="Search buses" noValidate>
      <BusPlaceField key={`f${swaps}`} id={`${idPrefix}-from`} className="bus-field-from" label="From" value={from} onChange={setFrom} exclude={to?.id} placeholder="Leaving from" />
      <button type="button" className="bus-swap" onClick={swap} aria-label="Swap From and To" title="Swap">
        <Icon name="refresh" size={18} />
      </button>
      <BusPlaceField key={`t${swaps}`} id={`${idPrefix}-to`} className="bus-field-to" label="To" value={to} onChange={setTo} exclude={from?.id} placeholder="Going to" />
      <div className="bus-field-date">
        <label htmlFor={`${idPrefix}-date`}>Date of journey</label>
        <div className="bus-date-row">
          <input id={`${idPrefix}-date`} type="date" className="input-field" min={today} value={date} onChange={(e) => setDate(e.target.value)} />
          <button type="button" className="bus-daychip" aria-pressed={date === today} onClick={() => setDate(today)}>Today</button>
          <button type="button" className="bus-daychip" aria-pressed={date === tomorrow} onClick={() => setDate(tomorrow)}>Tomorrow</button>
        </div>
      </div>
      <button type="submit" className="pc-btn bus-form-go"><Icon name="search" size={17} />Search buses</button>
      {error && <p className="bus-form-err" role="alert">{error}</p>}
    </form>
  )
}
