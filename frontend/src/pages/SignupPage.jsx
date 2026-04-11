import React, { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { sendOtp, userSignup } from '../utils/api'

export default function SignupPage() {
  const [step, setStep] = useState(1)
  const [form, setForm] = useState({ email: '', name: '', mobile: '', otp: '', password: '', confirmPassword: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [otpSent, setOtpSent] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const handleSendOtp = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      await sendOtp(form.email)
      setOtpSent(true)
      setStep(2)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to send OTP')
    } finally {
      setLoading(false)
    }
  }

  const handleSignup = async (e) => {
    e.preventDefault()
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
      const redirectUrl = redirect ? `${redirect}${redirectParams.toString() ? '?' + redirectParams.toString() : ''}` : '/user/dashboard'
      navigate(redirectUrl)
    } catch (err) {
      setError(err.response?.data?.error || 'Signup failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: '#f8fafc' }}>
      {/* Left panel */}
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden', display: 'none' }} className="lg-panel">
        <img src="/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg" alt="Travel"
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.7), rgba(0,0,0,0.2))' }} />
        <div style={{ position: 'absolute', bottom: 48, left: 48, right: 48 }}>
          <h2 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '2.4rem', fontWeight: 800, color: '#fff', marginBottom: 8, lineHeight: 1.2 }}>
            Start Your<br /><span style={{ color: '#f97316' }}>Adventure Today</span>
          </h2>
          <p style={{ color: 'rgba(255,255,255,0.7)', fontSize: 15, lineHeight: 1.6, maxWidth: 360 }}>Join 50,000+ travellers exploring India with Namma Journey.</p>
        </div>
      </div>

      {/* Right form panel */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '40px 24px' }}>
        <div style={{ width: '100%', maxWidth: 420 }}>
          <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 32, textDecoration: 'none' }}>
            <div style={{ width: 38, height: 38, borderRadius: 10, background: '#f97316', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
            <span style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 20, color: '#0F172A' }}>Namma <span style={{ color: '#f97316' }}>Journey</span></span>
          </Link>

          {/* Step progress dots */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 24 }}>
            <div className={`step-dot ${step > 1 ? 'done' : step === 1 ? 'active' : 'idle'}`} style={{ width: 28, height: 28, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, background: step >= 1 ? '#f97316' : '#e2e8f0', color: step >= 1 ? '#fff' : '#94a3b8' }}>
              {step > 1 ? '✓' : '1'}
            </div>
            <div style={{ flex: 1, height: 2, background: step > 1 ? '#f97316' : '#e2e8f0', borderRadius: 2 }} />
            <div className={`step-dot ${step > 2 ? 'done' : step === 2 ? 'active' : 'idle'}`} style={{ width: 28, height: 28, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, background: step >= 2 ? '#f97316' : '#e2e8f0', color: step >= 2 ? '#fff' : '#94a3b8' }}>
              2
            </div>
          </div>

          <h1 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '1.8rem', fontWeight: 800, color: '#0F172A', marginBottom: 4 }}>
            {step === 1 ? 'Create Account' : 'Verify & Complete'}
          </h1>
          <p style={{ color: '#64748b', marginBottom: 24, fontSize: 14 }}>
            {step === 1 ? 'Enter your details to get started' : `We sent a code to ${form.email}`}
          </p>

          {error && <div style={{ marginBottom: 16, padding: '10px 14px', borderRadius: 10, background: '#fef2f2', color: '#dc2626', fontSize: 13 }}>⚠ {error}</div>}

          {step === 1 ? (
            <form onSubmit={handleSendOtp} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Full Name</label>
                <input type="text" className="input-field" placeholder="Rajesh Kumar" value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Mobile Number</label>
                <input type="tel" className="input-field" placeholder="9876543210" maxLength={10} value={form.mobile}
                  onChange={e => setForm({ ...form, mobile: e.target.value.replace(/\D/g, '') })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email Address</label>
                <input type="email" className="input-field" placeholder="you@example.com" value={form.email}
                  onChange={e => setForm({ ...form, email: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <button type="submit" disabled={loading} className="btn-primary"
                style={{ width: '100%', padding: '13px', borderRadius: 10, background: loading ? '#fdba74' : 'linear-gradient(135deg, #f97316, #ea580c)', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Sending OTP...</> : 'Continue'}
              </button>
              <p style={{ fontSize: 12, textAlign: 'center', color: '#94a3b8' }}>Test OTP: <span style={{ color: '#f97316', fontWeight: 700 }}>123456</span></p>
            </form>
          ) : (
            <form onSubmit={handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>OTP Code</label>
                <input type="text" className="input-field" placeholder="Enter 6-digit OTP" maxLength={6} value={form.otp}
                  onChange={e => setForm({ ...form, otp: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 18, width: '100%', boxSizing: 'border-box', letterSpacing: '0.3em', textAlign: 'center', fontWeight: 700 }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Password</label>
                <input type="password" className="input-field" placeholder="Min 8 chars, include @#$" value={form.password}
                  onChange={e => setForm({ ...form, password: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Confirm Password</label>
                <input type="password" className="input-field" placeholder="Repeat password" value={form.confirmPassword}
                  onChange={e => setForm({ ...form, confirmPassword: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <button type="submit" disabled={loading} className="btn-primary"
                style={{ width: '100%', padding: '13px', borderRadius: 10, background: loading ? '#fdba74' : 'linear-gradient(135deg, #f97316, #ea580c)', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Creating account...</> : 'Create Account'}
              </button>
              <button type="button" onClick={() => setStep(1)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#64748b', width: '100%', textAlign: 'center' }}>
                ← Change email
              </button>
            </form>
          )}

          <p style={{ textAlign: 'center', marginTop: 20, fontSize: 14, color: '#64748b' }}>
            Already have an account?{' '}
            <Link to="/login" style={{ color: '#f97316', fontWeight: 700, textDecoration: 'none' }}>Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
