import React, { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion, useReducedMotion, useScroll, useSpring } from 'framer-motion'
import Icon from '../components/dash/Icon'
import { getPublicCityFares } from '../utils/api'
import { VEHICLES, VEHICLE_ORDER } from '../dash/vehicles'
import { SectionHead, rise } from './sectionParts'

/**
 * The home page below the hero when City ride is chosen: the same rhythm as the Trip page, told
 * for a short hop across town. Every claim here is something the app does today: the dispatch
 * round every 2 s, the widening 1/3/5 km search, the fare quoted before booking, cash or UPI at
 * the drop. Rates come from the owner's live City fares.
 */

const DEFAULT_MAX_KM = 50

/** The live rate card, or null while it loads; { rates: [] } when it could not be read. */
export function useCityFares() {
  const [card, setCard] = useState(null)
  useEffect(() => {
    let live = true
    getPublicCityFares()
      .then((res) => live && setCard(res.data || { rates: [] }))
      .catch(() => live && setCard({ rates: [] }))
    return () => { live = false }
  }, [])
  return card
}

const rupees = (v) => `₹${Number(v).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`

/* ── 1. From tap to drop: the ride as it happens ── */

const RIDE_STAGES = [
  { icon: 'search', title: 'Tap book', desc: 'Pickup, drop, bike, auto or car. The fare is on screen before you confirm.' },
  { icon: 'nav', title: 'Matched', desc: 'Every two seconds we pair waiting riders with the nearest free drivers.' },
  { icon: 'clock', title: 'On the way', desc: 'Watch your driver come to the pickup on the live map.' },
  { icon: 'route', title: 'Riding', desc: 'The map stays live until the drop, so nobody has to ask where you are.' },
  { icon: 'wallet', title: 'Drop and pay', desc: 'Cash or UPI to the driver at the end. Then rate the ride.' },
]

export function RideTimeline() {
  const host = useRef(null)
  const reduced = useReducedMotion()
  const { scrollYProgress } = useScroll({ target: host, offset: ['start 80%', 'end 60%'] })
  const progress = useSpring(scrollYProgress, { stiffness: 120, damping: 24, mass: 0.4 })

  return (
    <section ref={host} className="ws-sec ct-ride" aria-labelledby="ct-ride-title">
      <div className="ws-wrap">
        <div className="ct-ride-head">
          <h2 id="ct-ride-title">From tap to drop</h2>
          <p>Five moments, usually a few minutes apart.</p>
        </div>
        <ol className="ct-stages">
          <li className="ct-track" aria-hidden="true" role="presentation">
            <motion.span className="ct-track-fill" style={{ '--ct-p': reduced ? 1 : progress }} />
          </li>
          {RIDE_STAGES.map((s, i) => (
            <motion.li key={s.title} className="ct-stage" variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.6 }} custom={i}>
              <span className="ct-stage-ic"><Icon name={s.icon} size={20} /></span>
              <div>
                <h3>{s.title}</h3>
                <p>{s.desc}</p>
              </div>
            </motion.li>
          ))}
        </ol>
      </div>
    </section>
  )
}

/* ── 2. The bus can wait: what a City ride promises ── */

export function CityFacts({ maxKm }) {
  const facts = [
    { icon: 'clock', value: 'Every 2 s', label: 'A new search for your driver' },
    { icon: 'rupee', value: 'Upfront', label: 'Fare shown before you book' },
    { icon: 'route', value: `${maxKm} km`, label: 'Longest City ride' },
    { icon: 'wallet', value: 'At the drop', label: 'Pay by cash or UPI' },
  ]
  return (
    <section className="ws-arrive ct-facts" aria-label="Why ride with us">
      <div className="ws-wrap">
        <SectionHead title="The bus can wait.">
          A ride comes to your pickup, priced before you say yes.
        </SectionHead>
        <div className="ws-stats">
          {facts.map((s, i) => (
            <motion.div key={s.label} className="ws-stat" variants={rise} initial="hidden" whileInView="show" viewport={{ once: true }} custom={i}>
              <Icon name={s.icon} size={18} />
              <b>{s.value}</b>
              <span>{s.label}</span>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

/* ── 3. Bike, auto or car: the live rate card ── */

export function CityRates({ card }) {
  const byType = Object.fromEntries((card?.rates || []).map((r) => [r.vehicleType, r]))
  const loading = card === null
  return (
    <section id="fleet" className="ws-sec ws-cream">
      <div className="ws-wrap">
        <SectionHead title="Bike, auto or car">
          A base fare, then a rate per kilometre and per minute on the road. Never less than the minimum.
        </SectionHead>
        <div className="ct-rates">
          {VEHICLE_ORDER.map((type, i) => {
            const v = VEHICLES[type]
            const r = byType[type]
            return (
              <motion.article key={type} className={`ct-rate ct-rate-${type.toLowerCase()}`} variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} custom={i}>
                <div className="ct-rate-top">
                  <span className="ct-rate-ic"><Icon name={v.icon} size={30} /></span>
                  <div>
                    <h3>{v.label}</h3>
                    <p>{v.blurb}</p>
                  </div>
                </div>
                {loading ? (
                  <div className="ct-rate-skel" aria-hidden="true"><span /><span /><span /></div>
                ) : r ? (
                  <>
                    <div className="ct-rate-from"><small>Minimum fare</small><b>{rupees(r.minimumFare)}</b></div>
                    <dl className="ct-rate-lines">
                      <div><dt>Base</dt><dd>{rupees(r.baseFare)}</dd></div>
                      <div><dt>Per km</dt><dd>{rupees(r.perKm)}</dd></div>
                      <div><dt>Per minute</dt><dd>{rupees(r.perMinute)}</dd></div>
                    </dl>
                  </>
                ) : (
                  <p className="ct-rate-none">The fare shows as soon as you pick a pickup and drop.</p>
                )}
              </motion.article>
            )
          })}
        </div>
      </div>
    </section>
  )
}

/* ── 4. Where the city takes you: one tap starts the Drop search ── */

const PLACES = [
  { icon: 'suitcase', label: 'Office', query: 'IT park' },
  { icon: 'ticket', label: 'Railway station', query: 'Railway station' },
  { icon: 'nav', label: 'Bus stand', query: 'Bus stand' },
  { icon: 'send', label: 'Airport', query: 'Airport' },
  { icon: 'plus', label: 'Hospital', query: 'Hospital' },
  { icon: 'id', label: 'College', query: 'College' },
  { icon: 'temple', label: 'Temple', query: 'Temple' },
  { icon: 'card', label: 'Shopping', query: 'Mall' },
]

export function CityPlaces({ maxKm, onPick, onTrip }) {
  return (
    <section id="packages" className="ws-sec">
      <div className="ws-wrap">
        <SectionHead title="Where the city takes you">
          Tap a place and we&apos;ll look it up near you in the Drop box.
        </SectionHead>
        <div className="ct-places">
          {PLACES.map((p, i) => (
            <motion.button key={p.label} type="button" className="ct-place" onClick={() => onPick(p.query)} variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} custom={i}>
              <span className="ct-place-ic"><Icon name={p.icon} size={22} /></span>
              <b>{p.label}</b>
              <Icon name="arrow" size={16} className="ct-place-go" />
            </motion.button>
          ))}
        </div>
        <div className="ct-further">
          <span><Icon name="route" size={18} />Going further than {maxKm} km? That&apos;s a Trip, with a car for the day.</span>
          <button type="button" className="ws-btn ws-btn-light" onClick={onTrip}><Icon name="car" />Plan a Trip</button>
        </div>
      </div>
    </section>
  )
}

/* ── 5. Built for city traffic ── */

const CITY_FEATURES = [
  { icon: 'shield', title: 'Verified drivers', desc: 'Licence and Aadhaar checked and approved before a driver takes a single ride.' },
  { icon: 'nav', title: 'Nearest driver first', desc: 'We search 1 km around your pickup, then 3, then 5, so the closest free driver comes.' },
  { icon: 'pin', title: 'Live on the map', desc: 'Follow your driver to the pickup and all the way to the drop.' },
  { icon: 'rupee', title: 'Fare before you ride', desc: 'See the price for bike, auto and car side by side before you book.' },
  { icon: 'bike', title: 'A ride for every budget', desc: 'One rider on a bike, three in an auto, up to seven in a car.' },
  { icon: 'wallet', title: 'Cash or UPI', desc: 'Settle with the driver at the drop, whichever way suits you.' },
]

export function CityFeatures() {
  return (
    <section id="features" className="ws-sec ws-teal">
      <div className="ws-wrap">
        <SectionHead title="Built for city traffic" />
        <div className="ws-feats">
          {CITY_FEATURES.map((f, i) => (
            <motion.div key={f.title} className="ws-feat" variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} custom={i}>
              <span className="ws-feat-ic"><Icon name={f.icon} size={20} /></span>
              <h3>{f.title}</h3>
              <p>{f.desc}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

/* ── 6. Ride in three taps ── */

const CITY_STEPS = [
  { title: 'Set pickup and drop', desc: 'Start typing and choose the place from the list.' },
  { title: 'Choose bike, auto or car', desc: 'Each one shows its fare for your route before you book.' },
  { title: 'Your driver comes to you', desc: 'Track them live, ride, and pay by cash or UPI at the drop.' },
]

export function CitySteps({ onBook }) {
  return (
    <section id="about" className="ws-sec">
      <div className="ws-wrap ws-how">
        <div>
          <SectionHead title="Ride in three taps" />
          <ol className="ws-steps">
            {CITY_STEPS.map((s, i) => (
              <motion.li key={s.title} variants={rise} initial="hidden" whileInView="show" viewport={{ once: true }} custom={i}>
                <span className="ws-step-n">{i + 1}</span>
                <div><h3>{s.title}</h3><p>{s.desc}</p></div>
              </motion.li>
            ))}
          </ol>
          <button type="button" className="ws-btn" onClick={onBook}><Icon name="bike" />Book a ride</button>
        </div>
        <div className="ws-how-media">
          <img src="/images/travel%20places/mumbai-skyline.webp" alt="Crowds on the pavement outside the Taj Mahal Palace, Mumbai" loading="lazy" decoding="async" />
          <div className="ws-how-chip"><Icon name="auto" /><div><b>Auto on the way</b><small>Colaba to CST</small></div></div>
        </div>
      </div>
    </section>
  )
}

/* ── 8. The closing call ── */

export function CityCta({ onBook }) {
  return (
    <section className="ws-sec">
      <div className="ws-wrap">
        <div className="ws-cta">
          <h2>Your ride is a tap away</h2>
          <p>Bike, auto or car, with a verified driver and the fare shown up front.</p>
          <div className="ws-cta-actions">
            <button type="button" className="ws-btn" onClick={onBook}>Book a ride<Icon name="arrow" /></button>
            <Link to="/driver/signup" className="ws-btn ws-btn-light"><Icon name="bike" />Drive with us</Link>
          </div>
        </div>
      </div>
    </section>
  )
}

export { DEFAULT_MAX_KM }
