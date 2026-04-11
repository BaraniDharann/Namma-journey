import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ownerLogin, ownerForgotPassword } from '../utils/api'

export default function OwnerLoginPage() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [showForgot, setShowForgot] = useState(false)
  const [forgotForm, setForgotForm] = useState({ email: '', newPassword: '', confirmPassword: '' })
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

  const handleForgot = async (e) => {
    e.preventDefault()
    if (forgotForm.newPassword !== forgotForm.confirmPassword) {
      setForgotError('Passwords do not match')
      return
    }
    setForgotLoading(true)
    setForgotError('')
    setForgotMsg('')
    try {
      const res = await ownerForgotPassword(forgotForm)
      setForgotMsg(res.data?.message || 'Password reset successful')
      setTimeout(() => { setShowForgot(false); setForgotForm({ email: '', newPassword: '', confirmPassword: '' }); setForgotMsg('') }, 2000)
    } catch (err) {
      setForgotError(err.response?.data?.error || 'Reset failed. Check your email.')
    } finally {
      setForgotLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, background: '#f8fafc' }}>
      <div style={{ width: '100%', maxWidth: 440, background: '#fff', borderRadius: 18, padding: '36px 32px', boxShadow: '0 4px 24px rgba(0,0,0,0.06)' }}>
        <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 28, textDecoration: 'none' }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: 'linear-gradient(135deg,#8b5cf6,#ec4899)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
          <span style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: 20, color: '#0F172A' }}>Namma <span style={{ color: '#8b5cf6' }}>Journey</span></span>
        </Link>

        {!showForgot ? (
          <>
            <h1 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '1.5rem', fontWeight: 800, color: '#0F172A', marginBottom: 6 }}>👑 Owner Portal</h1>
            <p style={{ color: '#64748b', marginBottom: 24, fontSize: 14 }}>Platform administration access</p>

            {error && <div style={{ marginBottom: 14, padding: '10px 14px', borderRadius: 10, background: '#fef2f2', color: '#dc2626', fontSize: 13 }}>⚠ {error}</div>}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email Address</label>
                <input type="email" className="input-field" placeholder="admin@travelplatform.com" value={form.email}
                  onChange={e => setForm({ ...form, email: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Password</label>
                <input type="password" className="input-field" placeholder="Enter your password" value={form.password}
                  onChange={e => setForm({ ...form, password: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
                <div style={{ textAlign: 'right', marginTop: 6 }}>
                  <button type="button" onClick={() => setShowForgot(true)}
                    style={{ background: 'none', border: 'none', color: '#8b5cf6', fontSize: 13, fontWeight: 600, cursor: 'pointer', padding: 0 }}>
                    Forgot Password?
                  </button>
                </div>
              </div>
              <button type="submit" disabled={loading} className="btn-primary"
                style={{ width: '100%', padding: '13px', borderRadius: 10, background: loading ? '#c4b5fd' : 'linear-gradient(135deg, #8b5cf6, #ec4899)', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Signing in...</> : 'Access Owner Portal'}
              </button>
            </form>
          </>
        ) : (
          <>
            <h1 style={{ fontFamily: 'Poppins, sans-serif', fontSize: '1.5rem', fontWeight: 800, color: '#0F172A', marginBottom: 6 }}>🔑 Reset Password</h1>
            <p style={{ color: '#64748b', marginBottom: 24, fontSize: 14 }}>Enter your email and new password</p>

            {forgotError && <div style={{ marginBottom: 14, padding: '10px 14px', borderRadius: 10, background: '#fef2f2', color: '#dc2626', fontSize: 13 }}>⚠ {forgotError}</div>}
            {forgotMsg && <div style={{ marginBottom: 14, padding: '10px 14px', borderRadius: 10, background: '#f0fdf4', color: '#15803d', fontSize: 13, fontWeight: 600 }}>✓ {forgotMsg}</div>}

            <form onSubmit={handleForgot} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email Address</label>
                <input type="email" className="input-field" placeholder="admin@travelplatform.com" value={forgotForm.email}
                  onChange={e => setForgotForm({ ...forgotForm, email: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>New Password</label>
                <input type="password" className="input-field" placeholder="Enter new password" value={forgotForm.newPassword}
                  onChange={e => setForgotForm({ ...forgotForm, newPassword: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Confirm Password</label>
                <input type="password" className="input-field" placeholder="Repeat password" value={forgotForm.confirmPassword}
                  onChange={e => setForgotForm({ ...forgotForm, confirmPassword: e.target.value })} required
                  style={{ padding: '12px 14px', borderRadius: 10, border: '1.5px solid #e2e8f0', fontSize: 15, width: '100%', boxSizing: 'border-box' }} />
              </div>
              <button type="submit" disabled={forgotLoading} className="btn-primary"
                style={{ width: '100%', padding: '13px', borderRadius: 10, background: forgotLoading ? '#c4b5fd' : 'linear-gradient(135deg, #8b5cf6, #ec4899)', color: '#fff', fontWeight: 700, fontSize: 15, border: 'none', cursor: forgotLoading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                {forgotLoading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Resetting...</> : 'Reset Password'}
              </button>
              <button type="button" onClick={() => { setShowForgot(false); setForgotError(''); setForgotMsg('') }}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#64748b', width: '100%', textAlign: 'center' }}>
                ← Back to Login
              </button>
            </form>
          </>
        )}

        {/* Role switch links */}
        <div style={{ marginTop: 24, paddingTop: 20, borderTop: '1px solid #f1f5f9', display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Link to="/login" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '12px 14px', borderRadius: 10, background: '#fff7ed', border: '1px solid #fed7aa', color: '#f97316', fontSize: 13, fontWeight: 700, textDecoration: 'none' }}>
            🧳 User Login
          </Link>
          <Link to="/driver/login" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '12px 14px', borderRadius: 10, background: '#eff6ff', border: '1px solid #bfdbfe', color: '#3b82f6', fontSize: 13, fontWeight: 700, textDecoration: 'none' }}>
            🚗 Driver Login
          </Link>
        </div>
      </div>
    </div>
  )
}
