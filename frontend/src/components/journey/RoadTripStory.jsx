import React, { useEffect, useId, useMemo, useRef, useState } from 'react'
import {
  AnimatePresence, motion, useAnimationFrame, useMotionValue, useMotionValueEvent,
  useReducedMotion, useTransform, easeIn, easeInOut, easeOut,
} from 'framer-motion'
import { EASE, useIsCompact, useOnScreen } from '../motion/primitives'

/**
 * The dashboard's road-trip story, one loop every ~19s:
 * four friends plan the weekend beside the car → climb in with the driver →
 * drive out of the city past the Namma Journey billboard → arrive somewhere
 * green → group selfie, flash, and the moment lands in a polaroid.
 *
 * Everything hangs off one clock (`t`, ms into the loop). Continuous motion is
 * derived straight from it as motion values; discrete state (who is talking,
 * who is smiling) is re-rendered only when it actually changes.
 */

const T = {
  board: 4000,
  drive: 6200,
  arrive: 12200,
  selfie: 14700,
  flash: 15700,
  frame: 16000,
  reset: 18000,
}
const CYCLE = 18800
/** The frame a reduced-motion visitor sees: everyone happy, in the polaroid. */
const STILL = 17000

const GROUND = 312
const X = [330, 392, 454, 516]
const DOOR = 761
const CAR = { x: 575, y: 154, s: 1.2 }
/** Nearest the car climbs in first and hops out first. */
const ORDER = [3, 2, 1, 0]
const SEATS = [52, 88, 137, 173]
const LEAN = [7, 5, 3, -4]

const walkDur = (i) => 350 + (DOOR - X[i]) * 1.6
const boardAt = (i) => T.board + ORDER.indexOf(i) * 380
const exitAt = (i) => T.arrive + 400 + ORDER.indexOf(i) * 320

/** The ground the friends stand on once the photo takes over. */
const GREEN = ['#3f6212', '#4d7c0f', '#365314']
const SAND = ['#b08850', '#c9a36b', '#a47c45']
const img = (file) => `/images/travel%20places/${file}`
const DESTINATIONS = [
  { name: 'Kashmir Valley', short: 'Kashmir', cheer: 'Kashmir Valley!', ground: GREEN, src: img('kashmir-valley.webp') },
  { name: 'Varkala Beach', short: 'Varkala', cheer: 'Varkala beach!', ground: SAND, src: img('varkala-beach-kerala.webp') },
  { name: 'Nohkalikai Falls', short: 'Cherrapunji', cheer: 'The waterfalls!', ground: GREEN, src: img('nohkalikai-falls.webp') },
  { name: 'Alappuzha', short: 'Alappuzha', cheer: 'Kerala backwaters!', ground: GREEN, src: img('alappuzha-kerala.webp') },
]

export const FRIENDS = [
  { body: 'shirt', skin: '#b07a4f', hair: '#1c1917', style: 'short', mustache: true, shirt: '#0f766e', pants: '#1e3a5f' },
  { body: 'kurta', skin: '#c68a5e', hair: '#231815', style: 'long', bindi: true, shirt: '#db2777', scarf: '#fbbf24', pants: '#f1f5f9' },
  { body: 'shirt', skin: '#8d5a3b', hair: '#292524', style: 'curly', glasses: true, beard: true, shirt: '#f59e0b', pants: '#334155' },
  { body: 'kurta', skin: '#a86b45', hair: '#1c1917', style: 'bun', shirt: '#2563eb', scarf: '#f9a8d4', pants: '#1e293b' },
]
export const DRIVER = { skin: '#8d5524', hair: '#111827', style: 'short', cap: true, mustache: true, shirt: '#f8fafc' }

const LINES = (dest) => ['Weekend trip?', dest.cheer, "Who's driving?", "Our driver's here!", 'Hop in!']
const BUBBLE_TIMES = [300, 1200, 2100, 3000, 3800, 4700]

const ARMS = {
  down: [10, -10],
  gesture: [10, -70],
  up: [150, -150],
  selfie: [10, -165],
}

/* ── the discrete half of the clock ── */

function snapshot(v) {
  const phase = v < T.board ? 'talk' : v < T.drive ? 'board' : v < T.arrive ? 'drive' : v < T.selfie ? 'arrive' : v < T.reset ? 'selfie' : 'reset'
  let bubble = -1
  for (let b = 0; b < BUBBLE_TIMES.length - 1; b++) {
    if (v >= BUBBLE_TIMES[b] && v < BUBBLE_TIMES[b + 1]) bubble = b
  }
  const revealing = v >= T.drive + 1500 && v < T.drive + 4200
  const joyful = v >= T.selfie + 600
  const people = X.map((_, i) => {
    const b = boardAt(i), e = exitAt(i), d = walkDur(i)
    const walking = (v >= b && v < b + d) || (v >= e && v < e + d)
    const seated = v >= b + d - 120 && v < e + 60
    let mood = 'smile'
    if (bubble === i) mood = 'talk'
    else if (seated ? revealing : joyful) mood = 'joy'
    let arms = 'down'
    if (bubble === i) arms = 'gesture'
    else if (v >= T.selfie && v < T.reset + 400) arms = i === 3 ? 'selfie' : joyful ? 'up' : 'down'
    const lean = v >= T.selfie && v < T.reset + 400 ? LEAN[i] : 0
    return { walking, seated, mood, arms, lean }
  })
  const chip =
    phase === 'talk' ? 'Planning the weekend'
      : phase === 'board' ? 'Everyone in'
        : phase === 'drive' ? 'On the road'
          : phase === 'arrive' ? 'arrived'
            : phase === 'selfie' && v < T.frame ? 'Say cheese!' : null
  const s = {
    bubble,
    driving: phase === 'drive',
    cheering: v >= T.frame && v < T.reset,
    people,
    chip,
  }
  s.key = JSON.stringify(s)
  return s
}

/* ══════════════════  the story  ══════════════════ */

export default function RoadTripStory() {
  const reduced = useReducedMotion()
  const compact = useIsCompact('(max-width: 640px)')
  const hostRef = useRef(null)
  const onScreen = useOnScreen(hostRef, '0px')
  const uid = useId().replace(/:/g, '')

  const [paused, setPaused] = useState(false)
  const [loop, setLoop] = useState(0)
  const t = useMotionValue(reduced ? STILL : 0)
  const [snap, setSnap] = useState(() => snapshot(reduced ? STILL : 0))
  const snapKey = useRef(snap.key)

  const running = !reduced && !paused && onScreen
  const runningRef = useRef(running)
  useEffect(() => { runningRef.current = running }, [running])

  useEffect(() => {
    if (reduced) t.set(STILL)
  }, [reduced, t])

  useAnimationFrame((_, delta) => {
    if (!runningRef.current) return
    let next = t.get() + Math.min(delta, 100)
    if (next >= CYCLE) {
      next -= CYCLE
      setLoop((l) => l + 1)
    }
    t.set(next)
  })

  useMotionValueEvent(t, 'change', (v) => {
    const s = snapshot(v)
    if (s.key !== snapKey.current) {
      snapKey.current = s.key
      setSnap(s)
    }
  })

  const dest = DESTINATIONS[loop % DESTINATIONS.length]
  useEffect(() => {
    const next = new Image()
    next.src = DESTINATIONS[(loop + 1) % DESTINATIONS.length].src
  }, [loop])

  /* ── the continuous half of the clock ── */
  const worldX = useTransform(
    t,
    [0, T.drive, T.drive + 2000, T.drive + 3800, T.arrive, T.arrive + 1200, T.arrive + 1201, CYCLE],
    [0, 0, -1150, -1350, -2400, -2400, 0, 0],
    { ease: [easeIn, easeInOut, (p) => p, easeOut, (p) => p, (p) => p, (p) => p] }
  )
  const farX = useTransform(
    t,
    [0, T.drive, T.arrive, T.arrive + 1200, T.arrive + 1201, CYCLE],
    [0, 0, -700, -700, 0, 0]
  )
  const photoOpacity = useTransform(t, [0, 11600, 12500, T.reset, CYCLE], [0, 0, 1, 1, 0])
  const reveal = useTransform(t, [0, T.drive + 1600, T.drive + 2700, CYCLE], [0, 0, 320, 320], { ease: [(p) => p, easeOut, (p) => p] })
  const flash = useTransform(t, [0, T.flash, T.flash + 70, T.flash + 450, CYCLE], [0, 0, 0.92, 0, 0])
  const framed = useTransform(t, [0, T.frame, T.frame + 450, T.reset, T.reset + 400, CYCLE], [0, 0, 1, 1, 0, 0], { ease: EASE_SEGMENTS })
  const shotScale = useTransform(framed, (p) => 1 - 0.05 * p)
  const shotRotate = useTransform(framed, (p) => -1.4 * p)
  const progress = useTransform(t, (v) => v / CYCLE)

  const view = compact ? { x: 286, w: 704 } : { x: 0, w: 1200 }
  const lines = LINES(dest)
  const chip = snap.chip === 'arrived' ? `Arrived at ${dest.name}` : snap.chip

  return (
    <section
      ref={hostRef}
      className={`rts${paused ? ' is-paused' : ''}`}
      aria-label="Your next road trip"
    >
      <motion.div className="rts-shot" style={{ scale: shotScale, rotate: shotRotate }}>
        <svg
          className={`rts-svg${snap.driving ? ' rts-driving' : ''}`}
          viewBox={`${view.x} 0 ${view.w} 380`}
          preserveAspectRatio="xMidYMid slice"
          role="img"
          aria-label={`Four friends plan a weekend, ride with a Namma Journey driver to ${dest.name}, and take a happy group selfie there.`}
        >
          <defs>
            <linearGradient id={`${uid}-sky`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#dbeafe" />
              <stop offset="0.7" stopColor="#fef3e2" />
              <stop offset="1" stopColor="#fde7cf" />
            </linearGradient>
            <linearGradient id={`${uid}-meadow`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor={dest.ground[0]} stopOpacity="0" />
              <stop offset="0.45" stopColor={dest.ground[1]} stopOpacity="0.85" />
              <stop offset="1" stopColor={dest.ground[2]} />
            </linearGradient>
          </defs>

          {/* sky */}
          <rect x="0" y="0" width="1200" height="380" fill={`url(#${uid}-sky)`} />
          <circle cx="1010" cy="86" r="34" fill="#fde68a" />
          <circle cx="1010" cy="86" r="52" fill="#fde68a" opacity=".25" />

          {/* far layer: skyline, gopuram, then hills */}
          <motion.g style={{ x: farX }}>
            <FarLayer />
          </motion.g>

          {/* near layer: streets, then trees, the billboard, the milestone */}
          <motion.g style={{ x: worldX }}>
            <NearLayer uid={uid} reveal={reveal} dest={dest} />
          </motion.g>

          {/* road */}
          <rect x="0" y="318" width="1200" height="62" fill="#334155" />
          <rect x="0" y="318" width="1200" height="3" fill="#e2e8f0" opacity=".7" />
          <motion.g style={{ x: worldX }}>
            {Array.from({ length: 44 }, (_, i) => (
              <rect key={i} x={i * 90} y="365" width="40" height="4" rx="2" fill="#f8fafc" opacity=".85" />
            ))}
          </motion.g>

          {/* the destination, as a photograph */}
          <motion.g style={{ opacity: photoOpacity }}>
            <image href={dest.src} x="0" y="0" width="1200" height="380" preserveAspectRatio="xMidYMid slice" />
            <rect x="0" y="270" width="1200" height="110" fill={`url(#${uid}-meadow)`} />
          </motion.g>

          {/* the four friends, on foot */}
          {FRIENDS.map((look, i) => (
            <Walker key={i} i={i} t={t} look={look} state={snap.people[i]} />
          ))}

          {/* the car, with whoever is inside */}
          <g transform={`translate(${CAR.x} ${CAR.y}) scale(${CAR.s})`}>
            <Car uid={uid} people={snap.people} />
          </g>

          {/* hearts once the selfie is taken */}
          {snap.cheering && (
            <g key={`hearts-${loop}`}>
              {[[350, 150, 0], [410, 138, 0.25], [470, 150, 0.5], [530, 132, 0.15], [440, 120, 0.7]].map(([hx, hy, d], k) => (
                <g key={k} transform={`translate(${hx} ${hy})`}>
                  <path
                    className="rts-heart"
                    style={{ animationDelay: `${d}s` }}
                    d="M0 5 C-7 -1 -11 -6 -7 -10.5 C-4 -13.5 0 -11 0 -8 C0 -11 4 -13.5 7 -10.5 C11 -6 7 -1 0 5 Z"
                    fill={k % 2 ? '#fb7185' : '#f97316'}
                  />
                </g>
              ))}
            </g>
          )}

          {/* speech */}
          {lines.map((text, b) => {
            const anchor = b < 4 ? { x: X[b], y: GROUND - 152 } : { x: 872, y: 172 }
            return <Bubble key={b} text={text} show={snap.bubble === b} anchor={anchor} view={view} />
          })}
        </svg>

        <motion.div className="rts-flash" style={{ opacity: flash }} aria-hidden="true" />

        <motion.div className="rts-frame" style={{ opacity: framed }} aria-hidden="true">
          <div className="rts-frame-cap">
            <span>{dest.name}</span>
            <span className="rts-frame-brand">Namma Journey</span>
          </div>
        </motion.div>

        <div className="rts-chip-slot" aria-hidden="true">
          <AnimatePresence mode="wait" initial={false}>
            {chip && (
              <motion.span
                key={chip}
                className="rts-chip"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.35, ease: EASE }}
              >
                {chip}
              </motion.span>
            )}
          </AnimatePresence>
        </div>

        {!reduced && <motion.div className="rts-progress" style={{ scaleX: progress }} aria-hidden="true" />}
      </motion.div>

      {!reduced && (
        <button
          type="button"
          className="rts-ctrl"
          onClick={() => setPaused((p) => !p)}
          aria-pressed={paused}
          aria-label={paused ? 'Play the trip animation' : 'Pause the trip animation'}
        >
          {paused ? (
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.8v10.4a.6.6 0 0 0 .9.5l8.4-5.2a.6.6 0 0 0 0-1L3.9 1.3a.6.6 0 0 0-.9.5Z" fill="currentColor" /></svg>
          ) : (
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><rect x="2.5" y="1.5" width="3.2" height="11" rx="1" fill="currentColor" /><rect x="8.3" y="1.5" width="3.2" height="11" rx="1" fill="currentColor" /></svg>
          )}
        </button>
      )}
    </section>
  )
}

const EASE_SEGMENTS = [(p) => p, easeOut, (p) => p, easeInOut, (p) => p]

/* ══════════════════  scenery  ══════════════════ */

const BUILDINGS = [
  [0, 110, 170, '#cbd5e1'], [118, 80, 118, '#e2e8f0'], [206, 130, 200, '#c3d3e4'],
  [344, 92, 140, '#dbe4ee'], [444, 120, 178, '#cbd5e1'], [572, 72, 108, '#e2e8f0'],
  [652, 140, 212, '#c3d3e4'], [800, 92, 150, '#dbe4ee'], [900, 120, 190, '#cbd5e1'],
  [1028, 100, 128, '#e2e8f0'], [1136, 110, 160, '#c3d3e4'],
]

const TREES = (() => {
  const out = []
  let x = 1330
  let i = 0
  while (x < 3700) {
    if (x < 1490 || x > 1920) out.push({ x, pine: i % 3 === 1, s: 0.8 + ((i * 37) % 5) / 10 })
    x += 70 + ((i * 53) % 70)
    i++
  }
  return out
})()

function FarLayer() {
  return (
    <g>
      {[[40, 90, 150], [150, 70, 110], [300, 110, 170], [760, 80, 130], [880, 100, 160]].map(([x, w, h], k) => (
        <rect key={k} x={x} y={300 - h} width={w} height={h} fill="#e6ecf3" />
      ))}
      {/* a temple tower on the skyline */}
      <g transform="translate(600 300)" fill="#eadfce">
        {[0, 1, 2, 3, 4].map((k) => {
          const w = 92 - k * 16
          const y = -26 * (k + 1)
          return <path key={k} d={`M${-w / 2} ${y + 26} L${-w / 2 + 5} ${y} L${w / 2 - 5} ${y} L${w / 2} ${y + 26} Z`} />
        })}
        <path d="M-14 -130 Q0 -154 14 -130 Z" />
        <circle cy="-156" r="4" fill="#f5c26b" />
      </g>
      {/* hills take over as the city falls behind */}
      <path d="M880 300 C980 206 1090 196 1240 242 S1480 170 1640 222 S1890 158 2050 228 S2300 188 2560 250 V300 Z" fill="#cfe8bf" />
      <path d="M1000 300 C1120 244 1230 238 1360 262 S1600 220 1760 256 S2040 228 2200 262 S2440 240 2600 270 V300 Z" fill="#b1d99f" />
      {[[200, 70], [640, 50], [1180, 76], [1620, 56], [2040, 70]].map(([cx, cy], k) => (
        <g key={k} transform={`translate(${cx} ${cy})`} fill="#fff" opacity=".9">
          <ellipse rx="34" ry="11" />
          <ellipse cx="-14" cy="-7" rx="16" ry="11" />
          <ellipse cx="10" cy="-10" rx="18" ry="13" />
        </g>
      ))}
    </g>
  )
}

function NearLayer({ uid, reveal, dest }) {
  const windows = useMemo(() => {
    const out = []
    BUILDINGS.forEach(([x, w, h], b) => {
      for (let wy = 300 - h + 16; wy < 280; wy += 24) {
        for (let wx = x + 12; wx < x + w - 16; wx += 22) {
          out.push(<rect key={`${b}-${wx}-${wy}`} x={wx} y={wy} width="10" height="13" rx="1.5" fill="#f8fafc" opacity=".85" />)
        }
      }
    })
    return out
  }, [])

  return (
    <g>
      {BUILDINGS.map(([x, w, h, c], k) => (
        <rect key={k} x={x} y={300 - h} width={w} height={h} fill={c} />
      ))}
      {windows}
      {[150, 560, 960].map((lx) => (
        <g key={lx} fill="#64748b">
          <rect x={lx} y="206" width="4" height="96" />
          <path d={`M${lx + 2} 208 Q${lx + 2} 198 ${lx + 18} 198`} fill="none" stroke="#64748b" strokeWidth="3" />
          <rect x={lx + 14} y="198" width="12" height="5" rx="2" fill="#fbbf24" />
        </g>
      ))}

      {/* pavement gives way to grass */}
      <rect x="-40" y="300" width="1340" height="20" fill="#e5e7eb" />
      <rect x="1300" y="300" width="2500" height="20" fill="#84a83a" />

      {TREES.map(({ x, pine, s }) => (
        <g key={x} transform={`translate(${x} 304) scale(${s})`}>
          <rect x="-4" y="-40" width="8" height="42" fill="#92400e" />
          {pine ? (
            <g fill="#15803d">
              <path d="M0 -118 L22 -76 L-22 -76 Z" />
              <path d="M0 -96 L28 -48 L-28 -48 Z" />
              <path d="M0 -74 L32 -30 L-32 -30 Z" />
            </g>
          ) : (
            <g>
              <circle cy="-62" r="27" fill="#16a34a" />
              <circle cx="-17" cy="-48" r="19" fill="#16a34a" />
              <circle cx="17" cy="-50" r="19" fill="#16a34a" />
              <circle cx="-7" cy="-72" r="11" fill="#22c55e" />
            </g>
          )}
        </g>
      ))}

      {/* the reveal */}
      <g transform="translate(1700 0)">
        <rect x="-122" y="200" width="8" height="104" fill="#64748b" />
        <rect x="114" y="200" width="8" height="104" fill="#64748b" />
        <rect x="-170" y="104" width="340" height="104" rx="12" fill="#fff" stroke="#e2e8f0" strokeWidth="2" />
        <path d="M-158 104 H158 Q170 104 170 116 V118 H-170 V116 Q-170 104 -158 104 Z" fill="#f97316" />
        <clipPath id={`${uid}-bb`}>
          <motion.rect x="-160" y="120" height="84" width={reveal} />
        </clipPath>
        <g clipPath={`url(#${uid}-bb)`}>
          <text y="164" textAnchor="middle" fontFamily="Poppins, sans-serif" fontWeight="800" fontSize="34" fill="#ea580c" letterSpacing="-0.5">
            Namma Journey
          </text>
          <text y="191" textAnchor="middle" fontFamily="Inter, sans-serif" fontWeight="600" fontSize="13" fill="#475569">
            Book the ride. Keep the memories.
          </text>
        </g>
      </g>

      {/* almost there */}
      <g transform="translate(3060 304)">
        <path d="M-20 0 V-38 Q-20 -52 0 -52 Q20 -52 20 -38 V0 Z" fill="#fff" stroke="#cbd5e1" />
        <path d="M-20 -34 V-38 Q-20 -52 0 -52 Q20 -52 20 -38 V-34 Z" fill="#f97316" />
        <text y="-20" textAnchor="middle" fontFamily="Inter, sans-serif" fontWeight="700" fontSize="7" fill="#0f172a">{dest.short.toUpperCase()}</text>
        <text y="-9" textAnchor="middle" fontFamily="Inter, sans-serif" fontWeight="800" fontSize="9" fill="#ea580c">2 km</text>
      </g>
    </g>
  )
}

/* ══════════════════  people  ══════════════════ */

function Walker({ i, t, look, state }) {
  const b = boardAt(i), e = exitAt(i), d = walkDur(i)
  const lin = (p) => p
  const x = useTransform(t, [0, b, b + d, e, e + d, CYCLE], [X[i], X[i], DOOR, DOOR, X[i], X[i]], {
    ease: [lin, easeInOut, lin, easeInOut, lin],
  })
  const opacity = useTransform(t, [0, b + d - 180, b + d, e, e + 180, CYCLE], [1, 1, 0, 0, 1, 1])
  return (
    <motion.g style={{ x, opacity }} className={state.walking ? 'rts-walking' : undefined}>
      <Figure look={look} mood={state.mood} arms={state.arms} lean={state.lean} />
    </motion.g>
  )
}

export function Figure({ look, mood, arms, lean }) {
  const kurta = look.body === 'kurta'
  const hip = kurta ? -44 : -58
  const legW = kurta ? 9 : 12
  const legH = -hip - 2
  const [al, ar] = ARMS[arms]
  return (
    <g style={{ transform: `translate(0px, ${GROUND}px) rotate(${lean}deg)`, transition: 'transform .6s cubic-bezier(.16,1,.3,1)' }}>
      <ellipse cy="2" rx="21" ry="4.5" fill="rgba(15,23,42,.2)" />
      <g className="rts-bob">
        {[-1, 1].map((side) => (
          <g key={side} transform={`translate(${side * (kurta ? 6 : 7)} ${hip})`}>
            <g className={side < 0 ? 'rts-leg-a' : 'rts-leg-b'}>
              <rect x={-legW / 2} y="0" width={legW} height={legH} rx={legW / 2} fill={look.pants} />
              <ellipse cx={side * 2} cy={legH} rx="8" ry="4" fill="#1f2937" />
            </g>
          </g>
        ))}

        {kurta ? (
          <g>
            <path d="M-18 -98 Q-18 -110 -7 -110 L7 -110 Q18 -110 18 -98 L25 -40 Q0 -34 -25 -40 Z" fill={look.shirt} />
            <path d="M-13 -109 L17 -62 L11 -58 L-17 -101 Z" fill={look.scarf} />
          </g>
        ) : (
          <g>
            <rect x="-19" y="-60" width="38" height="6" rx="2" fill="#1f2937" />
            <path d="M-20 -98 Q-20 -110 -8 -110 L8 -110 Q20 -110 20 -98 L19 -58 L-19 -58 Z" fill={look.shirt} />
            <path d="M-6 -110 L0 -101 L6 -110" fill="none" stroke="rgba(15,23,42,.25)" strokeWidth="2" strokeLinejoin="round" />
          </g>
        )}

        <Arm side={-1} angle={al} sleeve={look.shirt} skin={look.skin} />
        <Arm side={1} angle={ar} sleeve={look.shirt} skin={look.skin} phone={arms === 'selfie'} />

        <rect x="-5" y="-116" width="10" height="9" fill={look.skin} />
        <g transform="translate(0 -129)">
          <Head look={look} mood={mood} />
        </g>
      </g>
    </g>
  )
}

function Arm({ side, angle, sleeve, skin, phone }) {
  return (
    <g style={{ transform: `translate(${side * 18}px, -102px) rotate(${angle}deg)`, transition: 'transform .5s cubic-bezier(.16,1,.3,1)' }}>
      <path d="M0 0 L0 20" stroke={sleeve} strokeWidth="10" strokeLinecap="round" />
      <path d="M0 19 L0 41" stroke={skin} strokeWidth="8" strokeLinecap="round" />
      <circle cy="44" r="5.2" fill={skin} />
      {phone && <rect x="-6" y="44" width="12" height="19" rx="2.5" fill="#0f172a" />}
    </g>
  )
}

function Head({ look, mood }) {
  const { skin, hair, style } = look
  return (
    <g>
      {style === 'long' && <path d="M-19 0 C-21 -26 21 -26 19 0 L21 30 Q0 36 -21 30 Z" fill={hair} />}
      {style === 'bun' && <circle cy="-20" r="9" fill={hair} />}
      <circle cx="-17" cy="1" r="3.6" fill={skin} />
      <circle cx="17" cy="1" r="3.6" fill={skin} />
      <circle r="17" fill={skin} />
      {look.beard && <path d="M-15.5 2 Q-15 17.5 0 18.5 Q15 17.5 15.5 2 Q11 12 0 12.5 Q-11 12 -15.5 2 Z" fill={hair} opacity=".9" />}
      <Hair style={style} color={hair} />
      {look.cap && (
        <g>
          <path d="M-17.5 -6 C-17 -25 17 -25 17.5 -6 Z" fill="#ea580c" />
          <ellipse cy="-6" rx="20" ry="3.2" fill="#c2410c" />
        </g>
      )}
      <Face mood={mood} />
      {look.bindi && <circle cy="-8" r="1.7" fill="#dc2626" />}
      {look.glasses && (
        <g fill="none" stroke="#1e293b" strokeWidth="1.6">
          <circle cx="-6.5" cy="-1" r="5" />
          <circle cx="6.5" cy="-1" r="5" />
          <path d="M-1.5 -1.5 L1.5 -1.5" />
        </g>
      )}
      {look.mustache && <path d="M-7 5 Q-3 2.6 0 4.4 Q3 2.6 7 5 Q3 6.6 0 5.6 Q-3 6.6 -7 5 Z" fill={hair} />}
    </g>
  )
}

function Hair({ style, color }) {
  if (style === 'curly') {
    return (
      <g fill={color}>
        <circle cx="-13" cy="-8" r="6.5" />
        <circle cx="-8" cy="-14" r="7" />
        <circle cx="0" cy="-16.5" r="7.5" />
        <circle cx="8" cy="-14" r="7" />
        <circle cx="13" cy="-8" r="6.5" />
      </g>
    )
  }
  if (style === 'long') return <path d="M-17.4 -1 C-17 -22 17 -22 17.4 -1 C12 -11 2 -14 -9 -11 C-13 -9 -16 -5 -17.4 -1 Z" fill={color} />
  if (style === 'bun') return <path d="M-18 3 C-20 -22 20 -22 18 3 C16.5 -8 9 -12.5 0 -12 C-9 -12.5 -16.5 -8 -18 3 Z" fill={color} />
  return <path d="M-17.6 -1 C-19.5 -23 19.5 -23 17.6 -1 C16 -9 9 -12.5 0 -12 C-9 -12.5 -16 -9 -17.6 -1 Z" fill={color} />
}

function Face({ mood }) {
  const ink = '#1e293b'
  return (
    <g>
      {mood === 'joy' ? (
        <g fill="none" stroke={ink} strokeWidth="2.1" strokeLinecap="round">
          <path d="M-9.5 0 Q-6.5 -4.8 -3.5 0" />
          <path d="M3.5 0 Q6.5 -4.8 9.5 0" />
        </g>
      ) : (
        <g fill={ink}>
          <circle cx="-6.5" cy="-1" r="2.1" />
          <circle cx="6.5" cy="-1" r="2.1" />
        </g>
      )}
      {mood === 'joy' && (
        <g fill="#fb7185" opacity=".45">
          <circle cx="-10.5" cy="6" r="3.2" />
          <circle cx="10.5" cy="6" r="3.2" />
        </g>
      )}
      {mood === 'talk' && <ellipse className="rts-talk" cy="9" rx="3.6" ry="2.8" fill="#7f1d1d" />}
      {mood === 'smile' && <path d="M-5 7.5 Q0 11.5 5 7.5" fill="none" stroke="#7c2d12" strokeWidth="2" strokeLinecap="round" />}
      {mood === 'joy' && (
        <g>
          <path d="M-7.5 6 Q0 18 7.5 6 Z" fill="#7f1d1d" />
          <path d="M-6.6 6.4 H6.6 L5.8 8.6 H-5.8 Z" fill="#fff" />
        </g>
      )}
    </g>
  )
}

function Bubble({ text, show, anchor, view }) {
  const w = text.length * 7.6 + 30
  const min = view.x + w / 2 + 10
  const max = view.x + view.w - w / 2 - 10
  const cx = Math.min(max, Math.max(min, anchor.x))
  const tail = anchor.x - cx
  return (
    <g transform={`translate(${cx} ${anchor.y})`}>
      <motion.g
        initial={false}
        animate={show ? { opacity: 1, scale: 1, y: 0 } : { opacity: 0, scale: 0.6, y: 10 }}
        transition={{ duration: 0.38, ease: EASE }}
        style={{ originY: 1 }}
      >
        <g className="rts-bubble">
          <rect x={-w / 2} y="-32" width={w} height="30" rx="15" fill="#fff" />
          <path d={`M${tail - 7} -4 L${tail} 8 L${tail + 7} -4 Z`} fill="#fff" />
          <text y="-12" textAnchor="middle" fontFamily="Inter, sans-serif" fontWeight="700" fontSize="13" fill="#0f172a">
            {text}
          </text>
        </g>
      </motion.g>
    </g>
  )
}

/* ══════════════════  the car  ══════════════════ */

function Car({ uid, people }) {
  const glass = `${uid}-glass`
  return (
    <g>
      <ellipse cx="170" cy="170" rx="160" ry="7" fill="rgba(15,23,42,.28)" />
      <g className="rts-carbody">
        {/* roof rack and bags */}
        <rect x="48" y="25" width="160" height="5" rx="2" fill="#334155" />
        <rect x="62" y="30" width="4" height="5" fill="#334155" />
        <rect x="190" y="30" width="4" height="5" fill="#334155" />
        <rect x="62" y="7" width="54" height="19" rx="4" fill="#0f766e" />
        <rect x="86" y="7" width="5" height="19" fill="#115e59" />
        <rect x="122" y="11" width="42" height="15" rx="4" fill="#fbbf24" />
        <rect x="140" y="11" width="5" height="15" fill="#d97706" />

        <path d="M10 134 V72 Q10 36 44 34 L224 30 Q248 30 264 48 L304 90 Q328 95 330 114 V134 Q330 144 320 144 H20 Q10 144 10 134 Z" fill="#f97316" />
        <path d="M10 118 H330 V134 Q330 144 320 144 H20 Q10 144 10 134 Z" fill="#ea580c" />
        <rect x="12" y="100" width="316" height="6" fill="#fff" opacity=".9" />

        {/* glass, and whoever is behind it */}
        <clipPath id={glass}>
          <rect x="30" y="46" width="75" height="42" rx="6" />
          <rect x="115" y="46" width="80" height="42" rx="6" />
          <path d="M205 44 H238 Q246 44 252 51 L284 88 H205 Z" />
        </clipPath>
        <g clipPath={`url(#${glass})`}>
          <rect x="20" y="40" width="280" height="54" fill="#d6ebf7" />
          <Seat x={236} look={DRIVER} mood="smile" visible />
          {FRIENDS.map((look, i) => (
            <Seat key={i} x={SEATS[i]} look={look} mood={people[i].mood === 'talk' ? 'smile' : people[i].mood} visible={people[i].seated} />
          ))}
          <path d="M40 92 L78 40 H96 L58 92 Z M150 92 L188 40 H198 L160 92 Z" fill="#fff" opacity=".28" />
        </g>

        <path d="M110 40 V138 M200 40 V138" stroke="#c2410c" strokeWidth="1.5" opacity=".55" />
        <rect x="120" y="92" width="13" height="3.5" rx="1.75" fill="#9a3412" />
        <rect x="208" y="92" width="13" height="3.5" rx="1.75" fill="#9a3412" />
        <path d="M252 70 L264 66 V78 L252 78 Z" fill="#1f2937" />
        <text x="155" y="130" textAnchor="middle" fontFamily="Poppins, sans-serif" fontWeight="700" fontSize="9.5" fill="#fff" opacity=".95">Namma Journey</text>

        <path d="M312 100 Q327 100 329 111 L314 111 Z" fill="#fef9c3" />
        <rect x="10" y="84" width="6" height="16" rx="2" fill="#dc2626" />
        <rect x="302" y="126" width="32" height="10" rx="4" fill="#334155" />
        <rect x="6" y="126" width="22" height="10" rx="4" fill="#334155" />

        <path d="M49 144 A31 31 0 0 1 111 144 Z" fill="#1f2937" />
        <path d="M231 144 A31 31 0 0 1 293 144 Z" fill="#1f2937" />
      </g>

      {[80, 262].map((wx) => (
        <g key={wx} transform={`translate(${wx} 144)`}>
          <g className="rts-wheel">
            <circle r="25" fill="#111827" />
            <circle r="14" fill="#e5e7eb" />
            <path d="M0 -12 V12 M-12 0 H12" stroke="#94a3b8" strokeWidth="3" />
            <circle r="4" fill="#64748b" />
          </g>
        </g>
      ))}
    </g>
  )
}

function Seat({ x, look, mood, visible }) {
  return (
    <g className="rts-seat" style={{ opacity: visible ? 1 : 0 }} transform={`translate(${x} 68)`}>
      <ellipse cy="26" rx="18" ry="11" fill={look.shirt} />
      <g transform="scale(.78)">
        <Head look={look} mood={mood} />
      </g>
    </g>
  )
}
