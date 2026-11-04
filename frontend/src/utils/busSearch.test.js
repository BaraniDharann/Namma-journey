import { describe, it, expect } from 'vitest'
import {
  departureBucket, clockText, dayShift, durationText, ratingText, sortTrips, filterTrips, emptyFilters,
  filterOptions, activeFilterCount, toggleIn,
} from './busSearch'

const trip = (over) => ({
  tripId: 1, travelsName: 'KPN', busName: 'Night Rider', ac: true, hasSleeper: true, hasSeater: false,
  departureAt: '2026-11-10T21:30:00', arrivalAt: '2026-11-11T05:00:00', durationMinutes: 450,
  ratingAvg: 4.2, ratingCount: 30, minFare: 900, seatsLeft: 12, singleSeatsLeft: 3,
  boardingPoints: [{ id: 1, name: 'Koyambedu', at: '2026-11-10T21:30:00' }],
  droppingPoints: [{ id: 2, name: 'Gandhipuram', at: '2026-11-11T05:00:00' }],
  ...over,
})

const trips = [
  trip({ tripId: 1, minFare: 900, departureAt: '2026-11-10T21:30:00', durationMinutes: 450, ratingAvg: 4.2, ratingCount: 30 }),
  trip({ tripId: 2, travelsName: 'SRS', minFare: 650, departureAt: '2026-11-10T05:45:00', durationMinutes: 540, ratingAvg: 3.9, ratingCount: 120, ac: false, hasSleeper: false, hasSeater: true, singleSeatsLeft: 0, boardingPoints: [{ id: 3, name: 'Guindy', at: '' }] }),
  trip({ tripId: 3, travelsName: 'Parveen', minFare: 650, departureAt: '2026-11-10T13:00:00', durationMinutes: 400, ratingAvg: null, ratingCount: 0, hasSeater: true, droppingPoints: [{ id: 4, name: 'Ukkadam', at: '' }] }),
  trip({ tripId: 4, travelsName: 'KPN', minFare: 1200, departureAt: '2026-11-10T09:00:00', durationMinutes: 420, ratingAvg: 4.2, ratingCount: 80 }),
]
const ids = (list) => list.map((t) => t.tripId)

describe('departure buckets', () => {
  it('splits the day at 6, 12 and 18 with the boundary in the later bucket', () => {
    expect(departureBucket('2026-11-10T00:00:00')).toBe('EARLY')
    expect(departureBucket('2026-11-10T05:59:00')).toBe('EARLY')
    expect(departureBucket('2026-11-10T06:00:00')).toBe('MORNING')
    expect(departureBucket('2026-11-10T11:59:00')).toBe('MORNING')
    expect(departureBucket('2026-11-10T12:00:00')).toBe('AFTERNOON')
    expect(departureBucket('2026-11-10T17:59:00')).toBe('AFTERNOON')
    expect(departureBucket('2026-11-10T18:00:00')).toBe('NIGHT')
    expect(departureBucket('2026-11-10T23:59:00')).toBe('NIGHT')
    expect(departureBucket(null)).toBeNull()
  })
})

describe('card texts', () => {
  it('reads clock times from the text', () => {
    expect(clockText('2026-11-10T21:05:00')).toBe('21:05')
    expect(clockText(undefined)).toBe('—')
  })
  it('marks next-day arrivals', () => {
    expect(dayShift('2026-11-10T21:30:00', '2026-11-11T05:00:00')).toBe(1)
    expect(dayShift('2026-11-10T06:00:00', '2026-11-10T12:00:00')).toBe(0)
    expect(dayShift('2026-11-30T22:00:00', '2026-12-02T01:00:00')).toBe(2)
  })
  it('formats durations', () => {
    expect(durationText(450)).toBe('7h 30m')
    expect(durationText(45)).toBe('45m')
    expect(durationText(720)).toBe('12h')
  })
  it('shows New for an unrated bus', () => {
    expect(ratingText({ ratingAvg: 4.25, ratingCount: 3 })).toBe('★ 4.3')
    expect(ratingText({ ratingAvg: null, ratingCount: 0 })).toBe('New')
  })
})

describe('sorting', () => {
  it('cheapest first, earlier departure breaks a tie', () => {
    expect(ids(sortTrips(trips, 'CHEAPEST'))).toEqual([2, 3, 1, 4])
  })
  it('earliest departure', () => {
    expect(ids(sortTrips(trips, 'EARLIEST'))).toEqual([2, 4, 3, 1])
  })
  it('highest rated: more ratings break a tie, unrated last', () => {
    expect(ids(sortTrips(trips, 'RATED'))).toEqual([4, 1, 2, 3])
  })
  it('fastest', () => {
    expect(ids(sortTrips(trips, 'FASTEST'))).toEqual([3, 4, 1, 2])
  })
  it('does not change the input and defaults to cheapest', () => {
    const copy = [...trips]
    expect(ids(sortTrips(trips, 'NOPE'))).toEqual([2, 3, 1, 4])
    expect(trips).toEqual(copy)
  })
})

describe('filters', () => {
  const run = (over) => ids(filterTrips(trips, { ...emptyFilters(), ...over }))
  it('passes everything with no filter on', () => {
    expect(run({})).toEqual([1, 2, 3, 4])
  })
  it('A/C and Non-A/C', () => {
    expect(run({ acTypes: ['NONAC'] })).toEqual([2])
    expect(run({ acTypes: ['AC', 'NONAC'] })).toEqual([1, 2, 3, 4])
  })
  it('sleeper or seater within the group', () => {
    expect(run({ kinds: ['SEATER'] })).toEqual([2, 3])
    expect(run({ kinds: ['SLEEPER'] })).toEqual([1, 3, 4])
  })
  it('groups combine with AND', () => {
    expect(run({ kinds: ['SEATER'], acTypes: ['AC'] })).toEqual([3])
  })
  it('single seats only', () => {
    expect(run({ singleOnly: true })).toEqual([1, 3, 4])
  })
  it('departure buckets', () => {
    expect(run({ buckets: ['EARLY', 'NIGHT'] })).toEqual([1, 2])
  })
  it('boarding, dropping and travels names', () => {
    expect(run({ boarding: ['Guindy'] })).toEqual([2])
    expect(run({ dropping: ['Ukkadam'] })).toEqual([3])
    expect(run({ travels: ['KPN'] })).toEqual([1, 4])
  })
  it('price range is inclusive', () => {
    expect(run({ minPrice: 650, maxPrice: 900 })).toEqual([1, 2, 3])
    expect(run({ minPrice: 1000 })).toEqual([4])
  })
  it('counts active groups', () => {
    expect(activeFilterCount(emptyFilters())).toBe(0)
    expect(activeFilterCount({ ...emptyFilters(), kinds: ['SEATER'], singleOnly: true, maxPrice: 900 })).toBe(3)
  })
  it('offers choices taken from the results', () => {
    const o = filterOptions(trips)
    expect(o.travels).toEqual(['KPN', 'Parveen', 'SRS'])
    expect(o.boarding).toEqual(['Guindy', 'Koyambedu'])
    expect(o.dropping).toEqual(['Gandhipuram', 'Ukkadam'])
    expect([o.minFare, o.maxFare]).toEqual([650, 1200])
  })
  it('toggles a value in a list', () => {
    expect(toggleIn(['a'], 'b')).toEqual(['a', 'b'])
    expect(toggleIn(['a', 'b'], 'a')).toEqual(['b'])
  })
})
