import React, { useState } from 'react'
import Icon from './dash/Icon'

/** Blur used for every personal photo and ID document. Strong enough that text and faces can't be read. */
export const PRIVATE_BLUR = 'blur(14px)'

/**
 * A personal document (Aadhaar, licence, PAN, RC, a driver's photo), always shown blurred. There is
 * no tap-to-view: the picture is never shown clearly on screen. The server side still protects the
 * file itself: signed links that expire (SignedUploadUrls), no caching, random names.
 */
export default function PrivateImage({ src, alt, height = 96, fit = 'cover' }) {
  const [failed, setFailed] = useState(false)

  if (!src) return null

  const frame = {
    position: 'relative', width: '100%', height, borderRadius: 12, overflow: 'hidden',
    border: '1px solid var(--pc-line)', background: 'var(--pc-wash)',
  }

  if (failed) {
    return (
      <div role="alert" style={{ ...frame, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 8, textAlign: 'center', fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)' }}>
        This view link has expired. Refresh the page to see it.
      </div>
    )
  }

  return (
    <div style={frame}>
      <img
        src={src}
        alt={alt}
        draggable={false}
        referrerPolicy="no-referrer"
        onContextMenu={(e) => e.preventDefault()}
        onError={() => setFailed(true)}
        style={{ width: '100%', height: '100%', objectFit: fit, display: 'block', userSelect: 'none', pointerEvents: 'none',
          filter: PRIVATE_BLUR, transform: 'scale(1.1)' }}
      />
      <span aria-hidden="true" style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4,
        color: '#fff', textShadow: '0 1px 3px rgba(0,0,0,.6)', fontSize: 11.5, fontWeight: 800 }}>
        <Icon name="lock" size={16} />Private
      </span>
    </div>
  )
}

/** A small profile photo (driver, owner), always shown blurred. */
export function BlurredPhoto({ src, alt = 'Photo', style }) {
  if (!src) return null
  return (
    <span style={{ display: 'inline-block', overflow: 'hidden', lineHeight: 0, borderRadius: style?.borderRadius, width: style?.width, height: style?.height, flexShrink: 0 }}>
      <img src={src} alt={alt} draggable={false} referrerPolicy="no-referrer" onContextMenu={(e) => e.preventDefault()}
        style={{ ...style, display: 'block', filter: 'blur(8px)', transform: 'scale(1.08)', userSelect: 'none', pointerEvents: 'none' }} />
    </span>
  )
}
