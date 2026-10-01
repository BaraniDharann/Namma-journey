/* eslint-disable react/no-unknown-property -- react-three-fiber JSX props (args, geometry, flatShading…) are three.js object props, not DOM attributes */
import React, { useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { BIOMES, SEG, segmentDistance, biomeAt, lookAt } from '../../landing/biomes'

/**
 * The landing hero: a game-style chase view of the Namma Journey car cruising, by itself,
 * through five places modelled on the photo library — Kashmir valley, Nohkalikai falls,
 * Varkala beach, the Alappuzha backwaters and a temple town at dusk. Low-poly primitives only.
 *
 * The car stays put; the world slides towards it. Each place is one road segment, and a
 * segment that has passed behind the camera is recycled to the far end, past the fog.
 */

const SPEED = 8.5           // world units per second: an easy cruise
const ROAD_W = 9

const rand = (seed) => {
  let s = seed
  return () => { s = (s * 16807) % 2147483647; return (s - 1) / 2147483646 }
}
const side = (r) => (r() > 0.5 ? 1 : -1)

/** Instanced field placed by `place(i, rnd)` → { x, y, z, sx, sy, sz, rx, ry, rz, color }. */
function Field({ count, place, seed = 1, children }) {
  const ref = useRef()
  useLayoutEffect(() => {
    const r = rand(seed)
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler(), c = new THREE.Color()
    for (let i = 0; i < count; i++) {
      const p = place(i, r)
      e.set(p.rx || 0, p.ry || 0, p.rz || 0)
      q.setFromEuler(e)
      m.compose(new THREE.Vector3(p.x, p.y, p.z), q, new THREE.Vector3(p.sx ?? 1, p.sy ?? 1, p.sz ?? 1))
      ref.current.setMatrixAt(i, m)
      if (p.color) ref.current.setColorAt(i, c.set(p.color))
    }
    ref.current.instanceMatrix.needsUpdate = true
    if (ref.current.instanceColor) ref.current.instanceColor.needsUpdate = true
  }, [count, place, seed])
  return <instancedMesh ref={ref} args={[null, null, count]}>{children}</instancedMesh>
}

const along = (i, n, r) => -((i + r()) / n) * SEG

/* ── shared pieces ─────────────────────────────────────────────── */

function RoadPiece({ ground }) {
  const place = useMemo(() => (i) => ({ x: 0, y: 0.03, z: -4 - i * 8, sx: 1, sy: 1, sz: 1 }), [])
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.02, -SEG / 2]}>
        <planeGeometry args={[700, SEG + 0.5]} />
        <meshLambertMaterial color={ground} />
      </mesh>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.01, -SEG / 2]}>
        <planeGeometry args={[ROAD_W, SEG + 0.5]} />
        <meshLambertMaterial color="#3a4250" />
      </mesh>
      {[-ROAD_W / 2 + 0.3, ROAD_W / 2 - 0.3].map((x) => (
        <mesh key={x} rotation={[-Math.PI / 2, 0, 0]} position={[x, 0.02, -SEG / 2]}>
          <planeGeometry args={[0.16, SEG + 0.5]} />
          <meshBasicMaterial color="#e5e7eb" />
        </mesh>
      ))}
      <Field count={Math.floor(SEG / 8)} place={place} seed={3}>
        <boxGeometry args={[0.2, 0.02, 3]} />
        <meshBasicMaterial color="#f8fafc" />
      </Field>
    </group>
  )
}

function Trees({ count, seed, near = 7, far = 55, color = '#1f7a3a', alt = '#166534', scale = 1, pine = true }) {
  // One layout, read by both the crowns and the trunks, so every trunk sits under its tree.
  const items = useMemo(() => {
    const r = rand(seed)
    return Array.from({ length: count }, (_, i) => {
      const z = along(i, count, r), s = side(r), k = (1.4 + r() * 2.2) * scale
      return { x: s * (near + r() * (far - near)), z, k, color: r() > 0.5 ? color : alt }
    })
  }, [count, seed, near, far, scale, color, alt])
  const placeTop = useMemo(() => (i) => {
    const t = items[i]
    return { x: t.x, y: pine ? t.k * 1.5 + 0.8 : t.k * 1.2 + 1.2, z: t.z, sx: t.k, sy: pine ? t.k * 2.4 : t.k, sz: t.k, color: t.color }
  }, [items, pine])
  const placeTrunk = useMemo(() => (i) => {
    const t = items[i]
    return { x: t.x, y: 0.7, z: t.z, sx: 0.22 * t.k, sy: 1.4, sz: 0.22 * t.k, color: '#7c4a22' }
  }, [items])
  return (
    <group>
      <Field count={count} place={placeTop} seed={seed}>
        {pine ? <coneGeometry args={[1, 1, 7]} /> : <icosahedronGeometry args={[1, 0]} />}
        <meshLambertMaterial flatShading />
      </Field>
      {pine && (
        <Field count={count} place={placeTrunk} seed={seed}>
          <cylinderGeometry args={[1, 1, 1, 5]} />
          <meshLambertMaterial />
        </Field>
      )}
    </group>
  )
}

function Palms({ count, seed, xs, lean = 0.25 }) {
  const items = useMemo(() => {
    const r = rand(seed)
    return Array.from({ length: count }, (_, i) => {
      const z = along(i, count, r), x = xs(r)
      const tilt = (x < 0 ? 1 : -1) * (lean * 0.6 + r() * lean * 0.5)
      return { x, z, tilt, ry: r() * 3, color: r() > 0.5 ? '#15803d' : '#22a04b' }
    })
  }, [count, seed, xs, lean])
  const trunk = useMemo(() => (i) => {
    const p = items[i]
    return { x: p.x - Math.sin(p.tilt) * 2.7, y: 2.8, z: p.z, sx: 0.22, sy: 5.6, sz: 0.22, rz: p.tilt, color: '#8a5a2b' }
  }, [items])
  const crown = useMemo(() => (i) => {
    const p = items[i]
    return { x: p.x - Math.sin(p.tilt) * 5.4, y: 5.4, z: p.z, sx: 2.8, sy: 0.55, sz: 2.8, ry: p.ry, color: p.color }
  }, [items])
  return (
    <group>
      <Field count={count} place={trunk} seed={seed}><cylinderGeometry args={[0.8, 1, 1, 6]} /><meshLambertMaterial flatShading /></Field>
      <Field count={count} place={crown} seed={seed}><coneGeometry args={[1, 1, 8]} /><meshLambertMaterial flatShading /></Field>
    </group>
  )
}

/* ── the five places ───────────────────────────────────────────── */

/** Kashmir valley: pine forest, a river beside the road, snow peaks on both horizons. */
function Valley({ lite }) {
  const peaks = lite ? 10 : 16
  const body = useMemo(() => (i, r) => {
    const s = side(r), k = 34 + r() * 46
    return { x: s * (95 + r() * 110), y: k * 0.45, z: along(i, peaks, r), sx: k * 0.9, sy: k, sz: k * 0.9, color: r() > 0.5 ? '#6b7f8e' : '#7c8d99' }
  }, [peaks])
  const cap = useMemo(() => (i, r) => {
    const s = side(r), k = 34 + r() * 46
    return { x: s * (95 + r() * 110), y: k * 0.82, z: along(i, peaks, r), sx: k * 0.34, sy: k * 0.3, sz: k * 0.34, color: '#f8fafc' }
  }, [peaks])
  return (
    <group>
      <Field count={peaks} place={body} seed={101}><coneGeometry args={[1, 1, 6]} /><meshLambertMaterial flatShading /></Field>
      <Field count={peaks} place={cap} seed={101}><coneGeometry args={[1, 1, 6]} /><meshLambertMaterial flatShading /></Field>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[-17, 0.04, -SEG / 2]}>
        <planeGeometry args={[7, SEG + 0.5]} />
        <meshLambertMaterial color="#5bb0d6" emissive="#2b7fa8" emissiveIntensity={0.25} />
      </mesh>
      <Trees count={lite ? 60 : 120} seed={103} near={8} far={70} />
    </group>
  )
}

/** Nohkalikai: a green-topped cliff with a white fall pouring into a pool, jungle opposite. */
function Waterfall({ lite }) {
  const blocks = useMemo(() => {
    const r = rand(201)
    return Array.from({ length: 9 }, (_, i) => ({ h: 34 + r() * 30, x: 44 + r() * 18, z: -10 - i * 24 - r() * 6, w: 22 + r() * 14, color: r() > 0.5 ? '#6b5b4a' : '#7a6852' }))
  }, [])
  const cliff = useMemo(() => (i) => { const b = blocks[i]; return { x: b.x, y: b.h / 2, z: b.z, sx: b.w, sy: b.h, sz: 26, color: b.color } }, [blocks])
  const moss = useMemo(() => (i) => { const b = blocks[i]; return { x: b.x, y: b.h + 1.2, z: b.z, sx: b.w + 2, sy: 3, sz: 28, color: '#2f7d3a' } }, [blocks])
  const fall = useRef()
  useFrame((s) => {
    if (!fall.current) return
    fall.current.children.forEach((m, k) => { m.position.y = 26 + ((s.clock.elapsedTime * 14 + k * 9) % 46) - 23 })
  })
  return (
    <group>
      <Field count={9} place={cliff} seed={201}><boxGeometry args={[1, 1, 1]} /><meshLambertMaterial flatShading /></Field>
      <Field count={9} place={moss} seed={201}><boxGeometry args={[1, 1, 1]} /><meshLambertMaterial flatShading /></Field>
      <group position={[31, 0, -130]}>
        <mesh position={[0, 26, 0]}>
          <boxGeometry args={[5, 52, 0.6]} />
          <meshLambertMaterial color="#e6f6fb" emissive="#cfeefa" emissiveIntensity={0.5} transparent opacity={0.9} />
        </mesh>
        <group ref={fall}>
          {[0, 1, 2, 3, 4].map((k) => (
            <mesh key={k} position={[(k - 2) * 0.9, 20, 0.5]}>
              <boxGeometry args={[0.5, 7, 0.2]} />
              <meshBasicMaterial color="#ffffff" transparent opacity={0.85} />
            </mesh>
          ))}
        </group>
        <mesh position={[0, 1.2, 4]}>
          <sphereGeometry args={[5, 10, 8]} />
          <meshLambertMaterial color="#ffffff" transparent opacity={0.55} />
        </mesh>
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[-4, 0.05, 8]}>
          <circleGeometry args={[11, 20]} />
          <meshLambertMaterial color="#3fa3c4" />
        </mesh>
      </group>
      <Trees count={lite ? 50 : 100} seed={203} near={8} far={40} pine={false} color="#1d6b33" alt="#2a8541" scale={1.5} />
    </group>
  )
}

/** Varkala: the sea with rolling foam on the left, red laterite cliffs with palms on the right. */
function Beach({ lite }) {
  const foam = useRef()
  useFrame((s) => { if (foam.current) foam.current.position.x = -15 + Math.sin(s.clock.elapsedTime * 1.3) * 0.8 })
  const cliff = useMemo(() => (i, r) => {
    const h = 9 + r() * 7
    return { x: 26 + r() * 10, y: h / 2, z: -6 - i * 21 - r() * 6, sx: 14 + r() * 8, sy: h, sz: 22, color: r() > 0.5 ? '#b45f3a' : '#a4532f' }
  }, [])
  const xs = useMemo(() => (r) => 22 + r() * 18, [])
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[-170, 0.03, -SEG / 2]}>
        <planeGeometry args={[310, SEG + 0.5]} />
        <meshLambertMaterial color="#1f8fc4" emissive="#0e6b99" emissiveIntensity={0.25} />
      </mesh>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[-10, 0.035, -SEG / 2]}>
        <planeGeometry args={[9, SEG + 0.5]} />
        <meshLambertMaterial color="#f1dcaa" />
      </mesh>
      <mesh ref={foam} rotation={[-Math.PI / 2, 0, 0]} position={[-15, 0.05, -SEG / 2]}>
        <planeGeometry args={[1.6, SEG + 0.5]} />
        <meshBasicMaterial color="#ffffff" transparent opacity={0.85} />
      </mesh>
      <Field count={11} place={cliff} seed={301}><boxGeometry args={[1, 1, 1]} /><meshLambertMaterial flatShading /></Field>
      <Palms count={lite ? 16 : 28} seed={303} xs={xs} />
      <mesh position={[-190, 22, -SEG - 90]}>
        <sphereGeometry args={[16, 20, 20]} />
        <meshBasicMaterial color="#fde68a" fog={false} />
      </mesh>
    </group>
  )
}

/** Alappuzha: water on both banks, leaning coconut palms, a houseboat drifting alongside. */
function Backwaters({ lite }) {
  const boat = useRef()
  useFrame((s, dt) => {
    if (!boat.current) return
    boat.current.position.z -= dt * 1.6
    if (boat.current.position.z < -SEG + 20) boat.current.position.z = -30
    boat.current.position.y = 0.15 + Math.sin(s.clock.elapsedTime * 1.4) * 0.06
  })
  const xs = useMemo(() => (r) => side(r) * (7 + r() * 3), [])
  return (
    <group>
      {[-1, 1].map((s) => (
        <mesh key={s} rotation={[-Math.PI / 2, 0, 0]} position={[s * 90, 0.03, -SEG / 2]}>
          <planeGeometry args={[160, SEG + 0.5]} />
          <meshLambertMaterial color="#2f8f7c" emissive="#1b6a5a" emissiveIntensity={0.25} />
        </mesh>
      ))}
      {[-1, 1].map((s) => (
        <mesh key={s} rotation={[-Math.PI / 2, 0, 0]} position={[s * 8.6, 0.035, -SEG / 2]}>
          <planeGeometry args={[3.2, SEG + 0.5]} />
          <meshLambertMaterial color="#5a9a3f" />
        </mesh>
      ))}
      <Palms count={lite ? 22 : 40} seed={401} xs={xs} lean={0.45} />
      <group ref={boat} position={[-24, 0.15, -60]}>
        <mesh position={[0, 0.6, 0]}><boxGeometry args={[4.2, 1.2, 14]} /><meshLambertMaterial color="#6b3f1f" /></mesh>
        <mesh position={[0, 2.1, 0]} rotation={[Math.PI / 2, 0, 0]}><cylinderGeometry args={[2.4, 2.4, 11, 12, 1, false, 0, Math.PI]} /><meshLambertMaterial color="#d6a85c" side={THREE.DoubleSide} /></mesh>
        <mesh position={[0, 1.5, 0]}><boxGeometry args={[3.6, 1.4, 9]} /><meshLambertMaterial color="#f5e6c8" /></mesh>
      </group>
    </group>
  )
}

/** A tiered gopuram, the tower that marks a South Indian temple. */
function Gopuram({ x, z, k = 1 }) {
  return (
    <group position={[x, 0, z]} scale={k}>
      {[0, 1, 2, 3, 4, 5].map((t) => (
        <mesh key={t} position={[0, 3 + t * 4.4, 0]}>
          <boxGeometry args={[16 - t * 2.3, 4.4, 10 - t * 1.3]} />
          <meshLambertMaterial color={t % 2 ? '#e8a355' : '#d48b3e'} flatShading />
        </mesh>
      ))}
      <mesh position={[0, 3 + 6 * 4.4 + 1, 0]}><sphereGeometry args={[1.3, 10, 10]} /><meshLambertMaterial color="#ffd27a" emissive="#f59e0b" emissiveIntensity={0.9} /></mesh>
    </group>
  )
}

/** Temple town at dusk: colourful houses, two gopurams and warm lamps along the road. */
function Temple({ lite }) {
  const houses = lite ? 24 : 40
  const placeHouse = useMemo(() => (i, r) => {
    const s = side(r), h = 3 + r() * 4, w = 5 + r() * 4
    const cols = ['#f4b860', '#e07a5f', '#81b29a', '#f2cc8f', '#c97b84']
    return { x: s * (10 + w / 2 + r() * 8), y: h / 2, z: along(i, houses, r), sx: w, sy: h, sz: 6, color: cols[Math.floor(r() * cols.length)] }
  }, [houses])
  const lamps = lite ? 14 : 24
  const placeLamp = useMemo(() => (i) => ({ x: (i % 2 ? 1 : -1) * (ROAD_W / 2 + 0.8), y: 3.4, z: -6 - (i / lamps) * SEG, sx: 0.38, sy: 0.38, sz: 0.38, color: '#ffd27a' }), [lamps])
  const placePost = useMemo(() => (i) => ({ x: (i % 2 ? 1 : -1) * (ROAD_W / 2 + 0.8), y: 1.6, z: -6 - (i / lamps) * SEG, sx: 0.12, sy: 3.2, sz: 0.12, color: '#3b3a36' }), [lamps])
  return (
    <group>
      <Field count={houses} place={placeHouse} seed={501}><boxGeometry args={[1, 1, 1]} /><meshLambertMaterial flatShading /></Field>
      <Field count={lamps} place={placePost} seed={503}><boxGeometry args={[1, 1, 1]} /><meshLambertMaterial /></Field>
      <Field count={lamps} place={placeLamp} seed={505}><sphereGeometry args={[1, 10, 10]} /><meshBasicMaterial /></Field>
      <Gopuram x={-34} z={-90} k={1.2} />
      <Gopuram x={38} z={-190} k={1.5} />
      <Trees count={lite ? 16 : 30} seed={507} near={22} far={60} pine={false} color="#3f7d3a" alt="#2f6b2f" />
    </group>
  )
}

const PLACE = { valley: Valley, waterfall: Waterfall, beach: Beach, backwaters: Backwaters, temple: Temple }

/* ── the car ───────────────────────────────────────────────────── */

function Car() {
  const body = useRef(), wheels = useRef([])
  useFrame((s, dt) => {
    const t = s.clock.elapsedTime
    if (body.current) {
      body.current.position.y = 0.06 + Math.abs(Math.sin(t * 7)) * 0.03
      body.current.rotation.z = Math.sin(t * 0.8) * 0.012
      body.current.position.x = Math.sin(t * 0.35) * 0.35
    }
    wheels.current.forEach((w) => { if (w) w.rotation.x -= dt * SPEED / 0.42 })
  })
  const wheel = (x, z, k) => (
    <group key={k} position={[x, 0.42, z]} ref={(el) => { wheels.current[k] = el }}>
      <mesh rotation={[0, 0, Math.PI / 2]}><cylinderGeometry args={[0.42, 0.42, 0.34, 14]} /><meshLambertMaterial color="#1f2328" /></mesh>
      <mesh rotation={[0, 0, Math.PI / 2]} position={[x > 0 ? 0.18 : -0.18, 0, 0]}><cylinderGeometry args={[0.22, 0.22, 0.02, 10]} /><meshLambertMaterial color="#d7dce2" /></mesh>
    </group>
  )
  return (
    <group position={[-1.9, 0, 0]}>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.02, 0]}>
        <planeGeometry args={[2.6, 5.2]} />
        <meshBasicMaterial color="#000" transparent opacity={0.22} />
      </mesh>
      <group ref={body}>
        <mesh position={[0, 0.85, 0]}><boxGeometry args={[2, 0.9, 4.6]} /><meshLambertMaterial color="#f97316" /></mesh>
        <mesh position={[0, 0.52, 0]}><boxGeometry args={[2.02, 0.26, 4.62]} /><meshLambertMaterial color="#c2410c" /></mesh>
        <mesh position={[0, 1.62, 0.25]}><boxGeometry args={[1.86, 0.72, 3.4]} /><meshLambertMaterial color="#f97316" /></mesh>
        <mesh position={[0, 1.62, 0.25]}><boxGeometry args={[1.9, 0.5, 3.1]} /><meshLambertMaterial color="#bfe1f2" emissive="#6aa9c9" emissiveIntensity={0.15} /></mesh>
        <mesh position={[0, 1.55, -1.5]} rotation={[0.55, 0, 0]}><boxGeometry args={[1.8, 0.05, 1]} /><meshLambertMaterial color="#bfe1f2" /></mesh>
        <mesh position={[0, 2.03, 0.25]}><boxGeometry args={[1.6, 0.08, 2.8]} /><meshLambertMaterial color="#334155" /></mesh>
        <mesh position={[-0.35, 2.28, 0.6]}><boxGeometry args={[0.8, 0.42, 1.2]} /><meshLambertMaterial color="#0f766e" /></mesh>
        <mesh position={[0.45, 2.22, -0.2]}><boxGeometry args={[0.7, 0.3, 0.9]} /><meshLambertMaterial color="#eda100" /></mesh>
        <mesh position={[0, 1.66, 1.96]}><boxGeometry args={[1.56, 0.5, 0.04]} /><meshLambertMaterial color="#9fd0ea" emissive="#5d9cc0" emissiveIntensity={0.25} /></mesh>
        <mesh position={[0, 0.98, 2.31]}><boxGeometry args={[1.7, 0.08, 0.04]} /><meshBasicMaterial color="#ffffff" /></mesh>
        <mesh position={[0, 0.66, 2.33]}><boxGeometry args={[0.7, 0.22, 0.03]} /><meshBasicMaterial color="#fef3c7" /></mesh>
        {[-0.72, 0.72].map((x) => (
          <mesh key={`t${x}`} position={[x, 0.92, 2.32]}><boxGeometry args={[0.36, 0.2, 0.05]} /><meshBasicMaterial color="#ef4444" /></mesh>
        ))}
        {[-0.72, 0.72].map((x) => (
          <mesh key={`h${x}`} position={[x, 0.92, -2.31]}><boxGeometry args={[0.4, 0.2, 0.05]} /><meshBasicMaterial color="#fff7cc" /></mesh>
        ))}
      </group>
      {wheel(-1.02, -1.45, 0)}{wheel(1.02, -1.45, 1)}{wheel(-1.02, 1.45, 2)}{wheel(1.02, 1.45, 3)}
    </group>
  )
}

/* ── the world loop ────────────────────────────────────────────── */

function World({ lite, onPlace }) {
  const { camera, scene } = useThree()
  const offset = useRef(0)
  const segs = useRef([])
  const hemi = useRef(), sun = useRef()
  const placeIdx = useRef(-1)
  useEffect(() => {
    scene.background = new THREE.Color(BIOMES[0].sky)
    scene.fog = new THREE.Fog(BIOMES[0].fog, 45, 230)
  }, [scene])
  useFrame((s, dt) => {
    // Wall-clock speed, so a slow device still cruises at the same pace (just in fewer frames).
    offset.current += Math.min(dt, 0.25) * SPEED
    const o = offset.current
    segs.current.forEach((g, i) => { if (g) g.position.z = -segmentDistance(i, o) })
    const look = lookAt(o)
    scene.background.set(look.sky)
    scene.fog.color.set(look.fog)
    if (hemi.current) hemi.current.intensity = 0.95 * look.light
    if (sun.current) sun.current.intensity = 1.05 * look.light
    const t = s.clock.elapsedTime
    camera.position.set(-1.9 + Math.sin(t * 0.21) * 1.2, 4.3 + Math.sin(t * 0.17) * 0.25, 11.8)
    camera.lookAt(-1.9 + Math.sin(t * 0.21) * 0.5, 1.4, -10)
    const { index } = biomeAt(o + 30)
    if (index !== placeIdx.current) { placeIdx.current = index; onPlace?.(index) }
  })
  return (
    <group>
      <hemisphereLight ref={hemi} args={['#ffffff', '#5b6b4a', 0.95]} />
      <directionalLight ref={sun} position={[-40, 60, 30]} intensity={1.05} />
      <directionalLight position={[4, 9, 24]} intensity={0.75} />
      {BIOMES.map((b, i) => {
        const Place = PLACE[b.id]
        return (
          <group key={b.id} ref={(el) => { segs.current[i] = el }}>
            <RoadPiece ground={b.ground} />
            <Place lite={lite} />
          </group>
        )
      })}
      <Car />
    </group>
  )
}

/** Stop drawing frames while the hero is off screen. */
function Pause({ active }) {
  const { setFrameloop } = useThree()
  useEffect(() => { setFrameloop(active ? 'always' : 'never') }, [active, setFrameloop])
  return null
}

export default function RoadWorld({ active = true, lite = false, onPlace }) {
  return (
    <Canvas
      className="rw-canvas"
      dpr={lite ? [1, 1] : [1, 1.5]}
      gl={{ antialias: !lite, powerPreference: 'high-performance' }}
      camera={{ fov: 52, near: 0.1, far: 600, position: [-1.9, 4.3, 11.8] }}
      aria-hidden="true"
    >
      <Pause active={active} />
      <World lite={lite} onPlace={onPlace} />
    </Canvas>
  )
}
