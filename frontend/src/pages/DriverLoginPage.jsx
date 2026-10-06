import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { driverLogin, driverForgotPassword, driverVerifyOtp, driverRequestResetOtp } from '../utils/api'
import { offerToSaveLogin } from '../utils/rememberLogin'
import AuthShell, { Field, PasswordInput, Alert, Submit, TextButton } from '../components/auth/AuthShell'
import Icon from '../components/dash/Icon'

const OTP_VALIDITY_SECONDS = 5 * 60

export default function DriverLoginPage() {
  const [form, setForm] = useState({ mobile: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [showForgot, setShowForgot] = useState(false)
  const [forgotStep, setForgotStep] = useState(1) // 1 = enter mobile / send OTP, 2 = enter OTP + new password
  const [forgotForm, setForgotForm] = useState({ mobile: '', otp: '', newPassword: '', confirmPassword: '' })
  const [forgotLoading, setForgotLoading] = useState(false)
  const [forgotMsg, setForgotMsg] = useState('')
  const [forgotError, setForgotError] = useState('')
  const [showOtp, setShowOtp] = useState(false)
  const [otpEmail, setOtpEmail] = useState('')
  const [otpForm, setOtpForm] = useState({ otp: '', newPassword: '', confirmPassword: '' })
  const [otpLoading, setOtpLoading] = useState(false)
  const [otpError, setOtpError] = useState('')
  const [otpMsg, setOtpMsg] = useState('')
  const [otpExpiresAt, setOtpExpiresAt] = useState(null)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [resendLoading, setResendLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!otpExpiresAt) return
    const tick = () => {
      const remaining = Math.max(0, Math.ceil((otpExpiresAt - Date.now()) / 1000))
      setSecondsLeft(remaining)
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [otpExpiresAt])

  const formatTime = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await driverLogin(form)
      if (res.data.firstLogin) {
        setOtpEmail(res.data.email)
        setShowOtp(true)
        setOtpExpiresAt(Date.now() + OTP_VALIDITY_SECONDS * 1000)
        return
      }
      void offerToSaveLogin(form.mobile, form.password, res.data?.name)
      login(res.data)
      navigate('/driver/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Invalid mobile or password')
    } finally {
      setLoading(false)
    }
  }

  const handleResendOtp = async () => {
    setResendLoading(true)
    setOtpError('')
    setOtpMsg('')
    try {
      await driverLogin(form)
      setOtpExpiresAt(Date.now() + OTP_VALIDITY_SECONDS * 1000)
      setOtpForm({ otp: '', newPassword: '', confirmPassword: '' })
      setOtpMsg('A new OTP has been sent to your email.')
      setTimeout(() => setOtpMsg(''), 3000)
    } catch (err) {
      setOtpError(err.response?.data?.error || 'Failed to resend OTP. Please try again.')
    } finally {
      setResendLoading(false)
    }
  }

  const handleOtpSubmit = async (e) => {
    e.preventDefault()
    if (otpForm.newPassword !== otpForm.confirmPassword) {
      setOtpError('Passwords do not match')
      return
    }
    setOtpLoading(true)
    setOtpError('')
    try {
      await driverVerifyOtp({ email: otpEmail, otp: otpForm.otp, newPassword: otpForm.newPassword })
      setOtpMsg('Password set successfully! Redirecting to login...')
      setTimeout(() => {
        setShowOtp(false)
        setOtpForm({ otp: '', newPassword: '', confirmPassword: '' })
        setOtpMsg('')
      }, 2000)
    } catch (err) {
      setOtpError(err.response?.data?.error || 'Invalid or expired OTP')
    } finally {
      setOtpLoading(false)
    }
  }

  const handleSendForgotOtp = async (e) => {
    e.preventDefault()
    if (!/^\d{10}$/.test(forgotForm.mobile)) { setForgotError('Enter a 10-digit mobile number'); return }
    setForgotLoading(true); setForgotError(''); setForgotMsg('')
    try {
      const res = await driverRequestResetOtp(forgotForm.mobile)
      setForgotStep(2)
      setForgotMsg(res.data?.message || 'If this mobile is registered, an OTP has been sent.')
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Failed to send OTP. Try again.')
    } finally {
      setForgotLoading(false)
    }
  }

  const handleForgot = async (e) => {
    e.preventDefault()
    if (!/^\d{4,6}$/.test(forgotForm.otp)) { setForgotError('Enter the OTP you received'); return }
    if (forgotForm.newPassword.length < 6) { setForgotError('Password must be at least 6 characters'); return }
    if (forgotForm.newPassword !== forgotForm.confirmPassword) {
      setForgotError('Passwords do not match'); return
    }
    setForgotLoading(true); setForgotError(''); setForgotMsg('')
    try {
      const res = await driverForgotPassword(forgotForm)
      setForgotMsg(res.data?.message || 'Password reset successful')
      setTimeout(() => {
        setShowForgot(false); setForgotStep(1)
        setForgotForm({ mobile: '', otp: '', newPassword: '', confirmPassword: '' })
        setForgotMsg('')
      }, 2000)
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Reset failed. Check the OTP and try again.')
    } finally {
      setForgotLoading(false)
    }
  }

  if (showOtp) {
    return (
      <AuthShell role="driver" title="Verify and set password" sub={<>OTP sent to <strong>{otpEmail}</strong>. Enter it with your new password.</>}>
        <div className={`au-timer ${secondsLeft > 0 ? '' : 'is-out'}`}>
          <span><Icon name="clock" size={16} />{secondsLeft > 0 ? `OTP expires in ${formatTime(secondsLeft)}` : 'OTP expired. Please resend.'}</span>
          <button type="button" className="au-textbtn" onClick={handleResendOtp} disabled={resendLoading || secondsLeft > 0}>
            {resendLoading ? 'Sending…' : 'Resend OTP'}
          </button>
        </div>
        <Alert>{otpError}</Alert>
        <Alert tone="ok">{otpMsg}</Alert>
        <form onSubmit={handleOtpSubmit} className="au-form">
          <Field label="OTP" icon="lock">
            <input type="text" inputMode="numeric" className="au-otp" placeholder="Enter OTP" maxLength={6} value={otpForm.otp}
              onChange={e => setOtpForm({ ...otpForm, otp: e.target.value })} required disabled={secondsLeft === 0} />
          </Field>
          <Field label="New password" icon="lock">
            <PasswordInput placeholder="Enter new password" value={otpForm.newPassword} autoComplete="new-password"
              onChange={e => setOtpForm({ ...otpForm, newPassword: e.target.value })} required disabled={secondsLeft === 0} />
          </Field>
          <Field label="Confirm password" icon="lock">
            <PasswordInput placeholder="Repeat password" value={otpForm.confirmPassword} autoComplete="new-password"
              onChange={e => setOtpForm({ ...otpForm, confirmPassword: e.target.value })} required disabled={secondsLeft === 0} />
          </Field>
          <Submit loading={otpLoading} disabled={secondsLeft === 0} loadingText="Verifying...">Verify and set password</Submit>
          <TextButton back onClick={() => { setShowOtp(false); setOtpError(''); setOtpExpiresAt(null) }}>Back to login</TextButton>
        </form>
      </AuthShell>
    )
  }

  if (showForgot) {
    return (
      <AuthShell role="driver" title="Reset your password" sub={forgotStep === 1 ? 'We will send a code to the email linked to your mobile.' : 'Enter the code and choose a new password.'}>
        <Alert>{forgotError}</Alert>
        <Alert tone="ok">{forgotMsg}</Alert>
        {forgotStep === 1 ? (
          <form onSubmit={handleSendForgotOtp} className="au-form">
            <Field label="Mobile number" icon="phone">
              <input type="tel" inputMode="numeric" placeholder="9876543210" maxLength={10} value={forgotForm.mobile} autoComplete="tel"
                onChange={e => setForgotForm({ ...forgotForm, mobile: e.target.value.replace(/\D/g, '') })} required />
            </Field>
            <Submit loading={forgotLoading} loadingText="Sending OTP...">Send OTP</Submit>
            <TextButton back onClick={() => { setShowForgot(false); setForgotError(''); setForgotMsg('') }}>Back to login</TextButton>
          </form>
        ) : (
          <form onSubmit={handleForgot} className="au-form">
            <Field label="OTP" icon="lock">
              <input type="text" inputMode="numeric" className="au-otp" placeholder="Enter OTP from email" maxLength={6} value={forgotForm.otp}
                onChange={e => setForgotForm({ ...forgotForm, otp: e.target.value.replace(/\D/g, '') })} required />
            </Field>
            <Field label="New password" icon="lock">
              <PasswordInput placeholder="Min 6 characters" value={forgotForm.newPassword} autoComplete="new-password"
                onChange={e => setForgotForm({ ...forgotForm, newPassword: e.target.value })} required />
            </Field>
            <Field label="Confirm password" icon="lock">
              <PasswordInput placeholder="Repeat password" value={forgotForm.confirmPassword} autoComplete="new-password"
                onChange={e => setForgotForm({ ...forgotForm, confirmPassword: e.target.value })} required />
            </Field>
            <Submit loading={forgotLoading} loadingText="Resetting...">Reset password</Submit>
            <TextButton back onClick={() => { setForgotStep(1); setForgotError(''); setForgotMsg('') }}>Use a different mobile</TextButton>
          </form>
        )}
      </AuthShell>
    )
  }

  return (
    <AuthShell role="driver" title="Driver login" sub="Sign in with the mobile number your owner registered.">
      <Alert>{error}</Alert>
      <form onSubmit={handleSubmit} className="au-form">
        <Field label="Mobile number" icon="phone">
          <input type="tel" inputMode="numeric" placeholder="9876543210" maxLength={10} value={form.mobile} name="username" autoComplete="username"
            onChange={e => setForm({ ...form, mobile: e.target.value })} required />
        </Field>
        <Field label="Password" icon="lock" aside={<TextButton onClick={() => setShowForgot(true)}>Forgot password?</TextButton>}>
          <PasswordInput placeholder="Enter your password" value={form.password} autoComplete="current-password"
            onChange={e => setForm({ ...form, password: e.target.value })} required />
        </Field>
        <Submit loading={loading} loadingText="Signing in...">Sign in as driver</Submit>
      </form>
    </AuthShell>
  )
}
