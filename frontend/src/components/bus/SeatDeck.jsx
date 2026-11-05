import React from 'react'
import Icon from '../dash/Icon'
import { seatAt, seatClassOf, seatClassName, seatLook, seatRows, deckSize, SEAT_LOOKS } from '../../utils/busLayouts'
import '../../styles/bus.css'

// The bus seen from above, front at the top. Pure drawing: the owner's layout editor, the trip
// seat chart, the admin preview and (later) the customer seat map all pass their own states.

// Seat cell size in px on wide screens; phones get bigger, easier to tap cells via --bus-cell-w and --bus-cell-h in bus.css.
export const CELL = 36
const GAP = 6

const selectedHas = (selected, label) => (selected instanceof Set ? selected.has(label) : (selected || []).includes(label))

function SeatShape({ seat, look, price, dim }) {
  const sleeper = seat.kind === 'SLEEPER'
  const ring = `2px ${look.dash ? 'dashed' : 'solid'} ${look.border}`
  return (
    <span style={{
      position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      width: '100%', height: '100%', borderRadius: sleeper ? 8 : 7, border: ring, background: look.bg, color: look.ink,
      boxSizing: 'border-box', lineHeight: 1.05, overflow: 'hidden', opacity: dim ? 0.3 : 1, transition: 'opacity .2s',
    }}>
      {/* Sleeper: pillow at the head end. Seater: backrest along the back edge, like a chair. */}
      {sleeper
        ? <span aria-hidden="true" style={{ position: 'absolute', top: 4, left: 5, right: 5, height: 5, borderRadius: 3, background: look.border, opacity: 0.45 }} />
        : <span aria-hidden="true" style={{ position: 'absolute', bottom: 3, left: 4, right: 4, height: 4, borderRadius: 2, background: look.border, opacity: 0.55 }} />}
      <b className="bus-seat-label">{seat.label}</b>
      {look.tag && <small className={`bus-seat-tag${look.tag.length > 5 ? ' is-long' : ''}`}>{look.tag}</small>}
      {/* A seater is too small for a tag and a price; the price is in its label then. */}
      {price != null && (!look.tag || sleeper) && <small className="bus-seat-price">₹{price}</small>}
      {seat.ladiesOnly && !look.tag && <small className="bus-seat-tag" style={{ color: '#9d174d' }}>W</small>}
    </span>
  )
}

/**
 * One deck. `stateOf(seat)` returns a SEAT_LOOKS key; seats in `selected` are drawn as SELECTED.
 * With `onCellClick`, empty cells are drawn too so the owner can place seats.
 */
export function SeatDeck({ seats, deck, rows, cols, stateOf, onSeatClick, selected, onCellClick, priceOf, dimOf, title, active }) {
  const own = seats.filter((s) => s.deck === deck)
  const fit = deckSize(seats, deck)
  const R = Math.max(rows || 0, fit.rows, 1)
  const C = Math.max(cols || 0, fit.cols, 1)

  const empties = []
  if (onCellClick) {
    for (let r = 0; r < R; r++) {
      for (let c = 0; c < C; c++) if (!seatAt(own, deck, r, c)) empties.push([r, c])
    }
  }

  return (
    <div style={{ border: '1px solid var(--pc-line)', borderRadius: 16, padding: '10px 14px 14px', background: 'var(--pc-panel)', display: 'inline-block' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minHeight: 30, marginBottom: 8, gap: 12 }}>
        <span style={{ fontSize: 12, fontWeight: 800, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.05em' }}>{title || (deck === 0 ? 'Lower deck' : 'Upper deck')}</span>
        {/* The driver sits at the front of the lower deck. */}
        {deck === 0 && <span title="Driver" style={{ color: 'var(--pc-ink-2)', display: 'inline-flex' }}><Icon name="wheel" size={24} /></span>}
      </div>
      <div
        role="group"
        aria-label={title || (deck === 0 ? 'Lower deck' : 'Upper deck')}
        className="bus-deck-grid"
        style={{ display: 'grid', gridTemplateColumns: `repeat(${C}, var(--bus-cell-w, ${CELL}px))`, gridAutoRows: `var(--bus-cell-h, ${CELL}px)`, gridTemplateRows: `repeat(${R}, var(--bus-cell-h, ${CELL}px))`, gap: GAP }}
      >
        {empties.map(([r, c]) => (
          <button key={`e${r}-${c}`} type="button" onClick={() => onCellClick(deck, r, c)}
            aria-label={`Add a seat at row ${r + 1}, column ${c + 1}`}
            style={{ gridRow: r + 1, gridColumn: c + 1, border: `1px dashed ${active ? 'var(--pc-axis)' : 'var(--pc-line)'}`, borderRadius: 6, background: 'transparent', cursor: 'pointer', padding: 0 }} />
        ))}
        {own.map((seat) => {
          const isSel = selectedHas(selected, seat.label)
          const state = isSel ? 'SELECTED' : (stateOf ? stateOf(seat) : 'AVAILABLE')
          const look = seatLook(state)
          const price = priceOf ? priceOf(seat) : null
          const dim = !isSel && dimOf ? dimOf(seat) : false
          const name = `Seat ${seat.label}, ${seatClassName(seatClassOf(seat)).toLowerCase()}${seat.ladiesOnly ? ', women only' : ''}, ${look.name.toLowerCase()}${price != null ? `, ₹${price}` : ''}`
          const style = { gridRow: `${seat.row + 1} / span ${seatRows(seat)}`, gridColumn: seat.col + 1, padding: 0, border: 'none', background: 'none' }
          return onSeatClick ? (
            <button key={seat.label} type="button" onClick={() => onSeatClick(seat)} aria-pressed={isSel} aria-label={name} title={name}
              style={{ ...style, cursor: 'pointer' }}>
              <SeatShape seat={seat} look={look} price={price} dim={dim} />
            </button>
          ) : (
            <span key={seat.label} role="img" aria-label={name} title={name} style={style}><SeatShape seat={seat} look={look} price={price} dim={dim} /></span>
          )
        })}
      </div>
    </div>
  )
}

/** Lower and upper deck side by side, sized alike. The upper deck is left out when it is empty. */
export function SeatDecks({ seats, showUpper, rows, cols, ...rest }) {
  const lower = deckSize(seats, 0)
  const upper = deckSize(seats, 1)
  const R = Math.max(rows || 0, lower.rows, upper.rows)
  const C = Math.max(cols || 0, lower.cols, upper.cols)
  const hasUpper = showUpper || seats.some((s) => s.deck === 1)
  return (
    <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'flex-start', overflowX: 'auto' }}>
      <SeatDeck seats={seats} deck={0} rows={R} cols={C} {...rest} />
      {hasUpper && <SeatDeck seats={seats} deck={1} rows={R} cols={C} {...rest} />}
    </div>
  )
}

/** Key for the colours actually used on a chart. */
export function SeatLegend({ states }) {
  return (
    <ul style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 16px', listStyle: 'none', padding: 0, margin: '12px 0 0', fontSize: 12, color: 'var(--pc-ink-2)' }}>
      {states.map((s) => {
        const look = SEAT_LOOKS[s]
        return (
          <li key={s} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span aria-hidden="true" style={{ width: 16, height: 16, borderRadius: 4, background: look.bg, border: `2px ${look.dash ? 'dashed' : 'solid'} ${look.border}` }} />
            {look.name}
          </li>
        )
      })}
    </ul>
  )
}

export default SeatDecks
