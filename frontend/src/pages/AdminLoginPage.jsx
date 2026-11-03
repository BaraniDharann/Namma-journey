import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { adminLogin } from '../utils/api'
import AuthShell, { Field, PasswordInput, Alert, Submit } from '../components/auth/AuthShell'

/** NammaJourney admins sign in here to review travels owner applications. */
export default function AdminLoginPage() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await adminLogin(form)
      login(res.data)
      navigate('/admin/owners')
    } catch (err) {
      setError(err.response?.data?.error || 'Invalid credentials')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell role="owner" title="NammaJourney admin" sub="Verify travels owners who applied to join.">
      <Alert>{error}</Alert>
      <form onSubmit={submit} className="au-form">
        <Field label="Email address" icon="mail">
          <input type="email" value={form.email} name="username" autoComplete="username"
            onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        </Field>
        <Field label="Password" icon="lock">
          <PasswordInput value={form.password} autoComplete="current-password"
            onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </Field>
        <Submit loading={loading} loadingText="Signing in...">Sign in</Submit>
      </form>
    </AuthShell>
  )
}
