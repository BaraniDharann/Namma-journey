import { describe, it, expect } from 'vitest'
import { pickActiveRide, describeRide, todayIso, shortName, stepIndex } from './activeRide'

const NOW = new Date(2026, 10, 16, 10, 0) // 16 Nov 2026, local
const driver = { name: 'Ravi Kumar', vehicleModel: 'Swift', vehicleNumber: 'TN09AB1234' }

describe('pickActiveRide', () => {
  it('prefers a started trip over everything else', () => {
    const started = { bookingId: 's', status: 'STARTED' }
    const coming = { bookingId: 'c', status: 'CONFIRMED', driver, dispatchMode: 'RIDE_NOW' }
    expect(pickActiveRide([coming, started], NOW)).toBe(started)
  })

  it('shows a confirmed ride now / city ride with a driver', () => {
    const city = { bookingId: 'c', status: 'CONFIRMED', driver, serviceMode: 'CITY', fromDate: '2026-12-01' }
    expect(pickActiveRide([city], NOW)).toBe(city)
  })

  it('shows a confirmed scheduled trip only on its day', () => {
    const later = { bookingId: 'l', status: 'CONFIRMED', driver, dispatchMode: 'SCHEDULED', fromDate: '2026-11-20' }
    const today = { ...later, bookingId: 't', fromDate: todayIso(NOW) }
    expect(pickActiveRide([later], NOW)).toBeNull()
    expect(pickActiveRide([later, today], NOW)).toBe(today)
  })

  it('ignores a confirmed booking with no driver yet', () => {
    expect(pickActiveRide([{ status: 'CONFIRMED', dispatchMode: 'RIDE_NOW' }], NOW)).toBeNull()
  })

  it('shows a Ride Now still searching, but not one nobody took', () => {
    const searching = { status: 'PENDING', dispatchMode: 'RIDE_NOW', dispatchState: 'SEARCHING' }
    expect(pickActiveRide([searching], NOW)).toBe(searching)
    expect(pickActiveRide([{ ...searching, dispatchState: 'NO_DRIVER' }], NOW)).toBeNull()
  })

  it('copes with no list', () => {
    expect(pickActiveRide(null, NOW)).toBeNull()
  })
})

describe('describeRide', () => {
  const coming = { status: 'CONFIRMED', driver, toPlace: 'Anna Nagar, Chennai' }

  it('says how far away the driver is when the distance is known', () => {
    const d = describeRide(coming, 2.04)
    expect(d.step).toBe('coming')
    expect(d.title).toBe('Ravi is on the way')
    expect(d.text).toBe('2.0 km away · Swift · TN09AB1234')
    expect(describeRide(coming, 0.42).text).toMatch(/^400 m away/)
  })

  it('says the driver has arrived when close to the pickup', () => {
    expect(describeRide(coming, 0.05).step).toBe('arrived')
  })

  it('falls back to "assigned" with no location yet', () => {
    expect(describeRide(coming).step).toBe('assigned')
  })

  it('describes a trip in progress', () => {
    const d = describeRide({ ...coming, status: 'STARTED' })
    expect(d.step).toBe('started')
    expect(d.title).toBe('On the way to Anna Nagar')
  })

  it('describes the search', () => {
    expect(describeRide({ status: 'PENDING', dispatchState: 'OFFERED' }).tone).toBe('search')
  })
})

describe('helpers', () => {
  it('shortens a place and orders steps', () => {
    expect(shortName('Anna Nagar, Chennai')).toBe('Anna Nagar')
    expect(stepIndex('started')).toBeGreaterThan(stepIndex('arrived'))
  })
})
