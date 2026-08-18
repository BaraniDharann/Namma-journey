import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { journeyStops } from '../../data/journeyData'
import { Counter, EASE, Magnetic } from '../motion/primitives'

const ROTATING = ['journey', 'road trip', 'pilgrimage', 'escape', 'getaway']

const HERO_STATS = [
  { value: 7420, suffix: ' km', label: 'Roads covered' },
  { value: 50, suffix: 'K+', label: 'Travellers' },
  { value: 1200, suffix: '+', label: 'Verified drivers' },
  { value: 4.9, suffix: '★', label: 'Average rating', decimals: 1 },
]

/** Photographs that slide past behind the car — the country going by. */
const SCENERY = journeyStops.flatMap((s) => s.images.map((img) => ({ img, name: s.name })))

export default function JourneyHero({ onStart, children }) {
  const reduced = useReducedMotion()
  const [word, setWord] = useState(0)

  useEffect(() => {
    if (reduced) return
    const t = setInterval(() => setWord((w) => (w + 1) % ROTATING.length), 2600)
    return () => clearInterval(t)
  }, [reduced])

  useEffect(() => {
    const run = window.requestIdleCallback || ((cb) => setTimeout(cb, 400))
    run(() => {
      journeyStops.slice(0, 2).forEach((s) => s.images.forEach((i) => { new Image().src = i.webp }))
    })
  }, [])

  return (
    <section id="home" className="hero">
      <span className="hero-blob hero-blob-a" aria-hidden />
      <span className="hero-blob hero-blob-b" aria-hidden />

      <div className="hero-inner">
        <div className="hero-copy">
          <motion.span
            className="hero-badge"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: EASE }}
          >
            <i className="hero-badge-dot" />
            India&apos;s temple &amp; outstation cab service
          </motion.span>

          <h1 className="hero-title">
            <motion.span
              className="hero-title-a"
              initial={{ opacity: 0, y: 34 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.06, ease: EASE }}
            >
              Every great
            </motion.span>
            <span className="hero-title-swap">
              <AnimatePresence initial={false}>
                <motion.span
                  key={ROTATING[word]}
                  className="hero-title-word"
                  initial={{ opacity: 0, y: '58%' }}
                  animate={{ opacity: 1, y: '0%' }}
                  exit={{ opacity: 0, y: '-58%' }}
                  transition={{ duration: 0.5, ease: EASE }}
                >
                  {ROTATING[word]}
                </motion.span>
              </AnimatePresence>
            </span>
            <motion.span
              className="hero-title-b"
              initial={{ opacity: 0, y: 34 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.14, ease: EASE }}
            >
              starts with a car
            </motion.span>
          </h1>

          <motion.p
            className="hero-sub"
            initial={{ opacity: 0, y: 22 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.24, ease: EASE }}
          >
            Book an AC or non-AC car with a verified driver — temple trips, hill stations,
            outstation runs. Scroll down and ride the whole country with us first.
          </motion.p>

          <motion.div
            className="hero-actions"
            initial={{ opacity: 0, y: 22 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.32, ease: EASE }}
          >
            <Magnetic strength={12}>
              <button type="button" className="btn-journey" onClick={onStart}>
                <span className="btn-journey-shine" />
                🚗 Take the journey
              </button>
            </Magnetic>
            <Link to="/signup" className="btn-outline">
              Book a ride <span className="btn-arrow">→</span>
            </Link>
          </motion.div>

          <motion.div
            className="hero-stats"
            initial="hidden"
            animate="show"
            variants={{ hidden: {}, show: { transition: { staggerChildren: 0.08, delayChildren: 0.44 } } }}
          >
            {HERO_STATS.map((s) => (
              <motion.div
                key={s.label}
                className="hero-stat"
                variants={{
                  hidden: { opacity: 0, y: 18 },
                  show: { opacity: 1, y: 0, transition: { duration: 0.55, ease: EASE } },
                }}
              >
                <b><Counter value={s.value} suffix={s.suffix} decimals={s.decimals || 0} /></b>
                <span>{s.label}</span>
              </motion.div>
            ))}
          </motion.div>
        </div>

        <motion.div
          className="hero-side"
          initial={{ opacity: 0, y: 34 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.85, delay: 0.24, ease: EASE }}
        >
          {children}
        </motion.div>
      </div>

      {/* ── the country going past ── */}
      <div className="hero-strip">
        <div className="hero-scenery" aria-hidden>
          <div className="hero-scenery-track">
            {[...SCENERY, ...SCENERY].map((s, i) => (
              <div className="hero-scenery-card" key={i}>
                <picture>
                  <source type="image/webp" srcSet={s.img.webp} />
                  <img src={s.img.jpg} alt="" loading="lazy" decoding="async" />
                </picture>
              </div>
            ))}
          </div>
        </div>

        <button type="button" className="hero-scroll" onClick={onStart}>
          <span className="hero-scroll-track">
            <motion.span
              className="hero-scroll-dot"
              animate={{ y: [0, 14, 0], opacity: [1, 0.35, 1] }}
              transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
            />
          </span>
          Scroll to travel
        </button>
      </div>
    </section>
  )
}
