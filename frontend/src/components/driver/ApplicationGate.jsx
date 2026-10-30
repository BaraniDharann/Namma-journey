import React, { useCallback, useEffect, useState } from 'react'
import Icon from '../dash/Icon'
import DocTile from './DocTile'
import { gateView, DOCUMENTS, getCachedVerification, setCachedVerification } from './gateView'
import { getDriverVerification, resubmitDriverDocuments } from '../../utils/api'

const POLL_REVIEW_MS = 30000 // waiting on the owner: notice approval without a manual refresh

/**
 * Sits outside every other driver gate. Until the owner approves the application, drivers see
 * only a status screen: a short wait message while under review, or the reason and a re-upload
 * form when rejected. Approved drivers (every owner-created driver, too) pass straight through.
 */
export default function ApplicationGate({ driverId, children }) {
  const [verification, setVerification] = useState(() => getCachedVerification(driverId))
  const [failed, setFailed] = useState(false)
  const [files, setFiles] = useState({})
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')

  const check = useCallback(async () => {
    try {
      const { data } = await getDriverVerification(driverId)
      setCachedVerification(driverId, data)
      setVerification(data)
      setFailed(false)
    } catch {
      // Only matters while we have nothing to show; a later blip keeps the screen we have.
      setFailed(true)
    }
  }, [driverId])

  const view = gateView(verification)

  // Always confirm in the background, but once approved there is nothing left to watch for.
  useEffect(() => {
    if (!driverId) return undefined
    check()
    if (view === 'app') return undefined
    const onFocus = () => check()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [driverId, check, view === 'app']) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (view !== 'review') return undefined
    const t = setInterval(check, POLL_REVIEW_MS)
    return () => clearInterval(t)
  }, [view, check])

  const resubmit = async (e) => {
    e.preventDefault()
    const chosen = DOCUMENTS.filter((d) => files[d.key])
    if (!chosen.length) { setError('Choose at least one document to upload.'); return }
    setSending(true); setError('')
    try {
      const fd = new FormData()
      chosen.forEach((d) => fd.append(d.key, files[d.key]))
      await resubmitDriverDocuments(driverId, fd)
      setFiles({})
      await check()
    } catch (err) {
      if (err.response?.status === 409) {
        // The state changed under us (e.g. the owner already acted); show what it is now.
        setError('Your application was just updated. Please review the latest status.')
        await check()
      } else {
        setError(err.response?.data?.error || err.response?.data?.message || 'Could not upload. Check your connection and try again.')
      }
    } finally {
      setSending(false)
    }
  }

  if (view === 'app') return children

  if (view === 'loading') {
    if (failed) {
      return (
        <section className="pc-panel pc-span-12 pc-tg" role="alert">
          <h1 className="pc-tg-title">Can&apos;t reach Namma Journey</h1>
          <p className="pc-tg-lead">We need to check your application before you can see trips.</p>
          <button type="button" className="pc-btn" onClick={() => { setFailed(false); check() }}>
            <Icon name="refresh" size={18} /> Try again
          </button>
        </section>
      )
    }
    return <div className="pc-skel-stack" aria-busy="true"><span className="pc-skel" style={{ height: 220, width: '100%' }} /></div>
  }

  if (view === 'review') {
    return (
      <section className="pc-panel pc-span-12 pc-tg" aria-labelledby="pc-app-title">
        <div className="pc-tg-copy">
          <span className="pc-tg-badge"><Icon name="clock" size={28} /></span>
          <h1 id="pc-app-title" className="pc-tg-title">Your application is under review</h1>
          <p className="pc-tg-lead">We&apos;re checking your documents. Most drivers are approved within 15 minutes; if anything needs a closer look, it can take a little longer. This screen unlocks by itself once you&apos;re approved.</p>
          <p className="pc-tg-wait" aria-live="polite"><span className="pc-tg-pulse" aria-hidden /> Waiting for approval…</p>
        </div>
      </section>
    )
  }

  const missing = verification.missingDocuments || []
  return (
    <section className="pc-panel pc-span-12 pc-tg" aria-labelledby="pc-app-title" style={{ display: 'block' }}>
      <span className="pc-tg-badge"><Icon name="alert" size={28} /></span>
      <h1 id="pc-app-title" className="pc-tg-title">Your application needs changes</h1>
      <p className="pc-tg-lead">{verification.rejectionReason || 'The owner could not approve your documents.'}</p>
      {missing.length > 0 && <p className="pc-tg-lead">Still needed: {missing.map((k) => DOCUMENTS.find((d) => d.key === k)?.label || k).join(', ')}.</p>}
      <form onSubmit={resubmit}>
        <p className="pc-tg-lead">Upload the documents that need replacing, then resubmit.</p>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 12, margin: '12px 0 16px' }}>
          {DOCUMENTS.map((d) => (
            <DocTile key={d.key} label={d.label} icon={d.icon} file={files[d.key]} onChange={(f) => setFiles((p) => ({ ...p, [d.key]: f }))} />
          ))}
        </div>
        {error && <p className="pc-tg-error" role="alert"><Icon name="alert" size={16} /> {error}</p>}
        <button type="submit" className="pc-btn" disabled={sending}>{sending ? 'Uploading…' : 'Resubmit for review'}</button>
      </form>
    </section>
  )
}
