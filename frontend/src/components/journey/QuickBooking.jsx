import React, { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { useAuth } from '../../context/AuthContext'
import { createBooking, getPublicPricing } from '../../utils/api'
import PlaceAutocomplete from '../PlaceAutocomplete'
import { EASE } from '../motion/primitives'
import Icon from '../dash/Icon'
import { useCelebrate } from '../celebrate/Celebration'

// Shortcuts carry their coordinates, so picking one is a complete destination.
const TEMPLES = [
  { name: 'Tirupati Balaji', short: 'Tirupati', lat: 13.6833, lon: 79.3474 },
  { name: 'Shirdi Sai Baba', short: 'Shirdi', lat: 19.7667, lon: 74.4771 },
  { name: 'Vaishno Devi', short: 'Vaishno Devi', lat: 33.0308, lon: 74.9490 },
  { name: 'Kedarnath', short: 'Kedarnath', lat: 30.7352, lon: 79.0669 },
]

const EMPTY = {
  fromPlace: '', toPlace: '', fromLat: null, fromLon: null, toLat: null, toLon: null,
  travelMembers: 1, acType: 'AC', bookingHours: 2,
}

/** Local calendar date n days from today, as yyyy-mm-dd (not UTC, which is yesterday before 5:30 IST). */
function dayAfter(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const shortDate = (iso) => new Date(iso + 'T00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

/** A compact − value + control, so a number never needs a dropdown or a row of chips. */
function Stepper({ label, value, min, max, unit = '', onChange }) {
  const id = `qb-${label.toLowerCase()}`
  return (
    <div className="qb-field">
      <span className="qb-label" id={id}>{label}</span>
      <div className="qb-step" role="group" aria-labelledby={id}>
        <button type="button" aria-label={`Fewer ${label.toLowerCase()}`} disabled={value <= min} onClick={() => onChange(Math.max(min, value - 1))}>−</button>
        <output aria-live="polite">{value}{unit && <small>{unit}</small>}</output>
        <button type="button" aria-label={`More ${label.toLowerCase()}`} disabled={value >= max} onClick={() => onChange(Math.min(max, value + 1))}>+</button>
      </div>
    </div>
  )
}

export default function QuickBooking() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const celebrate = useCelebrate()
  const [qb, setQb] = useState(EMPTY)
  const [pricing, setPricing] = useState({ pricePerHour: 150 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)
  // An hourly ride happens on one day: today unless they say otherwise.
  const [when, setWhen] = useState('today')
  const [laterDate, setLaterDate] = useState('')
  const dateRef = useRef(null)
  const travelDate = when === 'today' ? dayAfter(0) : when === 'tomorrow' ? dayAfter(1) : laterDate

  const openPicker = () => {
    const el = dateRef.current
    if (!el) return
    try { el.showPicker() } catch { el.focus() }
    if (laterDate) setWhen('later')
  }

  useEffect(() => {
    getPublicPricing().then((res) => { if (res.data) setPricing(res.data) }).catch(() => {})
  }, [])

  const rate = pricing.pricePerHour || 150
  const estimate = rate * qb.bookingHours

  const submit = async () => {
    if (!user) {
      navigate('/signup?redirect=/user/bookings/new')
      return
    }
    if (!qb.fromLat || !qb.toLat) {
      setError('Pick both locations from the dropdown suggestions.')
      return
    }
    if (!travelDate) {
      setError('Pick the day you want to travel.')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await createBooking(user.userId, {
        userName: user.name,
        userPhone: user.mobile || '',
        fromPlace: qb.fromPlace, toPlace: qb.toPlace,
        fromLat: qb.fromLat, fromLon: qb.fromLon,
        toLat: qb.toLat, toLon: qb.toLon,
        fromDate: travelDate, toDate: travelDate,
        travelMembers: qb.travelMembers, acType: qb.acType,
        bookingType: 'HOUR_BASED', bookingHours: qb.bookingHours,
      })
      setSuccess(res.data)
      celebrate({
        title: 'Your ride is booked',
        message: `${res.data?.fromPlace || qb.fromPlace} → ${res.data?.toPlace || qb.toPlace} · ${qb.bookingHours}h. A verified driver will be assigned shortly.`,
        actionLabel: 'View bookings',
        onAction: () => navigate('/user/bookings'),
      })
    } catch (err) {
      setError(
        err.response?.data?.message ||
        Object.values(err.response?.data || {}).join(', ') ||
        'Booking failed. Please try again.'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="qb">
      <div className="qb-glow" aria-hidden />
      <AnimatePresence mode="wait">
        {success ? (
          <motion.div
            key="done"
            initial={{ opacity: 0, scale: 0.94 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.94 }}
            transition={{ duration: 0.5, ease: EASE }}
            className="qb-done"
          >
            <motion.div
              className="qb-done-icon"
              initial={{ scale: 0, rotate: -40 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ type: 'spring', stiffness: 220, damping: 14 }}
            >
              <Icon name="check" size={30} />
            </motion.div>
            <h3>Your ride is booked</h3>
            <p className="qb-done-sub">A verified driver will be assigned shortly.</p>
            <div className="qb-receipt">
              {[
                ['From', success.fromPlace],
                ['To', success.toPlace],
                ['Hours', `${success.bookingHours}h`],
                ['Amount', `₹${Math.round(Number(success.totalAmount) || 0).toLocaleString('en-IN')}`],
              ].map(([k, v], i) => (
                <motion.div
                  key={k}
                  className="qb-receipt-row"
                  initial={{ opacity: 0, x: -14 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.15 + i * 0.08, duration: 0.4, ease: EASE }}
                >
                  <span>{k}</span>
                  <b>{v}</b>
                </motion.div>
              ))}
            </div>
            <div className="qb-done-actions">
              <button type="button" className="qb-submit" onClick={() => navigate('/user/bookings')}>
                View bookings
              </button>
              <button
                type="button"
                className="qb-reset"
                onClick={() => { setSuccess(null); setQb(EMPTY); setWhen('today'); setLaterDate('') }}
              >
                New booking
              </button>
            </div>
          </motion.div>
        ) : (
          <motion.div
            key="form"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.35 }}
          >
            <div className="qb-head">
              <div>
                <h3>Quick booking</h3>
                <p className="qb-sub">An hourly ride with a verified driver</p>
              </div>
              <div className="qb-price" aria-live="polite">
                <b>₹{estimate.toLocaleString('en-IN')}</b>
                <span>{qb.bookingHours} h × ₹{rate}</span>
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
              {/* the route: pickup and destination joined by one line, like a trip */}
              <div className="qb-route">
                <div className="qb-stop">
                  <span className="qb-dot" aria-hidden />
                  <PlaceAutocomplete
                    value={qb.fromPlace}
                    onChange={({ name, lat, lon }) => setQb((p) => ({ ...p, fromPlace: name, fromLat: lat, fromLon: lon }))}
                    placeholder="Pickup: city, town or area"
                  />
                </div>
                <div className="qb-stop">
                  <span className="qb-dot qb-dot-end" aria-hidden><Icon name="pin" size={13} weight="fill" /></span>
                  <PlaceAutocomplete
                    value={qb.toPlace}
                    onChange={({ name, lat, lon }) => setQb((p) => ({ ...p, toPlace: name, toLat: lat, toLon: lon }))}
                    placeholder="Where to? Temple, city or place"
                  />
                </div>
              </div>
              <div className="qb-quick" aria-label="Popular destinations">
                {TEMPLES.map((t) => (
                  <button
                    key={t.name}
                    type="button"
                    className={`qb-chip${qb.toPlace === t.name ? ' is-on' : ''}`}
                    onClick={() => setQb((p) => ({ ...p, toPlace: t.name, toLat: t.lat, toLon: t.lon }))}
                  >
                    {t.short}
                  </button>
                ))}
              </div>

              <div className="qb-field">
                <span className="qb-label" id="qb-when">When</span>
                <div className="qb-seg qb-seg-3" role="group" aria-labelledby="qb-when">
                  <button type="button" aria-pressed={when === 'today'} onClick={() => setWhen('today')}>Today</button>
                  <button type="button" aria-pressed={when === 'tomorrow'} onClick={() => setWhen('tomorrow')}>Tomorrow</button>
                  <button type="button" aria-pressed={when === 'later'} onClick={openPicker} className="qb-seg-date">
                    <Icon name="calendar" size={15} />
                    {when === 'later' && laterDate ? shortDate(laterDate) : 'Later'}
                    <input
                      ref={dateRef}
                      type="date"
                      className="qb-date-native"
                      tabIndex={-1}
                      aria-label="Pick a travel date"
                      min={dayAfter(1)}
                      value={laterDate}
                      onChange={(e) => { if (e.target.value) { setLaterDate(e.target.value); setWhen('later') } }}
                    />
                  </button>
                </div>
              </div>

              <div className="qb-trio">
                <Stepper label="Hours" value={qb.bookingHours} min={1} max={24} unit="h"
                  onChange={(v) => setQb((p) => ({ ...p, bookingHours: v }))} />
                <Stepper label="People" value={qb.travelMembers} min={1} max={20}
                  onChange={(v) => setQb((p) => ({ ...p, travelMembers: v }))} />
                <div className="qb-field">
                  <span className="qb-label" id="qb-ac">Car</span>
                  <div className="qb-seg" role="group" aria-labelledby="qb-ac">
                    {[['AC', 'AC'], ['NON_AC', 'Non-AC']].map(([v, l]) => (
                      <button key={v} type="button" aria-pressed={qb.acType === v} onClick={() => setQb((p) => ({ ...p, acType: v }))}>{l}</button>
                    ))}
                  </div>
                </div>
              </div>

              <motion.button
                type="button"
                className="qb-submit"
                disabled={loading}
                onClick={submit}
                whileHover={{ scale: loading ? 1 : 1.015 }}
                whileTap={{ scale: loading ? 1 : 0.985 }}
              >
                {loading ? 'Booking…' : <>Confirm booking <Icon name="arrow" size={17} /></>}
              </motion.button>
              <p className="qb-note">No hidden charges · Cancel anytime</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
