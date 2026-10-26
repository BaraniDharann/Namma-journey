import React from 'react'

/**
 * A person in three-quarter view, facing right (facing={-1} mirrors them).
 *
 * Origin is the ground between the feet; a standing adult is ~200 units tall.
 * Limbs are jointed (shoulder → elbow → hand, hip → knee → foot) and posed by
 * angle, so the same rig stands, sits, rides a bike or holds a phone. Walking,
 * breathing, blinking and looking around are CSS (see .pp-* in journey.css),
 * which keeps a crowd cheap: React only re-renders when a pose or mood changes.
 *
 * Angles: 0° hangs straight down; negative swings forward (toward the face).
 */

const HIP_Y = -96
const THIGH = 47
const SHIN = 43
const UPPER = 34
const FORE = 30
const POSE_T = 'transform .55s cubic-bezier(.16, 1, .3, 1)'

// [upper, forearm] per arm; [thigh, knee] per leg.
const POSES = {
  stand: { an: [5, -8], af: [-5, -10], ln: [0, 0], lf: [-3, 3], head: 0, lean: 0, drop: 0 },
  talk: { an: [-32, -62], af: [-5, -10], ln: [0, 0], lf: [-3, 3], head: -4, lean: 0, drop: 0 },
  phone: { an: [-14, -116], af: [-8, -84], ln: [0, 0], lf: [-3, 3], head: 13, lean: 2, drop: 0 },
  watch: { an: [-36, -106], af: [-5, -10], ln: [0, 0], lf: [-3, 3], head: 15, lean: 2, drop: 0 },
  rail: { an: [-168, 6], af: [-4, -14], ln: [0, 0], lf: [-3, 3], head: -2, lean: 0, drop: 0 },
  sit: { an: [-26, -52], af: [-20, -60], ln: [-88, 88], lf: [-84, 86], head: 2, lean: -3, drop: THIGH },
  drive: { an: [-48, -38], af: [-44, -40], ln: [-84, 80], lf: [-80, 78], head: 0, lean: 2, drop: THIGH },
  ride: { an: [-55, -6], af: [-50, -8], ln: [-70, 72], lf: [-66, 68], head: -4, lean: 12, drop: 0 },
  offer: { an: [62, -24], af: [-50, -8], ln: [-70, 72], lf: [-66, 68], head: 8, lean: 4, drop: 0 },
  pillion: { an: [-50, -30], af: [-44, -30], ln: [-75, 110], lf: [-72, 106], head: -2, lean: 4, drop: 0 },
}

/** Lighten (amt > 0) or darken (amt < 0) a #rrggbb colour. */
export function shade(hex, amt) {
  const n = parseInt(hex.slice(1), 16)
  const f = (c) => Math.max(0, Math.min(255, Math.round(amt < 0 ? c * (1 + amt) : c + (255 - c) * amt)))
  const rgb = (f(n >> 16) << 16) | (f((n >> 8) & 255) << 8) | f(n & 255)
  return `#${rgb.toString(16).padStart(6, '0')}`
}

export default function Person({
  look, pose = 'stand', mood = 'neutral', facing = 1, walking = false, idle = false,
  blink = 0, hold, helmet,
}) {
  const p = POSES[pose] || POSES.stand
  const cls = `pp${walking ? ' pp-walking' : ''}${idle ? ' pp-idle' : ''}`
  // A saree drapes over the legs when standing; seated, it follows the lap, so the legs wear it.
  const seated = p.drop > 0 || pose === 'ride' || pose === 'pillion'
  const skirt = look.body === 'saree' && !seated
  if (look.body === 'saree' && seated) look = { ...look, pants: look.saree }
  return (
    <g className={cls} transform={facing < 0 ? 'scale(-1 1)' : undefined}>
      <ellipse cy="2" rx={pose === 'sit' ? 30 : 24} ry="5" fill="rgba(15,23,42,.2)" />
      <g className="pp-bob">
        <g transform={p.drop ? `translate(0 ${p.drop})` : undefined}>
          <Leg x={-3} angles={p.lf} look={look} far />
          <Leg x={3} angles={p.ln} look={look} />
          {skirt && <Saree look={look} />}

          <g transform={p.lean ? `rotate(${p.lean} 0 ${HIP_Y})` : undefined}>
            <g className="pp-breathe" style={{ animationDelay: `${-blink * 0.7}s` }}>
              <Arm x={-4} angles={p.af} look={look} far hold={hold === 'phone' && pose === 'phone' ? 'thumb' : null} />
              <BackLayer look={look} head={p.head} helmet={helmet} />
              <Torso look={look} />
              <g transform="translate(1 -152)">
                <g className="pp-look">
                  <g style={{ transform: `rotate(${p.head}deg)`, transition: POSE_T }}>
                    <rect x="-5.5" y="-12" width="11" height="14" rx="3" fill={look.skin} />
                    <rect x="-5.5" y="-4" width="11" height="5" fill={shade(look.skin, -0.18)} opacity=".5" />
                    <g transform="translate(1 -24)">
                      <Head look={look} mood={mood} blink={blink} helmet={helmet} />
                    </g>
                  </g>
                </g>
              </g>
              {look.bag === 'backpack' && <path d="M-7 -152 Q8 -142 4 -112" stroke={shade(look.bagColor, -0.25)} strokeWidth="3.5" fill="none" strokeLinecap="round" />}
              {look.bag === 'sling' && <path d="M9 -152 Q2 -122 -14 -100" stroke={shade(look.bagColor, -0.25)} strokeWidth="3" fill="none" strokeLinecap="round" />}
              <Arm x={4} angles={p.an} look={look} hold={hold} />
            </g>
          </g>
        </g>
      </g>
    </g>
  )
}

/* ── limbs ── */

function Leg({ x, angles: [thigh, knee], look, far }) {
  const pants = far ? shade(look.pants, -0.16) : look.pants
  return (
    <g transform={`translate(${x} ${HIP_Y})`}>
      <g className={`pp-thigh${far ? ' pp-far' : ''}`} style={{ transform: `rotate(${thigh}deg)`, transition: POSE_T }}>
        <path d={`M0 0 L0 ${THIGH}`} stroke={pants} strokeWidth="14.5" strokeLinecap="round" />
        <g transform={`translate(0 ${THIGH})`}>
          <g className={`pp-knee${far ? ' pp-far' : ''}`} style={{ transform: `rotate(${knee}deg)`, transition: POSE_T }}>
            <path d={`M0 0 L0 ${SHIN - 3}`} stroke={pants} strokeWidth="11.5" strokeLinecap="round" />
            <g transform={`translate(0 ${SHIN})`}>
              <Shoe look={look} far={far} />
            </g>
          </g>
        </g>
      </g>
    </g>
  )
}

function Shoe({ look, far }) {
  const d = 'M-6 -3 Q-6 -8 -1 -8 H4 Q8 -5 14 -3 Q18 -1 17 3 Q17 6 13 6 H-5 Q-8 6 -7.5 2 Z'
  if (look.shoes === 'sandal') {
    const skin = far ? shade(look.skin, -0.12) : look.skin
    return (
      <g>
        <path d={d} fill={skin} />
        <path d="M-7 5.5 H17" stroke="#7c2d12" strokeWidth="2.4" strokeLinecap="round" />
        <path d="M2 -5 L9 2" stroke="#b45309" strokeWidth="2.2" strokeLinecap="round" />
      </g>
    )
  }
  const color = look.shoes === 'sneaker' ? '#f8fafc' : '#1f2937'
  return (
    <g>
      <path d={d} fill={far ? shade(color, -0.15) : color} />
      <path d="M-7.5 5 H16.5" stroke={look.shoes === 'sneaker' ? '#94a3b8' : '#0b0f19'} strokeWidth="2.4" strokeLinecap="round" />
      {look.shoes === 'sneaker' && <path d="M1 -4 L8 -1" stroke="#f97316" strokeWidth="1.8" strokeLinecap="round" />}
    </g>
  )
}

function Arm({ x, angles: [upper, fore], look, far, hold }) {
  const sleeveC = far ? shade(look.top, -0.18) : look.top
  const skin = far ? shade(look.skin, -0.14) : look.skin
  const sleeves = look.sleeves || 'short'
  const foreSleeve = sleeves === 'long' ? 'full' : sleeves === 'three' ? 'half' : null
  return (
    <g transform={`translate(${x} -146)`}>
      <g className={`pp-arm${far ? ' pp-far' : ''}`} style={{ transform: `rotate(${upper}deg)`, transition: POSE_T }}>
        <path d={`M0 0 L0 ${UPPER}`} stroke={skin} strokeWidth="9.5" strokeLinecap="round" />
        {sleeves !== 'none' && <path d={`M0 -2 L0 ${sleeves === 'short' ? 15 : UPPER}`} stroke={sleeveC} strokeWidth="12" strokeLinecap="round" />}
        <g transform={`translate(0 ${UPPER})`}>
          <g className={`pp-fore${far ? ' pp-far' : ''}`} style={{ transform: `rotate(${fore}deg)`, transition: POSE_T }}>
            <path d={`M0 0 L0 ${FORE - 4}`} stroke={skin} strokeWidth="8.5" strokeLinecap="round" />
            {foreSleeve && <path d={`M0 -2 L0 ${foreSleeve === 'half' ? 12 : FORE - 6}`} stroke={sleeveC} strokeWidth="10.5" strokeLinecap="round" />}
            {look.bangles && <path d={`M-4.5 ${FORE - 8} H4.5`} stroke="#f59e0b" strokeWidth="2.2" strokeLinecap="round" />}
            {look.watch && !far && <rect x="-5" y={FORE - 10} width="10" height="4" rx="1.5" fill="#334155" />}
            <ellipse cy={FORE} rx="4.8" ry="5.6" fill={skin} />
            {hold === 'phone' && (
              <g>
                <rect x="-1" y={FORE - 10} width="10" height="17" rx="2.2" fill="#0f172a" />
                <rect x="0.4" y={FORE - 8.6} width="7.2" height="12.6" rx="1" fill="#7dd3fc" opacity=".9" />
              </g>
            )}
            {hold === 'umbrella' && (
              <g>
                <path d={`M0 ${FORE} V${FORE + 70}`} stroke="#1f2937" strokeWidth="3" strokeLinecap="round" />
                <path d={`M-4 ${FORE + 8} Q0 ${FORE + 40} 0 ${FORE + 58} Q0 ${FORE + 40} 4 ${FORE + 8} Z`} fill="#1e293b" />
                <path d={`M0 ${FORE - 4} Q-6 ${FORE - 6} -6 ${FORE}`} stroke="#1f2937" strokeWidth="3" fill="none" strokeLinecap="round" />
              </g>
            )}
            {hold === 'helmet' && (
              <g transform={`translate(4 ${FORE + 6})`}>
                <path d="M-12 4 C-13 -12 13 -14 13 2 Z" fill="#f8fafc" stroke="#cbd5e1" />
                <path d="M-8 -6 Q2 -12 10 -6" stroke="#f97316" strokeWidth="3" fill="none" />
              </g>
            )}
          </g>
        </g>
      </g>
    </g>
  )
}

/* ── body ── */

const TORSO = 'M-12 -153 C-16 -153 -17 -149 -17 -143 L-16 -96 Q-15 -91 -10 -91 H11 Q15 -91 15 -96 L16 -141 C16 -149 13 -153 8 -153 Z'
const TORSO_BACK = 'M-12 -153 C-16 -153 -17 -149 -17 -143 L-16 -96 Q-15 -91 -10 -91 H-6 L-7 -153 Z'
const KURTA = 'M-12 -153 C-16 -153 -17 -149 -17 -143 L-20 -60 Q0 -55 21 -60 L16 -141 C16 -149 13 -153 8 -153 Z'
const KURTA_BACK = 'M-12 -153 C-16 -153 -17 -149 -17 -143 L-20 -60 Q-13 -58 -6 -57 L-7 -153 Z'
const BLOUSE = 'M-12 -153 C-16 -153 -17 -149 -17 -143 L-16 -122 H15 L16 -141 C16 -149 13 -153 8 -153 Z'

function Torso({ look }) {
  const c = look.top
  const dark = shade(c, -0.14)
  const line = shade(c, -0.32)
  if (look.body === 'saree') {
    return (
      <g>
        <rect x="-16" y="-124" width="31" height="10" fill={look.skin} />
        <path d={BLOUSE} fill={c} />
        <path d="M-12 -153 C-16 -153 -17 -149 -17 -143 L-16 -122 H-7 L-7 -153 Z" fill={dark} />
        {/* the pallu over the shoulder */}
        <path d="M11 -153 Q22 -134 0 -114 L-17 -112 L-16 -124 Q6 -136 2 -153 Z" fill={look.saree} />
        <path d="M0 -114 L-17 -112" stroke={look.border} strokeWidth="3" />
      </g>
    )
  }
  if (look.body === 'kurta') {
    return (
      <g>
        <path d={KURTA} fill={c} />
        <path d={KURTA_BACK} fill={dark} />
        <path d="M1 -153 Q6 -145 11 -153" stroke={look.trim || '#fbbf24'} strokeWidth="2.2" fill="none" />
        <path d="M6 -147 V-128" stroke={look.trim || '#fbbf24'} strokeWidth="1.4" opacity=".8" />
        <path d="M15 -96 L19 -61" stroke={line} strokeWidth="1.2" opacity=".6" />
        {look.scarf && <path d="M10 -153 Q21 -130 6 -100 L0 -104 Q13 -130 3 -153 Z" fill={look.scarf} />}
      </g>
    )
  }
  return (
    <g>
      <rect x="-15" y="-100" width="30" height="13" rx="5" fill={look.pants} />
      <path d={TORSO} fill={c} />
      <path d={TORSO_BACK} fill={dark} />
      {look.body === 'jacket' ? (
        <g>
          <path d="M-17 -121 H16" stroke="#e5e7eb" strokeWidth="4" />
          <path d="M6 -152 V-92" stroke={line} strokeWidth="1.6" />
          <path d="M-2 -153 L4 -147 L12 -153" stroke={line} strokeWidth="2" fill="none" />
        </g>
      ) : look.body === 'tshirt' ? (
        <path d="M1 -153 Q6 -147 11 -153" stroke={line} strokeWidth="1.8" fill="none" />
      ) : (
        <g>
          <path d="M0 -153 L6 -144 L12 -153" stroke={line} strokeWidth="1.8" fill="none" strokeLinejoin="round" />
          <path d="M6 -144 V-93" stroke={line} strokeWidth="1" opacity=".6" />
          {[-136, -122, -108].map((y) => <circle key={y} cx="6" cy={y} r="1" fill={line} opacity=".7" />)}
          {look.pocket && <rect x="8" y="-140" width="6" height="7" rx="1" fill="none" stroke={line} strokeWidth="1" opacity=".6" />}
        </g>
      )}
    </g>
  )
}

function Saree({ look }) {
  return (
    <g className="pp-skirt">
      <path d="M-17 -116 H16 Q20 -60 23 -4 Q0 2 -23 -4 Q-20 -60 -17 -116 Z" fill={look.saree} />
      <path d="M-17 -116 H-8 Q-11 -60 -14 -2 Q-19 -3 -23 -4 Q-20 -60 -17 -116 Z" fill={shade(look.saree, -0.15)} />
      <path d="M6 -110 L9 -3 M11 -110 L15 -3" stroke={shade(look.saree, -0.22)} strokeWidth="1.2" />
      <path d="M-23 -5 Q0 1 23 -5" stroke={look.border} strokeWidth="4" fill="none" />
    </g>
  )
}

/** Things that hang behind the body: long hair, a braid, a dupatta's tail, a bag. */
function BackLayer({ look, head, helmet }) {
  return (
    <g>
      {look.bag === 'backpack' && <rect x="-29" y="-150" width="17" height="46" rx="7" fill={look.bagColor} />}
      {look.bag === 'sling' && <rect x="-25" y="-108" width="16" height="20" rx="4" fill={look.bagColor} />}
      {look.scarf && look.body === 'kurta' && <path d="M-11 -152 Q-25 -120 -21 -82 L-14 -84 Q-18 -118 -6 -152 Z" fill={shade(look.scarf, -0.1)} />}
      {look.body === 'saree' && <path d="M-11 -152 Q-29 -112 -24 -66 L-16 -70 Q-20 -112 -6 -152 Z" fill={shade(look.saree, -0.08)} />}
      {!helmet && (look.hairStyle === 'long' || look.hairStyle === 'braid') && (
        <g transform="translate(1 -152)">
          <g style={{ transform: `rotate(${head}deg)`, transition: POSE_T }}>
            <g transform="translate(1 -24)">
              {look.hairStyle === 'long' ? (
                <path d="M-15 -6 C-17 -22 -2 -30 8 -26 L-6 -10 C-10 -2 -10 14 -8 36 Q-14 40 -21 33 C-21 18 -18 4 -15 -6 Z" fill={look.hair} />
              ) : (
                <g fill={look.hair}>
                  <path d="M-15 -4 C-16 -16 -8 -24 0 -24 L-8 -6 C-10 2 -10 10 -10 16 L-16 16 Z" />
                  {[0, 1, 2, 3, 4, 5].map((k) => (
                    <ellipse key={k} cx={-13 + (k % 2 ? 1.6 : -1.2)} cy={20 + k * 10} rx={5 - k * 0.3} ry="6.5" />
                  ))}
                  <path d="M-13 78 L-15 88 M-12 78 L-11 88" stroke={look.hair} strokeWidth="2" strokeLinecap="round" />
                  <circle cx="-12.5" cy="76" r="2.2" fill="#f97316" />
                </g>
              )}
            </g>
          </g>
        </g>
      )}
    </g>
  )
}

/* ── head ── */

const FACE = 'M-14 -4 C-15 -18 -6 -26 3 -26 C11 -26 16 -18 16 -7 C16 1 15 8 12 13 C9 18 4 20 0 20 C-7 20 -12 14 -14 6 Z'

function Head({ look, mood, blink, helmet }) {
  const skinD = shade(look.skin, -0.16)
  return (
    <g>
      {!helmet && look.hairStyle === 'bun' && (
        <g>
          <circle cx="-13" cy="-15" r="8" fill={look.hair} />
          {look.flowers && <path d="M-20 -10 Q-14 -2 -6 -6" stroke="#fef9c3" strokeWidth="3.4" fill="none" strokeDasharray="0.1 4.2" strokeLinecap="round" />}
        </g>
      )}
      <ellipse cx="-10.5" cy="3" rx="4" ry="5.2" fill={look.skin} />
      <ellipse cx="-10.2" cy="3.4" rx="1.8" ry="2.8" fill={skinD} />
      {look.earring && <circle cx="-10" cy="9.4" r="1.7" fill="#f59e0b" />}
      <path d={FACE} fill={look.skin} />
      <path d="M-14 -4 C-14 4 -12 12 -5 17.5 C-11 16 -15.5 9 -14 -4 Z" fill={skinD} opacity=".45" />
      {!helmet && <FrontHair look={look} />}
      {helmet === 'rider' ? (
        <RiderHelmet />
      ) : (
        <g>
          <Face mood={mood} blink={blink} hair={look.hair} skinD={skinD} />
          {look.beard && <path d="M-12 5 C-10 16 -2 21 4 20 C9 19 12 14 13 9 C10 13 6 14.5 3 13.5 C-2 12.5 -8 10 -12 5 Z" fill={look.hair} />}
          {look.mustache && <path d="M3.5 8.4 Q8 6 12.6 8.2 Q8.4 9.8 3.5 8.4 Z" fill={look.hair} />}
          {look.bindi && <circle cx="4.5" cy="-10" r="1.5" fill="#dc2626" />}
          {look.glasses && (
            <g fill="rgba(255,255,255,.12)" stroke="#1e293b" strokeWidth="1.5">
              <circle cx="8.2" cy="-2" r="4.4" />
              <ellipse cx="-1.6" cy="-2" rx="3.4" ry="4.1" />
              <path d="M3.8 -2.6 H1.8 M-5 -2.6 L-10.5 -0.5" fill="none" />
            </g>
          )}
          {helmet === 'pillion' && <PillionHelmet />}
        </g>
      )}
    </g>
  )
}

function FrontHair({ look }) {
  const c = look.hair
  switch (look.hairStyle) {
    case 'curly':
      return (
        <g fill={c}>
          {[[-12, -13, 6.5], [-7, -20, 7], [1, -23, 7.5], [9, -21, 6.8], [14, -14, 5.2], [-14.5, -4, 5.5]].map(([cx, cy, r]) => (
            <circle key={`${cx}${cy}`} cx={cx} cy={cy} r={r} />
          ))}
        </g>
      )
    case 'grey':
      return (
        <g>
          <path d="M-14.6 1 C-16 -12 -12 -21 -5 -23 C-9 -16 -10 -8 -10 -1 Z" fill={c} />
          <path d="M2 -24 Q9 -23 12 -19" stroke="#fff" strokeWidth="2" opacity=".35" fill="none" strokeLinecap="round" />
        </g>
      )
    case 'long':
    case 'braid':
    case 'bun':
      return <path d="M-14.6 1 C-16 -20 -4 -28.6 5 -27.6 C13 -27 17 -19 16.3 -11 C12 -18.5 6 -20.5 1 -19.5 C-4 -17.5 -8 -12 -10 -4 L-12 4 Z" fill={c} />
    default:
      return (
        <g>
          <path d="M-14.6 -2 C-16.5 -20 -6 -28.6 4 -28 C12 -27.4 17.4 -20 16.5 -10 C12.6 -14.5 6 -16.5 -1 -15.5 C-6 -14.5 -9 -10.5 -10 -4.5 L-12.5 3 Z" fill={c} />
          <path d="M-2 -25 Q6 -26 11 -22" stroke="#fff" strokeWidth="1.6" opacity=".18" fill="none" strokeLinecap="round" />
        </g>
      )
  }
}

const BROWS = {
  neutral: 'M4.5 -7.6 Q8 -9.2 11.6 -7.8 M-4.6 -7.4 Q-2 -8.6 0.8 -7.6',
  tired: 'M4.5 -8.8 Q8 -8.6 11.6 -6.4 M-4.6 -6.6 Q-2 -8.4 0.8 -8.8',
  shock: 'M4.5 -11 Q8 -13.6 11.6 -11 M-4.6 -10.8 Q-2 -12.8 0.8 -11',
  joy: 'M4.5 -8.6 Q8 -10.8 11.6 -8.6 M-4.6 -8.4 Q-2 -10.2 0.8 -8.6',
}

function Face({ mood, blink, hair, skinD }) {
  const ink = '#1c1917'
  const shock = mood === 'shock'
  const down = mood === 'focus' ? 1 : 0
  return (
    <g>
      <path d={BROWS[mood] || BROWS.neutral} stroke={hair} strokeWidth="2.3" fill="none" strokeLinecap="round" />
      {mood === 'joy' ? (
        <g stroke={ink} strokeWidth="1.9" fill="none" strokeLinecap="round">
          <path d="M5 -1.6 Q8.2 -4.8 11.4 -1.6" />
          <path d="M-4 -1.6 Q-1.6 -4.2 0.8 -1.6" />
        </g>
      ) : (
        <g className="pp-eyes" style={{ animationDelay: `${-blink}s` }}>
          <ellipse cx="8.2" cy="-2" rx="3.4" ry={shock ? 3.8 : 2.8} fill="#fff" />
          <ellipse cx="-1.6" cy="-2" rx="2.6" ry={shock ? 3.4 : 2.6} fill="#fff" />
          <circle cx="9.1" cy={-1.8 + down} r={shock ? 1.5 : 1.95} fill={ink} />
          <circle cx="-0.8" cy={-1.8 + down} r={shock ? 1.3 : 1.7} fill={ink} />
          <circle cx="9.7" cy={-2.6 + down} r=".6" fill="#fff" />
          {mood === 'tired' && (
            <g fill={skinD}>
              <path d="M4.6 -5 Q8.2 -6 11.8 -4.6 L11.8 -2 Q8.2 -3 4.6 -2.2 Z" />
              <path d="M-4.4 -4.6 Q-1.6 -5.6 1.2 -4.8 L1.2 -2.2 Q-1.6 -3 -4.4 -2.2 Z" />
            </g>
          )}
        </g>
      )}
      <path d="M12 -1 Q15.6 5 12 6.6" stroke={skinD} strokeWidth="1.8" fill="none" strokeLinecap="round" />
      <Mouth mood={mood} />
      {mood === 'joy' && (
        <g fill="#fb7185" opacity=".32">
          <circle cx="10.5" cy="5.5" r="3" />
          <circle cx="-4" cy="5.5" r="2.6" />
        </g>
      )}
    </g>
  )
}

function Mouth({ mood }) {
  const lip = '#7c2d12'
  switch (mood) {
    case 'shock':
      return <ellipse cx="7.6" cy="11.4" rx="2.6" ry="3.6" fill="#7f1d1d" />
    case 'talk':
      return <ellipse className="pp-talk" cx="7.6" cy="10.8" rx="2.9" ry="2.3" fill="#7f1d1d" />
    case 'joy':
      return (
        <g>
          <path d="M3.4 8.8 Q7.6 16.4 11.8 8.8 Z" fill="#7f1d1d" />
          <path d="M4.2 9.2 H11 L10.4 10.8 H4.8 Z" fill="#fff" />
        </g>
      )
    case 'smile':
      return <path d="M3.6 9.2 Q7.6 13.4 11.4 9" stroke={lip} strokeWidth="1.8" fill="none" strokeLinecap="round" />
    case 'tired':
      return <path d="M4.6 11.2 Q7.6 9.8 10.4 11.2" stroke={lip} strokeWidth="1.7" fill="none" strokeLinecap="round" />
    default:
      return <path d="M4.6 10.4 Q7.6 11.3 10.2 10.2" stroke={lip} strokeWidth="1.7" fill="none" strokeLinecap="round" />
  }
}

function RiderHelmet() {
  return (
    <g>
      <path d="M-16.5 -2 C-18.5 -24 -4 -33 6 -32 C16.5 -31 21.5 -20 20.5 -5 L19.5 14 Q12 22.5 0 22.5 Q-12.5 21.5 -16.5 12 Z" fill="#f97316" />
      <path d="M-16.5 -2 C-18 8 -14 18 -6 21.5 Q-14 21 -16.5 12 Z" fill="#c2410c" />
      <path d="M3.5 -12.5 Q16 -13 20.5 -4 L19.5 7.5 Q12 9.5 3.5 7.5 Q0.5 -2.5 3.5 -12.5 Z" fill="#0f172a" />
      <path d="M7 -10 Q14 -10 17.5 -5" stroke="#7dd3fc" strokeWidth="1.6" opacity=".55" fill="none" strokeLinecap="round" />
      <path d="M-15 -10 Q-4 -27 12 -27.5" stroke="#fff" strokeWidth="3.2" fill="none" strokeLinecap="round" />
    </g>
  )
}

function PillionHelmet() {
  return (
    <g>
      <path d="M-16.4 1 C-18.4 -22 -4 -31.5 6 -30.6 C15.6 -29.8 20.4 -21 19.4 -10.6 L17.4 -8.6 C12.4 -16 4.4 -18.2 -2.6 -16.2 C-7.6 -13.4 -10.6 -7.2 -11.8 1.4 Z" fill="#f8fafc" stroke="#cbd5e1" strokeWidth="1.2" />
      <path d="M-12 -20 Q2 -31 14 -22.5" stroke="#f97316" strokeWidth="3.6" fill="none" strokeLinecap="round" />
      <path d="M-11 1 Q-8 14 2.5 19.5" stroke="#334155" strokeWidth="1.5" fill="none" />
    </g>
  )
}
