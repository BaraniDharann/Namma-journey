import React, { useCallback, useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { getDriverTelegramStatus, createOwnTelegramLink } from '../../utils/api'

const POLL_LOCKED_MS = 4000   // waiting for the driver to tap Start: feel instant
const POLL_OPEN_MS = 60000    // already connected: notice a later block within a minute

/**
 * Telegram trip alerts are mandatory for drivers, and there is no way to switch them off.
 * Until the driver's Telegram is connected, this replaces every driver page with one screen:
 * open the bot (or scan the code), tap Start, and the app unlocks by itself.
 *
 * If the driver later blocks the bot, the backend unlinks them and this locks again.
 * When the Telegram integration is switched off on the server, nobody is locked out.
 */
export default function TelegramGate({ driverId, children }) {
  const [state, setState] = useState('checking') // checking | open | locked | error
  const [link, setLink] = useState('')
  const [qr, setQr] = useState('')
  const [linkError, setLinkError] = useState('')
  const wasLocked = useRef(false)

  const check = useCallback(async () => {
    try {
      const { data } = await getDriverTelegramStatus(driverId)
      if (!data.required || data.linked) {
        if (wasLocked.current) toast.success('Telegram connected. New trips will arrive there.')
        wasLocked.current = false
        setState('open')
      } else {
        wasLocked.current = true
        setState('locked')
      }
    } catch {
      // Keep the current state on a blip; only a first check that fails shows the error.
      setState((s) => (s === 'checking' ? 'error' : s))
    }
  }, [driverId])

  const newLink = useCallback(async () => {
    setLinkError('')
    try {
      const { data } = await createOwnTelegramLink(driverId)
      setLink(data.linkUrl)
      setQr(await QRCode.toDataURL(data.linkUrl, { margin: 1, width: 232, color: { dark: '#0f2a2b', light: '#ffffff' } }))
    } catch {
      setLinkError('Could not create your link. Check your connection and try again.')
    }
  }, [driverId])

  useEffect(() => {
    if (!driverId) return undefined
    check()
    const onFocus = () => check()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [driverId, check])

  useEffect(() => {
    if (state !== 'locked' && state !== 'open') return undefined
    const t = setInterval(check, state === 'locked' ? POLL_LOCKED_MS : POLL_OPEN_MS)
    return () => clearInterval(t)
  }, [state, check])

  useEffect(() => {
    if (state === 'locked' && !link) newLink()
  }, [state, link, newLink])

  if (state === 'open') return children

  if (state === 'checking') {
    return <div className="pc-skel-stack" aria-busy="true"><span className="pc-skel" style={{ height: 220, width: '100%' }} /></div>
  }

  if (state === 'error') {
    return (
      <section className="pc-panel pc-span-12 pc-tg" role="alert">
        <h1 className="pc-tg-title">Can&apos;t reach Namma Journey</h1>
        <p className="pc-tg-lead">We need to check your Telegram connection before you can see trips.</p>
        <button type="button" className="pc-btn" onClick={() => { setState('checking'); check() }}>
          <Icon name="refresh" size={18} /> Try again
        </button>
      </section>
    )
  }

  return (
    <section className="pc-panel pc-span-12 pc-tg" aria-labelledby="pc-tg-title">
      <div className="pc-tg-copy">
        <span className="pc-tg-badge"><Icon name="telegram" size={28} weight="fill" /></span>
        <h1 id="pc-tg-title" className="pc-tg-title">Connect Telegram to start driving</h1>
        <p className="pc-tg-lead">
          New trips arrive on Telegram with an Accept button. Telegram alerts are always on for every
          driver, so your trips stay locked until it&apos;s connected.
        </p>
        <ol className="pc-tg-steps">
          <li><b>1</b><span>Tap <em>Open Telegram</em><span className="pc-tg-desk">, or scan the code with your phone</span>.</span></li>
          <li><b>2</b><span>In <em>Namma Journey Driver</em>, tap <em>Start</em>.</span></li>
          <li><b>3</b><span>Come back here. This screen unlocks by itself.</span></li>
        </ol>
        <div className="pc-tg-actions">
          <a className={`pc-btn${link ? '' : ' is-disabled'}`} href={link || undefined} target="_blank" rel="noopener noreferrer" aria-disabled={!link}>
            <Icon name="telegram" size={18} weight="fill" /> Open Telegram
          </a>
          <button type="button" className="pc-btn pc-btn-ghost" onClick={newLink}>Get a new link</button>
        </div>
        {linkError && <p className="pc-tg-error"><Icon name="alert" size={16} /> {linkError}</p>}
        <p className="pc-tg-wait" aria-live="polite"><span className="pc-tg-pulse" aria-hidden /> Waiting for you to tap Start…</p>
      </div>
      <div className="pc-tg-qr">
        {qr ? <img src={qr} alt="QR code that opens the Namma Journey Driver bot in Telegram" width={232} height={232} /> : <span className="pc-skel" style={{ width: 232, height: 232 }} />}
        <small>Scan with your phone camera</small>
      </div>
    </section>
  )
}
