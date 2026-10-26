import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { useAuth } from '../../context/AuthContext'
import PlaceAutocomplete from '../PlaceAutocomplete'
import { EASE } from '../motion/primitives'
import Icon from '../dash/Icon'
import { VEHICLES, VEHICLE_ORDER, vehicleLabel } from '../../dash/vehicles'

/**
 * The home page's City ride booking. The fare needs a signed-in rider, so this
 * only collects the route and the vehicle and hands them to the full booking
 * page, which quotes the fare and books.
 *
 * layout="mini" is a small card for the top right of wide screens, in the sky
 * above the street, so the bus and the people stay in view; the default is the
 * card that sits under the story on smaller screens.
 */
export default function CityBooking({ layout = 'card' }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [route, setRoute] = useState({ fromPlace: '', toPlace: '', fromLat: null, fromLon: null, toLat: null, toLon: null })
  const [vehicle, setVehicle] = useState('BIKE')
  const [error, setError] = useState('')

  const submit = () => {
    if (!route.fromLat || !route.toLat) {
      setError('Pick both places from the dropdown suggestions.')
      return
    }
    const params = new URLSearchParams({
      mode: 'city', vehicle,
      from: route.fromPlace, to: route.toPlace,
      fromLat: route.fromLat, fromLon: route.fromLon, toLat: route.toLat, toLon: route.toLon,
    })
    navigate(user ? `/user/bookings/new?${params}` : `/signup?redirect=/user/bookings/new&${params}`)
  }

  const setFrom = ({ name, lat, lon }) => { setError(''); setRoute((r) => ({ ...r, fromPlace: name, fromLat: lat, fromLon: lon })) }
  const setTo = ({ name, lat, lon }) => { setError(''); setRoute((r) => ({ ...r, toPlace: name, toLat: lat, toLon: lon })) }

  if (layout === 'mini') {
    return (
      <div className="cb-mini">
        <div className="cb-mini-head">
          <b>Ride now</b>
          <span>The nearest driver comes to you</span>
        </div>
        <div className="qb-route cb-mini-route">
          <div className="qb-stop">
            <span className="qb-dot" aria-hidden />
            <PlaceAutocomplete value={route.fromPlace} onChange={setFrom} placeholder="Pickup: where are you now?" />
          </div>
          <div className="qb-stop">
            <span className="qb-dot qb-dot-end" aria-hidden><Icon name="pin" size={13} weight="fill" /></span>
            <PlaceAutocomplete value={route.toPlace} onChange={setTo} placeholder="Drop: where to?" />
          </div>
        </div>
        <div className="cb-mini-rides" role="radiogroup" aria-label="Ride with">
          {VEHICLE_ORDER.map((type) => (
            <button key={type} type="button" role="radio" aria-checked={vehicle === type} onClick={() => setVehicle(type)}>
              <Icon name={VEHICLES[type].icon} size={16} />
              {VEHICLES[type].label}
            </button>
          ))}
        </div>
        <AnimatePresence>
          {error && (
            <motion.p
              className="cb-mini-err"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.25, ease: EASE }}
            >
              <Icon name="alert" size={14} /> {error}
            </motion.p>
          )}
        </AnimatePresence>
        <motion.button type="button" className="cb-mini-go" onClick={submit} whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.985 }}>
          Book {vehicleLabel(vehicle).toLowerCase()} now<Icon name="arrow" size={16} />
        </motion.button>
      </div>
    )
  }

  return (
    <div className="qb">
      <div className="qb-glow" aria-hidden />
      <div className="qb-head">
        <div>
          <h3>Ride now</h3>
          <p className="qb-sub">The nearest driver comes to you</p>
        </div>
      </div>

      <AnimatePresence>
        {error && (
          <motion.div
            className="qb-error"
            initial={{ opacity: 0, height: 0, marginBottom: 0 }}
            animate={{ opacity: 1, height: 'auto', marginBottom: 12 }}
            exit={{ opacity: 0, height: 0, marginBottom: 0 }}
            transition={{ duration: 0.3, ease: EASE }}
          >
            <span className="qb-error-inner"><Icon name="alert" size={16} /> {error}</span>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="qb-fields">
        <div className="qb-route">
          <div className="qb-stop">
            <span className="qb-dot" aria-hidden />
            <PlaceAutocomplete
              value={route.fromPlace}
              onChange={setFrom}
              placeholder="Pickup: where are you now?"
            />
          </div>
          <div className="qb-stop">
            <span className="qb-dot qb-dot-end" aria-hidden><Icon name="pin" size={13} weight="fill" /></span>
            <PlaceAutocomplete
              value={route.toPlace}
              onChange={setTo}
              placeholder="Drop: where are you going?"
            />
          </div>
        </div>

        <div className="qb-field">
          <span className="qb-label" id="cb-vehicle">Ride with</span>
          <div className="cb-rides" role="radiogroup" aria-labelledby="cb-vehicle">
            {VEHICLE_ORDER.map((type) => (
              <button
                key={type}
                type="button"
                role="radio"
                aria-checked={vehicle === type}
                className="cb-ride"
                onClick={() => setVehicle(type)}
              >
                <Icon name={VEHICLES[type].icon} size={22} />
                <b>{VEHICLES[type].label}</b>
                <small>{VEHICLES[type].blurb}</small>
              </button>
            ))}
          </div>
        </div>

        <motion.button
          type="button"
          className="qb-submit"
          onClick={submit}
          whileHover={{ scale: 1.015 }}
          whileTap={{ scale: 0.985 }}
        >
          Book {vehicleLabel(vehicle).toLowerCase()} now <Icon name="arrow" size={17} />
        </motion.button>
        <p className="qb-note">Verified drivers · Pay your driver at the end</p>
      </div>
    </div>
  )
}
