import L from 'leaflet'

/**
 * Per-trip vehicle markers for the live map.
 *
 * Every trip used to render the same taxi emoji, so two bookings open side by
 * side were indistinguishable and the marker carried no information at all.
 * Here the body shape comes from the party size (the only capacity signal the
 * booking actually has) and the paint colour from a hash of the booking id, so
 * a given trip always draws the same vehicle for the passenger and the driver
 * while neighbouring trips look different.
 *
 * The SVG is drawn nose-up: heading 0 is north, and callers rotate the
 * `.nj-veh-rot` element rather than re-rendering the icon.
 */

// Body shapes in the sizes this platform actually dispatches. Dimensions are in
// the 44x44 user-space the marker is drawn in.
const SHAPES = {
  hatchback: { w: 17, h: 26, rx: 7, label: 'Hatchback' },
  sedan: { w: 17, h: 31, rx: 7, label: 'Sedan' },
  suv: { w: 19, h: 33, rx: 5, label: 'SUV' },
  tempo: { w: 20, h: 36, rx: 4, label: 'Tempo Traveller' },
}

const SHAPE_KEYS = ['hatchback', 'sedan', 'suv', 'tempo']

const PALETTE = [
  { body: '#ef4444', dark: '#991b1b', glow: '239,68,68' },
  { body: '#3b82f6', dark: '#1d4ed8', glow: '59,130,246' },
  { body: '#10b981', dark: '#047857', glow: '16,185,129' },
  { body: '#f59e0b', dark: '#b45309', glow: '245,158,11' },
  { body: '#8b5cf6', dark: '#6d28d9', glow: '139,92,246' },
  { body: '#0ea5e9', dark: '#0369a1', glow: '14,165,233' },
  { body: '#ec4899', dark: '#be185d', glow: '236,72,153' },
  { body: '#475569', dark: '#1e293b', glow: '71,85,105' },
]

// FNV-1a. Any stable string hash would do; this one is short and spreads the
// low bits well enough that consecutive booking ids pick different colours.
function hash(str) {
  let h = 0x811c9dc5
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return Math.abs(h)
}

/**
 * Decide which vehicle a booking is drawn as.
 *
 * @param bookingId     stable id; also seeds the colour
 * @param travelMembers party size, when the caller knows it
 */
export function pickVehicle(bookingId, travelMembers) {
  const seed = hash(String(bookingId || 'trip'))
  const members = Number(travelMembers) || 0

  let shapeKey
  if (members >= 10) shapeKey = 'tempo'
  else if (members >= 5) shapeKey = 'suv'
  else if (members >= 3) shapeKey = 'sedan'
  else if (members >= 1) shapeKey = 'hatchback'
  else shapeKey = SHAPE_KEYS[seed % SHAPE_KEYS.length]

  const colour = PALETTE[(seed >> 3) % PALETTE.length]
  return { key: shapeKey, label: SHAPES[shapeKey].label, ...colour }
}

/** Nose-up vehicle silhouette as an SVG string. */
export function vehicleSvg(vehicle, size = 44) {
  const s = SHAPES[vehicle.key] || SHAPES.sedan
  const { w, h, rx } = s
  const x = (44 - w) / 2
  const y = (44 - h) / 2
  const r = (n) => +(y + h * n).toFixed(2)

  return `
    <svg viewBox="0 0 44 44" width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">
      <ellipse cx="22" cy="23.5" rx="${w / 2 + 1.5}" ry="${h / 2 + 1.5}" fill="rgba(15,23,42,0.22)"/>
      <rect x="${x - 2.8}" y="${r(0.3)}" width="3.2" height="2.4" rx="1.2" fill="${vehicle.dark}"/>
      <rect x="${x + w - 0.4}" y="${r(0.3)}" width="3.2" height="2.4" rx="1.2" fill="${vehicle.dark}"/>
      <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}"
            fill="${vehicle.body}" stroke="${vehicle.dark}" stroke-width="1.1"/>
      <path d="M${x + 2} ${r(0.3)} Q22 ${r(0.19)} ${x + w - 2} ${r(0.3)}
               L${x + w - 3} ${r(0.4)} Q22 ${r(0.34)} ${x + 3} ${r(0.4)} Z" fill="#e0f2fe"/>
      <rect x="${x + 2.6}" y="${r(0.41)}" width="${w - 5.2}" height="${(h * 0.25).toFixed(2)}"
            rx="2" fill="${vehicle.dark}" opacity="0.7"/>
      <path d="M${x + 3} ${r(0.68)} Q22 ${r(0.73)} ${x + w - 3} ${r(0.68)}
               L${x + w - 2} ${r(0.78)} Q22 ${r(0.82)} ${x + 2} ${r(0.78)} Z" fill="#cbd5e1"/>
      <rect x="${x + 1.8}" y="${(y + 0.9).toFixed(2)}" width="4" height="2.2" rx="1.1" fill="#fef9c3"/>
      <rect x="${x + w - 5.8}" y="${(y + 0.9).toFixed(2)}" width="4" height="2.2" rx="1.1" fill="#fef9c3"/>
      <rect x="${x + 1.8}" y="${(y + h - 3).toFixed(2)}" width="4" height="2" rx="1" fill="#fca5a5"/>
      <rect x="${x + w - 5.8}" y="${(y + h - 3).toFixed(2)}" width="4" height="2" rx="1" fill="#fca5a5"/>
    </svg>`
}

/**
 * Leaflet icon for a vehicle. Built once per trip: the animation rotates the
 * inner `.nj-veh-rot` node in place, because swapping the icon on every heading
 * change replaces the DOM node and makes the marker twitch.
 */
export function createVehicleIcon(vehicle) {
  return L.divIcon({
    className: 'nj-veh-icon',
    html: `
      <div class="nj-veh" style="--veh-glow:${vehicle.glow}">
        <div class="nj-veh-ring"></div>
        <div class="nj-veh-rot">${vehicleSvg(vehicle, 40)}</div>
      </div>`,
    iconSize: [44, 44],
    iconAnchor: [22, 22],
  })
}

/** Shared styles for the marker chrome; injected once by the map. */
export const VEHICLE_MARKER_CSS = `
  .nj-veh-icon { background: none !important; border: none !important; }
  .nj-veh {
    position: relative; width: 44px; height: 44px;
    display: flex; align-items: center; justify-content: center;
  }
  .nj-veh-ring {
    position: absolute; width: 44px; height: 44px; border-radius: 50%;
    border: 2px solid rgba(var(--veh-glow), 0.55);
    background: radial-gradient(circle, rgba(var(--veh-glow), 0.22) 0%, transparent 70%);
    animation: nj-veh-ping 2.2s ease-out infinite;
  }
  @keyframes nj-veh-ping {
    0% { transform: scale(0.55); opacity: 1; }
    100% { transform: scale(1.7); opacity: 0; }
  }
  .nj-veh-rot {
    width: 40px; height: 40px; line-height: 0;
    filter: drop-shadow(0 2px 5px rgba(15,23,42,0.35));
    will-change: transform;
  }
  @media (prefers-reduced-motion: reduce) {
    .nj-veh-ring { animation: none; opacity: 0.5; }
  }
`
