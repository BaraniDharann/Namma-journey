import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { ownerChangePassword } from '../../utils/api'
import AuthShell, { Field, PasswordInput, Alert, Submit, TextButton } from '../../components/auth/AuthShell'

/**
 * First sign-in of an approved owner: the emailed one-time password only opens this page. Choosing
 * a new one ends that session and signs the owner in properly.
 */
export default function OwnerChangePassword() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState('')
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()

  const submit = async (e) => {
    e.preventDefault()
    if (form.newPassword.length < 8) { setError('Choose a password of at least 8 characters'); return }
    if (form.newPassword !== form.confirm) { setError('The two new passwords do not match'); return }
    setLoading(true)
    setError('')
    try {
      const res = await ownerChangePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      if (!res.data?.token) {
        // Saved, but this owner's portal is not open yet: the server says why.
        // The setup token is already revoked server-side; signing out happens from the message
        // screen, since signing out here would redirect away from this protected page first.
        setSaved(res.data?.message || 'Your password is saved.')
        return
      }
      login(res.data)
      navigate('/owner/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Could not change the password. Try again.')
    } finally {
      setLoading(false)
    }
  }

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  if (saved) {
    return (
      <AuthShell role="owner" title="Password saved" sub="Thank you for joining Namma Journey.">
        <Alert tone="ok">{saved}</Alert>
        <TextButton onClick={() => { logout(); navigate('/owner/login') }}>Back to sign in</TextButton>
      </AuthShell>
    )
  }

  return (
    <AuthShell role="owner" title="Choose your password"
      sub={`Welcome${user?.name ? `, ${user.name}` : ''}. Replace the password we emailed you to continue.`}>
      <Alert>{error}</Alert>
      <form onSubmit={submit} className="au-form">
        <Field label="Password from the email" icon="lock">
          <PasswordInput value={form.currentPassword} autoComplete="current-password" onChange={set('currentPassword')} required />
        </Field>
        <Field label="New password" icon="lock">
          <PasswordInput placeholder="At least 8 characters" value={form.newPassword} autoComplete="new-password" onChange={set('newPassword')} required />
        </Field>
        <Field label="Repeat new password" icon="lock">
          <PasswordInput value={form.confirm} autoComplete="new-password" onChange={set('confirm')} required />
        </Field>
        <Submit loading={loading} loadingText="Saving...">Save and open owner portal</Submit>
        <TextButton back onClick={() => { logout(); navigate('/owner/login') }}>Sign out</TextButton>
      </form>
    </AuthShell>
  )
}
