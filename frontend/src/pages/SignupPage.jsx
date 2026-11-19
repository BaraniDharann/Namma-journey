import React, { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { sendOtp, userSignup } from '../utils/api'
import AuthShell, { Field, PasswordInput, Alert, Submit, TextButton } from '../components/auth/AuthShell'
import { takeReturn, USER_HOME } from '../utils/returnTo'
import { F, fieldProps } from '../utils/indianFormats'

export default function SignupPage() {
  const [step, setStep] = useState(1)
  const [form, setForm] = useState({ email: '', name: '', mobile: '', otp: '', password: '', confirmPassword: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const handleSendOtp = async (e) => {
    e.preventDefault()
    // The server checks the same official formats; this says what to fix before a code is sent.
    const problem = F.name.error(form.name, 'Name') || F.mobile.error(form.mobile) || F.email.error(form.email)
    if (problem) { setError(problem); return }
    setLoading(true)
    setError('')
    try {
      await sendOtp(form.email)
      setStep(2)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to send OTP')
    } finally {
      setLoading(false)
    }
  }

  const handleSignup = async (e) => {
    e.preventDefault()
    if (!/^\d{6}$/.test(form.otp)) { setError('Please enter the full 6-digit OTP'); return }
    if (F.password.error(form.password)) { setError(F.password.error(form.password)); return }
    if (form.password !== form.confirmPassword) { setError('Passwords do not match'); return }
    setLoading(true)
    setError('')
    try {
      const res = await userSignup({ email: form.email, name: form.name, mobile: form.mobile, otp: form.otp, password: form.password })
      login(res.data)
      const redirect = searchParams.get('redirect')
      const redirectParams = new URLSearchParams()
      for (const [key, value] of searchParams.entries()) {
        if (key !== 'redirect') redirectParams.set(key, value)
      }
      const redirectUrl = redirect ? `${redirect}${redirectParams.toString() ? '?' + redirectParams.toString() : ''}` : (takeReturn() || USER_HOME)
      navigate(redirectUrl)
    } catch (err) {
      setError(err.response?.data?.error || 'Signup failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      role="user"
      title={step === 1 ? 'Create your account' : 'Verify and finish'}
      sub={step === 1 ? 'Two quick steps and you can book your first trip.' : <>We sent a code to <strong>{form.email}</strong></>}
      footer={<p className="au-alt">Already have an account? <Link to="/login">Sign in</Link></p>}
    >
      <div className="au-steps" aria-label={`Step ${step} of 2`}>
        <span className={`au-step ${step >= 1 ? 'is-on' : ''}`}>1</span>
        <span className="au-step-l">Details</span>
        <span className={`au-step-bar ${step > 1 ? 'is-on' : ''}`}><i /></span>
        <span className={`au-step ${step >= 2 ? 'is-on' : ''}`}>2</span>
        <span className="au-step-l">Verify</span>
      </div>

      <Alert>{error}</Alert>

      {step === 1 ? (
        <form onSubmit={handleSendOtp} className="au-form">
          <Field label="Full name" icon="user">
            <input type="text" {...fieldProps('name', form.name, v => setForm({ ...form, name: v }))} placeholder="Rajesh Kumar" autoComplete="name" required />
          </Field>
          <Field label="Mobile number" icon="phone">
            <input type="tel" {...fieldProps('mobile', form.mobile, v => setForm({ ...form, mobile: v }))} autoComplete="tel" required />
          </Field>
          <Field label="Email address" icon="mail">
            <input type="email" {...fieldProps('email', form.email, v => setForm({ ...form, email: v }))} autoComplete="email" required />
          </Field>
          <Submit loading={loading} loadingText="Sending OTP...">Continue</Submit>
        </form>
      ) : (
        <form onSubmit={handleSignup} className="au-form">
          <Field label="OTP code" icon="lock">
            <input type="text" inputMode="numeric" className="au-otp" placeholder="Enter 6-digit OTP" maxLength={6} value={form.otp}
              onChange={e => setForm({ ...form, otp: e.target.value.replace(/\D/g, '').slice(0, 6) })} required />
          </Field>
          <Field label="Password" icon="lock">
            <PasswordInput placeholder="8+ characters, a letter and a number" maxLength={F.password.max} value={form.password} autoComplete="new-password"
              onChange={e => setForm({ ...form, password: e.target.value })} required />
          </Field>
          <Field label="Confirm password" icon="lock">
            <PasswordInput placeholder="Repeat password" maxLength={F.password.max} value={form.confirmPassword} autoComplete="new-password"
              onChange={e => setForm({ ...form, confirmPassword: e.target.value })} required />
          </Field>
          <Submit loading={loading} loadingText="Creating account...">Create account</Submit>
          <TextButton back onClick={() => setStep(1)}>Change email</TextButton>
        </form>
      )}
    </AuthShell>
  )
}
