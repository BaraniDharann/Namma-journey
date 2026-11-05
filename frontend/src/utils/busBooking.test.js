import { describe, it, expect, beforeEach } from 'vitest'
import {
  seatState, isSelectable, genderLock, toggleSeat, priceChips, round2, fareBreakup, cleanPhone,
  passengerRows, bookingProblems, secondsUntil, formatCountdown, whenText, slabRows, ticketTab,
  ticketsForTab, cancellableSeats, payoutTotals, saveDraft, loadDraft, clearDraft,
} from './busBooking'
import { safePath } from './returnTo'

const seat = (label, state, fare = 1460) => ({ label, state, fare, deck: 0, row: 0, col: 0, kind: 'SLEEPER' })

describe('seat states', () => {
  it('a seat with no fare for the pair shows as sold', () => {
    expect(seatState(seat('L1', 'AVAILABLE', null))).toBe('SOLD')
    expect(isSelectable(seat('L1', 'AVAILABLE', null))).toBe(false)
  })
  it('available, women-only and men-only seats can be picked; booked ones cannot', () => {
    for (const s of ['AVAILABLE', 'FEMALE_ONLY', 'MALE_ONLY']) expect(isSelectable(seat('L1', s))).toBe(true)
    for (const s of ['SOLD', 'BOOKED_F', 'BOOKED_M']) expect(isSelectable(seat('L1', s))).toBe(false)
  })
})

describe('gender lock per seat state', () => {
  it('locks women-only seats to F and men-only seats to M', () => {
    expect(genderLock(seat('U1', 'FEMALE_ONLY'))).toBe('F')
    expect(genderLock(seat('U1', 'MALE_ONLY'))).toBe('M')
    expect(genderLock(seat('U1', 'AVAILABLE'))).toBeNull()
    expect(genderLock(undefined)).toBeNull()
  })
  it('passenger rows take the lock and keep what was typed', () => {
    const seats = [seat('U1', 'FEMALE_ONLY'), seat('U2', 'AVAILABLE')]
    const rows = passengerRows(seats, [{ seatLabel: 'U1', name: 'Asha', age: 30, gender: 'M' }, { seatLabel: 'U2', name: 'Ravi', age: 31, gender: 'M' }])
    expect(rows).toEqual([
      { seatLabel: 'U1', name: 'Asha', age: 30, gender: 'F' },
      { seatLabel: 'U2', name: 'Ravi', age: 31, gender: 'M' },
    ])
  })
  it('refuses a man on a women-only seat', () => {
    const problems = bookingProblems({
      seats: [seat('U1', 'FEMALE_ONLY')],
      passengers: [{ seatLabel: 'U1', name: 'Ravi', age: 30, gender: 'M' }],
      contactPhone: '9876543210', contactEmail: 'a@b.in',
    })
    expect(problems).toEqual(['Seat U1 is for women only.'])
  })
})

describe('seat selection', () => {
  it('selects, unselects and stops at the maximum', () => {
    const a = toggleSeat([], seat('L1', 'AVAILABLE'), 2)
    expect(a).toEqual({ selected: ['L1'], error: '' })
    const b = toggleSeat(['L1', 'L2'], seat('L3', 'AVAILABLE'), 2)
    expect(b.selected).toEqual(['L1', 'L2'])
    expect(b.error).toMatch(/up to 2 seats/)
    expect(toggleSeat(['L1'], seat('L1', 'AVAILABLE'), 2).selected).toEqual([])
    expect(toggleSeat([], seat('L4', 'SOLD'), 2).error).toMatch(/not available/)
  })
  it('price chips list the fares of pickable seats', () => {
    expect(priceChips([seat('a', 'AVAILABLE', 1760), seat('b', 'SOLD', 999), seat('c', 'FEMALE_ONLY', 1460), seat('d', 'AVAILABLE', 1760)])).toEqual([1460, 1760])
  })
})

describe('fare breakup', () => {
  it('rounds half-up to paise', () => {
    expect(round2(1.005)).toBe(1.01)
    expect(round2(73.125)).toBe(73.13)
  })
  it('adds fare and per-seat GST', () => {
    const b = fareBreakup([{ fare: 1460 }, { fare: 1760 }], 5)
    expect(b).toEqual({ count: 2, fare: 3220, gst: 161, gstPercent: 5, total: 3381 })
  })
  it('rounds GST per seat before adding', () => {
    // 5% of 999.9 = 49.995 → 50.00 each; on the sum it would be 99.99
    expect(fareBreakup([{ fare: 999.9 }, { fare: 999.9 }], 5).gst).toBe(100)
  })
  it('is zero for no seats', () => {
    expect(fareBreakup([], 5)).toEqual({ count: 0, fare: 0, gst: 0, gstPercent: 5, total: 0 })
  })
})

describe('contact checks', () => {
  it('accepts Indian mobiles with a prefix', () => {
    expect(cleanPhone('+91 98765 43210')).toBe('9876543210')
    expect(cleanPhone('09876543210')).toBe('9876543210')
    expect(cleanPhone('12345')).toBeNull()
  })
  it('lists every problem', () => {
    const problems = bookingProblems({
      seats: [seat('U1', 'AVAILABLE')],
      passengers: [{ seatLabel: 'U1', name: '', age: '', gender: '' }],
      contactPhone: '', contactEmail: 'x', extraPhone: '123',
    })
    expect(problems).toHaveLength(6)
  })
})

describe('countdown', () => {
  it('formats minutes and seconds', () => {
    expect(formatCountdown(581)).toBe('09:41')
    expect(formatCountdown(0)).toBe('00:00')
    expect(formatCountdown(-5)).toBe('00:00')
    expect(formatCountdown(3725)).toBe('1:02:05')
  })
  it('counts seconds to the hold expiry, never below zero', () => {
    const now = new Date('2026-11-10T10:00:00').getTime()
    expect(secondsUntil('2026-11-10T10:09:41', now)).toBe(581)
    expect(secondsUntil('2026-11-10T09:59:00', now)).toBe(0)
    expect(secondsUntil(null, now)).toBe(0)
  })
})

describe('cancellation slabs', () => {
  it('shows each slab with clock times', () => {
    expect(whenText('2026-11-08T21:30:00')).toBe('Sun 8 Nov, 21:30')
    const rows = slabRows([
      { minHours: 48, percent: 10, until: '2026-11-08T21:30:00' },
      { minHours: 24, percent: 25, until: '2026-11-09T21:30:00' },
      { minHours: 0, percent: 100, until: null },
    ])
    expect(rows.map((r) => r.when)).toEqual([
      'Until Sun 8 Nov, 21:30',
      'Sun 8 Nov, 21:30 to Mon 9 Nov, 21:30',
      'After Mon 9 Nov, 21:30',
    ])
    expect(rows.map((r) => r.charge)).toEqual(['10%', '25%', '100%'])
    expect(rows[2].refund).toBe('No refund')
    expect(rows[0].refund).toBe('90% of the fare + GST back')
  })
  it('a single slab applies any time', () => {
    expect(slabRows([{ minHours: 0, percent: 50, until: null }])[0].when).toBe('Any time before boarding')
  })
})

describe('tickets', () => {
  const t = (id, status, tripStatus, travelDate = '2026-11-10') => ({ id, status, tripStatus, travelDate, boardingPoint: { at: `${travelDate}T21:00:00` } })
  it('sorts tickets into tabs and hides unpaid holds', () => {
    expect(ticketTab(t(1, 'CONFIRMED', 'SCHEDULED'))).toBe('UPCOMING')
    expect(ticketTab(t(1, 'PARTIALLY_CANCELLED', 'DEPARTED'))).toBe('UPCOMING')
    expect(ticketTab(t(1, 'CONFIRMED', 'COMPLETED'))).toBe('COMPLETED')
    expect(ticketTab(t(1, 'COMPLETED', 'COMPLETED'))).toBe('COMPLETED')
    expect(ticketTab(t(1, 'SERVICE_CANCELLED', 'CANCELLED'))).toBe('CANCELLED')
    expect(ticketTab(t(1, 'EXPIRED', 'SCHEDULED'))).toBe('CANCELLED')
    expect(ticketTab(t(1, 'PENDING', 'SCHEDULED'))).toBeNull()
  })
  it('upcoming soonest first', () => {
    const list = [t(1, 'CONFIRMED', 'SCHEDULED', '2026-11-12'), t(2, 'CONFIRMED', 'SCHEDULED', '2026-11-10')]
    expect(ticketsForTab(list, 'UPCOMING').map((x) => x.id)).toEqual([2, 1])
  })
  it('only confirmed passengers can be cancelled', () => {
    expect(cancellableSeats({ passengers: [{ seatLabel: 'U1', status: 'CONFIRMED' }, { seatLabel: 'U2', status: 'CANCELLED' }] })).toEqual(['U1'])
  })
})

describe('payout totals', () => {
  it('adds due and paid separately', () => {
    const totals = payoutTotals([
      { status: 'DUE', faresTravelled: 1000, chargesKept: 100.1, gross: 1100.1, commission: 110.01, net: 990.09 },
      { status: 'DUE', faresTravelled: 500, chargesKept: 0, gross: 500, commission: 50, net: 450 },
      { status: 'PAID', faresTravelled: 200, chargesKept: 0, gross: 200, commission: 20, net: 180 },
    ])
    expect(totals.DUE).toEqual({ count: 2, faresTravelled: 1500, chargesKept: 100.1, gross: 1600.1, commission: 160.01, net: 1440.09 })
    expect(totals.PAID.net).toBe(180)
  })
})

describe('booking draft across login', () => {
  const store = new Map()
  beforeEach(() => {
    store.clear()
    globalThis.sessionStorage = {
      getItem: (k) => (store.has(k) ? store.get(k) : null),
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: (k) => store.delete(k),
    }
  })
  it('restores the draft for the same trip and pair only', () => {
    saveDraft({ tripId: 7, from: 1, to: 2, selected: ['U1'] }, 1000)
    expect(loadDraft(7, 1, 2, 2000).selected).toEqual(['U1'])
    expect(loadDraft(8, 1, 2, 2000)).toBeNull()
    expect(loadDraft(7, 1, 3, 2000)).toBeNull()
    expect(loadDraft(7, 1, 2, 1000 + 31 * 60 * 1000)).toBeNull()
    clearDraft()
    expect(loadDraft(7, 1, 2, 2000)).toBeNull()
  })
  it('return paths stay inside the app', () => {
    expect(safePath('/bus/trip/7?from=1')).toBe('/bus/trip/7?from=1')
    expect(safePath('//evil.com')).toBeNull()
    expect(safePath('https://evil.com')).toBeNull()
  })
})
