import { describe, it, expect } from 'vitest'
import {
  LAYOUT_TEMPLATES, buildTemplate, computePairs, layoutProblems, seatClassOf, seatClassName, seatClassesOf,
  canPlace, seatAt, cleanSeats, nextLabel, tripSeatState, seatLook,
} from './busLayouts'

const seat = (label, deck, row, col, kind = 'SEATER', berth = null) => ({ label, deck, row, col, kind, berth, pairLabel: null, ladiesOnly: false })

describe('layout templates', () => {
  it.each(LAYOUT_TEMPLATES.map((t) => [t.name, t.key]))('%s: unique labels, no overlaps, symmetric pairs', (_, key) => {
    const seats = buildTemplate(key)
    expect(new Set(seats.map((s) => s.label)).size).toBe(seats.length)
    expect(layoutProblems(seats)).toEqual([])
    for (const s of seats) {
      if (s.pairLabel) {
        const p = seats.find((x) => x.label === s.pairLabel)
        expect(p.pairLabel).toBe(s.label)
        expect(p.deck).toBe(s.deck)
        expect(p.row).toBe(s.row)
        expect(Math.abs(p.col - s.col)).toBe(1)
      }
    }
  })

  it('2+1 Sleeper 30: 15 berths per deck, single berths alone, doubles paired', () => {
    const seats = buildTemplate('SLEEPER_2_1_30')
    expect(seats).toHaveLength(30)
    expect(seats.filter((s) => s.deck === 0)).toHaveLength(15)
    expect(seats.filter((s) => s.deck === 1)).toHaveLength(15)
    expect(seats.every((s) => s.kind === 'SLEEPER')).toBe(true)
    expect(seats.filter((s) => s.berth === 'SINGLE').every((s) => s.pairLabel === null)).toBe(true)
    expect(seats.filter((s) => s.berth === 'DOUBLE').every((s) => s.pairLabel)).toBe(true)
    expect(seats.filter((s) => s.deck === 0).every((s) => s.label.startsWith('L'))).toBe(true)
    expect(seats.filter((s) => s.deck === 1).every((s) => s.label.startsWith('U'))).toBe(true)
  })

  it('2+2 Seater 40: one deck, numbered 1..40, everyone has a neighbour across no aisle', () => {
    const seats = buildTemplate('SEATER_2_2_40')
    expect(seats).toHaveLength(40)
    expect(seats.every((s) => s.deck === 0 && s.kind === 'SEATER')).toBe(true)
    expect(seats.map((s) => s.label)).toEqual(Array.from({ length: 40 }, (_, i) => String(i + 1)))
    expect(seats.every((s) => s.pairLabel)).toBe(true)
    // Column 1 and column 3 sit either side of the aisle, so they are never a pair.
    const s2 = seats.find((s) => s.label === '2')
    expect(s2.col).toBe(1)
    expect(s2.pairLabel).toBe('1')
  })

  it('Seater + Sleeper: seaters and single sleepers below, sleepers above', () => {
    const seats = buildTemplate('SEATER_SLEEPER')
    const lower = seats.filter((s) => s.deck === 0)
    expect(lower.some((s) => s.kind === 'SEATER')).toBe(true)
    expect(lower.filter((s) => s.kind === 'SLEEPER').every((s) => s.berth === 'SINGLE' && !s.pairLabel)).toBe(true)
    expect(seats.filter((s) => s.deck === 1).every((s) => s.kind === 'SLEEPER')).toBe(true)
    expect(seatClassesOf(seats)).toEqual(['SEATER', 'LOWER_SINGLE', 'UPPER_SINGLE', 'UPPER_DOUBLE'])
  })
})

describe('pair calculation', () => {
  it('pairs adjacent seats of the same kind on the same deck and row', () => {
    const out = computePairs([seat('1', 0, 0, 0), seat('2', 0, 0, 1)])
    expect(out.map((s) => s.pairLabel)).toEqual(['2', '1'])
  })
  it('an aisle (empty column) between seats breaks the pair', () => {
    const out = computePairs([seat('1', 0, 0, 1), seat('2', 0, 0, 3)])
    expect(out.every((s) => s.pairLabel === null)).toBe(true)
  })
  it('different rows, decks or kinds are not pairs', () => {
    expect(computePairs([seat('1', 0, 0, 0), seat('2', 0, 1, 1)]).every((s) => !s.pairLabel)).toBe(true)
    expect(computePairs([seat('1', 0, 0, 0), seat('2', 1, 0, 1)]).every((s) => !s.pairLabel)).toBe(true)
    expect(computePairs([seat('1', 0, 0, 0), seat('L1', 0, 0, 1, 'SLEEPER', 'DOUBLE')]).every((s) => !s.pairLabel)).toBe(true)
  })
  it('a single berth has no pair, even next to another sleeper', () => {
    const out = computePairs([seat('L1', 0, 0, 0, 'SLEEPER', 'SINGLE'), seat('L2', 0, 0, 1, 'SLEEPER', 'DOUBLE')])
    expect(out.every((s) => s.pairLabel === null)).toBe(true)
  })
  it('three in a row pair from the left and leave the third alone', () => {
    const out = computePairs([seat('3', 0, 0, 2), seat('1', 0, 0, 0), seat('2', 0, 0, 1)])
    const by = Object.fromEntries(out.map((s) => [s.label, s.pairLabel]))
    expect(by).toEqual({ 1: '2', 2: '1', 3: null })
  })
  it('flags one-sided and missing pairs', () => {
    const one = [{ ...seat('1', 0, 0, 0), pairLabel: '2' }, seat('2', 0, 0, 1)]
    expect(layoutProblems(one).join(' ')).toMatch(/paired with each other/)
    expect(layoutProblems([{ ...seat('1', 0, 0, 0), pairLabel: '9' }]).join(' ')).toMatch(/does not exist/)
  })
})

describe('placing seats', () => {
  it('a sleeper covers two rows and needs both free', () => {
    const seats = [seat('L1', 0, 0, 0, 'SLEEPER', 'SINGLE')]
    expect(seatAt(seats, 0, 1, 0)?.label).toBe('L1')
    expect(seatAt(seats, 0, 2, 0)).toBeNull()
    expect(canPlace(seats, 0, 1, 0, 'SEATER')).toBe(false)
    expect(canPlace(seats, 0, 2, 0, 'SLEEPER')).toBe(true)
    expect(canPlace([seat('5', 0, 3, 0)], 0, 2, 0, 'SLEEPER')).toBe(false)
  })
  it('next labels follow the deck and kind', () => {
    const seats = [seat('L1', 0, 0, 0, 'SLEEPER', 'SINGLE'), seat('1', 0, 0, 2)]
    expect(nextLabel(seats, 0, 'SLEEPER')).toBe('L2')
    expect(nextLabel(seats, 1, 'SLEEPER')).toBe('U1')
    expect(nextLabel(seats, 0, 'SEATER')).toBe('2')
  })
  it('cleanSeats drops a seater berth and fills the seat class', () => {
    const [s] = cleanSeats([{ ...seat(' 7 ', 1, 0, 0), berth: 'DOUBLE' }])
    expect(s).toMatchObject({ label: '7', berth: null, seatClass: 'SEATER', ladiesOnly: false })
  })
})

describe('seat classes', () => {
  it('derives the class from kind, deck and berth', () => {
    expect(seatClassOf(seat('1', 1, 0, 0))).toBe('SEATER')
    expect(seatClassOf(seat('L1', 0, 0, 0, 'SLEEPER', 'SINGLE'))).toBe('LOWER_SINGLE')
    expect(seatClassOf(seat('L2', 0, 0, 0, 'SLEEPER', 'DOUBLE'))).toBe('LOWER_DOUBLE')
    expect(seatClassOf(seat('U1', 1, 0, 0, 'SLEEPER', 'SINGLE'))).toBe('UPPER_SINGLE')
    expect(seatClassOf(seat('U2', 1, 0, 0, 'SLEEPER', 'DOUBLE'))).toBe('UPPER_DOUBLE')
  })
  it('has a friendly name for each class', () => {
    expect(seatClassName('SEATER')).toBe('Seater')
    expect(seatClassName('LOWER_SINGLE')).toBe('Lower single sleeper')
    expect(seatClassName('UPPER_DOUBLE')).toBe('Upper double sleeper')
  })
})

describe('trip seat state and look', () => {
  const a = (status, gender = null) => ({ fromSeq: 0, toSeq: 1, status, gender })
  it('maps allocations to a chart state', () => {
    expect(tripSeatState({ allocations: [] })).toBe('AVAILABLE')
    expect(tripSeatState({ allocations: [a('CANCELLED', 'F'), a('EXPIRED', 'M')] })).toBe('AVAILABLE')
    expect(tripSeatState({ allocations: [a('BLOCKED')] })).toBe('BLOCKED')
    expect(tripSeatState({ allocations: [a('CONFIRMED', 'F')] })).toBe('BOOKED_F')
    expect(tripSeatState({ allocations: [a('HELD', 'M')] })).toBe('BOOKED_M')
    expect(tripSeatState({ allocations: [a('CONFIRMED')] })).toBe('SOLD')
  })
  it('women-only and men-only are dashed and labelled, never colour alone', () => {
    expect(seatLook('FEMALE_ONLY')).toMatchObject({ dash: true, tag: 'Women' })
    expect(seatLook('MALE_ONLY')).toMatchObject({ dash: true, tag: 'Men' })
    expect(seatLook('AVAILABLE').dash).toBe(false)
  })
})
