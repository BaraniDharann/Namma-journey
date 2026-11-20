import { describe, it, expect } from 'vitest'
import { describeSuccess } from './actionSuccess'

describe('describeSuccess: which writes show the success popup', () => {
  it('names the common actions of every role', () => {
    expect(describeSuccess('post', '/user/abc/bookings').title).toBe('Trip booked')
    expect(describeSuccess('delete', '/user/abc/bookings/b1').title).toBe('Booking cancelled')
    expect(describeSuccess('post', '/user/abc/bookings/b1/payment/razorpay/verify').title).toBe('Payment successful')
    expect(describeSuccess('post', '/owner/bookings/b1/assign-driver').title).toBe('Driver assigned')
    expect(describeSuccess('post', '/admin/owners/3/approve').title).toBe('Owner approved')
    expect(describeSuccess('post', '/driver/7/bookings/b1/start-trip').title).toBe('Trip started')
  })

  it('reads the driver action from the body', () => {
    expect(describeSuccess('post', '/driver/7/bookings/b1/action', '{"action":"ACCEPT"}').title).toBe('Ride accepted')
    expect(describeSuccess('post', '/driver/7/bookings/b1/action', { action: 'REJECT' }).tone).toBe('calm')
  })

  it('a payment sent but not yet confirmed is calm, not a tick', () => {
    expect(describeSuccess('post', '/user/abc/bookings/b1/payment').tone).toBe('calm')
  })

  it('stays quiet for reads, logins and background calls', () => {
    expect(describeSuccess('get', '/user/abc/bookings')).toBeNull()
    expect(describeSuccess('post', '/auth/user/login')).toBeNull()
    expect(describeSuccess('put', '/notifications/5/read')).toBeNull()
    expect(describeSuccess('put', '/driver/7/presence')).toBeNull()
    expect(describeSuccess('post', '/driver/location/update')).toBeNull()
    expect(describeSuccess('post', '/user/abc/bookings/b1/payment/razorpay/order')).toBeNull()
  })

  it('ignores the /api prefix, the host and the query', () => {
    expect(describeSuccess('post', 'https://nammajourneys.site/api/user/abc/bookings?x=1').title).toBe('Trip booked')
  })

  it('falls back to a plain word for anything not listed', () => {
    expect(describeSuccess('put', '/owner/something-new').title).toBe('Saved')
    expect(describeSuccess('delete', '/owner/something-new/1').title).toBe('Deleted')
    expect(describeSuccess('post', '/owner/something-new').title).toBe('Done')
  })
})
