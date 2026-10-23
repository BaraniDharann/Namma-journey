import React, { useState } from 'react'
import { driverVerifySignupEmail } from '../../utils/api'
import AuthShell, { Field, Alert, Submit, TextButton } from './AuthShell'

/**
 * The "enter the code we emailed you" step for an independent driver. Shared by the sign-up page
 * and by the login page (an unverified driver who signs in lands here too). `onResend` asks the
 * server for a fresh code; `onVerified` receives the same payload as a normal driver login.
 */
export default function EmailCodeScreen({ email, onVerified, onResend, onBack }) {
  const [otp, setOtp] = useState('')
  const [loading, setLoading] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    if (!/^\d{4,6}$/.test(otp)) { setError('Enter the code from your email'); return }
    setLoading(true); setError(''); setNotice('')
    try {
      const res = await driverVerifySignupEmail(email, otp)
      onVerified(res.data)
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Invalid or expired code')
    } finally {
      setLoading(false)
    }
  }

  const resend = async () => {
    setResending(true); setError(''); setNotice('')
    try {
      await onResend()
      setOtp('')
      setNotice('A new code has been sent to your email.')
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Could not resend the code. Please try again.')
    } finally {
      setResending(false)
    }
  }

  return (
    <AuthShell role="driver" title="Check your email" sub={<>We sent a code to <strong>{email}</strong>. Enter it to finish signing up.</>}>
      <Alert>{error}</Alert>
      <Alert tone="ok">{notice}</Alert>
      <form onSubmit={submit} className="au-form">
        <Field label="Email code" icon="lock">
          <input type="text" inputMode="numeric" className="au-otp" placeholder="Enter code" maxLength={6} value={otp} autoFocus
            autoComplete="one-time-code" onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} required />
        </Field>
        <Submit loading={loading} loadingText="Verifying...">Verify and continue</Submit>
        <TextButton onClick={resend}>{resending ? 'Sending…' : 'Resend code'}</TextButton>
        <TextButton back onClick={onBack}>Back</TextButton>
      </form>
    </AuthShell>
  )
}
