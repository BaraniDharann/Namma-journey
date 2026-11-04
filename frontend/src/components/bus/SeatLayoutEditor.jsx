import React, { useState } from 'react'
import Icon from '../dash/Icon'
import { SeatDecks, SeatLegend } from './SeatDeck'
import {
  LAYOUT_TEMPLATES, buildTemplate, canPlace, computePairs, deckSize, layoutProblems, nextLabel,
  seatClassName, seatClassesOf, seatClassOf,
} from '../../utils/busLayouts'

const capsStyle = { fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.03em' }
const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const wellStyle = { padding: '12px 14px', borderRadius: 12, background: 'var(--pc-wash)', border: '1px solid var(--pc-line)' }
const chipOn = { background: 'var(--pc-ink)', color: 'var(--pc-panel)' }

/**
 * The owner draws the bus: pick a template, then click an empty cell to add a seat of the chosen
 * kind, or click a seat to change it. Pairs are recalculated whenever the shape changes and can
 * be set by hand afterwards. `locked` (tickets exist) shows the layout read-only.
 */
export default function SeatLayoutEditor({ seats, onChange, locked }) {
  const [tool, setTool] = useState({ kind: 'SLEEPER', berth: 'SINGLE' })
  const [picked, setPicked] = useState(null)
  const [extra, setExtra] = useState({ rows: 0, cols: 0 })
  const [upper, setUpper] = useState(false)
  const [note, setNote] = useState('')

  const fit = [deckSize(seats, 0), deckSize(seats, 1)]
  const rows = Math.max(fit[0].rows, fit[1].rows, 6) + extra.rows
  const cols = Math.max(fit[0].cols, fit[1].cols, 4) + extra.cols
  const seat = seats.find((s) => s.label === picked) || null
  const problems = layoutProblems(seats)

  // Shape changed: work the pairs out again.
  const reshape = (next) => onChange(computePairs(next))
  const patch = (label, change) => seats.map((s) => (s.label === label ? { ...s, ...change } : s))

  const applyTemplate = (key) => {
    if (seats.length && !window.confirm('Replace the current layout with this template?')) return
    setPicked(null); setExtra({ rows: 0, cols: 0 }); setNote('')
    onChange(buildTemplate(key))
  }

  const addAt = (deck, row, col) => {
    setNote('')
    if (!canPlace(seats, deck, row, col, tool.kind)) {
      setNote('A sleeper needs two free cells, one above the other.')
      return
    }
    const label = nextLabel(seats, deck, tool.kind)
    reshape([...seats, { label, deck, row, col, kind: tool.kind, berth: tool.kind === 'SLEEPER' ? tool.berth : null, pairLabel: null, ladiesOnly: false }])
    setPicked(label)
  }

  const setKind = (kind) => {
    if (kind === 'SLEEPER' && !canPlace(seats, seat.deck, seat.row, seat.col, 'SLEEPER', seat.label)) {
      setNote('No room for a sleeper here: the cell behind this seat is taken.')
      return
    }
    setNote('')
    reshape(patch(seat.label, { kind, berth: kind === 'SLEEPER' ? (seat.berth || 'SINGLE') : null }))
  }

  const rename = (label) => {
    const old = seat.label
    onChange(seats.map((s) => {
      if (s.label === old) return { ...s, label }
      if (s.pairLabel === old) return { ...s, pairLabel: label }
      return s
    }))
    setPicked(label)
  }

  // Pair by hand: both seats point at each other, and their old partners are freed.
  const setPair = (other) => {
    const mine = seat.label
    const freed = new Set([seat.pairLabel, seats.find((s) => s.label === other)?.pairLabel].filter(Boolean))
    onChange(seats.map((s) => {
      if (s.label === mine) return { ...s, pairLabel: other || null }
      if (other && s.label === other) return { ...s, pairLabel: mine }
      if (freed.has(s.label)) return { ...s, pairLabel: null }
      return s
    }))
  }

  const remove = () => { reshape(seats.filter((s) => s.label !== seat.label)); setPicked(null) }

  const counts = seatClassesOf(seats).map((c) => [c, seats.filter((s) => seatClassOf(s) === c).length])
  const showUpper = upper || seats.some((s) => s.deck === 1)

  if (locked) {
    return (
      <div>
        <div style={{ ...wellStyle, marginBottom: 14, display: 'flex', gap: 10, alignItems: 'center' }}>
          <Icon name="lock" size={16} /><span style={{ fontSize: 13 }}>Tickets have been sold on this bus, so its seat layout can no longer change.</span>
        </div>
        <SeatDecks seats={seats} stateOf={(s) => (s.ladiesOnly ? 'FEMALE_ONLY' : 'AVAILABLE')} />
      </div>
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 14 }}>
        <span style={capsStyle}>Start from</span>
        {LAYOUT_TEMPLATES.map((t) => (
          <button key={t.key} type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => applyTemplate(t.key)}>{t.name}</button>
        ))}
        {seats.length > 0 && (
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }}
            onClick={() => { if (window.confirm('Remove every seat?')) { onChange([]); setPicked(null) } }}>
            <Icon name="trash" size={14} />Clear
          </button>
        )}
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 14 }}>
        <span style={capsStyle}>Click an empty cell to add</span>
        {[['SEATER', null, 'Seater'], ['SLEEPER', 'SINGLE', 'Single sleeper'], ['SLEEPER', 'DOUBLE', 'Double sleeper']].map(([kind, berth, label]) => {
          const on = tool.kind === kind && (kind === 'SEATER' || tool.berth === berth)
          return (
            <button key={label} type="button" className="pc-chip" aria-pressed={on} onClick={() => setTool({ kind, berth: berth || tool.berth })}
              style={{ border: 'none', cursor: 'pointer', ...(on ? chipOn : {}) }}>{label}</button>
          )
        })}
        <span style={{ flex: 1 }} />
        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setExtra((e) => ({ ...e, rows: e.rows + 2 }))}><Icon name="plus" size={14} />Rows</button>
        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setExtra((e) => ({ ...e, cols: e.cols + 1 }))}><Icon name="plus" size={14} />Column</button>
        {!showUpper && <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setUpper(true)}><Icon name="plus" size={14} />Upper deck</button>}
      </div>

      {note && <div className="pc-error" role="alert" style={{ marginBottom: 12 }}><Icon name="alert" size={16} /><span>{note}</span></div>}

      <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <SeatDecks seats={seats} rows={rows} cols={cols} showUpper={showUpper} active
          stateOf={(s) => (s.ladiesOnly ? 'FEMALE_ONLY' : 'EDIT')}
          selected={picked ? [picked] : []}
          onSeatClick={(s) => { setPicked(s.label); setNote('') }}
          onCellClick={addAt} />

        <div style={{ ...wellStyle, flex: '1 1 240px', minWidth: 240 }}>
          {!seat ? (
            <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', margin: 0 }}>Click a seat to change its number, kind, pair or women-only setting.</p>
          ) : (
            <div style={{ display: 'grid', gap: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={capsStyle}>Seat {seat.label} · {seat.deck === 1 ? 'upper' : 'lower'} deck</span>
                <button type="button" className="pc-iconbtn" aria-label="Close" onClick={() => setPicked(null)}><Icon name="x" size={16} /></button>
              </div>
              <div>
                <label htmlFor="seat-label" style={labelStyle}>Seat number</label>
                <input id="seat-label" className="input-field" value={seat.label} maxLength={6}
                  onChange={(e) => rename(e.target.value.toUpperCase().replace(/\s/g, ''))} />
              </div>
              <div>
                <span style={labelStyle}>Kind</span>
                <div className="pc-chips">
                  {[['SEATER', 'Seater'], ['SLEEPER', 'Sleeper']].map(([k, l]) => (
                    <button key={k} type="button" className="pc-chip" aria-pressed={seat.kind === k} onClick={() => setKind(k)}
                      style={{ border: 'none', cursor: 'pointer', ...(seat.kind === k ? chipOn : {}) }}>{l}</button>
                  ))}
                </div>
              </div>
              {seat.kind === 'SLEEPER' && (
                <div>
                  <span style={labelStyle}>Berth</span>
                  <div className="pc-chips">
                    {[['SINGLE', 'Single'], ['DOUBLE', 'Double']].map(([b, l]) => (
                      <button key={b} type="button" className="pc-chip" aria-pressed={seat.berth === b} onClick={() => reshape(patch(seat.label, { berth: b }))}
                        style={{ border: 'none', cursor: 'pointer', ...(seat.berth === b ? chipOn : {}) }}>{l}</button>
                    ))}
                  </div>
                </div>
              )}
              <div>
                <label htmlFor="seat-pair" style={labelStyle}>Next to (pair)</label>
                <select id="seat-pair" className="input-field" value={seat.pairLabel || ''} onChange={(e) => setPair(e.target.value)}>
                  <option value="">No neighbour</option>
                  {seats.filter((s) => s.deck === seat.deck && s.label !== seat.label).map((s) => <option key={s.label} value={s.label}>{s.label}</option>)}
                </select>
              </div>
              <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, fontWeight: 700 }}>
                <input type="checkbox" checked={!!seat.ladiesOnly} onChange={(e) => onChange(patch(seat.label, { ladiesOnly: e.target.checked }))} />
                Women only
              </label>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }} onClick={remove}>
                <Icon name="trash" size={14} />Delete seat
              </button>
            </div>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', marginTop: 14, fontSize: 13 }}>
        <b>{seats.length} seats</b>
        {counts.map(([c, n]) => <span key={c} className="pc-chip">{seatClassName(c)}: {n}</span>)}
        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => reshape(seats)}><Icon name="refresh" size={14} />Work out pairs again</button>
      </div>
      {problems.length > 0 && seats.length > 0 && (
        <ul className="pc-error" style={{ display: 'block', marginTop: 12, paddingLeft: 30 }}>
          {problems.map((p) => <li key={p}>{p}</li>)}
        </ul>
      )}

      {seats.length > 0 && (
        <div style={{ marginTop: 22 }}>
          <span style={capsStyle}>Preview: what travellers will see</span>
          <div style={{ marginTop: 10 }}>
            <SeatDecks seats={seats} stateOf={(s) => (s.ladiesOnly ? 'FEMALE_ONLY' : 'AVAILABLE')} />
            <SeatLegend states={['AVAILABLE', 'FEMALE_ONLY']} />
          </div>
        </div>
      )}
    </div>
  )
}
