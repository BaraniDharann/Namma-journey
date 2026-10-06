import React, { useState, useRef, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { GoogleLogin } from '@react-oauth/google'
import { useAuth } from '../context/AuthContext'
import { userLogin, googleLogin, sendOtp, userForgotPassword, saveUserPassword } from '../utils/api'
import { offerToSaveLogin, wasSaveOfferSkipped, skipSaveOffer } from '../utils/rememberLogin'
import AuthShell, { Field, PasswordInput, Alert, Submit, TextButton } from '../components/auth/AuthShell'

export default function LoginPage() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [googleLoading, setGoogleLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()

  // Google sign-in waiting on the "save a password" step: { token, userId, email, name, ... }
  const [saveFor, setSaveFor] = useState(null)
  const [savePw, setSavePw] = useState({ password: '', confirm: '' })
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')

  const finishGoogle = (auth) => {
    login(auth)
    navigate('/user/dashboard')
  }

  const handleSavePassword = async (e) => {
    e.preventDefault()
    if (savePw.password.length < 8) { setSaveError('Password must be at least 8 characters'); return }
    if (savePw.password !== savePw.confirm) { setSaveError('Passwords do not match'); return }
    setSaving(true); setSaveError('')
    try {
      await saveUserPassword(saveFor.userId, savePw.password, saveFor.token)
      void offerToSaveLogin(saveFor.email, savePw.password, saveFor.name)
      finishGoogle(saveFor)
    } catch (err) {
      setSaveError(err.response?.data?.error || 'Could not save the password. Try again.')
    } finally {
      setSaving(false)
    }
  }

  const skipSave = () => {
    skipSaveOffer(saveFor.email)
    finishGoogle(saveFor)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await userLogin({ loginType: 'EMAIL', email: form.email, password: form.password })
      void offerToSaveLogin(form.email, form.password, res.data?.name)
      login(res.data)
      navigate('/user/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Invalid credentials. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleGoogleSuccess = async (credentialResponse) => {
    setGoogleLoading(true)
    setError('')
    try {
      const res = await googleLogin(credentialResponse.credential)
      // First time in with Google there is no password for the browser to remember, so offer
      // to save one before the session starts (storing it redirects away from this page).
      if (res.data?.hasPassword === false && !wasSaveOfferSkipped(res.data.email)) {
        setSaveFor(res.data)
        return
      }
      login(res.data)
      navigate('/user/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Google login failed. Please try again.')
    } finally {
      setGoogleLoading(false)
    }
  }

  // The Google Identity button only accepts a fixed pixel width (no percentage/responsive
  // support), so it's measured off its own container and re-measured on resize — otherwise
  // its hard-coded 420px overflows any viewport narrower than that (phones included).
  const googleBtnRef = useRef(null)
  const [googleBtnWidth, setGoogleBtnWidth] = useState(420)
  useEffect(() => {
    const el = googleBtnRef.current
    if (!el) return
    const update = () => setGoogleBtnWidth(Math.max(200, Math.min(420, Math.floor(el.clientWidth))))
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const [showForgot, setShowForgot] = useState(false)
  const [forgotStep, setForgotStep] = useState(1)
  const [forgotForm, setForgotForm] = useState({ email: '', otp: '', newPassword: '' })
  const [forgotLoading, setForgotLoading] = useState(false)
  const [forgotMsg, setForgotMsg] = useState('')
  const [forgotError, setForgotError] = useState('')

  const handleForgotSendOtp = async (e) => {
    e.preventDefault()
    setForgotLoading(true); setForgotError('')
    try {
      await sendOtp(forgotForm.email)
      setForgotStep(2)
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Failed to send OTP')
    } finally { setForgotLoading(false) }
  }

  const handleForgotReset = async (e) => {
    e.preventDefault()
    setForgotLoading(true); setForgotError('')
    try {
      await userForgotPassword(forgotForm)
      setForgotMsg('Password reset successful! Please login.')
      setForgotStep(3)
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Reset failed. Check OTP and try again.')
    } finally { setForgotLoading(false) }
  }

  if (saveFor) return (
    <AuthShell role="user" title="Save your login" sub={<>Add a password for <strong>{saveFor.email}</strong> and your browser can remember it. Next time, tap the email box and pick it.</>}>
      <Alert>{saveError}</Alert>
      <form onSubmit={handleSavePassword} className="au-form">
        <Field label="Email address" icon="mail">
          <input type="email" name="username" autoComplete="username" value={saveFor.email} readOnly />
        </Field>
        <Field label="Password" icon="lock">
          <PasswordInput name="new-password" placeholder="Min 8 characters" value={savePw.password} autoComplete="new-password" autoFocus
            onChange={e => setSavePw(p => ({ ...p, password: e.target.value }))} required />
        </Field>
        <Field label="Confirm password" icon="lock">
          <PasswordInput placeholder="Repeat password" value={savePw.confirm} autoComplete="new-password"
            onChange={e => setSavePw(p => ({ ...p, confirm: e.target.value }))} required />
        </Field>
        <Submit loading={saving} loadingText="Saving...">Save and continue</Submit>
        <TextButton back onClick={skipSave}>Not now, continue with Google only</TextButton>
      </form>
    </AuthShell>
  )

  if (showForgot) return (
    <AuthShell role="user" title="Reset your password" sub={forgotStep === 1 ? 'We will email you a one-time code.' : forgotStep === 2 ? <>Code sent to <strong>{forgotForm.email}</strong></> : null}>
      <Alert>{forgotError}</Alert>
      {forgotStep === 3 ? (
        <div className="au-form">
          <Alert tone="ok">{forgotMsg}</Alert>
          <button type="button" className="au-submit" onClick={() => { setShowForgot(false); setForgotStep(1); setForgotForm({ email: '', otp: '', newPassword: '' }) }}>
            Back to login
          </button>
        </div>
      ) : forgotStep === 1 ? (
        <form onSubmit={handleForgotSendOtp} className="au-form">
          <Field label="Email address" icon="mail">
            <input type="email" placeholder="you@example.com" value={forgotForm.email} autoComplete="email"
              onChange={e => setForgotForm(p => ({ ...p, email: e.target.value }))} required />
          </Field>
          <Submit loading={forgotLoading} loadingText="Sending...">Send OTP</Submit>
          <TextButton back onClick={() => setShowForgot(false)}>Back to login</TextButton>
        </form>
      ) : (
        <form onSubmit={handleForgotReset} className="au-form">
          <Field label="OTP" icon="lock">
            <input type="text" inputMode="numeric" className="au-otp" placeholder="6-digit OTP" maxLength={6} value={forgotForm.otp}
              onChange={e => setForgotForm(p => ({ ...p, otp: e.target.value }))} required />
          </Field>
          <Field label="New password" icon="lock">
            <PasswordInput placeholder="New password (min 8 chars, include @#$)" value={forgotForm.newPassword} autoComplete="new-password"
              onChange={e => setForgotForm(p => ({ ...p, newPassword: e.target.value }))} required />
          </Field>
          <Submit loading={forgotLoading} loadingText="Resetting...">Reset password</Submit>
          <TextButton back onClick={() => setForgotStep(1)}>Change email</TextButton>
        </form>
      )}
    </AuthShell>
  )

  return (
    <AuthShell
      role="user"
      title="Welcome back"
      sub="Sign in to plan, book and track your trips."
      footer={<p className="au-alt">Don&apos;t have an account? <Link to="/signup">Sign up free</Link></p>}
    >
      <Alert>{error}</Alert>

      <div ref={googleBtnRef} className="au-google">
        {googleLoading ? (
          <div className="au-google-wait"><span className="spinner" /> Signing in with Google...</div>
        ) : (
          <GoogleLogin onSuccess={handleGoogleSuccess} onError={() => setError('Google sign-in failed.')}
            width={String(googleBtnWidth)} theme="outline" size="large" text="continue_with" shape="pill" />
        )}
      </div>

      <div className="au-divider">or sign in with email</div>

      <form onSubmit={handleSubmit} className="au-form">
        <Field label="Email address" icon="mail">
          <input type="email" name="username" placeholder="you@example.com" value={form.email} autoComplete="username"
            onChange={e => setForm({ ...form, email: e.target.value })} required />
        </Field>
        <Field label="Password" icon="lock" aside={<TextButton onClick={() => setShowForgot(true)}>Forgot password?</TextButton>}>
          <PasswordInput name="password" placeholder="Enter your password" value={form.password} autoComplete="current-password"
            onChange={e => setForm({ ...form, password: e.target.value })} required />
        </Field>
        <Submit loading={loading} loadingText="Signing in...">Sign in</Submit>
      </form>
    </AuthShell>
  )
}
