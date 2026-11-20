import React, { useEffect, useState } from 'react'
import Icon from './dash/Icon'

const SHOW_SECONDS = 30
const BLUR = 'blur(14px)'

/** Shown-for-30-seconds toggle shared by the document card and the small photo. */
function useReveal() {
  const [shown, setShown] = useState(false)
  const [left, setLeft] = useState(SHOW_SECONDS)
  useEffect(() => {
    if (!shown) return undefined
    const started = Date.now()
    const tick = setInterval(() => {
      const remaining = SHOW_SECONDS - Math.floor((Date.now() - started) / 1000)
      if (remaining <= 0) { setShown(false); setLeft(SHOW_SECONDS) } else setLeft(remaining)
    }, 500)
    return () => clearInterval(tick)
  }, [shown])
  return { shown, left, show: () => setShown(true), hide: () => { setShown(false); setLeft(SHOW_SECONDS) } }
}

/**
 * A personal document (Aadhaar, licence, PAN, RC, a driver's photo) shown blurred. Tapping it
 * shows it clearly for 30 seconds, then it blurs again. The server side does the real protecting:
 * these files are only served with a signed link that expires (SignedUploadUrls), are never
 * cached, and have random names. The blur keeps them unreadable on screens that are left open,
 * shared or looked over.
 */
export default function PrivateImage({ src, alt, height = 96, fit = 'cover', onOpen }) {
  const { shown, left, show, hide } = useReveal()
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
        onClick={shown ? onOpen : show}
        style={{
          width: '100%', height: '100%', objectFit: fit, display: 'block', userSelect: 'none',
          filter: shown ? 'none' : BLUR, transform: shown ? 'none' : 'scale(1.1)',
          transition: 'filter .2s ease', cursor: shown ? (onOpen ? 'zoom-in' : 'default') : 'pointer',
        }}
      />
      {shown ? (
        <button type="button" onClick={hide}
          style={{ position: 'absolute', top: 6, right: 6, display: 'inline-flex', alignItems: 'center', gap: 4, padding: '3px 8px',
            borderRadius: 999, border: 0, cursor: 'pointer', fontSize: 11, fontWeight: 800, background: 'rgba(17,24,39,.75)', color: '#fff' }}>
          <Icon name="eye-off" size={12} />Blur · {left}s
        </button>
      ) : (
        <button type="button" onClick={show} aria-label={`View ${alt}`}
          style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2,
            border: 0, padding: 0, cursor: 'pointer', background: 'transparent', color: '#fff', textShadow: '0 1px 3px rgba(0,0,0,.6)' }}>
          <Icon name="lock" size={20} />
          <span style={{ fontSize: 11.5, fontWeight: 800 }}>Tap to view</span>
        </button>
      )}
    </div>
  )
}

/** A small profile photo (driver, owner) shown blurred; tap to see it for 30 seconds. */
export function BlurredPhoto({ src, alt = 'Photo', style }) {
  const { shown, show, hide } = useReveal()
  if (!src) return null
  return (
    <button type="button" onClick={shown ? hide : show} aria-label={shown ? `Blur ${alt}` : `View ${alt}`}
      title={shown ? 'Tap to blur' : 'Tap to view'}
      style={{ padding: 0, border: 0, background: 'transparent', cursor: 'pointer', overflow: 'hidden', lineHeight: 0, borderRadius: style?.borderRadius }}>
      <img src={src} alt={alt} draggable={false} referrerPolicy="no-referrer" onContextMenu={(e) => e.preventDefault()}
        style={{ ...style, display: 'block', filter: shown ? 'none' : 'blur(8px)', transition: 'filter .2s ease' }} />
    </button>
  )
}
