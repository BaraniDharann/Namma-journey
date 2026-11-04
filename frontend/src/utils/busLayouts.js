// Bus seat layouts: the starting templates, seat classes and the pair (neighbour) calculation.
//
// A deck is a grid. `row` runs from the front of the bus to the back, `col` from left to right.
// A seater fills one cell; a sleeper is drawn one column wide and two rows long, so it fills
// (row, col) and (row + 1, col). An empty column is the aisle.

export const SEAT_CLASSES = ['SEATER', 'LOWER_SINGLE', 'LOWER_DOUBLE', 'UPPER_SINGLE', 'UPPER_DOUBLE']

export const SEAT_CLASS_NAMES = {
  SEATER: 'Seater',
  LOWER_SINGLE: 'Lower single sleeper',
  LOWER_DOUBLE: 'Lower double sleeper',
  UPPER_SINGLE: 'Upper single sleeper',
  UPPER_DOUBLE: 'Upper double sleeper',
}

/** Seat class is derived, never entered (same rule as the server). */
export function seatClassOf(seat) {
  if (seat.kind !== 'SLEEPER') return 'SEATER'
  return `${seat.deck === 1 ? 'UPPER' : 'LOWER'}_${seat.berth === 'DOUBLE' ? 'DOUBLE' : 'SINGLE'}`
}

export const seatClassName = (cls) => SEAT_CLASS_NAMES[cls] || cls

/** The classes a bus actually has, in the fixed display order. */
export function seatClassesOf(seats) {
  const present = new Set((seats || []).map(seatClassOf))
  return SEAT_CLASSES.filter((c) => present.has(c))
}

/** Rows a seat covers on its deck: one for a seater, two for a sleeper. */
export const seatRows = (seat) => (seat.kind === 'SLEEPER' ? 2 : 1)

/** The seat drawn over (deck, row, col), if any; a sleeper also covers the row below its anchor. */
export function seatAt(seats, deck, row, col) {
  return seats.find((s) => s.deck === deck && s.col === col && row >= s.row && row < s.row + seatRows(s)) || null
}

/** True when a seat of `kind` could be placed with its top-left at (deck, row, col). */
export function canPlace(seats, deck, row, col, kind, ignoreLabel) {
  const rest = ignoreLabel == null ? seats : seats.filter((s) => s.label !== ignoreLabel)
  const rows = kind === 'SLEEPER' ? 2 : 1
  for (let r = row; r < row + rows; r++) if (seatAt(rest, deck, r, col)) return false
  return true
}

/**
 * Who sits next to whom. Two seats are a pair when they are on the same deck and row, in
 * adjacent columns (so no aisle between them) and of the same kind. A single berth has no
 * neighbour. Three seats side by side pair from the left: the first two, then the third alone.
 * Returns new seat objects with `pairLabel` set (null when unpaired).
 */
export function computePairs(seats) {
  const byLabel = new Map()
  const groups = new Map()
  for (const s of seats) {
    const key = `${s.deck}:${s.row}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(s)
  }
  for (const group of groups.values()) {
    const sorted = [...group].sort((a, b) => a.col - b.col)
    for (let i = 0; i < sorted.length; i++) {
      const a = sorted[i]
      const b = sorted[i + 1]
      const pairable = (s) => !(s.kind === 'SLEEPER' && s.berth === 'SINGLE')
      if (b && b.col === a.col + 1 && a.kind === b.kind && pairable(a) && pairable(b)) {
        byLabel.set(a.label, b.label)
        byLabel.set(b.label, a.label)
        i++
      }
    }
  }
  return seats.map((s) => ({ ...s, pairLabel: byLabel.get(s.label) || null }))
}

/** Problems the server would refuse, in words the owner can act on. Empty when the layout is fine. */
export function layoutProblems(seats) {
  const problems = []
  const labels = new Map(seats.map((s) => [s.label, s]))
  if (!seats.length) problems.push('Add at least one seat.')
  const seen = new Set()
  for (const s of seats) {
    const label = String(s.label || '').trim()
    if (!label) problems.push('Every seat needs a number.')
    else if (seen.has(label)) problems.push(`Seat number ${label} is used twice.`)
    seen.add(label)
    if (s.kind === 'SLEEPER' && !s.berth) problems.push(`Sleeper ${label} needs single or double.`)
    if (s.pairLabel) {
      const p = labels.get(s.pairLabel)
      if (!p) problems.push(`Seat ${label} is paired with ${s.pairLabel}, which does not exist.`)
      else if (p.pairLabel !== s.label) problems.push(`Seat ${label} and ${s.pairLabel} must be paired with each other.`)
    }
  }
  for (const s of seats) {
    if (s.kind === 'SLEEPER' && seats.some((o) => o !== s && o.deck === s.deck && o.col === s.col && o.row === s.row + 1)) {
      problems.push(`Sleeper ${s.label} overlaps the seat behind it.`)
    }
  }
  return [...new Set(problems)]
}

/** Seats ready to send: trimmed labels, seater berth null, seat class filled in. */
export function cleanSeats(seats) {
  return seats.map((s) => {
    const seat = {
      label: String(s.label).trim(),
      deck: s.deck,
      row: s.row,
      col: s.col,
      kind: s.kind,
      berth: s.kind === 'SLEEPER' ? (s.berth || 'SINGLE') : null,
      pairLabel: s.pairLabel || null,
      ladiesOnly: !!s.ladiesOnly,
    }
    return { ...seat, seatClass: seatClassOf(seat) }
  })
}

/** Grid size that fits a deck's seats, with a little room to grow in the editor. */
export function deckSize(seats, deck) {
  const own = seats.filter((s) => s.deck === deck)
  const rows = own.reduce((m, s) => Math.max(m, s.row + seatRows(s)), 0)
  const cols = own.reduce((m, s) => Math.max(m, s.col + 1), 0)
  return { rows, cols }
}

/** The next free label: L7 / U3 for sleepers, plain numbers for seaters. */
export function nextLabel(seats, deck, kind) {
  const prefix = kind === 'SLEEPER' ? (deck === 1 ? 'U' : 'L') : ''
  const used = new Set(seats.map((s) => s.label))
  for (let n = 1; ; n++) if (!used.has(`${prefix}${n}`)) return `${prefix}${n}`
}

// --- Templates -----------------------------------------------------------------------------

function sleeper(label, deck, row, col, berth) {
  return { label, deck, row, col, kind: 'SLEEPER', berth, pairLabel: null, ladiesOnly: false }
}
function seater(label, deck, row, col) {
  return { label, deck, row, col, kind: 'SEATER', berth: null, pairLabel: null, ladiesOnly: false }
}

/** One deck of 2+1 sleepers: single berths in column 0, aisle in 1, double berths in 2 and 3. */
function sleeperDeck(deck, rows, prefix, start = 1) {
  const out = []
  let n = start
  for (let i = 0; i < rows; i++) {
    const row = i * 2
    out.push(sleeper(`${prefix}${n++}`, deck, row, 0, 'SINGLE'))
    out.push(sleeper(`${prefix}${n++}`, deck, row, 2, 'DOUBLE'))
    out.push(sleeper(`${prefix}${n++}`, deck, row, 3, 'DOUBLE'))
  }
  return out
}

/** 2+1 sleeper: five rows of berths on each deck, 30 berths. */
function sleeper30() {
  return [...sleeperDeck(0, 5, 'L'), ...sleeperDeck(1, 5, 'U')]
}

/** 2+2 seater: ten rows of four with the aisle in column 2, 40 seats, no upper deck. */
function seater40() {
  const out = []
  let n = 1
  for (let row = 0; row < 10; row++) {
    for (const col of [0, 1, 3, 4]) out.push(seater(String(n++), 0, row, col))
  }
  return out
}

/**
 * Seater + sleeper (as on most Tamil Nadu night services): the lower deck has a column of single
 * sleepers on the left, the aisle, then two seaters per row; the upper deck is 2+1 sleepers.
 */
function seaterSleeper() {
  const out = []
  for (let i = 0; i < 5; i++) out.push(sleeper(`L${i + 1}`, 0, i * 2, 0, 'SINGLE'))
  let n = 1
  for (let row = 0; row < 10; row++) {
    out.push(seater(String(n++), 0, row, 2))
    out.push(seater(String(n++), 0, row, 3))
  }
  return [...out, ...sleeperDeck(1, 5, 'U')]
}

export const LAYOUT_TEMPLATES = [
  { key: 'SLEEPER_2_1_30', name: '2+1 Sleeper 30', build: sleeper30 },
  { key: 'SEATER_2_2_40', name: '2+2 Seater 40', build: seater40 },
  { key: 'SEATER_SLEEPER', name: 'Seater + Sleeper', build: seaterSleeper },
]

/** A template's seats with pairs already worked out. */
export function buildTemplate(key) {
  const t = LAYOUT_TEMPLATES.find((x) => x.key === key)
  return t ? computePairs(t.build()) : []
}

// --- Seat look ------------------------------------------------------------------------------

/**
 * How a seat state is drawn. Women-only and men-only must never be mistaken for plain available
 * seats (a known redBus complaint), so they get a dashed border AND a word, not colour alone.
 */
export const SEAT_LOOKS = {
  AVAILABLE: { name: 'Available', bg: '#ffffff', border: '#16a34a', dash: false, ink: '#14532d', tag: '' },
  SELECTED: { name: 'Selected', bg: '#16a34a', border: '#15803d', dash: false, ink: '#ffffff', tag: '' },
  SOLD: { name: 'Sold', bg: '#e5e7eb', border: '#cbd5e1', dash: false, ink: '#64748b', tag: 'Sold' },
  BOOKED_F: { name: 'Booked by woman', bg: '#fbcfe8', border: '#db2777', dash: false, ink: '#831843', tag: 'F' },
  BOOKED_M: { name: 'Booked by man', bg: '#bfdbfe', border: '#2563eb', dash: false, ink: '#1e3a8a', tag: 'M' },
  FEMALE_ONLY: { name: 'Women only', bg: '#ffffff', border: '#db2777', dash: true, ink: '#9d174d', tag: 'Women' },
  MALE_ONLY: { name: 'Men only', bg: '#ffffff', border: '#2563eb', dash: true, ink: '#1e40af', tag: 'Men' },
  BLOCKED: { name: 'Blocked', bg: '#334155', border: '#1e293b', dash: false, ink: '#ffffff', tag: 'Blocked' },
  EDIT: { name: 'Seat', bg: '#ffffff', border: '#3e5a5b', dash: false, ink: '#0f2a2b', tag: '' },
}

export const seatLook = (state) => SEAT_LOOKS[state] || SEAT_LOOKS.AVAILABLE

const LIVE = new Set(['HELD', 'CONFIRMED', 'BLOCKED'])

/** Live allocations on a trip seat (cancelled and expired ones no longer hold it). */
export const liveAllocations = (seat) => (seat.allocations || []).filter((a) => LIVE.has(a.status))

/** A seat on the owner's trip chart: blocked, booked by a woman or man, sold, or free. */
export function tripSeatState(seat) {
  const live = liveAllocations(seat)
  if (!live.length) return 'AVAILABLE'
  if (live.some((a) => a.status === 'BLOCKED')) return 'BLOCKED'
  if (live.some((a) => a.gender === 'F')) return 'BOOKED_F'
  if (live.some((a) => a.gender === 'M')) return 'BOOKED_M'
  return 'SOLD'
}
