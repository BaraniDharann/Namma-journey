import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ownerLogin, ownerForgotPassword, sendOtp } from '../utils/api'
import AuthShell, { Field, PasswordInput, Alert, Submit, TextButton } from '../components/auth/AuthShell'

export default function OwnerLoginPage() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [showForgot, setShowForgot] = useState(false)
  const [forgotStep, setForgotStep] = useState(1) // 1 = send OTP, 2 = enter OTP + new password
  const [forgotForm, setForgotForm] = useState({ email: '', otp: '', newPassword: '', confirmPassword: '' })
  const [forgotLoading, setForgotLoading] = useState(false)
  const [forgotMsg, setForgotMsg] = useState('')
  const [forgotError, setForgotError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await ownerLogin(form)
      login(res.data)
      navigate('/owner/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Invalid credentials')
    } finally {
      setLoading(false)
    }
  }

  const handleSendForgotOtp = async (e) => {
    e.preventDefault()
    setForgotLoading(true); setForgotError(''); setForgotMsg('')
    try {
      await sendOtp(forgotForm.email)
      setForgotStep(2)
      setForgotMsg('OTP sent to your email. Enter it below to reset your password.')
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Failed to send OTP. Check your email.')
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
      const res = await ownerForgotPassword(forgotForm)
      setForgotMsg(res.data?.message || 'Password reset successful')
      setTimeout(() => {
        setShowForgot(false); setForgotStep(1)
        setForgotForm({ email: '', otp: '', newPassword: '', confirmPassword: '' })
        setForgotMsg('')
      }, 2000)
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Reset failed. Check the OTP and try again.')
    } finally {
      setForgotLoading(false)
    }
  }

  if (showForgot) {
    return (
      <AuthShell role="owner" title="Reset your password" sub={forgotStep === 1 ? 'We will email you a one-time code.' : 'Enter the code and choose a new password.'}>
        <Alert>{forgotError}</Alert>
        <Alert tone="ok">{forgotMsg}</Alert>
        {forgotStep === 1 ? (
          <form onSubmit={handleSendForgotOtp} className="au-form">
            <Field label="Email address" icon="mail">
              <input type="email" placeholder="owner@example.com" value={forgotForm.email} autoComplete="email"
                onChange={e => setForgotForm({ ...forgotForm, email: e.target.value })} required />
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
            <TextButton back onClick={() => { setForgotStep(1); setForgotError(''); setForgotMsg('') }}>Use a different email</TextButton>
          </form>
        )}
      </AuthShell>
    )
  }

  return (
    <AuthShell role="owner" title="Owner portal" sub="Sign in to run your fleet and bookings.">
      <Alert>{error}</Alert>
      <form onSubmit={handleSubmit} className="au-form">
        <Field label="Email address" icon="mail">
          <input type="email" placeholder="owner@example.com" value={form.email} autoComplete="email"
            onChange={e => setForm({ ...form, email: e.target.value })} required />
        </Field>
        <Field label="Password" icon="lock" aside={<TextButton onClick={() => setShowForgot(true)}>Forgot password?</TextButton>}>
          <PasswordInput placeholder="Enter your password" value={form.password} autoComplete="current-password"
            onChange={e => setForm({ ...form, password: e.target.value })} required />
        </Field>
        <Submit loading={loading} loadingText="Signing in...">Open owner portal</Submit>
      </form>
    </AuthShell>
  )
}
