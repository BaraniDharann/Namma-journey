import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { journeyStops, landmarks, stopEntryIndex, totalKm } from '../../data/journeyData'
import { EASE, useOnScreen } from '../motion/primitives'

const N = landmarks.length

/**
 * One memory every five seconds:
 * the place appears → the camera focuses → *click* → the photo flies
 * into your album → a moment to admire it → on to the next place.
 */
const ARRIVE_END = 650
const FOCUS_END = 2100
const SHOT_END = 2450
const FLY_END = 3600
const CYCLE = 5000

/** How many polaroids stay visible on the album stack. */
const STACK_SIZE = 5

const clamp = (v, a, b) => Math.min(b, Math.max(a, v))
const lerp = (a, b, t) => a + (b - a) * t

const phaseName = (p) =>
  p < ARRIVE_END ? 'arrive' : p < FOCUS_END ? 'focus' : p < SHOT_END ? 'shot' : p < FLY_END ? 'fly' : 'dwell'

/** Deterministic scatter so the pile looks handled, not machine-stacked. */
const tilt = (i) => ((i * 47) % 15) - 7
const offX = (i) => (((i * 29) % 9) - 4) * 9
const offY = (i) => (((i * 53) % 7) - 3) * 7

export default function JourneyStage() {
  const reduced = useReducedMotion()
  return reduced ? <JourneyList /> : <JourneyCamera />
}

/* ══════════════════  the camera  ══════════════════ */

function JourneyCamera() {
  const hostRef = useRef(null)
  const onScreen = useOnScreen(hostRef, '120px')

  const [active, setActive] = useState(0)
  const [phase, setPhase] = useState('arrive')
  const [captured, setCaptured] = useState([])
  const [paused, setPaused] = useState(false)
  const [finished, setFinished] = useState(false)

  const anchorRef = useRef(0)
  const baseRef = useRef(0)
  const activeRef = useRef(0)
  const phaseRef = useRef('arrive')
  const shotTakenRef = useRef(-1)
  const finishedRef = useRef(false)
  const pausedRef = useRef(false)
  const visibleRef = useRef(true)

  const odoEl = useRef(null)
  const railEl = useRef(null)
  const stageRef = useRef(null)
  const finderRef = useRef(null)
  const pileRef = useRef(null)
  const [flight, setFlight] = useState(null)

  useEffect(() => { pausedRef.current = paused }, [paused])
  useEffect(() => { visibleRef.current = onScreen }, [onScreen])

  /** Where the photo flies from (the viewfinder) and to (the album pile). */
  const measureFlight = useCallback(() => {
    const stage = stageRef.current
    const finder = finderRef.current
    const pile = pileRef.current
    if (!stage || !finder || !pile) return
    const s = stage.getBoundingClientRect()
    const f = finder.getBoundingClientRect()
    const a = pile.getBoundingClientRect()
    setFlight({
      x0: f.left + f.width / 2 - s.left,
      y0: f.top + f.height / 2 - s.top,
      x1: a.left + a.width / 2 - s.left,
      y1: a.top + a.height / 2 - s.top,
    })
  }, [])

  /* ── the clock ── */
  useEffect(() => {
    let raf
    let prev = performance.now()
    anchorRef.current = prev

    const tick = (now) => {
      const raw = now - prev
      prev = now

      const running = !pausedRef.current && visibleRef.current && !finishedRef.current
      // Timed against the wall clock so a slow device still spends five seconds
      // per memory. Pausing slides the anchor; so does a hard freeze. Hidden
      // tabs are handled by visibilitychange.
      if (!running || raw > 4000) anchorRef.current += raw

      let p = now - anchorRef.current
      let guard = 0
      while (p >= CYCLE && guard++ < N) {
        if (baseRef.current >= N - 1) {
          anchorRef.current = now - (FLY_END + 100)
          p = FLY_END + 100
          if (!finishedRef.current) {
            finishedRef.current = true
            setFinished(true)
          }
          break
        }
        anchorRef.current += CYCLE
        baseRef.current += 1
        p -= CYCLE
      }

      if (baseRef.current !== activeRef.current) {
        activeRef.current = baseRef.current
        setActive(baseRef.current)
      }

      const name = phaseName(p)
      if (name !== phaseRef.current) {
        phaseRef.current = name
        setPhase(name)
        // the shutter fires once per place — the moment becomes a memory
        if (name === 'fly') measureFlight()
        if (name === 'shot' && shotTakenRef.current !== activeRef.current) {
          shotTakenRef.current = activeRef.current
          const idx = activeRef.current
          setCaptured((list) => (list.includes(idx) ? list : [...list, idx]))
        }
      }

      const d = baseRef.current + clamp(p / CYCLE, 0, 1)
      if (odoEl.current) {
        const i = clamp(Math.floor(d), 0, N - 1)
        const km = lerp(landmarks[i].km, landmarks[Math.min(N - 1, i + 1)].km, d - i)
        odoEl.current.textContent = Math.round(km).toLocaleString('en-IN')
      }
      if (railEl.current) {
        railEl.current.style.setProperty('--rail', `${(d / (N - 1)) * 100}%`)
      }

      raf = requestAnimationFrame(tick)
    }

    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [])

  // rAF stops while the tab is hidden — hand the lost time back so the trip
  // resumes where it was instead of jumping ahead.
  useEffect(() => {
    let hiddenAt = 0
    const onVisibility = () => {
      if (document.hidden) hiddenAt = performance.now()
      else if (hiddenAt) {
        anchorRef.current += performance.now() - hiddenAt
        hiddenAt = 0
      }
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [])

  const goTo = useCallback((i) => {
    const target = clamp(i, 0, N - 1)
    baseRef.current = target
    activeRef.current = target
    anchorRef.current = performance.now()
    phaseRef.current = 'arrive'
    shotTakenRef.current = -1
    finishedRef.current = false
    setFinished(false)
    setActive(target)
    setPhase('arrive')
    // memories are where you've already been
    setCaptured((list) => list.filter((x) => x < target))
  }, [])

  // keep upcoming photographs warm so nothing arrives grey
  useEffect(() => {
    for (let i = active + 1; i <= Math.min(N - 1, active + 2); i++) {
      new Image().src = landmarks[i].img.webp
    }
  }, [active])

  const lm = landmarks[active]
  const stop = lm.stop
  const next = landmarks[Math.min(N - 1, active + 1)]
  const focusing = phase === 'focus'
  const flash = phase === 'shot'
  const stack = captured.slice(-STACK_SIZE)

  return (
    <section id="journey" className="dv" ref={hostRef}>
      {/* ── trip strap ── */}
      <div className="dv-strap jp-wrap">
        <span className="dv-tag">
          <i className="dv-pip" />
          {finished ? 'Album complete' : paused ? 'Paused' : 'Making memories'}
        </span>
        <span className="dv-route">Kashmir <b>→</b> Kanyakumari</span>
        <span className="dv-odo">
          <b ref={odoEl}>0</b> / {totalKm.toLocaleString('en-IN')} km
        </span>
        <div className="dv-controls">
          <button type="button" onClick={() => goTo(active - 1)} aria-label="Previous place">‹</button>
          <button
            type="button"
            className="dv-play"
            onClick={() => (finished ? goTo(0) : setPaused((v) => !v))}
          >
            {finished ? '↻ Travel again' : paused ? '▶ Resume' : '❚❚ Pause'}
          </button>
          <button type="button" onClick={() => goTo(active + 1)} aria-label="Next place">›</button>
        </div>
      </div>

      {/* ── the camera and the album ── */}
      <div className="cam-stage jp-wrap" ref={stageRef}>
        {/* viewfinder */}
        <div className={`cam-finder${flash ? ' is-shot' : ''}`} ref={finderRef}>
          <div className="cam-screen">
            <AnimatePresence initial={false}>
              <motion.picture
                key={lm.id}
                initial={{ opacity: 0, scale: 1.08 }}
                animate={{ opacity: 1, scale: focusing || flash ? 1.01 : 1.045 }}
                exit={{ opacity: 0 }}
                transition={{
                  opacity: { duration: 0.55, ease: 'easeOut' },
                  scale: { duration: 3.2, ease: 'easeOut' },
                }}
              >
                <source type="image/webp" srcSet={lm.img.webp} />
                <img src={lm.img.jpg} alt={`${lm.title}, ${stop.region}`} decoding="async" />
              </motion.picture>
            </AnimatePresence>

            {/* rule-of-thirds grid, corner brackets, focus box */}
            <span className="cam-grid" />
            <span className="cam-corner tl" /><span className="cam-corner tr" />
            <span className="cam-corner bl" /><span className="cam-corner br" />

            <motion.span
              className="cam-reticle"
              animate={
                phase === 'arrive'
                  ? { opacity: 0, scale: 1.5 }
                  : focusing
                    ? { opacity: 1, scale: [1.35, 1, 1.06, 1], borderColor: '#ffffff' }
                    : { opacity: 1, scale: 1, borderColor: '#4ade80' }
              }
              transition={focusing ? { duration: 1.2, ease: 'easeOut' } : { duration: 0.25 }}
            />

            {/* camera HUD */}
            <div className="cam-hud-top">
              <span className="cam-rec"><i /> REC</span>
              <span>AUTO</span>
              <span className="cam-batt"><i /><i /><i /></span>
            </div>
            <div className="cam-hud-bottom">
              <span className="cam-place">{lm.title}</span>
              <span className="cam-stamp">
                {phase === 'dwell' || phase === 'fly' ? '● SAVED' : focusing ? 'FOCUSING…' : flash ? 'CLICK!' : 'FRAMING'}
                {'  ·  '}KM {lm.km.toLocaleString('en-IN')}
              </span>
            </div>

            {/* the shutter */}
            <AnimatePresence>
              {flash && (
                <motion.span
                  className="cam-flash"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: [0, 1, 0, 0.6, 0] }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.4, times: [0, 0.2, 0.5, 0.65, 1] }}
                />
              )}
            </AnimatePresence>
          </div>
          <span className="cam-shutter-btn" aria-hidden />
        </div>

        {/* the polaroid flying into the album */}
        <AnimatePresence>
          {phase === 'fly' && flight && (
            <motion.div
              className="cam-flyer"
              initial={{ left: flight.x0, top: flight.y0, opacity: 1 }}
              animate={{ left: flight.x1, top: flight.y1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 1, ease: EASE }}
            >
              <motion.div
                initial={{ rotate: 0, scale: 1 }}
                animate={{ rotate: tilt(active), scale: 0.55 }}
                transition={{ duration: 1, ease: EASE }}
              >
                <PolaroidInner lm={lm} />
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* the album */}
        <div className="cam-album">
          <div className="cam-album-head">
            <h5>📸 Your travel album</h5>
            <b>{captured.length} / {N} memories</b>
          </div>
          <div className="cam-pile" ref={pileRef}>
            {stack.length === 0 && (
              <p className="cam-empty">The first click is coming…</p>
            )}
            {stack.map((idx, k) => {
              const item = landmarks[idx]
              const topmost = k === stack.length - 1
              return (
                <motion.div
                  key={item.id}
                  className="cam-polaroid"
                  style={{ zIndex: k + 1 }}
                  initial={topmost ? { opacity: 0, scale: 0.8, rotate: tilt(idx) } : false}
                  animate={{
                    opacity: 1,
                    scale: topmost ? 1 : 0.93,
                    rotate: tilt(idx),
                    x: offX(idx),
                    y: offY(idx),
                  }}
                  transition={{ duration: 0.45, ease: EASE }}
                  onClick={() => goTo(idx)}
                >
                  <PolaroidInner lm={item} />
                </motion.div>
              )
            })}
          </div>
          <p className="cam-album-note">Every place you pass becomes a memory</p>
        </div>

        <AnimatePresence>
          {finished && (
            <motion.div
              className="cam-finish"
              initial={{ opacity: 0, y: 22, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -14 }}
              transition={{ duration: 0.6, ease: EASE }}
            >
              <span>🎞️</span>
              <b>{N} memories · {totalKm.toLocaleString('en-IN')} km — your album is full</b>
              <Link to="/signup">Make your own memories →</Link>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* ── the chapter we're in ── */}
      <div className="dv-panel jp-wrap">
        {
          <motion.div
            key={lm.id}
            className="dv-panel-inner"
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: EASE }}
          >
            <div className="dv-panel-head">
              <span className="dv-chapter">
                <b>{String(lm.stopIndex + 1).padStart(2, '0')}</b>
                {stop.chapter}
              </span>
              <h4>{stop.name}</h4>
              <p>{lm.km.toLocaleString('en-IN')} km in · photo {lm.photoIndex + 1} of {lm.photoCount}</p>
            </div>
            <div className="dv-panel-mid">
              <p className="dv-quote">“{stop.tagline}”</p>
              <div className="dv-facts">
                {stop.facts.map((f) => <span key={f}>{f}</span>)}
              </div>
            </div>
            <div className="dv-panel-cta">
              <Link to="/signup" className="btn-journey">Book a car to {stop.name}</Link>
              {!finished && <span className="dv-next">Next shot · {next.title}</span>}
            </div>
          </motion.div>
        }
      </div>

      {/* ── itinerary ── */}
      <div className="dv-rail jp-wrap" ref={railEl}>
        <div className="dv-rail-line">
          <span className="dv-rail-fill" />
          <span className="dv-rail-car">🚗</span>
        </div>
        <div className="dv-rail-stops">
          {journeyStops.map((s, i) => (
            <button
              key={s.id}
              type="button"
              onClick={() => goTo(stopEntryIndex[i])}
              className={`dv-stop${i === lm.stopIndex ? ' is-active' : ''}${i < lm.stopIndex ? ' is-done' : ''}`}
              aria-label={`Travel to ${s.name}`}
            >
              <span className="dv-stop-dot" />
              <span className="dv-stop-name">{s.name}</span>
            </button>
          ))}
        </div>
      </div>
    </section>
  )
}

function PolaroidInner({ lm }) {
  return (
    <div className="cam-polaroid-inner">
      <span className="cam-tape" />
      <picture>
        <source type="image/webp" srcSet={lm.img.webp} />
        <img src={lm.img.jpg} alt={lm.title} loading="lazy" decoding="async" />
      </picture>
      <figcaption>
        <b>{lm.title}</b>
        <span>{lm.stop.emoji} {lm.stop.region}</span>
      </figcaption>
    </div>
  )
}

/* ══════════════════  reduced-motion fallback  ══════════════════ */

function JourneyList() {
  return (
    <section id="journey" className="jl">
      <div className="jp-wrap jl-intro">
        <span className="jp-eyebrow">The drive</span>
        <h2 className="jp-h2">Kashmir to <em>Kanyakumari</em></h2>
        <p className="jp-lead">
          Ten stops, {totalKm.toLocaleString('en-IN')} km, one car — every place we pass along the way.
        </p>
      </div>

      {journeyStops.map((s, i) => (
        <article className="jl-stop jp-wrap" key={s.id}>
          <div className="jl-head">
            <span className="jl-chapter">
              <b>{String(i + 1).padStart(2, '0')}</b> {s.chapter}
            </span>
            <h3 className="jl-name">{s.name}</h3>
            <p className="jl-region">{s.emoji} {s.region}</p>
            <p className="jl-line">“{s.tagline}”</p>
            <p className="jl-blurb">{s.blurb}</p>
            <div className="jl-facts">{s.facts.map((f) => <span key={f}>{f}</span>)}</div>
          </div>
          {s.images.map((img) => (
            <figure className="jl-photo" key={img.jpg}>
              <picture>
                <source type="image/webp" srcSet={img.webp} />
                <img src={img.jpg} alt={img.title} loading="lazy" decoding="async" />
              </picture>
              <figcaption>{img.title}</figcaption>
            </figure>
          ))}
          <Link to="/signup" className="jl-cta">Book a car to {s.name} <span>→</span></Link>
        </article>
      ))}
    </section>
  )
}
