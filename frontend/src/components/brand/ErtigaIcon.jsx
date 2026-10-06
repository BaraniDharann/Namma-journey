import React from 'react'

/**
 * The client's car, a white Maruti Suzuki Ertiga, as an icon: front view with its wide chrome
 * grille, swept headlamps and the yellow taxi plate. No icon library has this vehicle, so it is
 * drawn here on Phosphor's grid and accepts the same props (size, weight, className, style),
 * which lets <Icon name="car" /> swap it in everywhere without callers changing.
 */
const STROKE = { thin: 1, light: 1.2, regular: 1.5, duotone: 1.5, bold: 2, fill: 1.5 }

export default function ErtigaIcon({ size = 24, weight = 'regular', className, style, ...rest }) {
  const sw = STROKE[weight] || 1.5
  const filled = weight === 'fill'
  const duo = weight === 'duotone'
  const line = { fill: 'none', stroke: 'currentColor', strokeWidth: sw, strokeLinecap: 'round', strokeLinejoin: 'round' }
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" className={className} style={style} {...rest}>
      {/* glasshouse: windscreen under a flat roof */}
      <path d="M6.3 10.3 7.5 5.6Q7.8 4.6 8.8 4.6h6.4q1 0 1.3 1l1.2 4.7" {...line} fill={filled || duo ? 'currentColor' : 'none'} fillOpacity={filled ? 1 : 0.2} />
      {filled && <path d="M7.7 9.6 8.6 6.1q.2-.5.7-.5h5.4q.5 0 .7.5l.9 3.5Z" fill="#000" fillOpacity=".3" />}
      {/* body and wheels */}
      <path d="M3.6 17.2v-4.1q0-1.6 1.5-2.2l1.2-.6h11.4l1.2.6q1.5.6 1.5 2.2v4.1q0 .6-.6.6H4.2q-.6 0-.6-.6Z" {...line} fill={filled || duo ? 'currentColor' : 'none'} fillOpacity={filled ? 1 : 0.2} />
      <path d="M5.2 17.8v1.4q0 .6.6.6h1.4q.6 0 .6-.6v-1.4M16.2 17.8v1.4q0 .6.6.6h1.4q.6 0 .6-.6v-1.4" {...line} />
      {/* mirrors */}
      <path d="M5.9 10.5 4.2 9.9M18.1 10.5l1.7-.6" {...line} />
      {/* swept headlamps and the chrome grille */}
      <path d="M5 12.6 8.4 13.5M19 12.6l-3.4.9" {...line} stroke={filled ? '#000' : 'currentColor'} strokeOpacity={filled ? 0.35 : 1} />
      <rect x="9.3" y="12.4" width="5.4" height="2.2" rx=".7" {...line} stroke={filled ? '#000' : 'currentColor'} strokeOpacity={filled ? 0.35 : 1} />
      {/* the yellow taxi plate */}
      <rect x="9.6" y="15.3" width="4.8" height="1.5" rx=".35" fill="#f5c518" stroke="#1f1f1f" strokeWidth=".5" />
    </svg>
  )
}
