import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import Icon from '../dash/Icon'
import { Panel } from '../dash/ui'

const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback
const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const CONFIRM_WORD = 'DELETE'

/**
 * "Delete account" for every role. The account is closed rather than erased: personal details are
 * wiped and login stops working, while past trips and payments stay on record (the server refuses
 * while a trip is open or money is still owed, and says what to finish first).
 *
 * @param onClose      (password) => Promise — the role's close call
 * @param passwordHint label under the password box (e.g. Google-only travellers have none)
 * @param extra        optional node listing what else is switched off (owner's fleet)
 * @param onOpen       optional callback when the dialog opens (owner loads that list)
 */
export default function CloseAccountPanel({ onClose, passwordHint, extra, onOpen, span = 12 }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const show = () => {
    setPassword(''); setTyped(''); setError(''); setOpen(true)
    onOpen?.()
  }
  const hide = () => { if (!busy) setOpen(false) }

  const confirm = async () => {
    setBusy(true); setError('')
    try {
      await onClose(password)
      logout()
      navigate('/', { replace: true })
    } catch (e) {
      setError(errorText(e, 'Could not close your account. Please try again.'))
      setBusy(false)
    }
  }

  return (
    <Panel span={span} title="Delete account">
      <p style={{ margin: '0 0 14px', fontSize: 13.5, color: 'var(--pc-muted)', lineHeight: 1.6 }}>
        Closes your account for good. Your name, contact details and documents are removed and you can no
        longer sign in. Past trips and payments stay on record, without your details.
      </p>
      <button type="button" className="pc-btn pc-btn-danger" onClick={show}>
        <Icon name="trash" />Delete account
      </button>

      {open && (
        <div className="modal-overlay" onClick={(e) => { if (e.target === e.currentTarget) hide() }}>
          <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="close-account-title" style={{ padding: 24, maxWidth: 460 }}>
            <h3 id="close-account-title" style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)', margin: '0 0 8px' }}>
              Delete your account?
            </h3>
            <p style={{ fontSize: 13.5, color: 'var(--pc-ink-2)', lineHeight: 1.6, margin: '0 0 14px' }}>
              This can&apos;t be undone. You&apos;ll be signed out on every device, and to use Namma Journey again
              you&apos;ll need to sign up afresh.
            </p>
            {extra}

            <label htmlFor="close-password" style={labelStyle}>Password</label>
            <input
              id="close-password"
              className="input-field"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              style={{ width: '100%', padding: '8px 12px' }}
            />
            {passwordHint && <div style={{ fontSize: 12, color: 'var(--pc-muted)', marginTop: 4 }}>{passwordHint}</div>}

            <label htmlFor="close-confirm" style={{ ...labelStyle, marginTop: 14 }}>
              Type <b>{CONFIRM_WORD}</b> to confirm
            </label>
            <input
              id="close-confirm"
              className="input-field"
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              autoCapitalize="characters"
              style={{ width: '100%', padding: '8px 12px' }}
            />

            {error && (
              <div className="pc-error" role="alert" style={{ marginTop: 14 }}>
                <Icon name="alert" size={16} /><span>{error}</span>
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, marginTop: 20, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
              <button type="button" className="pc-btn pc-btn-ghost" onClick={hide} disabled={busy}>Keep my account</button>
              <button
                type="button"
                className="pc-btn pc-btn-danger"
                onClick={confirm}
                disabled={busy || typed.trim().toUpperCase() !== CONFIRM_WORD}
              >
                <Icon name="trash" />{busy ? 'Deleting...' : 'Delete account'}
              </button>
            </div>
          </div>
        </div>
      )}
    </Panel>
  )
}
