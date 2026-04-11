import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { GoogleLogin } from '@react-oauth/google'
import { useAuth } from '../context/AuthContext'
import { userLogin, googleLogin, sendOtp, userForgotPassword } from '../utils/api'

export default function LoginPage() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [googleLoading, setGoogleLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await userLogin({ loginType: 'EMAIL', email: form.email, password: form.password })
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
      login(res.data)
      navigate('/user/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Google login failed. Please try again.')
    } finally {
      setGoogleLoading(false)
    }
  }

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

  if (showForgot) return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f8fafc', padding: 24 }}>
      <div style={{ width: '100%', maxWidth: 420, background: '#fff', borderRadius: 18, padding: '36px 32px', boxShadow: '0 4px 24px rgba(0,0,0,0.06)' }}>
        <h2 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '1.5rem', fontWeight: 800, color: '#0F172A', marginBottom: 6 }}>🔐 Forgot Password</h2>
        <p style={{ color: '#64748b', marginBottom: 24, fontSize: 14 }}>{forgotStep === 1 ? 'Enter your email to receive OTP' : forgotStep === 2 ? `OTP sent to ${forgotForm.email}` : ''}</p>

        {forgotError && <div style={{ marginBottom: 16, padding: '10px 14px', borderRadius: 10, background: '#fef2f2', color: '#dc2626', fontSize: 13 }}>{forgotError}</div>}

        {forgotStep === 3 ? (
          <div>
            <div style={{ marginBottom: 20, padding: '14px', borderRadius: 10, background: '#f0fdf4', color: '#15803d', fontSize: 14, fontWeight: 600, textAlign: 'center' }}>✅ {forgotMsg}</div>
            <button onClick={() => { setShowForgot(false); setForgotStep(1); setForgotForm({ email: '', otp: '', newPassword: '' }) }}
              className="btn-primary" style={{ width: '100%', padding: '12px', borderRadius: 10, background: '#f97316', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: 'pointer' }}>
              Back to Login
            </button>
          </div>
        ) : forgotStep === 1 ? (
          <form onSubmit={handleForgotSendOtp}>
            <input type="email" className="input-field" placeholder="you@example.com" value={forgotForm.email}
              onChange={e => setForgotForm(p => ({ ...p, email: e.target.value }))} required
              style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box', marginBottom: 16 }} />
            <button type="submit" disabled={forgotLoading} className="btn-primary"
              style={{ width: '100%', padding: '12px', borderRadius: 10, background: forgotLoading ? '#fdba74' : '#f97316', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: forgotLoading ? 'not-allowed' : 'pointer', marginBottom: 12 }}>
              {forgotLoading ? 'Sending...' : 'Send OTP'}
            </button>
            <button type="button" onClick={() => setShowForgot(false)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#64748b', width: '100%', textAlign: 'center' }}>
              ← Back to Login
            </button>
          </form>
        ) : (
          <form onSubmit={handleForgotReset}>
            <input type="text" className="input-field" placeholder="6-digit OTP" maxLength={6} value={forgotForm.otp}
              onChange={e => setForgotForm(p => ({ ...p, otp: e.target.value }))} required
              style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box', marginBottom: 14, letterSpacing: '0.2em', textAlign: 'center' }} />
            <input type="password" className="input-field" placeholder="New password (min 8 chars, include @#$)" value={forgotForm.newPassword}
              onChange={e => setForgotForm(p => ({ ...p, newPassword: e.target.value }))} required
              style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box', marginBottom: 16 }} />
            <button type="submit" disabled={forgotLoading} className="btn-primary"
              style={{ width: '100%', padding: '12px', borderRadius: 10, background: forgotLoading ? '#fdba74' : '#f97316', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: forgotLoading ? 'not-allowed' : 'pointer', marginBottom: 12 }}>
              {forgotLoading ? 'Resetting...' : 'Reset Password'}
            </button>
            <button type="button" onClick={() => setForgotStep(1)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#64748b', width: '100%', textAlign: 'center' }}>
              ← Change email
            </button>
          </form>
        )}
      </div>
    </div>
  )

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: '#f8fafc' }}>
      {/* Left image panel */}
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden', display: 'none' }} className="lg-panel">
        <img src="/images/indian-hindu-temple-singapore.jpg" alt="Temple"
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.7), rgba(0,0,0,0.2))' }} />
        <div style={{ position: 'absolute', bottom: 48, left: 48, right: 48 }}>
          <div style={{ fontSize: 48, marginBottom: 12 }}>🛕</div>
          <h2 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '2.4rem', fontWeight: 800, color: '#fff', marginBottom: 8, lineHeight: 1.2 }}>
            Your Sacred<br /><span style={{ color: '#f97316' }}>Journey Awaits</span>
          </h2>
          <p style={{ color: 'rgba(255,255,255,0.7)', fontSize: 15, lineHeight: 1.6, maxWidth: 360 }}>Book temple visits and tours across India with verified drivers.</p>
        </div>
      </div>

      {/* Right form panel */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '40px 24px' }}>
        <div style={{ width: '100%', maxWidth: 420 }}>
          <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 36, textDecoration: 'none' }}>
            <div style={{ width: 38, height: 38, borderRadius: 10, background: '#f97316', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
            <span style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 20, color: '#0F172A' }}>Namma <span style={{ color: '#f97316' }}>Journey</span></span>
          </Link>

          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '1.8rem', fontWeight: 800, color: '#0F172A', marginBottom: 4 }}>Welcome back</h1>
          <p style={{ color: '#64748b', marginBottom: 28, fontSize: 14 }}>Sign in to your traveller account</p>

          {error && <div style={{ marginBottom: 16, padding: '10px 14px', borderRadius: 10, background: '#fef2f2', color: '#dc2626', fontSize: 13 }}>⚠ {error}</div>}

          {/* Google Login */}
          <div style={{ marginBottom: 20 }}>
            {googleLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 14, background: '#f8fafc', borderRadius: 10, color: '#64748b', fontSize: 14, gap: 8, border: '1.5px solid #e2e8f0' }}>
                <div className="spinner" style={{ width: 18, height: 18 }} /> Signing in with Google...
              </div>
            ) : (
              <GoogleLogin onSuccess={handleGoogleSuccess} onError={() => setError('Google sign-in failed.')}
                width="420" theme="outline" size="large" text="continue_with" shape="rectangular" />
            )}
          </div>

          {/* Divider */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
            <div style={{ flex: 1, height: 1, background: '#e2e8f0' }} />
            <span style={{ color: '#94a3b8', fontSize: 13, fontWeight: 500 }}>or sign in with email</span>
            <div style={{ flex: 1, height: 1, background: '#e2e8f0' }} />
          </div>

          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email Address</label>
              <input type="email" className="input-field" placeholder="you@example.com" value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })} required
                style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
            </div>
            <div style={{ marginBottom: 24 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <label style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>Password</label>
                <button type="button" onClick={() => setShowForgot(true)}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, color: '#f97316', fontWeight: 600 }}>
                  Forgot password?
                </button>
              </div>
              <input type="password" className="input-field" placeholder="Enter your password" value={form.password}
                onChange={e => setForm({ ...form, password: e.target.value })} required
                style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
            </div>
            <button type="submit" disabled={loading} className="btn-primary"
              style={{ width: '100%', padding: '13px', fontSize: 15, borderRadius: 10, background: loading ? '#fdba74' : 'linear-gradient(135deg, #f97316, #ea580c)', color: '#fff', fontWeight: 700, border: 'none', cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
              {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Signing in...</> : 'Sign In'}
            </button>
          </form>

          <p style={{ textAlign: 'center', marginTop: 20, fontSize: 14, color: '#64748b' }}>
            Don't have an account?{' '}
            <Link to="/signup" style={{ color: '#f97316', fontWeight: 700, textDecoration: 'none' }}>Sign up free</Link>
          </p>

          {/* Role switch links */}
          <div style={{ marginTop: 28, paddingTop: 20, borderTop: '1px solid #f1f5f9' }}>
            <p style={{ fontSize: 12, textAlign: 'center', color: '#94a3b8', marginBottom: 12, fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Other login options</p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <Link to="/driver/login" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '12px 14px', borderRadius: 10, background: '#eff6ff', border: '1px solid #bfdbfe', color: '#3b82f6', fontSize: 13, fontWeight: 700, textDecoration: 'none' }}>
                🚗 Driver Login
              </Link>
              <Link to="/owner/login" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '12px 14px', borderRadius: 10, background: '#f5f3ff', border: '1px solid #ddd6fe', color: '#7c3aed', fontSize: 13, fontWeight: 700, textDecoration: 'none' }}>
                👑 Owner Login
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
