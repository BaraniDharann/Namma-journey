import React, { useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { driverBookingAction } from '../../utils/api'
import { inr, shortPlace } from '../../dash/metrics'
import { modeLabel, vehicleIcon, vehicleLabel } from '../../dash/vehicles'
import useDriverDispatch, { DRIVER_TRIPS_EVENT } from '../../hooks/useDriverDispatch'

/**
 * Ride Now for drivers, on every driver page: keeps their position reported while online, and
 * puts a nearby ride in front of them the moment it is offered, with the seconds they have left.
 */
export default function DriverDispatch({ driverId, children }) {
  const { online, offer, locationIssue, refresh } = useDriverDispatch(driverId)

  return (
    <>
      {online && locationIssue && (
        <div className="rn-notice" role="status"><Icon name="pin" size={16} />{locationIssue}</div>
      )}
      {children}
      {offer && (
        <RideOfferCard
          key={offer.booking.bookingId}
          driverId={driverId}
          booking={offer.booking}
          deadline={offer.deadline}
          onDone={() => { refresh(); window.dispatchEvent(new Event(DRIVER_TRIPS_EVENT)) }}
        />
      )}
    </>
  )
}

function RideOfferCard({ driverId, booking, deadline, onDone }) {
  // Read the clock once, when the card opens: the bar shrinks from full to empty over what was left.
  const [total] = useState(() => Math.max(1, Math.round((deadline - Date.now()) / 1000)))
  const [left, setLeft] = useState(total)
  const [busy, setBusy] = useState(null)
  const acceptRef = useRef(null)

  useEffect(() => {
    acceptRef.current?.focus()
    const tick = setInterval(() => setLeft(Math.max(0, Math.round((deadline - Date.now()) / 1000))), 250)
    return () => clearInterval(tick)
  }, [deadline])

  // Out of time: the server has moved the ride on, so drop the card rather than offer a dead button.
  const expiredRef = useRef(false)
  useEffect(() => {
    if (left === 0 && !busy && !expiredRef.current) {
      expiredRef.current = true
      onDone()
    }
  }, [left, busy, onDone])

  const act = async (action) => {
    setBusy(action)
    try {
      await driverBookingAction(driverId, booking.bookingId, action)
      if (action === 'ACCEPT') toast.success(`Ride accepted. Head to ${shortPlace(booking.fromPlace)}.`)
    } catch { /* surfaced by the api toast, e.g. "This ride offer has expired" */ }
    onDone()
  }

  const km = (v) => (v == null ? null : v < 1 ? `${Math.round(v * 1000)} m` : `${v.toFixed(1)} km`)
  const pickupAway = km(booking.offerDistanceKm)

  return (
    <div className="rn-overlay">
      <div className="rn-card" role="alertdialog" aria-modal="true" aria-labelledby="rn-title" aria-describedby="rn-left">
        <div className="rn-head">
          <div style={{ minWidth: 0 }}>
            <div className="rn-kicker">New {modeLabel(booking)} nearby</div>
            <h2 id="rn-title" className="rn-title">Pickup {pickupAway ? `${pickupAway} away` : 'near you'}</h2>
          </div>
          <div className="rn-fare">{inr(booking.totalAmount)}<small>fare</small></div>
        </div>
        <div className="rn-body">
          <div className="pc-route">
            <i /><div style={{ overflowWrap: 'anywhere' }}>{shortPlace(booking.fromPlace)}<small>Pickup · {booking.userName || 'Traveller'}</small></div>
            <span className="pc-route-ln" /><span />
            <i className="is-end" /><div style={{ overflowWrap: 'anywhere' }}>{shortPlace(booking.toPlace)}<small>Drop</small></div>
          </div>
          <div className="rn-facts">
            {booking.distanceKm != null && <span className="rn-fact"><Icon name="route" size={14} />{km(booking.distanceKm)} trip</span>}
            <span className="rn-fact"><Icon name="users" size={14} />{booking.travelMembers || 1} passenger{(booking.travelMembers || 1) === 1 ? '' : 's'}</span>
            <span className="rn-fact"><Icon name={vehicleIcon(booking.vehicleType)} size={14} />{vehicleLabel(booking.vehicleType)}</span>
          </div>
        </div>
        <div className={`rn-timer${left <= 10 ? ' is-low' : ''}`} aria-hidden="true">
          <i style={{ transform: `scaleX(${left / total})` }} />
        </div>
        <p id="rn-left" className="rn-left" aria-live="off">{left}s to accept</p>
        <div className="rn-actions">
          <button type="button" className="pc-btn pc-btn-ghost" disabled={!!busy} onClick={() => act('REJECT')}>
            {busy === 'REJECT' ? 'Declining…' : 'Decline'}
          </button>
          <button ref={acceptRef} type="button" className="pc-btn" disabled={!!busy} onClick={() => act('ACCEPT')}>
            <Icon name="check" />{busy === 'ACCEPT' ? 'Accepting…' : 'Accept ride'}
          </button>
        </div>
      </div>
    </div>
  )
}
