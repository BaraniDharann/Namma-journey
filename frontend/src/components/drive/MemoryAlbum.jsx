import React, { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import Icon from '../dash/Icon'
import { Figure, FRIENDS, DRIVER } from '../journey/RoadTripStory'
import { useOnScreen } from '../motion/primitives'

/**
 * "Every stop becomes a photo": at each place the four friends line up in the viewfinder,
 * the Namma Journey driver lifts the camera, the flash fires, and the polaroid flies across
 * into the album, which keeps the trip.
 */

const img = (f) => `/images/travel%20places/${f}.webp`
export const ALBUM_PLACES = [
  { id: 'kashmir', name: 'Kashmir Valley', region: 'Jammu & Kashmir', src: img('kashmir-valley') },
  { id: 'nohkalikai', name: 'Nohkalikai Falls', region: 'Meghalaya', src: img('nohkalikai-falls') },
  { id: 'varkala', name: 'Varkala Beach', region: 'Kerala', src: img('varkala-beach-kerala') },
  { id: 'alappuzha', name: 'Alappuzha backwaters', region: 'Kerala', src: img('alappuzha-kerala') },
  { id: 'hampi', name: 'Vijaya Vittala, Hampi', region: 'Karnataka', src: img('vijaya-vittala-temple-hampi') },
  { id: 'kanyakumari', name: 'Kanyakumari', region: 'Tamil Nadu', src: img('thiruvalluvar-statue-kanyakumari') },
]

/** One memory every 5.6 s: arrive, line up, aim, click, fly, admire. */
const T = { pose: 700, aim: 2100, shot: 3000, fly: 3350, dwell: 4500, cycle: 5600 }
const phaseAt = (t) => (t < T.pose ? 'arrive' : t < T.aim ? 'pose' : t < T.shot ? 'aim' : t < T.fly ? 'shot' : t < T.dwell ? 'fly' : 'dwell')
const STACK = 4
const tilt = (i) => ((i * 47) % 13) - 6

const PHOTOGRAPHER = { ...DRIVER, body: 'shirt', pants: '#334155' }

/** The four friends standing in front of the place, posing according to the phase. */
function Group({ phase, still }) {
  const happy = still || phase === 'shot' || phase === 'fly' || phase === 'dwell'
  const poses = happy ? ['up', 'gesture', 'down', 'up'] : ['down', 'down', 'down', 'down']
  const leans = phase === 'arrive' && !still ? [0, 0, 0, 0] : [6, 3, -3, -6]
  return (
    <svg className="ma-group" viewBox="150 140 300 180" preserveAspectRatio="xMidYMax meet" aria-hidden="true">
      {FRIENDS.map((look, i) => (
        <g key={i} transform={`translate(${222 + i * 52} 0)`}>
          <Figure look={look} mood={happy ? 'joy' : 'smile'} arms={poses[i]} lean={leans[i]} />
        </g>
      ))}
    </svg>
  )
}

function Polaroid({ place, still }) {
  return (
    <figure className="ma-polaroid">
      <div className="ma-polaroid-photo" style={{ backgroundImage: `url("${place.src}")` }}>
        <Group phase="dwell" still={still} />
      </div>
      <figcaption><b>{place.name}</b><span>{place.region}</span></figcaption>
    </figure>
  )
}

export default function MemoryAlbum() {
  const reduced = useReducedMotion()
  const host = useRef(null)
  const onScreen = useOnScreen(host, '0px')
  const stage = useRef(null), finder = useRef(null), pile = useRef(null)
  const [active, setActive] = useState(0)
  const [phase, setPhase] = useState('arrive')
  const [kept, setKept] = useState([])
  const [paused, setPaused] = useState(false)
  const [flight, setFlight] = useState(null)
  const runRef = useRef(true)
  useEffect(() => { runRef.current = onScreen && !paused && !reduced }, [onScreen, paused, reduced])

  const measure = useCallback(() => {
    const s = stage.current?.getBoundingClientRect(), f = finder.current?.getBoundingClientRect(), p = pile.current?.getBoundingClientRect()
    if (!s || !f || !p) return
    setFlight({ x0: f.left + f.width / 2 - s.left, y0: f.top + f.height / 2 - s.top, x1: p.left + p.width / 2 - s.left, y1: p.top + p.height / 2 - s.top })
  }, [])

  // One wall-clock timer; paused and off-screen time is simply not counted.
  useEffect(() => {
    if (reduced) return undefined
    let raf, last = performance.now(), t = 0, idx = 0, ph = 'arrive'
    const tick = (now) => {
      const dt = Math.min(now - last, 250)
      last = now
      if (runRef.current) {
        const before = t
        t += dt
        // Record the shot whenever the clock crosses it, even if a slow frame skips the click itself.
        if (before < T.shot && t >= T.shot) setKept((list) => [...list.filter((k) => k !== idx), idx])
        if (t >= T.cycle) {
          t -= T.cycle
          idx = (idx + 1) % ALBUM_PLACES.length
          setActive(idx)
        }
        const next = phaseAt(t)
        if (next !== ph) {
          ph = next
          setPhase(next)
          if (next === 'fly') measure()
        }
      }
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [reduced, measure])

  const place = ALBUM_PLACES[active]
  const stack = kept.slice(-STACK)

  if (reduced) {
    return (
      <section id="album" className="ma ma-still" aria-labelledby="ma-title">
        <div className="ws-wrap">
          <div className="ma-head">
            <h2 id="ma-title">Every stop becomes a photo</h2>
            <p>Your driver handles the road and the camera. You keep the album.</p>
          </div>
          <div className="ma-still-grid">
            {ALBUM_PLACES.map((p) => <Polaroid key={p.id} place={p} still />)}
          </div>
        </div>
      </section>
    )
  }

  return (
    <section id="album" className="ma" ref={host} aria-labelledby="ma-title">
      <div className="ws-wrap">
        <div className="ma-head">
          <h2 id="ma-title">Every stop becomes a photo</h2>
          <p>Your driver handles the road and the camera. You keep the album.</p>
        </div>

        <div className="ma-stage" ref={stage}>
          <div className="ma-shoot">
            <div className={`ma-finder${phase === 'shot' ? ' is-shot' : ''}`} ref={finder}>
              <AnimatePresence initial={false}>
                <motion.div
                  key={place.id}
                  className="ma-photo"
                  style={{ backgroundImage: `url("${place.src}")` }}
                  initial={{ opacity: 0, scale: 1.08 }}
                  animate={{ opacity: 1, scale: phase === 'aim' || phase === 'shot' ? 1.01 : 1.05 }}
                  exit={{ opacity: 0 }}
                  transition={{ opacity: { duration: 0.6 }, scale: { duration: 2.6, ease: [0.16, 1, 0.3, 1] } }}
                  role="img"
                  aria-label={`${place.name}, ${place.region}`}
                />
              </AnimatePresence>
              <Group phase={phase} />
              <span className={`ma-focus${phase === 'aim' ? ' is-aim' : ''}${phase === 'shot' || phase === 'fly' || phase === 'dwell' ? ' is-locked' : ''}`} aria-hidden="true" />
              <div className="ma-caption">
                <b>{place.name}</b>
                <span>{phase === 'aim' ? 'Focusing' : phase === 'shot' ? 'Click' : phase === 'fly' || phase === 'dwell' ? 'Saved to your album' : place.region}</span>
              </div>
              <AnimatePresence>
                {phase === 'shot' && (
                  <motion.span className="ma-flash" initial={{ opacity: 0 }} animate={{ opacity: [0, 1, 0] }} exit={{ opacity: 0 }} transition={{ duration: 0.35 }} />
                )}
              </AnimatePresence>
            </div>

            <svg className="ma-photographer" viewBox="-60 150 120 175" aria-hidden="true">
              <Figure look={PHOTOGRAPHER} mood={phase === 'shot' ? 'joy' : 'smile'} arms={phase === 'aim' || phase === 'shot' ? 'selfie' : 'down'} lean={phase === 'aim' || phase === 'shot' ? -4 : 0} />
              {phase === 'shot' && <circle cx="52" cy="148" r="16" fill="#fff8d6" opacity=".9" />}
            </svg>
          </div>

          <AnimatePresence>
            {phase === 'fly' && flight && (
              <motion.div
                className="ma-flyer"
                initial={{ left: flight.x0, top: flight.y0, scale: 1, rotate: 0, opacity: 1 }}
                animate={{ left: flight.x1, top: flight.y1, scale: 0.62, rotate: tilt(active) }}
                exit={{ opacity: 0 }}
                transition={{ duration: 1.05, ease: [0.16, 1, 0.3, 1] }}
              >
                <Polaroid place={place} />
              </motion.div>
            )}
          </AnimatePresence>

          <div className="ma-album">
            <div className="ma-album-head">
              <span><Icon name="camera" size={18} />Your travel album</span>
              <b>{kept.length} of {ALBUM_PLACES.length}</b>
            </div>
            <div className="ma-pile" ref={pile}>
              {stack.length === 0 && <p className="ma-empty">The first photo is on its way.</p>}
              {stack.map((idx, k) => (
                <motion.div
                  key={ALBUM_PLACES[idx].id}
                  className="ma-pile-item"
                  style={{ zIndex: k + 1 }}
                  initial={k === stack.length - 1 ? { opacity: 0, scale: 0.85 } : false}
                  animate={{ opacity: 1, scale: k === stack.length - 1 ? 1 : 0.94, rotate: tilt(idx), x: ((idx * 29) % 9 - 4) * 7, y: ((idx * 53) % 7 - 3) * 6 }}
                  transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
                >
                  <Polaroid place={ALBUM_PLACES[idx]} />
                </motion.div>
              ))}
            </div>
            <button type="button" className="ma-pause" onClick={() => setPaused((p) => !p)} aria-pressed={paused}>
              <Icon name={paused ? 'nav' : 'clock'} size={16} />{paused ? 'Resume the trip' : 'Pause the trip'}
            </button>
          </div>
        </div>
      </div>
    </section>
  )
}
