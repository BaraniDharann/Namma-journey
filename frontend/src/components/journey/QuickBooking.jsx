import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { useAuth } from '../../context/AuthContext'
import { createBooking, getPublicPricing } from '../../utils/api'
import PlaceAutocomplete from '../PlaceAutocomplete'
import { EASE } from '../motion/primitives'

const TEMPLE_SHORTCUTS = ['Tirupati Balaji', 'Shirdi Sai Baba', 'Vaishno Devi', 'Kedarnath', 'Badrinath']
const HOURS = [1, 2, 3, 4, 5, 6, 8, 10, 12, 24]

const EMPTY = {
  fromPlace: '', toPlace: '', fromLat: null, fromLon: null, toLat: null, toLon: null,
  fromDate: '', toDate: '', travelMembers: 1, acType: 'AC', bookingHours: 2,
}

export default function QuickBooking() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [qb, setQb] = useState(EMPTY)
  const [pricing, setPricing] = useState({ pricePerHour: 150 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)

  useEffect(() => {
    getPublicPricing().then((res) => { if (res.data) setPricing(res.data) }).catch(() => {})
  }, [])

  const estimate = (pricing.pricePerHour || 150) * qb.bookingHours

  const submit = async () => {
    if (!user) {
      navigate('/signup?redirect=/user/bookings/new')
      return
    }
    if (!qb.fromLat || !qb.toLat) {
      setError('Pick both locations from the dropdown suggestions.')
      return
    }
    if (!qb.fromDate || !qb.toDate) {
      setError('Choose your travel dates.')
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
        fromDate: qb.fromDate, toDate: qb.toDate,
        travelMembers: qb.travelMembers, acType: qb.acType,
        bookingType: 'HOUR_BASED', bookingHours: qb.bookingHours,
      })
      setSuccess(res.data)
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

  const today = new Date().toISOString().split('T')[0]

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
              🎉
            </motion.div>
            <h3>Your ride is booked</h3>
            <p className="qb-done-sub">A verified driver will be assigned shortly.</p>
            <div className="qb-receipt">
              {[
                ['From', success.fromPlace],
                ['To', success.toPlace],
                ['Hours', `${success.bookingHours}h`],
                ['Amount', `₹${success.totalAmount?.toLocaleString('en-IN')}`],
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
                onClick={() => { setSuccess(null); setQb(EMPTY) }}
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
                <span className="qb-eyebrow">Plan your leg of the journey</span>
                <h3>Quick booking</h3>
              </div>
              <div className="qb-price">
                <b>₹{estimate.toLocaleString('en-IN')}</b>
                <span>est. {qb.bookingHours}h</span>
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
                  <span className="qb-error-inner">⚠️ {error}</span>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="qb-fields">
              <div className="qb-field">
                <label>Pickup</label>
                <PlaceAutocomplete
                  value={qb.fromPlace}
                  onChange={({ name, lat, lon }) => setQb((p) => ({ ...p, fromPlace: name, fromLat: lat, fromLon: lon }))}
                  placeholder="Your city, town or area…"
                />
              </div>

              <div className="qb-field">
                <label>Destination</label>
                <PlaceAutocomplete
                  value={qb.toPlace}
                  onChange={({ name, lat, lon }) => setQb((p) => ({ ...p, toPlace: name, toLat: lat, toLon: lon }))}
                  placeholder="Temple, city or destination…"
                />
                <div className="qb-chips qb-chips-row">
                  {TEMPLE_SHORTCUTS.map((t) => (
                    <button
                      key={t}
                      type="button"
                      className={`qb-chip${qb.toPlace === t ? ' is-on' : ''}`}
                      onClick={() => setQb((p) => ({ ...p, toPlace: t, toLat: null, toLon: null }))}
                    >
                      🛕 {t}
                    </button>
                  ))}
                </div>
              </div>

              <div className="qb-field">
                <label>Hours</label>
                <div className="qb-chips qb-chips-row">
                  {HOURS.map((h) => (
                    <button
                      key={h}
                      type="button"
                      className={`qb-chip qb-chip-hour${qb.bookingHours === h ? ' is-on' : ''}`}
                      onClick={() => setQb((p) => ({ ...p, bookingHours: h }))}
                    >
                      {h}h
                    </button>
                  ))}
                </div>
              </div>

              <div className="qb-row">
                <div className="qb-field">
                  <label>From date</label>
                  <input
                    type="date"
                    className="qb-input"
                    value={qb.fromDate}
                    min={today}
                    onChange={(e) => setQb((p) => ({ ...p, fromDate: e.target.value }))}
                  />
                </div>
                <div className="qb-field">
                  <label>To date</label>
                  <input
                    type="date"
                    className="qb-input"
                    value={qb.toDate}
                    min={qb.fromDate || today}
                    onChange={(e) => setQb((p) => ({ ...p, toDate: e.target.value }))}
                  />
                </div>
              </div>

              <div className="qb-row">
                <div className="qb-field">
                  <label>Travellers</label>
                  <select
                    className="qb-input"
                    value={qb.travelMembers}
                    onChange={(e) => setQb((p) => ({ ...p, travelMembers: Number(e.target.value) }))}
                  >
                    {Array.from({ length: 20 }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>{n} {n > 1 ? 'people' : 'person'}</option>
                    ))}
                  </select>
                </div>
                <div className="qb-field">
                  <label>Vehicle</label>
                  <div className="qb-toggle">
                    {['AC', 'NON_AC'].map((type) => (
                      <button
                        key={type}
                        type="button"
                        className={`qb-toggle-btn${qb.acType === type ? ' is-on' : ''}`}
                        onClick={() => setQb((p) => ({ ...p, acType: type }))}
                      >
                        {type === 'AC' ? '❄️ AC' : '🌬️ Non-AC'}
                      </button>
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
                {loading ? 'Booking…' : '🚀 Confirm booking'}
              </motion.button>
              <p className="qb-note">Free to sign up · No hidden charges · Cancel anytime</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
