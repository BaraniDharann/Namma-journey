import React from 'react'

/**
 * An Indian auto-rickshaw in side view: rounded canvas hood, open passenger bay, one front wheel
 * and two at the back. No icon library draws one, so like ErtigaIcon it is drawn on Phosphor's
 * 24-unit grid and takes the same props, which lets <Icon name="auto" /> use it like any glyph.
 */
const STROKE = { thin: 1, light: 1.2, regular: 1.5, duotone: 1.5, bold: 2, fill: 1.5 }

export default function AutoRickshawIcon({ size = 24, weight = 'regular', className, style, ...rest }) {
  const sw = STROKE[weight] || 1.5
  const filled = weight === 'fill'
  const duo = weight === 'duotone'
  const line = { fill: 'none', stroke: 'currentColor', strokeWidth: sw, strokeLinecap: 'round', strokeLinejoin: 'round' }
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" className={className} style={style} {...rest}>
      {/* hood over the passenger bay, sloping down to the windscreen */}
      <path d="M4 15.5V9.2Q4 5.5 8.5 5.5h6.2q1.6 0 2.4 1.4l2.4 4.6V15.5" {...line} fill={filled || duo ? 'currentColor' : 'none'} fillOpacity={filled ? 1 : 0.2} />
      {/* open side and the driver's windscreen pillar */}
      <path d="M7 15.5V9.5q0-1 1-1h4.5v7M14.8 8.6l2.2 3.9" {...line} stroke={filled ? '#000' : 'currentColor'} strokeOpacity={filled ? 0.35 : 1} />
      {/* floor line */}
      <path d="M3.2 15.5h17.6" {...line} />
      {/* rear and front wheels */}
      <circle cx="7" cy="17.6" r="1.9" {...line} />
      <circle cx="17.6" cy="17.6" r="1.9" {...line} />
    </svg>
  )
}
