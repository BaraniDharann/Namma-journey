import React, { useRef, useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { driverSignup, driverLogin } from '../utils/api'
import { offerToSaveLogin } from '../utils/rememberLogin'
import AuthShell, { Field, PasswordInput, Alert, Submit, TextButton } from '../components/auth/AuthShell'
import { assertCodeResent } from '../components/auth/codeResend'
import EmailCodeScreen from '../components/auth/EmailCodeScreen'
import VehicleFields, { DEFAULT_VEHICLE } from '../components/owner/VehicleFields'
import DocTile from '../components/driver/DocTile'
import { DOCUMENTS } from '../components/driver/gateView'

const STEPS = ['About you', 'Vehicle', 'Documents']
// Drivers who sign themselves up can't take Trips; the server forces servesTrip=false regardless.
const START_VEHICLE = { ...DEFAULT_VEHICLE, servesTrip: false }

export default function DriverSignupPage() {
  const [step, setStep] = useState(0)
  const [about, setAbout] = useState({ name: '', mobile: '', email: '', password: '' })
  const [vehicle, setVehicle] = useState(START_VEHICLE)
  const [ids, setIds] = useState({ licenseNumber: '', aadhaarNumber: '' })
  const [files, setFiles] = useState({ photo: null, licensePhoto: null, aadhaarPhoto: null, rcPhoto: null })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [pendingEmail, setPendingEmail] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()
  const heading = useRef(null)

  // Move focus to the step's heading so keyboard and screen-reader users land at the top of it.
  useEffect(() => { heading.current?.focus() }, [step])

  const validate = () => {
    if (step === 0) {
      if (!about.name.trim()) return 'Enter your name'
      if (!/^\d{10}$/.test(about.mobile)) return 'Enter a 10-digit mobile number'
      if (!/^\S+@\S+\.\S+$/.test(about.email)) return 'Enter a valid email address'
      if (about.password.length < 8) return 'Password must be at least 8 characters'
    } else if (step === 1) {
      if (!vehicle.vehicleNumber.trim()) return 'Enter your vehicle number plate'
    } else {
      if (!ids.licenseNumber.trim()) return 'Enter your licence number'
      if (!/^\d{12}$/.test(ids.aadhaarNumber)) return 'Enter your 12-digit Aadhaar number'
      if (DOCUMENTS.some((d) => !files[d.key])) return 'Upload all four documents'
    }
    return ''
  }

  const next = (e) => {
    e.preventDefault()
    const problem = validate()
    setError(problem)
    if (problem) return
    if (step < 2) { setStep(step + 1); return }
    submit()
  }

  const submit = async () => {
    setLoading(true)
    try {
      const fd = new FormData()
      const fields = {
        ...about, name: about.name.trim(), ...ids,
        vehicleType: vehicle.vehicleType, vehicleNumber: vehicle.vehicleNumber.trim(), vehicleModel: vehicle.vehicleModel,
        vehicleSeats: vehicle.vehicleSeats, vehicleAc: vehicle.vehicleAc,
      }
      Object.entries(fields).forEach(([k, v]) => fd.append(k, String(v ?? '')))
      DOCUMENTS.forEach((d) => fd.append(d.key, files[d.key]))
      const res = await driverSignup(fd)
      setPendingEmail(res.data?.email || about.email)
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Sign-up failed. Please check your details and try again.')
    } finally {
      setLoading(false)
    }
  }

  // Signing in again is how the server re-sends the code to a driver whose email is unverified.
  const resend = () => driverLogin({ mobile: about.mobile, password: about.password }).then(assertCodeResent)

  const verified = (data) => {
    void offerToSaveLogin(about.mobile, about.password, data?.name)
    login(data)
    navigate('/driver/dashboard')
  }

  if (pendingEmail) {
    return <EmailCodeScreen email={pendingEmail} onVerified={verified} onResend={resend} onBack={() => setPendingEmail('')} />
  }

  const setA = (k) => (e) => setAbout({ ...about, [k]: e.target.value })

  return (
    <AuthShell role="driver" title="Become a driver" sub="Sign up, upload your documents, and we will verify you."
      footer={<p className="au-sub" style={{ marginTop: 16 }}>Already a driver? <Link to="/driver/login">Sign in</Link></p>}>
      <ol aria-label="Sign-up steps" style={{ display: 'flex', gap: 8, listStyle: 'none', padding: 0, margin: '0 0 12px', fontSize: 12.5, fontWeight: 700 }}>
        {STEPS.map((s, i) => (
          <li key={s} aria-current={i === step ? 'step' : undefined} style={{ flex: 1, paddingTop: 6, borderTop: `3px solid ${i <= step ? 'var(--rc)' : 'var(--au-line)'}`, color: i === step ? 'var(--rc-deep)' : 'var(--au-muted)' }}>
            {i + 1}. {s}
          </li>
        ))}
      </ol>
      <h2 ref={heading} tabIndex={-1} style={{ fontSize: 16, margin: '0 0 12px', outline: 'none' }}>Step {step + 1} of 3: {STEPS[step]}</h2>
      <Alert>{error}</Alert>
      <form onSubmit={next} className="au-form" noValidate>
        {step === 0 && (
          <>
            <Field label="Full name" icon="user">
              <input type="text" placeholder="As on your licence" value={about.name} autoComplete="name" onChange={setA('name')} required />
            </Field>
            <Field label="Mobile number" icon="phone">
              <input type="tel" inputMode="numeric" placeholder="9876543210" maxLength={10} value={about.mobile} autoComplete="tel"
                onChange={(e) => setAbout({ ...about, mobile: e.target.value.replace(/\D/g, '') })} required />
            </Field>
            <Field label="Email" icon="mail">
              <input type="email" placeholder="you@example.com" value={about.email} autoComplete="email" onChange={setA('email')} required />
            </Field>
            <Field label="Password" icon="lock">
              <PasswordInput placeholder="At least 8 characters" value={about.password} autoComplete="new-password" onChange={setA('password')} required />
            </Field>
          </>
        )}
        {step === 1 && (
          // VehicleFields is styled with the dashboard (pc) tokens, which only exist under .pc.
          <div className="pc" style={{ background: 'none', marginBottom: 16 }}>
            <VehicleFields value={vehicle} onChange={(p) => setVehicle({ ...vehicle, ...p })} idPrefix="signup-veh" hideTrips />
          </div>
        )}
        {step === 2 && (
          <>
            <Field label="Driving licence number" icon="id">
              <input type="text" value={ids.licenseNumber} maxLength={30} onChange={(e) => setIds({ ...ids, licenseNumber: e.target.value })} required />
            </Field>
            <Field label="Aadhaar number" icon="id">
              <input type="text" inputMode="numeric" placeholder="12 digits" maxLength={12} value={ids.aadhaarNumber}
                onChange={(e) => setIds({ ...ids, aadhaarNumber: e.target.value.replace(/\D/g, '') })} required />
            </Field>
            <div className="pc" style={{ background: 'none', marginBottom: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 12 }}>
                {DOCUMENTS.map((d) => (
                  <DocTile key={d.key} label={d.label} icon={d.icon} file={files[d.key]} missing={!!error && !files[d.key]}
                    onChange={(f) => setFiles((p) => ({ ...p, [d.key]: f }))} />
                ))}
              </div>
            </div>
          </>
        )}
        <Submit loading={loading} loadingText="Submitting...">{step < 2 ? 'Continue' : 'Create account'}</Submit>
        {step > 0 && <TextButton back onClick={() => { setError(''); setStep(step - 1) }}>Back</TextButton>}
      </form>
    </AuthShell>
  )
}
