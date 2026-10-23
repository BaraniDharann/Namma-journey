import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from './dash/Icon'
import { getBookingById } from '../utils/api'
import { vehicleIcon, vehicleLabel } from '../dash/vehicles'

const POLL_MS = 3000

/** What the traveller is told at each point of the Ride Now search. */
function describe(b) {
  if (b.status === 'CANCELLED') return { done: true, title: 'Ride cancelled', text: 'This ride was cancelled.' }
  if (b.driver && (b.status === 'CONFIRMED' || b.status === 'STARTED')) {
    const plate = [b.driver.vehicleModel, b.driver.vehicleNumber].filter(Boolean).join(' · ')
    return {
      done: true,
      title: `${b.driver.name} is coming to pick you up`,
      text: `${vehicleLabel(b.driver.vehicleType || b.vehicleType)}${plate ? ` — ${plate}` : ''}. Look for this vehicle at your pickup.`,
    }
  }
  if (b.dispatchState === 'NO_DRIVER') {
    return { done: true, title: 'No driver nearby yet', text: "Every driver near your pickup is busy. Our team is assigning one by hand, and we'll notify you." }
  }
  if (b.dispatchState === 'OFFERED') {
    return { done: false, title: 'A driver nearby is looking at your ride', text: 'Waiting for them to accept. If they can’t, the next nearest driver gets it.' }
  }
  return { done: false, title: 'Finding a driver near you', text: 'Searching close to your pickup first, then a little further out.' }
}

/**
 * Live status of a Ride Now booking, polled until the search settles: a driver accepted, nobody
 * nearby was free (the owner takes over), or the ride was cancelled.
 */
export default function RideSearchStatus({ userId, booking: initial }) {
  const [booking, setBooking] = useState(initial)
  const state = describe(booking)

  useEffect(() => {
    if (state.done) return
    const id = setInterval(() => {
      getBookingById(userId, initial.bookingId).then((r) => { if (r.data) setBooking(r.data) }).catch(() => {})
    }, POLL_MS)
    return () => clearInterval(id)
  }, [userId, initial.bookingId, state.done])

  return (
    <div>
      <div className="rn-search" role="status" aria-live="polite">
        <span className={`rn-radar${state.done ? ' is-done' : ''}`}>
          <Icon name={booking.driver ? 'check' : vehicleIcon(booking.vehicleType)} size={24} />
        </span>
        <div style={{ minWidth: 0 }}>
          <b>{state.title}</b>
          <small>{state.text}</small>
        </div>
      </div>
      {booking.driver && booking.driver.mobile && (
        <div className="pc-chips" style={{ marginTop: 14 }}>
          <a className="pc-btn pc-btn-sm" href={`tel:${booking.driver.mobile}`}><Icon name="phone" size={15} />Call {booking.driver.name?.split(' ')[0] || 'driver'}</a>
          <Link className="pc-btn pc-btn-ghost pc-btn-sm" to="/user/bookings"><Icon name="calendar" size={15} />My Bookings</Link>
        </div>
      )}
    </div>
  )
}
