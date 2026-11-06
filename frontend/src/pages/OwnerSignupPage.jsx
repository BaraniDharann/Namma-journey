import React, { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ownerSignup, ownerResubmit, ownerResubmitPrefill, publicCities } from '../utils/api'
import { ownerFormError } from '../utils/ownerForm'
import AuthShell, { Field, Alert, Submit, CtaLink, FormSection } from '../components/auth/AuthShell'
import Icon from '../components/dash/Icon'
import DocTile from '../components/driver/DocTile'

const EMPTY = {
  travelsName: '', contactName: '', email: '', phone: '', cityIds: [],
  panNumber: '', aadhaarNumber: '',
  bankAccountHolder: '', bankAccountNumber: '', bankIfsc: '', bankName: '', upiId: '',
  aadhaarConsent: false,
}

const DOCUMENTS = [
  { key: 'registrationDoc', label: 'Business registration', icon: 'id' },
  { key: 'panDoc', label: 'PAN card', icon: 'id' },
  { key: 'aadhaarDoc', label: 'Aadhaar card (QR side)', icon: 'id' },
]

const CONSENT = 'I consent to NammaJourney verifying my Aadhaar offline (UIDAI Secure QR) for owner registration.'

/**
 * A travels owner applying to list on NammaJourney: business, KYC and bank details plus three
 * document photos. With ?resubmit=<token> (from the rejection email) it pre-fills a rejected
 * application, keeps the documents already sent unless replaced, and sends it back for review.
 */
export default function OwnerSignupPage() {
  const [params] = useSearchParams()
  const resubmitToken = params.get('resubmit')
  const [form, setForm] = useState(EMPTY)
  const [docs, setDocs] = useState({ registrationDoc: null, panDoc: null, aadhaarDoc: null })
  const [cities, setCities] = useState([])
  const [rejection, setRejection] = useState(null)
  const [panHint, setPanHint] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [done, setDone] = useState(null)
  const top = useRef(null)
  const errorAt = useRef(null) // the error shows by the Apply button, where the person is looking

  useEffect(() => {
    publicCities().then((r) => setCities(r.data || [])).catch(() => setLoadError('Could not load the city list. Refresh to try again.'))
  }, [])

  useEffect(() => {
    if (!resubmitToken) return
    ownerResubmitPrefill(resubmitToken)
      .then(({ data }) => {
        setForm((f) => ({
          ...f, travelsName: data.travelsName || '', contactName: data.contactName || '', email: data.email || '',
          phone: data.phone || '', cityIds: data.cityIds || [], bankAccountHolder: data.bankAccountHolder || '',
          bankIfsc: data.bankIfsc || '', bankName: data.bankName || '',
        }))
        setRejection(data.rejectionReason || '')
        setPanHint(data.panMasked || '')
      })
      .catch((e) => setLoadError(e.response?.data?.error || 'This link has expired. Please contact support.'))
  }, [resubmitToken])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const toggleCity = (id) => setForm((f) => ({
    ...f, cityIds: f.cityIds.includes(id) ? f.cityIds.filter((c) => c !== id) : [...f.cityIds, id],
  }))

  const submit = async (e) => {
    e.preventDefault()
    const problem = ownerFormError(form, docs, !!resubmitToken)
    setError(problem)
    if (problem) { errorAt.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); return }
    setLoading(true)
    try {
      const fd = new FormData()
      Object.entries(form).forEach(([k, v]) => {
        if (k === 'cityIds') v.forEach((id) => fd.append('cityIds', String(id)))
        else fd.append(k, String(v ?? ''))
      })
      DOCUMENTS.forEach((d) => { if (docs[d.key]) fd.append(d.key, docs[d.key]) })
      const res = resubmitToken ? await ownerResubmit(resubmitToken, fd) : await ownerSignup(fd)
      setDone(res.data)
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Could not send the application. Please check your details and try again.')
      errorAt.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    const by = done.reviewBy ? new Date(done.reviewBy).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : null
    return (
      <AuthShell role="owner" title="Application received" sub="Thank you for applying to Namma Journey.">
        <Alert tone="ok">We will verify your documents within 48 hours{by ? ` (by ${by})` : ''}.</Alert>
        <p className="au-sub">Once approved, your Owner ID and password are emailed to <strong>{form.email}</strong>.</p>
        <CtaLink to="/owner/login" icon="crown" title="Go to owner sign in" line="Use the Owner ID and password from the approval email." />
      </AuthShell>
    )
  }

  const chosen = form.cityIds.length
  return (
    <AuthShell role="owner" wide title={resubmitToken ? 'Fix your application' : 'List your travels'}
      sub={resubmitToken ? 'Correct the details below and send it again.' : 'Apply once. We verify your documents within 48 hours.'}
      footer={<p className="au-alt">Already approved? <Link to="/owner/login">Sign in to the owner portal</Link></p>}>
      <div ref={top} />
      <Alert>{loadError}</Alert>
      {rejection && <Alert>{`Why it was not approved: ${rejection}`}</Alert>}
      <form onSubmit={submit} className="au-form" noValidate>
        <FormSection n={1} title="Your travels" hint="How customers and our team will know and reach you.">
          <div className="au-grid2">
            <Field label="Travels name" icon="bus">
              <input type="text" placeholder="e.g. Sri Murugan Travels" value={form.travelsName} onChange={set('travelsName')} maxLength={255} required />
            </Field>
            <Field label="Contact person" icon="user">
              <input type="text" placeholder="Full name" value={form.contactName} autoComplete="name" onChange={set('contactName')} maxLength={255} required />
            </Field>
            <Field label="Email" icon="mail">
              <input type="email" placeholder="owner@example.com" value={form.email} autoComplete="email" onChange={set('email')} required />
            </Field>
            <Field label="Mobile number" icon="phone">
              <input type="tel" inputMode="numeric" placeholder="10 digits" maxLength={10} value={form.phone} autoComplete="tel"
                onChange={(e) => setForm({ ...form, phone: e.target.value.replace(/\D/g, '') })} required />
            </Field>
          </div>
          <fieldset style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
            <legend className="au-label" style={{ marginBottom: 8 }}>Cities you serve</legend>
            <div className="au-chips">
              {cities.map((c) => (
                <label key={c.id} className="au-chip">
                  <input type="checkbox" checked={form.cityIds.includes(c.id)} onChange={() => toggleCity(c.id)} />
                  <Icon name="check" size={16} />
                  {c.name}
                </label>
              ))}
              {!cities.length && !loadError && <span className="au-help">Loading cities…</span>}
            </div>
            {cities.length > 0 && (
              <p className="au-chips-meta" aria-live="polite">{chosen ? `${chosen} selected` : 'Tap every city you run trips from'}</p>
            )}
          </fieldset>
        </FormSection>

        <FormSection n={2} title="Identity" hint="Used only to verify you. We keep just the last 4 digits of Aadhaar.">
          <div className="au-grid2">
            <Field label={panHint ? `PAN (on file: ${panHint})` : 'PAN'} icon="id">
              <input type="text" placeholder="ABCDE1234F" maxLength={10} value={form.panNumber} autoCapitalize="characters"
                onChange={(e) => setForm({ ...form, panNumber: e.target.value.toUpperCase() })} required />
            </Field>
            <Field label="Aadhaar number" icon="id">
              <input type="text" inputMode="numeric" placeholder="12 digits" maxLength={12} value={form.aadhaarNumber}
                onChange={(e) => setForm({ ...form, aadhaarNumber: e.target.value.replace(/\D/g, '') })} required />
            </Field>
          </div>
        </FormSection>

        <FormSection n={3} title="Bank and UPI" hint="Where your trip earnings and customer UPI payments go.">
          <div className="au-grid2">
            <Field label="Account holder name" icon="user">
              <input type="text" placeholder="As printed on the passbook" value={form.bankAccountHolder} onChange={set('bankAccountHolder')} required />
            </Field>
            <Field label="Bank name" icon="card">
              <input type="text" placeholder="e.g. State Bank of India" value={form.bankName} onChange={set('bankName')} required />
            </Field>
            <Field label="Account number" icon="card">
              <input type="text" inputMode="numeric" placeholder="9 to 18 digits" maxLength={18} value={form.bankAccountNumber}
                onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value.replace(/\D/g, '') })} required />
            </Field>
            <Field label="IFSC" icon="id">
              <input type="text" placeholder="SBIN0001234" maxLength={11} value={form.bankIfsc} autoCapitalize="characters"
                onChange={(e) => setForm({ ...form, bankIfsc: e.target.value.toUpperCase() })} required />
            </Field>
            <div className="au-span2">
              <Field label="UPI ID (customers pay you here)" icon="qr">
                <input type="text" placeholder="name@bank" value={form.upiId} autoCapitalize="none" onChange={set('upiId')} required />
              </Field>
            </div>
          </div>
        </FormSection>

        <FormSection n={4} title="Documents"
          hint={`Clear photos, JPEG or PNG, up to 5 MB each. For Aadhaar, photograph the side with the large QR code.${resubmitToken ? ' Leave one empty to keep the photo you sent before.' : ''}`}>
          <div className="pc au-docs">
            {DOCUMENTS.map((d) => (
              <DocTile key={d.key} label={d.label} icon={d.icon} file={docs[d.key]}
                missing={!!error && !resubmitToken && !docs[d.key]}
                onChange={(f) => setDocs((p) => ({ ...p, [d.key]: f }))} />
            ))}
          </div>
          <label className="au-consent">
            <input type="checkbox" checked={form.aadhaarConsent} onChange={(e) => setForm({ ...form, aadhaarConsent: e.target.checked })} />
            <span>{CONSENT}</span>
          </label>
        </FormSection>

        <div ref={errorAt}><Alert>{error}</Alert></div>
        <Submit loading={loading} loadingText="Sending...">{resubmitToken ? 'Send for review again' : 'Apply'}</Submit>
      </form>
    </AuthShell>
  )
}
