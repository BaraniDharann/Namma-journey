import React, { useEffect, useId, useMemo, useRef, useState } from 'react'
import {
  AnimatePresence, motion, useAnimationFrame, useMotionValue, useMotionValueEvent,
  useReducedMotion, useTransform, easeIn, easeInOut, easeOut,
} from 'framer-motion'
import { EASE, useIsCompact, useOnScreen } from '../motion/primitives'
import Person, { shade } from './Person'

/**
 * The City hero, filling the window: one loop every ~19s on a Bengaluru street.
 *
 * A crowd waits under the shelter → a packed 500D pulls in and they push in
 * through the door (the last one rides the footboard) → she is left behind,
 * books a bike on Namma Journey → the rider arrives and hands her a helmet →
 * they squeeze past the bus stuck at the signal while its passengers stare →
 * one of them starts booking too.
 *
 * The camera sits on the pavement, looking across the road. Everything hangs off
 * one clock (`t`, ms into the loop): motion is derived from it as motion values,
 * discrete state (poses, moods, who is talking) re-renders only when it changes.
 * On a phone the frame is narrower and the camera pans to follow the action.
 */

const T = {
  busIn: 2600,
  busStop: 4200,
  rush: 4500,
  full: 7400,
  busGo: 8400,
  phone: 8600,
  pick: 10400,
  booked: 11100,
  bikeIn: 11400,
  bikeStop: 12600,
  helmet: 13300,
  mount: 14400,
  go: 14800,
  pass: 15900,
  convert: 17200,
}
const CYCLE = 19400
/** How long the bus takes to crawl to the signal; the phone close-up waits for it, so it never covers the bus. */
const BUS_PULL = 1200
/** The frame a reduced-motion visitor sees: she is on the bike, the bus is stuck at the signal. */
const STILL = T.mount + 200

const W = 1600
const H = 900
const GROUND = 862
const LANE = 785
const BUS_Y = 464
const BUS_STOP_X = 300
const BUS_SIGNAL_X = 30
const DOOR = BUS_STOP_X + 188
const BIKE_STOP_X = 1330
const HER_X = 1240

const lin = (p) => p

/* ── who is in the story ── */

const OFFICE = { body: 'shirt', top: '#93c5fd', sleeves: 'long', pants: '#334155', shoes: 'shoe', skin: '#9a6440', hair: '#111827', hairStyle: 'short', watch: true, bag: 'backpack', bagColor: '#1f2937', pocket: true, mustache: true }
const GIRL = { body: 'kurta', top: '#be185d', sleeves: 'three', trim: '#fbbf24', scarf: '#fde68a', pants: '#f1f5f9', shoes: 'sandal', skin: '#c68a5e', hair: '#1c1917', hairStyle: 'long', bindi: true, earring: true, bangles: true }
const AUNTY = { body: 'saree', top: '#15803d', saree: '#16a34a', border: '#facc15', sleeves: 'short', pants: '#15803d', shoes: 'sandal', skin: '#a86b45', hair: '#27272a', hairStyle: 'bun', flowers: true, bindi: true, earring: true, bangles: true }
const STUDENT = { body: 'tshirt', top: '#f59e0b', sleeves: 'short', pants: '#1e3a5f', shoes: 'sneaker', skin: '#8d5a3b', hair: '#1c1917', hairStyle: 'curly', bag: 'backpack', bagColor: '#0f766e' }
const ELDER = { body: 'shirt', top: '#f5f5f4', sleeves: 'long', pants: '#78716c', shoes: 'sandal', skin: '#a0673f', hair: '#d6d3d1', hairStyle: 'grey', glasses: true, mustache: true }
const HER = { body: 'kurta', top: '#0f766e', sleeves: 'three', trim: '#fbbf24', scarf: '#fbbf24', pants: '#e2e8f0', shoes: 'sandal', skin: '#b5774c', hair: '#1c1917', hairStyle: 'braid', bindi: true, earring: true, bag: 'sling', bagColor: '#92400e', watch: true }
const RIDER = { body: 'jacket', top: '#ea580c', sleeves: 'long', pants: '#1e293b', shoes: 'shoe', skin: '#8d5524', hair: '#111827', hairStyle: 'short' }
const DRIVER = { body: 'shirt', top: '#b9a172', sleeves: 'short', pants: '#7c6a46', shoes: 'shoe', skin: '#8d5524', hair: '#111827', hairStyle: 'short', mustache: true }

/** The crowd at the stop, left to right. The office-goer boards last and ends up on the footboard. */
const COMMUTERS = [
  { x: 70, look: OFFICE, wait: 'watch' },
  { x: 150, look: GIRL, bench: true },
  { x: 224, look: AUNTY, bench: true },
  { x: 330, look: STUDENT, wait: 'phone', hold: 'phone', mood: 'focus' },
  { x: 405, look: ELDER, hold: 'umbrella' },
]
const HANGER = 0
const CONVERT = 3
/** Nearest the door pushes in first. */
const RANK = [4, 3, 2, 1, 0]
const startAt = (i) => T.rush + RANK[i] * 420
const walkEnd = (i) => startAt(i) + 300 + Math.abs(COMMUTERS[i].x - DOOR) * 3.2
const climbEnd = (i) => walkEnd(i) + 450

/** Standing room inside the bus; the commuters take the gaps as they board. */
const SLOTS = [258, 300, 342, 384, 428, 470, 514, 556, 600, 642, 686, 728, 768]
const SLOT_OF = { 4: 1, 3: 4, 2: 6, 1: 9 }
const PASSENGERS = {
  0: { body: 'shirt', top: '#e11d48', sleeves: 'short', pants: '#1f2937', shoes: 'shoe', skin: '#a86b45', hair: '#111827', hairStyle: 'short' },
  2: { body: 'kurta', top: '#7c3aed', sleeves: 'three', trim: '#fde68a', scarf: '#f9a8d4', pants: '#f1f5f9', shoes: 'sandal', skin: '#c68a5e', hair: '#231815', hairStyle: 'long', bindi: true },
  3: { body: 'tshirt', top: '#22c55e', sleeves: 'short', pants: '#334155', shoes: 'sneaker', skin: '#8d5524', hair: '#1c1917', hairStyle: 'curly' },
  5: { body: 'shirt', top: '#f8fafc', sleeves: 'long', pants: '#1f2937', shoes: 'shoe', skin: '#9a6440', hair: '#111827', hairStyle: 'short', glasses: true },
  7: { body: 'saree', top: '#b91c1c', saree: '#dc2626', border: '#fbbf24', sleeves: 'short', pants: '#b91c1c', shoes: 'sandal', skin: '#a0673f', hair: '#27272a', hairStyle: 'bun', bindi: true },
  8: { body: 'shirt', top: '#0ea5e9', sleeves: 'short', pants: '#57534e', shoes: 'shoe', skin: '#b07a4f', hair: '#292524', hairStyle: 'short', beard: true },
  10: { body: 'tshirt', top: '#1f2937', sleeves: 'short', pants: '#1e3a5f', shoes: 'sneaker', skin: '#8d5a3b', hair: '#111827', hairStyle: 'short', mustache: true },
  11: { body: 'kurta', top: '#f97316', sleeves: 'three', trim: '#fef3c7', scarf: '#fde68a', pants: '#fef3c7', shoes: 'sandal', skin: '#b5774c', hair: '#1c1917', hairStyle: 'braid' },
  12: { body: 'shirt', top: '#a16207', sleeves: 'long', pants: '#44403c', shoes: 'shoe', skin: '#8d5524', hair: '#e7e5e4', hairStyle: 'grey', mustache: true },
}
const SHOCKED_SPEAKER = 10

const BUBBLES = [
  { text: 'Bus is late again…', from: 900, to: 2300, who: 0, x: 165, y: 634 },
  { text: 'Full. Again.', from: T.full + 100, to: T.full + 1400, who: 'her', x: HER_X, y: 634 },
  { text: 'Priya? Your helmet.', from: T.bikeStop + 100, to: T.helmet + 200, who: 'rider', x: BIKE_STOP_X - 20, y: 592 },
  { text: 'Thanks! Let’s go.', from: T.helmet + 400, to: T.mount + 100, who: 'her', x: BIKE_STOP_X + 10, y: 596 },
  { text: 'Wait… how?!', from: T.pass + 100, to: T.convert, who: 'bus', x: BUS_SIGNAL_X + SLOTS[SHOCKED_SPEAKER], y: 450 },
  { text: 'Booking one tomorrow!', from: T.convert + 100, to: CYCLE - 700, who: 'convert', x: BUS_SIGNAL_X + SLOTS[SLOT_OF[CONVERT]], y: 450 },
]

/* ── the discrete half of the clock ── */

function snapshot(v) {
  let bubble = -1
  BUBBLES.forEach((b, k) => { if (v >= b.from && v < b.to) bubble = k })
  const speaker = bubble >= 0 ? BUBBLES[bubble].who : null
  const shock = v >= T.pass && v < CYCLE - 500

  const people = COMMUTERS.map((c, i) => {
    const s = startAt(i), w = walkEnd(i)
    const waiting = v < s
    let pose = 'stand'
    if (c.bench && waiting) pose = 'sit'
    else if (waiting && c.wait === 'watch') pose = v < 900 ? 'watch' : 'stand'
    else if (waiting && c.wait) pose = c.wait
    if (speaker === i) pose = 'talk'
    return {
      pose,
      walking: v >= s && v < w,
      climbing: v >= w && v < climbEnd(i),
      inBus: v >= climbEnd(i) - 100,
      mood: speaker === i ? 'talk' : waiting ? (c.mood || 'tired') : 'neutral',
      hold: waiting ? c.hold : c.hold === 'umbrella' ? 'umbrella' : null,
    }
  })

  const herWalking = (v >= T.rush + 600 && v < T.rush + 1200) || (v >= T.full && v < T.full + 700) || (v >= T.helmet + 200 && v < T.helmet + 1000)
  const her = {
    walking: herWalking,
    facing: v < T.busIn ? 1 : v < T.full ? -1 : v < T.full + 700 ? 1 : v < T.helmet + 200 ? -1 : 1,
    pose: v >= T.phone && v < T.bikeStop ? 'phone' : speaker === 'her' ? 'talk' : 'stand',
    mood: speaker === 'her' ? 'talk' : v >= T.bikeStop ? 'smile' : v >= T.booked ? 'joy' : v >= T.phone ? 'focus' : 'tired',
    helmet: v >= T.helmet + 200,
    onBike: v >= T.mount,
  }

  const chip =
    v < T.busIn ? '8:40 am · Silk Board bus stop'
      : v < T.rush ? 'The 500D pulls in, already packed'
        : v < T.phone ? 'Everyone pushes in'
          : v < T.booked ? 'She opens Namma Journey'
            : v < T.bikeStop ? 'Rider on the way'
              : v < T.go ? 'Right rider, helmet on'
                : v < CYCLE - 700 ? 'The bus is still at the signal'
                  : null

  const s = {
    bubble,
    speaker,
    people,
    her,
    chip,
    rider: v >= T.bikeStop && v < T.helmet + 200 ? 'offer' : 'ride',
    phone: v >= T.busGo + BUS_PULL && v < T.bikeIn + 800 ? (v < T.pick ? 'search' : v < T.booked ? 'pick' : 'booked') : null,
    doorOpen: v >= T.busStop + 150,
    hanger: v >= climbEnd(HANGER) - 100,
    busMoving: (v >= T.busIn && v < T.busStop) || (v >= T.busGo && v < T.busGo + BUS_PULL),
    bikeMoving: (v >= T.bikeIn && v < T.bikeStop) || v >= T.go,
    shock,
    convert: v >= T.convert,
  }
  s.key = JSON.stringify(s)
  return s
}

/* ══════════════════  the story  ══════════════════ */

export default function CityRideStory({ active = true }) {
  const reduced = useReducedMotion()
  const compact = useIsCompact('(max-width: 899px)')
  const hostRef = useRef(null)
  const onScreen = useOnScreen(hostRef, '0px')
  const uid = useId().replace(/:/g, '')
  const [aspect, setAspect] = useState(1.6)

  const [paused, setPaused] = useState(false)
  const t = useMotionValue(reduced ? STILL : 0)
  const [snap, setSnap] = useState(() => snapshot(reduced ? STILL : 0))
  const snapKey = useRef(snap.key)

  const running = !reduced && !paused && onScreen && active
  const runningRef = useRef(running)
  useEffect(() => { runningRef.current = running }, [running])

  useEffect(() => {
    if (reduced) t.set(STILL)
  }, [reduced, t])

  // Wide windows crop the sky; tall ones keep the whole street and let the sky run up behind the text.
  useEffect(() => {
    const el = hostRef.current
    if (!el || typeof ResizeObserver === 'undefined') return undefined
    const ro = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      if (width && height) setAspect(width / height)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  useAnimationFrame((_, delta) => {
    if (!runningRef.current) return
    let next = t.get() + Math.min(delta, 100)
    if (next >= CYCLE) next -= CYCLE
    t.set(next)
  })

  useMotionValueEvent(t, 'change', (v) => {
    const s = snapshot(v)
    if (s.key !== snapKey.current) {
      snapKey.current = s.key
      setSnap(s)
    }
  })

  /* ── the continuous half of the clock ── */
  // A phone sees a 600-unit slice: the crowd, then her and the bike, then the bus windows as they pass.
  const VW = compact ? 600 : W
  const camX = useTransform(
    t,
    [0, 6900, 7700, T.go, T.go + 1500, CYCLE],
    compact ? [-20, -20, -900, -900, -300, -300] : [0, 0, 0, 0, 0, 0],
    { ease: [lin, easeInOut, lin, easeInOut, lin] }
  )
  const busX = useTransform(
    t,
    [0, T.busIn, T.busStop, T.busGo, T.busGo + BUS_PULL, CYCLE],
    [1750, 1750, BUS_STOP_X, BUS_STOP_X, BUS_SIGNAL_X, BUS_SIGNAL_X],
    { ease: [lin, easeOut, lin, easeInOut, lin] }
  )
  const bikeX = useTransform(
    t,
    [0, T.bikeIn, T.bikeStop, T.go, T.go + 3000, CYCLE],
    [1900, 1900, BIKE_STOP_X, BIKE_STOP_X, -340, -340],
    { ease: [lin, easeOut, lin, easeIn, lin] }
  )
  const herX = useTransform(
    t,
    [0, T.rush + 600, T.rush + 1200, T.full, T.full + 700, T.helmet + 200, T.helmet + 1000, CYCLE],
    [HER_X, HER_X, 1070, 1070, HER_X, HER_X, BIKE_STOP_X + 46, BIKE_STOP_X + 46],
    { ease: [lin, easeInOut, lin, easeInOut, lin, easeInOut, lin] }
  )
  // Off the kerb and over to the bike: a step into the road, so a touch higher and smaller.
  const herDepth = useTransform(t, [0, T.helmet + 200, T.helmet + 1000, CYCLE], [0, 0, 1, 1], { ease: [lin, easeInOut, lin] })
  const herY = useTransform(herDepth, (d) => GROUND - d * 64)
  const herScale = useTransform(herDepth, (d) => 1 - d * 0.07)
  const autoX = useTransform(t, [0, 200, 4600, CYCLE], [-260, -260, 1820, 1820])
  const carX = useTransform(t, [0, 9200, 12600, CYCLE], [-320, -320, 1860, 1860])
  const trainX = useTransform(t, [0, 1200, 10200, CYCLE], [-760, -760, 1700, 1700])
  const dip = useTransform(t, [0, 450, CYCLE - 650, CYCLE - 150, CYCLE], [1, 0, 0, 1, 1])
  const progress = useTransform(t, (v) => v / CYCLE)

  const fit = aspect > VW / H ? 'xMidYMax slice' : 'xMidYMax meet'
  const talkScale = compact ? 1.45 : 1.2

  return (
    <div ref={hostRef} className={`crs${paused ? ' is-paused' : ''}`}>
      <svg
        className="crs-svg"
        viewBox={`0 0 ${VW} ${H}`}
        preserveAspectRatio={fit}
        role="img"
        aria-label="At a crowded bus stop a packed bus takes everyone but her. She books a Namma Journey bike, the rider arrives and hands her a helmet, and they ride past the bus stuck at the signal while its passengers stare. One of them starts booking too."
      >
        <defs>
          <linearGradient id={`${uid}-sky`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#bcd6ee" />
            <stop offset="0.5" stopColor="#dde9f3" />
            <stop offset="0.66" stopColor="#fdf0e0" />
          </linearGradient>
          <radialGradient id={`${uid}-sun`}>
            <stop offset="0" stopColor="#fff7d6" />
            <stop offset="0.35" stopColor="#fde68a" stopOpacity=".7" />
            <stop offset="1" stopColor="#fde68a" stopOpacity="0" />
          </radialGradient>
        </defs>
        <rect x="-10" y="-10" width={VW + 20} height={H + 20} fill={`url(#${uid}-sky)`} />

        <motion.g style={{ x: camX }}>
          <Backdrop uid={uid} trainX={trainX} />
          <Road />

          {/* traffic the other way, behind the bus */}
          <motion.g style={{ x: autoX }}>
            <g transform="translate(0 628) scale(.8)" className="cs-moving"><Auto /></g>
          </motion.g>
          <motion.g style={{ x: carX }}>
            <g transform="translate(0 630) scale(.8)" className="cs-moving"><Car /></g>
          </motion.g>

          <motion.g style={{ x: busX, y: BUS_Y }} className={snap.busMoving ? 'cs-moving' : undefined}>
            <Bus uid={uid} snap={snap} />
          </motion.g>

          <motion.g style={{ x: bikeX, y: LANE }} className={snap.bikeMoving ? 'cs-moving' : undefined}>
            <g transform="scale(-.94 .94)">
              <Bike rider={snap.rider} pillion={snap.her.onBike} herMood={snap.her.mood} shock={snap.shock} />
            </g>
          </motion.g>

          <Pavement />
          <Shelter />

          {COMMUTERS.map((c, i) => (
            <Commuter key={i} i={i} t={t} state={snap.people[i]} />
          ))}

          {!snap.her.onBike && (
            <motion.g style={{ x: herX, y: herY, scale: herScale, originX: 0.5, originY: 1 }}>
              <Person
                look={HER}
                pose={snap.her.pose}
                mood={snap.her.mood}
                facing={snap.her.facing}
                walking={snap.her.walking}
                idle={!snap.her.walking && snap.her.pose === 'stand'}
                blink={1.3}
                hold={snap.her.pose === 'phone' ? 'phone' : null}
                helmet={snap.her.helmet ? 'pillion' : null}
              />
            </motion.g>
          )}

          <AnimatePresence>
            {snap.phone && (
              <motion.g
                key="phone"
                initial={{ opacity: 0, y: 26 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 18 }}
                transition={{ duration: 0.5, ease: EASE }}
              >
                <PhonePop step={snap.phone} compact={compact} />
              </motion.g>
            )}
          </AnimatePresence>

          {BUBBLES.map((b, k) => (
            <SpeechBubble key={k} text={b.text} show={snap.bubble === k} x={b.x} y={b.y} scale={talkScale} />
          ))}
        </motion.g>
      </svg>

      <motion.div className="crs-dip" style={{ opacity: dip }} aria-hidden="true" />

      <div className="crs-chip-slot" aria-hidden="true">
        <AnimatePresence mode="wait" initial={false}>
          {snap.chip && (
            <motion.span
              key={snap.chip}
              className="crs-chip"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.35, ease: EASE }}
            >
              {snap.chip}
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {!reduced && <motion.div className="crs-progress" style={{ scaleX: progress }} aria-hidden="true" />}
      {!reduced && (
        <button
          type="button"
          className="crs-ctrl"
          onClick={() => setPaused((p) => !p)}
          aria-pressed={paused}
          aria-label={paused ? 'Play the city ride animation' : 'Pause the city ride animation'}
        >
          {paused ? (
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.8v10.4a.6.6 0 0 0 .9.5l8.4-5.2a.6.6 0 0 0 0-1L3.9 1.3a.6.6 0 0 0-.9.5Z" fill="currentColor" /></svg>
          ) : (
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><rect x="2.5" y="1.5" width="3.2" height="11" rx="1" fill="currentColor" /><rect x="8.3" y="1.5" width="3.2" height="11" rx="1" fill="currentColor" /></svg>
          )}
        </button>
      )}
    </div>
  )
}

/* ══════════════════  people at the stop  ══════════════════ */

function Commuter({ i, t, state }) {
  const c = COMMUTERS[i]
  const s = startAt(i), w = walkEnd(i), e = climbEnd(i)
  const x = useTransform(t, [0, s, w, CYCLE], [c.x, c.x, DOOR, DOOR], { ease: [lin, easeInOut, lin] })
  // Up the steps and into the bus: a little higher, a little smaller, then gone.
  const climb = useTransform(t, [0, w, e, CYCLE], [0, 0, 1, 1], { ease: [lin, easeIn, lin] })
  const y = useTransform(climb, (k) => GROUND - k * 82)
  const scale = useTransform(climb, (k) => 1 - k * 0.14)
  const opacity = useTransform(t, [0, e - 160, e, CYCLE], [1, 1, 0, 0])
  return (
    <motion.g style={{ x, y, scale, opacity, originX: 0.5, originY: 1 }}>
      <Person
        look={c.look}
        pose={state.pose}
        mood={state.mood}
        walking={state.walking}
        idle={!state.walking && state.pose !== 'phone'}
        blink={i * 0.9}
        hold={state.hold}
      />
    </motion.g>
  )
}

/* ══════════════════  the street  ══════════════════ */

const SKYLINE = [[0, 120, 250], [110, 90, 200], [220, 140, 290], [390, 100, 230], [520, 130, 270], [700, 110, 210], [840, 150, 300], [1020, 100, 240], [1150, 130, 280], [1310, 90, 220], [1430, 170, 260]]
const BLOCKS = [
  { x: -40, w: 230, h: 250, c: '#e7d8c4', awning: '#0f766e' },
  { x: 200, w: 180, h: 300, c: '#d9c7b0', awning: '#b45309' },
  { x: 390, w: 250, h: 220, c: '#efe4d3', awning: '#be123c' },
  { x: 650, w: 200, h: 280, c: '#d6ccbd', awning: '#1d4ed8' },
  { x: 860, w: 240, h: 240, c: '#ead9c2', awning: '#15803d' },
  { x: 1110, w: 190, h: 310, c: '#dccbb5', awning: '#7c3aed' },
  { x: 1310, w: 260, h: 230, c: '#eee2d0', awning: '#c2410c' },
  { x: 1580, w: 200, h: 270, c: '#d9c7b0', awning: '#0f766e' },
]

function Backdrop({ uid, trainX }) {
  const windows = useMemo(() => {
    const out = []
    BLOCKS.forEach((b, k) => {
      for (let y = 548 - b.h + 22; y < 470; y += 34) {
        for (let x = b.x + 18; x < b.x + b.w - 30; x += 36) {
          out.push(<rect key={`${k}-${x}-${y}`} x={x} y={y} width="20" height="22" rx="2" fill="#f8fafc" opacity=".8" />)
          out.push(<rect key={`${k}-${x}-${y}s`} x={x} y={y + 18} width="20" height="4" fill={shade(b.c, -0.25)} opacity=".6" />)
        }
      }
    })
    return out
  }, [])
  return (
    <g>
      <circle cx="1330" cy="150" r="130" fill={`url(#${uid}-sun)`} />
      <circle cx="1330" cy="150" r="40" fill="#fff4c2" />
      {[[140, 120, 1], [560, 80, 0.8], [980, 140, 1.1], [1500, 90, 0.9]].map(([cx, cy, s], k) => (
        <g key={k} className="cs-cloud" style={{ animationDelay: `${-k * 9}s` }}>
          <g transform={`translate(${cx} ${cy}) scale(${s})`} fill="#fff" opacity=".85">
            <ellipse rx="60" ry="16" />
            <ellipse cx="-22" cy="-10" rx="28" ry="18" />
            <ellipse cx="18" cy="-16" rx="32" ry="22" />
          </g>
        </g>
      ))}

      {/* the city behind, in haze */}
      {SKYLINE.map(([x, w, h], k) => <rect key={k} x={x} y={548 - h} width={w} height={h} fill="#d5dfe9" />)}

      {/* Namma Metro, on its viaduct */}
      <g>
        {[180, 620, 1060, 1500].map((px) => (
          <g key={px}>
            <rect x={px - 14} y="300" width="28" height="248" fill="#cfcac3" />
            <path d={`M${px - 34} 300 H${px + 34} L${px + 18} 322 H${px - 18} Z`} fill="#c4beb6" />
          </g>
        ))}
        <motion.g style={{ x: trainX }}>
          {[0, 1, 2].map((k) => (
            <g key={k} transform={`translate(${k * 196} 0)`}>
              <rect x="0" y="232" width="188" height="48" rx={k === 0 ? 0 : 6} fill="#f1f5f9" />
              <rect x="0" y="262" width="188" height="9" fill="#7e22ce" />
              {[14, 52, 90, 128, 160].map((wx) => <rect key={wx} x={wx} y="240" width="22" height="16" rx="3" fill="#475569" />)}
            </g>
          ))}
          <path d="M588 232 H604 Q624 236 628 256 V280 H588 Z" fill="#f1f5f9" />
          <rect x="588" y="262" width="40" height="9" fill="#7e22ce" />
        </motion.g>
        <rect x="-60" y="280" width={W + 200} height="22" fill="#d8d3cc" />
        <rect x="-60" y="296" width={W + 200} height="6" fill="#7e22ce" opacity=".85" />
      </g>

      {/* the far side of the road: shops under the flats */}
      {BLOCKS.map((b) => <rect key={b.x} x={b.x} y={548 - b.h} width={b.w} height={b.h} fill={b.c} />)}
      {windows}
      {BLOCKS.map((b) => (
        <g key={`s${b.x}`}>
          <rect x={b.x + 10} y="484" width={b.w - 20} height="64" fill="#57534e" />
          <path d={`M${b.x + 4} 472 H${b.x + b.w - 4} L${b.x + b.w - 14} 490 H${b.x + 14} Z`} fill={b.awning} />
          <path d={`M${b.x + 14} 490 H${b.x + b.w - 14}`} stroke="#fff" strokeWidth="3" strokeDasharray="10 10" opacity=".6" />
          {[0, 1, 2, 3].map((r) => <path key={r} d={`M${b.x + 14} ${500 + r * 12} H${b.x + b.w - 14}`} stroke="#78716c" strokeWidth="2" />)}
        </g>
      ))}

      {/* rain trees */}
      {[[560, 1], [1230, 1.15]].map(([tx, s]) => (
        <g key={tx} transform={`translate(${tx} 552) scale(${s})`}>
          <path d="M-7 0 L-5 -70 Q-30 -96 -54 -100 M5 -70 Q20 -98 46 -104 M-5 -70 L5 0" stroke="#6b4423" strokeWidth="10" fill="none" strokeLinecap="round" />
          <ellipse cx="-50" cy="-120" rx="62" ry="32" fill="#4d7c0f" />
          <ellipse cx="30" cy="-128" rx="70" ry="36" fill="#4d7c0f" />
          <ellipse cx="-8" cy="-150" rx="66" ry="34" fill="#65a30d" />
          <ellipse cx="-30" cy="-136" rx="34" ry="16" fill="#84cc16" opacity=".5" />
        </g>
      ))}

      {/* the far pavement and the signal the bus will wait at */}
      <rect x="-60" y="546" width={W + 200} height="16" fill="#d6d3d1" />
      <g transform="translate(40 0)">
        <rect x="-4" y="388" width="8" height="166" fill="#334155" />
        <rect x="-14" y="352" width="28" height="74" rx="7" fill="#1f2937" />
        <circle cy="370" r="8" fill="#ef4444" />
        <circle cy="370" r="15" fill="#ef4444" opacity=".25" className="cs-glow" />
        <circle cy="389" r="7" fill="#44403c" />
        <circle cy="408" r="7" fill="#44403c" />
      </g>
    </g>
  )
}

function Road() {
  return (
    <g>
      <rect x="-60" y="562" width={W + 200} height="236" fill="#434b57" />
      <rect x="-60" y="562" width={W + 200} height="10" fill="#3a414c" />
      {Array.from({ length: 22 }, (_, i) => (
        <rect key={i} x={i * 84 - 40} y="640" width="46" height="5" rx="2" fill="#f8fafc" opacity=".75" />
      ))}
      {/* the stop line under the signal */}
      <rect x="14" y="650" width="8" height="140" fill="#f8fafc" opacity=".7" />
    </g>
  )
}

function Pavement() {
  return (
    <g>
      {Array.from({ length: 46 }, (_, i) => (
        <rect key={i} x={i * 40 - 60} y="790" width="40" height="10" fill={i % 2 ? '#1f2937' : '#facc15'} />
      ))}
      <rect x="-60" y="800" width={W + 200} height="100" fill="#e2ddd6" />
      {[826, 858].map((y) => <path key={y} d={`M-60 ${y} H${W + 140}`} stroke="#cfc8be" strokeWidth="2" />)}
      {Array.from({ length: 30 }, (_, i) => (
        <path key={i} d={`M${i * 60 - 40 + (i % 2) * 30} 800 V900`} stroke="#d4cdc3" strokeWidth="2" />
      ))}
    </g>
  )
}

function Shelter() {
  return (
    <g>
      {[22, 300].map((px) => <rect key={px} x={px} y="568" width="9" height="296" fill="#475569" />)}
      <rect x="10" y="556" width="302" height="18" rx="4" fill="#0f766e" />
      <rect x="10" y="574" width="302" height="12" fill="#0b5e58" />
      <text x="161" y="570" textAnchor="middle" fontFamily="Nunito Sans, Inter, sans-serif" fontWeight="900" fontSize="13" letterSpacing="2" fill="#fff">BUS STOP · SILK BOARD</text>
      <rect x="26" y="586" width="270" height="6" fill="#e0f2fe" opacity=".5" />
      {/* the bench */}
      <rect x="104" y="768" width="168" height="10" rx="3" fill="#92400e" />
      <rect x="100" y="811" width="176" height="8" rx="3" fill="#b45309" />
      {[110, 262].map((lx) => <rect key={lx} x={lx} y="819" width="6" height="43" fill="#57534e" />)}
    </g>
  )
}

/* ══════════════════  vehicles  ══════════════════ */

function Wheel({ r, rim = '#cbd5e1' }) {
  return (
    <g className="cs-wheel">
      <circle r={r} fill="#111827" />
      <circle r={r * 0.52} fill={rim} />
      <path d={`M0 ${-r * 0.42} V${r * 0.42} M${-r * 0.42} 0 H${r * 0.42}`} stroke="#64748b" strokeWidth="3" />
      <circle r={r * 0.14} fill="#475569" />
    </g>
  )
}

function Bus({ uid, snap }) {
  const glass = `${uid}-busglass`
  const front = `${uid}-busfront`
  const { people, shock, convert, doorOpen, hanger } = snap
  return (
    <g>
      <ellipse cx="410" cy="306" rx="420" ry="10" fill="rgba(15,23,42,.3)" />
      <g className="cs-body">
        <path d="M0 44 Q0 0 44 0 H800 Q822 0 822 22 V262 Q822 274 810 274 H12 Q0 274 0 262 Z" fill="#f1f5f9" />
        <path d="M0 184 H822 V262 Q822 274 810 274 H12 Q0 274 0 262 Z" fill="#1d4ed8" />
        <rect x="0" y="178" width="822" height="8" fill="#fbbf24" />
        <rect x="0" y="0" width="822" height="10" rx="5" fill="#e2e8f0" />

        {/* route board */}
        <rect x="80" y="14" width="240" height="30" rx="4" fill="#0f172a" />
        <text x="200" y="35" textAnchor="middle" fontFamily="Consolas, monospace" fontWeight="700" fontSize="17" fill="#fb923c" letterSpacing="1">500D  SILK BOARD</text>

        {/* windscreen and the driver */}
        <clipPath id={front}><path d="M6 40 Q8 14 34 12 H72 V172 H6 Z" /></clipPath>
        <g clipPath={`url(#${front})`}>
          <rect x="0" y="0" width="80" height="180" fill="#c7dcef" />
          <g transform="translate(46 252) scale(.78)"><Person look={DRIVER} pose="drive" facing={-1} blink={2.2} /></g>
          <path d="M10 160 L52 18 H64 L22 160 Z" fill="#fff" opacity=".25" />
        </g>
        <path d="M6 40 Q8 14 34 12 H72 V172 H6 Z" fill="none" stroke="#334155" strokeWidth="4" />

        {/* the door, open once it stops */}
        <rect x="150" y="52" width="76" height="216" rx="4" fill={doorOpen ? '#1e293b' : '#c7dcef'} stroke="#334155" strokeWidth="4" />
        {doorOpen ? (
          <g>
            <rect x="154" y="232" width="68" height="16" fill="#475569" />
            <rect x="154" y="250" width="68" height="16" fill="#64748b" />
            <rect x="226" y="80" width="5" height="160" rx="2" fill="#cbd5e1" />
            <rect x="140" y="56" width="10" height="208" fill="#94a3b8" opacity=".6" />
          </g>
        ) : (
          <path d="M188 54 V266" stroke="#334155" strokeWidth="3" />
        )}

        {/* standing room only */}
        <clipPath id={glass}>
          {[0, 1, 2, 3, 4, 5].map((k) => <rect key={k} x={240 + k * 94} y="56" width="86" height="112" rx="7" />)}
        </clipPath>
        <g clipPath={`url(#${glass})`}>
          <rect x="236" y="50" width="580" height="124" fill="#d9e8f5" />
          <path d="M236 90 H816" stroke="#94a3b8" strokeWidth="4" />
          {Object.entries(PASSENGERS).map(([slot, look]) => (
            <g key={slot} transform={`translate(${SLOTS[slot]} 254) scale(.78)`}>
              <Person
                look={look}
                pose={slot % 3 === 0 ? 'rail' : 'stand'}
                mood={shock ? 'shock' : 'tired'}
                facing={shock ? 1 : -1}
                idle
                blink={Number(slot) * 0.7}
              />
            </g>
          ))}
          {COMMUTERS.map((c, i) => i !== HANGER && people[i].inBus && (
            <g key={`c${i}`} transform={`translate(${SLOTS[SLOT_OF[i]]} 254) scale(.78)`}>
              <Person
                look={c.look}
                pose={i === CONVERT && convert ? 'phone' : 'rail'}
                mood={i === CONVERT && convert ? 'smile' : shock ? 'shock' : 'tired'}
                facing={shock && !(i === CONVERT && convert) ? 1 : -1}
                hold={i === CONVERT && convert ? 'phone' : c.hold === 'umbrella' ? 'umbrella' : null}
                blink={i}
              />
            </g>
          ))}
          {[0, 1, 2, 3, 4, 5].map((k) => (
            <path key={k} d={`M${250 + k * 94} 166 L${292 + k * 94} 58 H${304 + k * 94} L${262 + k * 94} 166 Z`} fill="#fff" opacity=".2" />
          ))}
        </g>
        {[0, 1, 2, 3, 4, 5].map((k) => <rect key={k} x={240 + k * 94} y="56" width="86" height="112" rx="7" fill="none" stroke="#334155" strokeWidth="4" />)}

        {/* side advert */}
        <rect x="300" y="196" width="300" height="58" rx="8" fill="#fff" />
        <rect x="300" y="196" width="10" height="58" rx="4" fill="#f97316" />
        <text x="458" y="222" textAnchor="middle" fontFamily="Nunito Sans, Inter, sans-serif" fontWeight="900" fontSize="18" fill="#ea580c">Namma Journey</text>
        <text x="458" y="243" textAnchor="middle" fontFamily="Nunito Sans, Inter, sans-serif" fontWeight="700" fontSize="13" fill="#475569">Skip the queue. Ride in minutes.</text>

        <rect x="0" y="210" width="10" height="20" rx="3" fill="#fef9c3" />
        <rect x="812" y="200" width="10" height="34" rx="3" fill="#ef4444" />
        <rect x="-6" y="252" width="40" height="16" rx="5" fill="#1f2937" />
        <path d="M6 44 L-16 62" stroke="#1f2937" strokeWidth="4" />
        <rect x="-26" y="58" width="12" height="32" rx="4" fill="#1f2937" />
        <path d="M44 274 A46 46 0 0 1 136 274 Z M614 274 A46 46 0 0 1 706 274 Z" fill="#1f2937" />
      </g>

      {hanger && (
        <g transform="translate(206 264) scale(.88)">
          <Person look={OFFICE} pose="rail" mood={shock ? 'shock' : 'neutral'} facing={shock ? 1 : -1} blink={0.4} />
        </g>
      )}

      {[90, 660].map((wx) => (
        <g key={wx} transform={`translate(${wx} 272)`}><Wheel r={36} /></g>
      ))}
    </g>
  )
}

/** A Namma Journey bike, drawn facing right; the scene mirrors it to ride left. */
function Bike({ rider, pillion, herMood, shock }) {
  return (
    <g>
      <ellipse cy="3" rx="96" ry="7" fill="rgba(15,23,42,.26)" />
      <g className="cs-body">
        <path d="M-20 -34 L-94 -46" stroke="#94a3b8" strokeWidth="7" strokeLinecap="round" />
        <path d="M-62 -26 L-10 -42" stroke="#334155" strokeWidth="7" strokeLinecap="round" />
        <rect x="-18" y="-62" width="40" height="32" rx="7" fill="#334155" />
        <path d="M-92 -40 Q-80 -62 -48 -62" stroke="#f97316" strokeWidth="7" fill="none" strokeLinecap="round" />
        <path d="M-54 -64 H6 L2 -46 H-44 Q-56 -48 -54 -64 Z" fill="#ea580c" />
        <path d="M0 -76 Q22 -90 46 -79 L42 -60 H2 Z" fill="#f97316" />
        <path d="M8 -80 Q20 -86 34 -82" stroke="#fff" strokeWidth="3" opacity=".7" fill="none" strokeLinecap="round" />
        <path d="M-80 -68 Q-78 -78 -56 -78 H10 Q18 -78 16 -70 L14 -66 H-78 Z" fill="#1f2937" />
        <path d="M-82 -74 H-60" stroke="#334155" strokeWidth="4" strokeLinecap="round" />
        <rect x="-96" y="-68" width="10" height="9" rx="2" fill="#ef4444" />
        <path d="M44 -84 L68 -26" stroke="#64748b" strokeWidth="7" strokeLinecap="round" />
        <path d="M50 -42 Q68 -58 90 -42" stroke="#f97316" strokeWidth="6" fill="none" strokeLinecap="round" />
        <path d="M38 -100 L50 -90" stroke="#1f2937" strokeWidth="6" strokeLinecap="round" />
        <path d="M30 -102 H44" stroke="#111827" strokeWidth="7" strokeLinecap="round" />
        <path d="M50 -92 Q66 -94 64 -76 L52 -72 Z" fill="#f97316" />
        <circle cx="62" cy="-82" r="7" fill="#fef9c3" stroke="#334155" strokeWidth="2.5" />

        <g transform="translate(-6 22)">
          <Person look={RIDER} pose={rider} helmet="rider" blink={0.8} hold={rider === 'offer' ? 'helmet' : null} />
        </g>
        {pillion && (
          <g transform="translate(-50 24)">
            <g className="cs-hop">
              <Person look={HER} pose="pillion" mood={shock ? 'joy' : herMood} helmet="pillion" blink={1.3} />
            </g>
          </g>
        )}
      </g>
      {[-62, 68].map((wx) => (
        <g key={wx} transform={`translate(${wx} -26)`}><Wheel r={26} rim="#e5e7eb" /></g>
      ))}
    </g>
  )
}

/** A Bengaluru auto, drawn facing right. */
function Auto() {
  return (
    <g>
      <ellipse cx="0" cy="2" rx="90" ry="7" fill="rgba(15,23,42,.25)" />
      <g className="cs-body">
        <path d="M-80 -40 Q-82 -120 -40 -126 H40 Q70 -124 78 -96 L86 -44 Q86 -26 70 -26 H-70 Q-82 -26 -80 -40 Z" fill="#15803d" />
        <path d="M-82 -100 Q-80 -132 -36 -136 H44 Q72 -132 80 -104 Z" fill="#facc15" />
        <path d="M40 -98 L74 -98 L82 -54 H40 Z" fill="#c7dcef" />
        <rect x="-56" y="-98" width="76" height="44" rx="6" fill="#1f2937" />
        <rect x="-80" y="-50" width="166" height="8" fill="#facc15" />
      </g>
      {[-52, 64].map((wx) => <g key={wx} transform={`translate(${wx} -20)`}><Wheel r={20} /></g>)}
    </g>
  )
}

function Car() {
  return (
    <g>
      <ellipse cx="0" cy="2" rx="120" ry="8" fill="rgba(15,23,42,.25)" />
      <g className="cs-body">
        <path d="M-120 -30 V-62 Q-118 -76 -96 -78 L-62 -80 L-30 -116 Q-22 -122 -6 -122 H50 Q66 -122 76 -110 L100 -80 Q124 -76 126 -58 V-30 Q126 -22 116 -22 H-110 Q-120 -22 -120 -30 Z" fill="#f8fafc" />
        <path d="M-54 -82 L-26 -112 H8 V-82 Z M18 -82 V-112 H48 Q58 -112 64 -104 L82 -82 Z" fill="#9fb8cf" />
        <path d="M-120 -48 H126" stroke="#cbd5e1" strokeWidth="3" />
        <rect x="116" y="-66" width="10" height="10" rx="2" fill="#fef9c3" />
      </g>
      {[-72, 78].map((wx) => <g key={wx} transform={`translate(${wx} -24)`}><Wheel r={22} /></g>)}
    </g>
  )
}

/* ══════════════════  the phone close-up  ══════════════════ */

const PHONE_FONT = 'Nunito Sans, Inter, sans-serif'

/**
 * Where the close-up sits. Wide screens: on the open road between the bus (gone to
 * the signal) and her, left of the ride card. A phone's narrow frame is centred on her.
 */
const PHONE_AT = { wide: { x: 950, y: 640, s: 1.05 }, compact: { x: 1090, y: 572, s: 1.2 } }
/** Her phone hand while she looks at it, so a dotted line can tie the close-up to it. */
const HER_HAND = { x: HER_X - 34, y: GROUND - 132 }

function PhonePop({ step, compact }) {
  const at = compact ? PHONE_AT.compact : PHONE_AT.wide
  const hand = { x: (HER_HAND.x - at.x) / at.s + 88, y: (HER_HAND.y - at.y) / at.s + 170 }
  return (
    <g transform={`translate(${at.x} ${at.y}) scale(${at.s}) translate(-88 -170)`} className="crs-phone">
      <path d={`M176 250 Q${(176 + hand.x) / 2} ${Math.min(250, hand.y) - 20} ${hand.x} ${hand.y}`} stroke="#fff" strokeWidth="3" strokeDasharray="2 7" strokeLinecap="round" fill="none" opacity=".9" />
      <rect x="0" y="0" width="176" height="340" rx="28" fill="#0f172a" />
      <rect x="9" y="9" width="158" height="322" rx="21" fill="#fff8f0" />
      <rect x="66" y="16" width="44" height="7" rx="3.5" fill="#0f172a" />
      <text x="22" y="52" fontFamily={PHONE_FONT} fontWeight="900" fontSize="15" fill="#0f2a2b">Namma <tspan fill="#f97316">Journey</tspan></text>
      <AnimatePresence mode="wait" initial={false}>
        <motion.g
          key={step}
          initial={{ opacity: 0, x: 12 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -12 }}
          transition={{ duration: 0.3, ease: EASE }}
        >
          {step === 'search' && <PhoneSearch />}
          {step === 'pick' && <PhonePick />}
          {step === 'booked' && <PhoneBooked />}
        </motion.g>
      </AnimatePresence>
    </g>
  )
}

function PhoneSearch() {
  return (
    <g fontFamily={PHONE_FONT}>
      <rect x="20" y="66" width="136" height="30" rx="10" fill="#f1ece6" />
      <rect x="23" y="69" width="66" height="24" rx="8" fill="#fff" />
      <text x="56" y="86" textAnchor="middle" fontWeight="800" fontSize="11" fill="#c2410c">City ride</text>
      <text x="122" y="86" textAnchor="middle" fontWeight="800" fontSize="11" fill="#56706f">Trip</text>
      <rect x="20" y="108" width="136" height="36" rx="10" fill="#fff" stroke="#ebe8e4" />
      <circle cx="36" cy="126" r="5" fill="none" stroke="#f97316" strokeWidth="3" />
      <text x="48" y="130" fontWeight="800" fontSize="12" fill="#0f2a2b">Silk Board</text>
      <rect x="20" y="152" width="136" height="36" rx="10" fill="#fff" stroke="#f97316" strokeWidth="1.5" />
      <circle cx="36" cy="170" r="5" fill="#c2410c" />
      <clipPath id="crs-typing"><motion.rect x="46" y="156" height="28" initial={{ width: 0 }} animate={{ width: 96 }} transition={{ duration: 0.9, delay: 0.2, ease: 'linear' }} /></clipPath>
      <text x="48" y="174" fontWeight="800" fontSize="12" fill="#0f2a2b" clipPath="url(#crs-typing)">Koramangala</text>
      {[0, 1, 2].map((k) => (
        <g key={k}>
          <rect x="20" y={204 + k * 30} width="136" height="22" rx="7" fill="#f5efe8" />
          <rect x="30" y={211 + k * 30} width={60 - k * 12} height="8" rx="4" fill="#e3d9cd" />
        </g>
      ))}
    </g>
  )
}

const RIDE_ROWS = [['Bike', '2 min'], ['Auto', '4 min'], ['Car', '6 min']]

function PhonePick() {
  return (
    <g fontFamily={PHONE_FONT}>
      <text x="22" y="84" fontWeight="800" fontSize="12" fill="#56706f">Choose a ride</text>
      {RIDE_ROWS.map(([label, eta], k) => (
        <g key={label}>
          <rect x="20" y={96 + k * 46} width="136" height="38" rx="11" fill={k === 0 ? '#fff3e8' : '#fff'} stroke={k === 0 ? '#f97316' : '#ebe8e4'} strokeWidth={k === 0 ? 2 : 1} />
          <circle cx="40" cy={115 + k * 46} r="10" fill={k === 0 ? '#f97316' : '#e7e1da'} />
          <text x="58" y={119 + k * 46} fontWeight="900" fontSize="13" fill="#0f2a2b">{label}</text>
          <text x="146" y={119 + k * 46} textAnchor="end" fontWeight="800" fontSize="11" fill="#56706f">{eta}</text>
        </g>
      ))}
      <motion.g initial={{ scale: 1 }} animate={{ scale: [1, 0.93, 1] }} transition={{ duration: 0.45, delay: 0.55 }} style={{ originX: '88px', originY: '268px' }}>
        <rect x="20" y="250" width="136" height="38" rx="12" fill="#f97316" />
        <text x="88" y="274" textAnchor="middle" fontWeight="900" fontSize="13" fill="#fff">Book bike now</text>
      </motion.g>
    </g>
  )
}

function PhoneBooked() {
  return (
    <g fontFamily={PHONE_FONT}>
      <rect x="20" y="66" width="136" height="124" rx="12" fill="#e3ece6" />
      {[0, 1, 2, 3].map((k) => <rect key={`h${k}`} x="20" y={84 + k * 30} width="136" height="7" fill="#fff" />)}
      {[0, 1, 2, 3].map((k) => <rect key={`v${k}`} x={38 + k * 34} y="66" width="7" height="124" fill="#fff" />)}
      <circle cx="58" cy="160" r="7" fill="#2563eb" />
      <circle cx="58" cy="160" r="13" fill="#2563eb" opacity=".2" />
      <motion.g initial={{ x: 0, y: 0 }} animate={{ x: -40, y: 40 }} transition={{ duration: 1.6, ease: 'easeInOut' }}>
        <circle cx="128" cy="96" r="8" fill="#f97316" />
        <circle cx="128" cy="96" r="14" fill="#f97316" opacity=".22" />
      </motion.g>
      <text x="22" y="220" fontWeight="900" fontSize="20" fill="#c2410c">2 min <tspan fontSize="13" fill="#0f2a2b">away</tspan></text>
      <circle cx="36" cy="248" r="14" fill="#f97316" />
      <text x="36" y="253" textAnchor="middle" fontWeight="900" fontSize="13" fill="#fff">R</text>
      <text x="58" y="246" fontWeight="900" fontSize="13" fill="#0f2a2b">Ravi</text>
      <text x="58" y="261" fontWeight="700" fontSize="10.5" fill="#56706f">Verified · ★ 4.9</text>
      <rect x="20" y="276" width="136" height="30" rx="9" fill="#fef3c7" />
      <text x="88" y="296" textAnchor="middle" fontWeight="900" fontSize="12" letterSpacing="1" fill="#92400e">KA 05 HX 2210</text>
    </g>
  )
}

/* ══════════════════  speech  ══════════════════ */

function SpeechBubble({ text, show, x, y, scale }) {
  const w = text.length * 7.8 + 32
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      <motion.g
        initial={false}
        animate={show ? { opacity: 1, scale: 1, y: 0 } : { opacity: 0, scale: 0.6, y: 10 }}
        transition={{ duration: 0.38, ease: EASE }}
        style={{ originY: 1 }}
      >
        <g className="crs-bubble">
          <rect x={-w / 2} y="-34" width={w} height="32" rx="16" fill="#fff" />
          <path d="M-8 -4 L0 9 L8 -4 Z" fill="#fff" />
          <text y="-13" textAnchor="middle" fontFamily="Nunito Sans, Inter, sans-serif" fontWeight="800" fontSize="14" fill="#0f172a">{text}</text>
        </g>
      </motion.g>
    </g>
  )
}
