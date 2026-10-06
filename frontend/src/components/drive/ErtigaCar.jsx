/* eslint-disable react/no-unknown-property -- react-three-fiber JSX props are three.js object props */
import React, { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'

/**
 * The client's own car, a white Maruti Suzuki Ertiga on a yellow taxi plate (TN 21 BX 1239),
 * seen from behind in the chase view. Units are roughly metres; the nose points at -z.
 *
 * The body is one side profile extruded across the width (with bevelled edges), so it keeps
 * the MPV silhouette: short bonnet, raked windscreen, long flat roof, upright tailgate.
 */

const R = 0.32          // tyre radius
const SQUASH = 0.87     // the profile is drawn tall and squashed to the real 1.69 m roof
const ARCH_Y = R / SQUASH
const AXLE = 1.37       // half wheelbase
const TRACK = 0.79      // half track
const WIDTH = 1.74
const PLATE_TEXT = 'TN21BX1239'

/** Side profile, forward = +x. Wheel arches are cut into the sill as arcs over each axle. */
function profile() {
  const s = new THREE.Shape()
  const arch = ARCH_Y + 0.08
  s.moveTo(-2.22, 0.34)
  s.lineTo(-AXLE - arch, 0.34)
  s.absarc(-AXLE, ARCH_Y, arch, Math.PI, 0, true)
  s.lineTo(AXLE - arch, 0.34)
  s.absarc(AXLE, ARCH_Y, arch, Math.PI, 0, true)
  s.lineTo(2.16, 0.34)
  s.quadraticCurveTo(2.3, 0.4, 2.29, 0.62)
  s.lineTo(2.25, 0.92)
  s.quadraticCurveTo(1.8, 1.06, 1.12, 1.14)  // bonnet
  s.lineTo(0.22, 1.86)                         // windscreen
  s.quadraticCurveTo(0.08, 1.95, -0.2, 1.96)
  s.lineTo(-1.84, 1.95)                        // roof
  s.quadraticCurveTo(-2.06, 1.93, -2.1, 1.82)
  s.lineTo(-2.2, 1.16)                         // tailgate
  s.lineTo(-2.27, 0.9)
  s.lineTo(-2.25, 0.34)
  return s
}

/** The glasshouse sits just proud of the body so side and rear glass read as one dark band. */
function glassProfile() {
  const s = new THREE.Shape()
  s.moveTo(1.02, 1.24)
  s.lineTo(0.26, 1.82)
  s.lineTo(-1.8, 1.86)
  s.quadraticCurveTo(-2.08, 1.84, -2.16, 1.74)
  s.lineTo(-2.25, 1.28)
  s.lineTo(1.02, 1.24)
  return s
}

function extruded(shape, width, bevel) {
  const g = new THREE.ExtrudeGeometry(shape, {
    depth: width - bevel * 2, bevelEnabled: bevel > 0, bevelThickness: bevel, bevelSize: bevel * 0.8,
    bevelSegments: 3, curveSegments: 18,
  })
  g.translate(0, 0, -(width - bevel * 2) / 2)
  g.rotateY(Math.PI / 2)  // profile x (forward) -> world -z, extrusion -> world x
  g.computeVertexNormals()
  return g
}

function plateTexture() {
  const c = document.createElement('canvas')
  c.width = 512; c.height = 128
  const x = c.getContext('2d')
  x.fillStyle = '#f5c518'; x.fillRect(0, 0, 512, 128)
  x.strokeStyle = '#1a1a1a'; x.lineWidth = 8; x.strokeRect(6, 6, 500, 116)
  x.fillStyle = '#1d4ed8'; x.fillRect(14, 14, 44, 100)
  x.fillStyle = '#fff'; x.font = 'bold 22px sans-serif'; x.textAlign = 'center'; x.fillText('IND', 36, 104)
  x.fillStyle = '#111'; x.font = 'bold 84px "Arial Narrow", Arial, sans-serif'; x.textBaseline = 'middle'
  x.fillText(PLATE_TEXT, 284, 68)
  const t = new THREE.CanvasTexture(c)
  t.colorSpace = THREE.SRGBColorSpace
  t.anisotropy = 4
  return t
}

/** Tyre plus a five-spoke alloy, so the rolling is actually visible. */
function Wheel({ x, z, wheelRef }) {
  const out = x > 0 ? 1 : -1
  return (
    <group position={[x, R, z]} ref={wheelRef}>
      <mesh rotation={[0, 0, Math.PI / 2]}><cylinderGeometry args={[R, R, 0.24, 28]} /><meshStandardMaterial color="#17191c" roughness={0.9} /></mesh>
      <mesh rotation={[0, 0, Math.PI / 2]} position={[out * 0.121, 0, 0]}><cylinderGeometry args={[0.235, 0.235, 0.01, 28]} /><meshStandardMaterial color="#2a2e33" roughness={0.6} /></mesh>
      <mesh rotation={[0, Math.PI / 2, 0]} position={[out * 0.124, 0, 0]}><torusGeometry args={[0.225, 0.024, 8, 28]} /><meshStandardMaterial color="#cfd5dc" metalness={0.7} roughness={0.3} /></mesh>
      {[0, 1, 2, 3, 4].map((i) => (
        <group key={i} rotation={[(i * Math.PI * 2) / 5, 0, 0]}>
          <mesh position={[out * 0.128, 0.11, 0]}><boxGeometry args={[0.02, 0.22, 0.06]} /><meshStandardMaterial color="#dfe4ea" metalness={0.7} roughness={0.28} /></mesh>
        </group>
      ))}
      <mesh rotation={[0, 0, Math.PI / 2]} position={[out * 0.13, 0, 0]}><cylinderGeometry args={[0.05, 0.05, 0.02, 12]} /><meshStandardMaterial color="#9aa3ad" metalness={0.6} roughness={0.35} /></mesh>
    </group>
  )
}

export default function ErtigaCar({ speed = 8.5, x = 0 }) {
  const rig = useRef(), body = useRef(), wheels = useRef([])
  const geo = useMemo(() => ({ body: extruded(profile(), WIDTH, 0.09), glass: extruded(glassProfile(), WIDTH + 0.02, 0.05) }), [])
  const plate = useMemo(() => plateTexture(), [])

  useFrame((s, dt) => {
    const t = s.clock.elapsedTime
    // The whole car (wheels included) drifts gently in its lane; only the body rides the springs.
    if (rig.current) rig.current.position.x = x + Math.sin(t * 0.35) * 0.35
    if (body.current) {
      body.current.position.y = Math.abs(Math.sin(t * 6.5)) * 0.018
      body.current.rotation.z = Math.sin(t * 0.8) * 0.01
      body.current.rotation.x = Math.sin(t * 1.3) * 0.004
    }
    // Rolling without slipping: the tread moves exactly as fast as the road.
    const spin = Math.min(dt, 0.25) * speed / R
    wheels.current.forEach((w) => { if (w) w.rotation.x -= spin })
  })

  const white = <meshStandardMaterial color="#ffffff" emissive="#4a5058" emissiveIntensity={0.5} roughness={0.35} metalness={0} />
  const black = <meshStandardMaterial color="#111316" roughness={0.6} />
  const chrome = <meshStandardMaterial color="#e8edf2" metalness={0.85} roughness={0.2} />
  const rear = 2.3  // world z of the tailgate face around the lamps (profile plus bevel)

  return (
    <group ref={rig} position={[x, 0, 0]}>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.015, 0]}>
        <planeGeometry args={[2.1, 4.9]} />
        <meshBasicMaterial color="#000" transparent opacity={0.26} />
      </mesh>

      <group ref={body}>
        <group scale={[1, SQUASH, 1]}>
        <mesh geometry={geo.body}>{white}</mesh>
        <mesh geometry={geo.glass}><meshStandardMaterial color="#1b2430" roughness={0.12} metalness={0.4} /></mesh>
        {/* underbody, so the arches never show daylight */}
        <mesh position={[0, 0.5, 0]}><boxGeometry args={[1.4, 0.3, 4.2]} />{black}</mesh>
        {/* black B-pillar and the white D-pillar either side */}
        <mesh position={[0, 1.55, -0.3]}><boxGeometry args={[WIDTH + 0.05, 0.58, 0.12]} />{black}</mesh>
        {/* roof rails and shark-fin antenna */}
        {[-0.6, 0.6].map((rx) => (
          <mesh key={rx} position={[rx, 2.01, 0.8]}><boxGeometry args={[0.06, 0.06, 1.95]} />{black}</mesh>
        ))}
        <mesh position={[0, 2.03, 1.62]} rotation={[0.3, 0, 0]}><boxGeometry args={[0.06, 0.1, 0.22]} />{black}</mesh>
        {/* door mirrors */}
        {[-1, 1].map((sd) => (
          <mesh key={sd} position={[sd * 0.97, 1.3, -0.98]}><boxGeometry args={[0.16, 0.13, 0.2]} />{white}</mesh>
        ))}

        {/* rear: wraparound tail lamps, chrome garnish, plate, bumper */}
        {[-1, 1].map((sd) => (
          <group key={sd}>
            <mesh position={[sd * 0.66, 1.08, rear - 0.02]}><boxGeometry args={[0.42, 0.34, 0.08]} /><meshStandardMaterial color="#b3121b" emissive="#ff2a2a" emissiveIntensity={0.55} roughness={0.25} /></mesh>
            <mesh position={[sd * 0.84, 1.08, rear - 0.2]}><boxGeometry args={[0.08, 0.3, 0.32]} /><meshStandardMaterial color="#b3121b" emissive="#ff2a2a" emissiveIntensity={0.4} /></mesh>
            <mesh position={[sd * 0.66, 1.08, rear + 0.025]}><boxGeometry args={[0.2, 0.06, 0.01]} /><meshBasicMaterial color="#ffe1e1" /></mesh>
            <mesh position={[sd * 0.72, 0.5, 2.35]}><boxGeometry args={[0.16, 0.05, 0.02]} /><meshBasicMaterial color="#e11d2a" /></mesh>
          </group>
        ))}
        <mesh position={[0, 1.18, rear + 0.005]}><boxGeometry args={[0.86, 0.05, 0.03]} />{chrome}</mesh>
        <mesh position={[0, 1.07, rear + 0.01]}><boxGeometry args={[0.12, 0.08, 0.02]} />{chrome}</mesh>
        <mesh position={[0, 0.88, 2.365]}>
          <planeGeometry args={[0.56, 0.16]} />
          <meshBasicMaterial map={plate} toneMapped={false} />
        </mesh>
        <mesh position={[0, 0.44, 2.33]}><boxGeometry args={[1.5, 0.18, 0.06]} />{black}</mesh>
        {/* rear wiper */}
        <mesh position={[0.05, 1.42, 2.27]} rotation={[-0.15, 0, 0.35]}><boxGeometry args={[0.55, 0.025, 0.02]} />{black}</mesh>
        {/* headlamps, for the rare moment the camera swings wide */}
        {[-1, 1].map((sd) => (
          <mesh key={`h${sd}`} position={[sd * 0.62, 0.86, -2.36]}><boxGeometry args={[0.44, 0.14, 0.06]} /><meshBasicMaterial color="#fff6d8" /></mesh>
        ))}
        </group>
      </group>

      {[[-TRACK, -AXLE], [TRACK, -AXLE], [-TRACK, AXLE], [TRACK, AXLE]].map(([wx, wz], k) => (
        <Wheel key={k} x={wx} z={wz} wheelRef={(el) => { wheels.current[k] = el }} />
      ))}
    </group>
  )
}
