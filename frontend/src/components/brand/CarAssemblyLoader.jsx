import React, { useEffect, useState } from 'react'

/**
 * The landing page's opening: the Namma Journey car builds itself — body drops in, wheels roll
 * in and lock, glass and roof rack snap on, headlights blink — then it pulls away to the right
 * and wipes the wordmark on before the whole layer fades to reveal the page.
 *
 * Pure SVG + CSS so it paints instantly while the 3D scene downloads underneath. Shown once per
 * browser session; reduced motion gets a short fade. Click or Escape skips it.
 */

const KEY = 'nj_intro_seen'
const DURATION = 3900

export function shouldPlayIntro() {
  try {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return false
    return window.sessionStorage.getItem(KEY) !== '1'
  } catch {
    return false
  }
}

export default function CarAssemblyLoader({ onDone }) {
  const [leaving, setLeaving] = useState(false)

  useEffect(() => {
    try { window.sessionStorage.setItem(KEY, '1') } catch { /* private mode: replay next time */ }
    const out = setTimeout(() => setLeaving(true), DURATION)
    const done = setTimeout(() => onDone?.(), DURATION + 650)
    const skip = (e) => { if (e.key === 'Escape') { setLeaving(true); setTimeout(() => onDone?.(), 400) } }
    document.addEventListener('keydown', skip)
    document.documentElement.style.overflow = 'hidden'
    return () => {
      clearTimeout(out); clearTimeout(done)
      document.removeEventListener('keydown', skip)
      document.documentElement.style.overflow = ''
    }
  }, [onDone])

  const skipNow = () => { setLeaving(true); setTimeout(() => onDone?.(), 400) }

  return (
    <div className={`nj-intro${leaving ? ' is-leaving' : ''}`} onClick={skipNow} role="status" aria-label="Loading Namma Journey">
      <div className="nj-intro-stage" aria-hidden="true">
        <div className="nj-intro-word">Namma <b>Journey</b></div>
        <div className="nj-intro-car">
          <svg viewBox="0 0 260 110" width="260" height="110">
            <g className="nji-rack">
              <rect x="70" y="6" width="98" height="7" rx="3" fill="#334155" />
              <rect x="82" y="-8" width="40" height="15" rx="4" fill="#0f766e" />
              <rect x="128" y="-4" width="30" height="11" rx="3" fill="#eda100" />
            </g>
            <g className="nji-body">
              <path d="M12 82 V48 Q12 22 40 20 L170 17 Q188 17 200 30 L228 60 Q252 64 254 78 V82 Q254 92 244 92 H22 Q12 92 12 82 Z" fill="#f97316" />
              <path d="M12 74 H254 V82 Q254 92 244 92 H22 Q12 92 12 82 Z" fill="#c2410c" />
              <path d="M88 26 V90 M156 26 V90" stroke="#c2410c" strokeWidth="1.5" opacity=".5" />
              <path d="M58 92 A22 22 0 0 1 102 92 Z M178 92 A22 22 0 0 1 222 92 Z" fill="#1f2937" />
            </g>
            <g className="nji-glass">
              <rect x="30" y="30" width="52" height="26" rx="5" fill="#d6ebf7" />
              <rect x="94" y="30" width="56" height="26" rx="5" fill="#d6ebf7" />
              <path d="M162 28 H178 Q184 28 188 33 L208 56 H162 Z" fill="#d6ebf7" />
            </g>
            <rect className="nji-stripe" x="14" y="64" width="238" height="4" fill="#fff" />
            <g className="nji-lamp">
              <rect x="243" y="64" width="10" height="7" rx="2" fill="#fef9c3" />
              <path className="nji-beam" d="M253 62 L420 40 L420 100 L253 74 Z" fill="url(#njiBeam)" />
            </g>
            <defs>
              <linearGradient id="njiBeam" x1="0" x2="1">
                <stop offset="0" stopColor="#fef9c3" stopOpacity=".85" />
                <stop offset="1" stopColor="#fef9c3" stopOpacity="0" />
              </linearGradient>
            </defs>
            <g className="nji-wheel nji-w1"><circle cx="80" cy="92" r="17" fill="#111827" /><circle cx="80" cy="92" r="8" fill="#e5e7eb" /><path d="M80 85 V99 M73 92 H87" stroke="#94a3b8" strokeWidth="2.5" /></g>
            <g className="nji-wheel nji-w2"><circle cx="200" cy="92" r="17" fill="#111827" /><circle cx="200" cy="92" r="8" fill="#e5e7eb" /><path d="M200 85 V99 M193 92 H207" stroke="#94a3b8" strokeWidth="2.5" /></g>
          </svg>
          <span className="nji-shadow" />
        </div>
        <div className="nj-intro-cap">Building your ride</div>
      </div>
    </div>
  )
}
