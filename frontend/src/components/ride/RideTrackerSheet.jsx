import { BlurredPhoto } from '../PrivateImage'
import React, { useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import LiveTrackingMap from '../LiveTrackingMap'
import { vehicleLabel } from '../../dash/vehicles'

/**
 * The live map for one ride: full screen on a phone, a centred panel on wider screens. Shows the
 * status line from the ride card, the driver and car, a call button and the moving car on the map.
 */
export default function RideTrackerSheet({ booking, info, onClose }) {
  const closeRef = useRef(null)
  const d = booking.driver

  useEffect(() => {
    closeRef.current?.focus()
    toast.dismiss() // a status toast would sit over the close button on a phone
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    const { overflow } = document.body.style
    document.body.style.overflow = 'hidden'
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = overflow }
  }, [onClose])

  return (
    <div className="ar-scrim" onClick={onClose}>
      <motion.div
        className="ar-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ar-sheet-title"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, y: 60 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 340, damping: 32 }}
      >
        <header className="ar-sheet-hd">
          <span className={`ar-badge is-${info.tone}`} aria-hidden="true"><Icon name="nav" size={20} /></span>
          <div className="ar-copy">
            <b id="ar-sheet-title">{info.title}</b>
            <small>{info.text}</small>
          </div>
          <button ref={closeRef} type="button" className="ar-x" onClick={onClose} aria-label="Close live map">
            <Icon name="x" size={18} />
          </button>
        </header>

        <div className="ar-sheet-map">
          <LiveTrackingMap
            bookingId={booking.bookingId}
            fromLat={booking.fromLat}
            fromLon={booking.fromLon}
            toLat={booking.toLat}
            toLon={booking.toLon}
            fromPlace={booking.fromPlace}
            toPlace={booking.toPlace}
            travelMembers={booking.travelMembers}
            isDriver={false}
            mapHeight="min(440px, 52vh)"
          />
        </div>

        {d && (
          <footer className="ar-driver">
            <span className="ar-driver-av" aria-hidden="true">
              {d.photo ? <BlurredPhoto src={d.photo} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} /> : String(d.name || '?').charAt(0).toUpperCase()}
            </span>
            <span className="ar-copy">
              <b>{d.name}</b>
              <small>{[vehicleLabel(d.vehicleType || booking.vehicleType), d.vehicleModel, d.vehicleNumber].filter(Boolean).join(' · ')}</small>
            </span>
            {d.mobile && (
              <a className="pc-btn pc-btn-teal pc-btn-sm" href={`tel:${d.mobile}`}><Icon name="phone" size={15} />Call</a>
            )}
          </footer>
        )}
      </motion.div>
    </div>
  )
}
