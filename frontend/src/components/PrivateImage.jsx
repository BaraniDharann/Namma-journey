import React, { useEffect, useState } from 'react'
import Icon from './dash/Icon'

const SHOW_SECONDS = 30

/**
 * A personal document (Aadhaar, licence, PAN, RC, a driver's photo) hidden until someone asks to
 * see it. Hidden means not loaded: no <img> is rendered and nothing is downloaded until "View" is
 * tapped, so the picture is not in the page, the browser cache or a screenshot of the screen by
 * default. Once shown it hides itself again after 30 seconds.
 *
 * The server side does the real protecting: these files are only served with a signed link that
 * expires (SignedUploadUrls), are never cached, and have random names. This keeps them off screens
 * that are left open, shared or looked over.
 */
export default function PrivateImage({ src, alt, height = 96, fit = 'cover', onOpen }) {
  const [shown, setShown] = useState(false)
  const [left, setLeft] = useState(SHOW_SECONDS)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!shown) return undefined
    const started = Date.now()
    const tick = setInterval(() => {
      const remaining = SHOW_SECONDS - Math.floor((Date.now() - started) / 1000)
      if (remaining <= 0) { setShown(false); setLeft(SHOW_SECONDS) } else setLeft(remaining)
    }, 500)
    return () => clearInterval(tick)
  }, [shown])

  if (!src) return null

  const frame = {
    position: 'relative', width: '100%', height, borderRadius: 12, overflow: 'hidden',
    border: '1px solid var(--pc-line)', background: 'var(--pc-wash)',
  }

  if (!shown) {
    return (
      <button type="button" onClick={() => { setFailed(false); setShown(true) }} aria-label={`View ${alt}`}
        style={{ ...frame, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
          cursor: 'pointer', padding: 0, color: 'var(--pc-ink-2)',
          // A frosted look that says "something private is here", drawn without the real image.
          backgroundImage: 'repeating-linear-gradient(135deg, rgba(120,120,140,.10) 0 10px, rgba(120,120,140,.20) 10px 20px)' }}>
        <span aria-hidden="true" style={{ position: 'absolute', inset: 0, backdropFilter: 'blur(6px)', WebkitBackdropFilter: 'blur(6px)' }} />
        <span style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <Icon name="lock" size={20} />
          <span style={{ fontSize: 11.5, fontWeight: 800 }}>Hidden · tap to view</span>
        </span>
      </button>
    )
  }

  return (
    <div style={frame}>
      {failed ? (
        <div role="alert" style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 8, textAlign: 'center', fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)' }}>
          This view link has expired. Refresh the page to see it.
        </div>
      ) : (
        <img
          src={src}
          alt={alt}
          draggable={false}
          referrerPolicy="no-referrer"
          onContextMenu={(e) => e.preventDefault()}
          onError={() => setFailed(true)}
          onClick={onOpen}
          style={{ width: '100%', height: '100%', objectFit: fit, display: 'block', userSelect: 'none', cursor: onOpen ? 'zoom-in' : 'default' }}
        />
      )}
      <button type="button" onClick={() => setShown(false)}
        style={{ position: 'absolute', top: 6, right: 6, display: 'inline-flex', alignItems: 'center', gap: 4, padding: '3px 8px',
          borderRadius: 999, border: 0, cursor: 'pointer', fontSize: 11, fontWeight: 800, background: 'rgba(17,24,39,.75)', color: '#fff' }}>
        <Icon name="eye-off" size={12} />Hide · {left}s
      </button>
    </div>
  )
}
