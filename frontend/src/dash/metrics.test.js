import { describe, it, expect } from 'vitest'
import {
  inr, shortPlace, statusSegments, topRoutes, bookingHeatmap, fleetBusy, waitingForDriver,
  monthSeries, pctChange, earningsThisWeek, todayStats, completionRate, nextTrip,
  spendByMonth, tripLengthSegments, daysUntil, typeMix, totalKm, topDestinations,
} from './metrics'

const TODAY = new Date(2026, 8, 30, 15, 0) // Wed 30 Sep 2026, local time

describe('inr', () => {
  it('formats whole rupees in the Indian grouping', () => {
    expect(inr(138005.134)).toBe('₹1,38,005')
    expect(inr(6000.223)).toBe('₹6,000')
    expect(inr(101.686)).toBe('₹102')
  })
  it('treats missing values as zero', () => {
    expect(inr(null)).toBe('₹0')
    expect(inr(undefined)).toBe('₹0')
    expect(inr('abc')).toBe('₹0')
  })
})

describe('shortPlace', () => {
  it('keeps only the first part of a geocoded address', () => {
    expect(shortPlace('Kanchipuram Bus Stand, Kanchipuram, Tamil Nadu')).toBe('Kanchipuram Bus Stand')
    expect(shortPlace('Coimbatore')).toBe('Coimbatore')
    expect(shortPlace('')).toBe('—')
    expect(shortPlace(null)).toBe('—')
  })
})

describe('statusSegments', () => {
  it('counts statuses in a fixed order with a fixed colour per status', () => {
    const b = [{ status: 'PENDING' }, { status: 'COMPLETED' }, { status: 'PENDING' }, { status: 'STARTED' }]
    expect(statusSegments(b)).toEqual([
      ['Completed', 1, 's3'],
      ['Pending', 2, 's4'],
      ['Started', 1, 's2'],
    ])
  })
  it('keeps a status colour when other statuses disappear', () => {
    expect(statusSegments([{ status: 'STARTED' }])).toEqual([['Started', 1, 's2']])
  })
})

describe('topRoutes', () => {
  it('groups by short origin and destination, most frequent first', () => {
    const b = [
      { fromPlace: 'Coimbatore, TN', toPlace: 'Ooty, TN' },
      { fromPlace: 'Coimbatore', toPlace: 'Ooty' },
      { fromPlace: 'Chennai', toPlace: 'Mahabalipuram' },
    ]
    expect(topRoutes(b)).toEqual([['Coimbatore → Ooty', 2], ['Chennai → Mahabalipuram', 1]])
  })
  it('limits the list', () => {
    const b = Array.from({ length: 8 }, (_, i) => ({ fromPlace: `A${i}`, toPlace: 'B' }))
    expect(topRoutes(b, 3)).toHaveLength(3)
  })
})

describe('bookingHeatmap', () => {
  it('buckets booking creation times into weekday × time-band cells', () => {
    const m = bookingHeatmap([
      { bookingDate: '2026-09-28T07:30:00' }, // Monday, 6–9
      { bookingDate: '2026-09-28T08:10:00' }, // Monday, 6–9
      { bookingDate: '2026-10-04T22:00:00' }, // Sunday, 9–12p
      { bookingDate: '2026-10-04T03:00:00' }, // Sunday, before 6 → counted in 9–12p (night band)
    ])
    expect(m).toHaveLength(7)
    expect(m[0][0]).toBe(2)
    expect(m[6][5]).toBe(2)
  })
  it('ignores bookings without a creation time', () => {
    const m = bookingHeatmap([{ bookingDate: null }, {}])
    expect(m.flat().reduce((a, b) => a + b, 0)).toBe(0)
  })
})

describe('fleetBusy', () => {
  it('counts distinct drivers on a started trip against the whole fleet', () => {
    const bookings = [
      { status: 'STARTED', driverId: 1 }, { status: 'STARTED', driverId: 1 },
      { status: 'STARTED', driverId: 2 }, { status: 'CONFIRMED', driverId: 3 },
    ]
    expect(fleetBusy(bookings, [{}, {}, {}, {}])).toEqual({ busy: 2, total: 4, pct: 0.5 })
  })
  it('is zero for an empty fleet', () => {
    expect(fleetBusy([], [])).toEqual({ busy: 0, total: 0, pct: 0 })
  })
})

describe('waitingForDriver', () => {
  it('counts pending bookings that have no driver yet', () => {
    expect(waitingForDriver([
      { status: 'PENDING' }, { status: 'PENDING', driverId: 4 }, { status: 'CONFIRMED' },
    ])).toBe(1)
  })
})

describe('monthSeries', () => {
  it('maps the monthly series up to the current month', () => {
    const series = { months: [
      { month: 1, totalRevenue: 100, totalTrips: 1 },
      { month: 9, totalRevenue: 900.5, totalTrips: 9 },
      { month: 10, totalRevenue: 0, totalTrips: 0 },
    ] }
    const out = monthSeries(series, TODAY)
    expect(out).toHaveLength(9)
    expect(out[0]).toEqual({ label: 'Jan', revenue: 100, trips: 1 })
    expect(out[8]).toEqual({ label: 'Sep', revenue: 900.5, trips: 9 })
    expect(out[4]).toEqual({ label: 'May', revenue: 0, trips: 0 })
  })
  it('copes with a missing series', () => {
    expect(monthSeries(null, TODAY)).toHaveLength(9)
  })
})

describe('pctChange', () => {
  it('returns the percentage change, or null without a base', () => {
    expect(pctChange(112, 100)).toBeCloseTo(12)
    expect(pctChange(50, 0)).toBeNull()
    expect(pctChange(50, null)).toBeNull()
  })
})

describe('driver metrics', () => {
  const trips = [
    { status: 'COMPLETED', fromDate: '2026-09-30', totalAmount: 1200.4, distanceKm: 40 },
    { status: 'COMPLETED', fromDate: '2026-09-30', totalAmount: 800, distanceKm: 20.5 },
    { status: 'COMPLETED', fromDate: '2026-09-29', totalAmount: 500, distanceKm: 10 },
    { status: 'COMPLETED', fromDate: '2026-09-20', totalAmount: 999, distanceKm: 10 },
    { status: 'CONFIRMED', fromDate: '2026-10-02', totalAmount: 300, id: 'later' },
    { status: 'CONFIRMED', fromDate: '2026-10-01', totalAmount: 300, id: 'sooner' },
    { status: 'PENDING', fromDate: '2026-10-01', totalAmount: 100 },
  ]
  it('sums today', () => {
    expect(todayStats(trips, TODAY)).toEqual({ earned: 2000.4, trips: 2, km: 60.5 })
  })
  it('splits this week into Monday..Sunday', () => {
    const w = earningsThisWeek(trips, TODAY)
    expect(w.map((d) => d.label)).toEqual(['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'])
    expect(w[0].value).toBe(0)
    expect(w[1].value).toBe(500)
    expect(w[2].value).toBeCloseTo(2000.4)
    expect(w[3].value).toBe(0)
  })
  it('measures completion against every assigned trip', () => {
    expect(completionRate(trips)).toBeCloseTo(4 / 6)
    expect(completionRate([])).toBe(0)
  })
  it('picks a started trip first, otherwise the soonest confirmed one', () => {
    expect(nextTrip(trips, TODAY).id).toBe('sooner')
    expect(nextTrip([...trips, { status: 'STARTED', fromDate: '2026-09-30', id: 'now' }], TODAY).id).toBe('now')
    expect(nextTrip([], TODAY)).toBeNull()
  })
  it('splits the trip mix by booking type', () => {
    expect(typeMix([{ bookingType: 'DISTANCE_BASED' }, { bookingType: 'HOUR_BASED' }, { bookingType: 'DISTANCE_BASED' }]))
      .toEqual([['Distance', 2, 's1'], ['Hourly', 1, 's2']])
  })
})

describe('traveller metrics', () => {
  const trips = [
    { status: 'COMPLETED', fromDate: '2026-09-12', totalAmount: 6000.223, travelDays: 2, distanceKm: 500, toPlace: 'Chennai, TN' },
    { status: 'COMPLETED', fromDate: '2026-08-28', totalAmount: 2400, travelDays: 1, distanceKm: 80, toPlace: 'Kanchipuram' },
    { status: 'CANCELLED', fromDate: '2026-08-10', totalAmount: 9999, travelDays: 5, distanceKm: 900, toPlace: 'Madurai' },
    { status: 'CONFIRMED', fromDate: '2026-10-04', totalAmount: 1500, travelDays: 4, distanceKm: 58, toPlace: 'Chennai' },
  ]
  it('sums spend per month for the last six months, skipping cancelled trips', () => {
    const s = spendByMonth(trips, TODAY)
    expect(s.map((m) => m.label)).toEqual(['Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep'])
    expect(s[4].value).toBe(2400)
    expect(s[5].value).toBeCloseTo(6000.223)
  })
  it('groups trips by length', () => {
    expect(tripLengthSegments(trips)).toEqual([['1 day', 1, 's1'], ['2–3 days', 1, 's2'], ['4+ days', 1, 's3']])
  })
  it('counts days until a date', () => {
    expect(daysUntil('2026-10-04', TODAY)).toBe(4)
    expect(daysUntil('2026-09-30', TODAY)).toBe(0)
  })
  it('totals kilometres of trips that were not cancelled', () => {
    expect(totalKm(trips)).toBe(638)
  })
  it('ranks destinations', () => {
    expect(topDestinations(trips)).toEqual([['Chennai', 2], ['Kanchipuram', 1]])
  })
})
