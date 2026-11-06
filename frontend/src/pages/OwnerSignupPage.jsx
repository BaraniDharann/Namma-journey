import React, { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ownerSignup, ownerResubmit, ownerResubmitPrefill, publicCities } from '../utils/api'
import { ownerStepProblem, OWNER_STEPS } from '../utils/ownerForm'
import AuthShell, { Field, FieldError, Alert, Submit, CtaLink, FormSection, WizardSteps } from '../components/auth/AuthShell'
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

const STEPS = [
  { title: 'Your travels', short: 'Travels', hint: 'How customers and our team will know and reach you.' },
  { title: 'Identity', short: 'Identity', hint: 'Used only to verify you. We keep just the last 4 digits of Aadhaar.' },
  { title: 'Bank and UPI', short: 'Bank', hint: 'Where your trip earnings and customer UPI payments go.' },
  { title: 'Documents', short: 'Documents', hint: 'Clear photos, JPEG or PNG, up to 5 MB each. For Aadhaar, photograph the side with the large QR code.' },
]

/**
 * A travels owner applying to list on NammaJourney: business, KYC and bank details plus three
 * document photos. With ?resubmit=<token> (from the rejection email) it pre-fills a rejected
 * application, keeps the documents already sent unless replaced, and sends it back for review.
 *
 * Four short steps instead of one long page: each step is checked before moving on, Back keeps
 * everything typed, and a step already opened can be reopened from the step bar.
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
  const [badField, setBadField] = useState('')
  const [loadError, setLoadError] = useState('')
  const [done, setDone] = useState(null)
  const [step, setStep] = useState(0)
  const [reached, setReached] = useState(0) // the furthest step opened so far
  const formRef = useRef(null)
  const headRef = useRef(null)
  const moved = useRef(false)

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

  // A new step starts at the top with its heading focused, so screen readers announce it too.
  useEffect(() => {
    if (!moved.current) return
    formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    headRef.current?.focus({ preventScroll: true })
  }, [step])

  const clearIf = (k) => { if (badField === k) { setBadField(''); setError('') } }
  const setValue = (k, v) => { setForm((f) => ({ ...f, [k]: v })); clearIf(k) }
  const set = (k) => (e) => setValue(k, e.target.value)
  const toggleCity = (id) => setValue('cityIds', form.cityIds.includes(id) ? form.cityIds.filter((c) => c !== id) : [...form.cityIds, id])

  const goTo = (s) => { moved.current = true; setError(''); setBadField(''); setStep(s); setReached((r) => Math.max(r, s)) }

  /** Puts the cursor on the field that needs fixing (or the first choice of a group). */
  const focusField = (field) => {
    const el = formRef.current?.querySelector(`[name="${field}"]`)
    if (el) { el.focus({ preventScroll: true }); el.scrollIntoView({ behavior: 'smooth', block: 'center' }) }
  }

  const show = (problem) => {
    setError(problem.message)
    setBadField(problem.field)
    setTimeout(() => focusField(problem.field), 60)
  }

  const send = async () => {
    // Every step again, in case an earlier one was edited after it was checked.
    for (let s = 0; s < OWNER_STEPS; s++) {
      const problem = ownerStepProblem(s, form, docs, !!resubmitToken)
      if (problem) {
        if (s !== step) goTo(s)
        show(problem)
        return
      }
    }
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
    } finally {
      setLoading(false)
    }
  }

  // Enter in any field moves forward, like the Next button.
  const onSubmit = (e) => {
    e.preventDefault()
    if (step < OWNER_STEPS - 1) {
      const problem = ownerStepProblem(step, form, docs, !!resubmitToken)
      if (problem) show(problem)
      else goTo(step + 1)
    } else {
      send()
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

  const bad = (k) => (badField === k ? { 'aria-invalid': true } : {})
  const err = (k) => (badField === k ? error : '')
  const chosen = form.cityIds.length
  const last = step === OWNER_STEPS - 1
  const s = STEPS[step]
  return (
    <AuthShell role="owner" wide title={resubmitToken ? 'Fix your application' : 'List your travels'}
      sub={resubmitToken ? 'Correct the details below and send it again.' : 'Four short steps, about 5 minutes. We verify your documents within 48 hours.'}
      footer={<p className="au-alt">Already approved? <Link to="/owner/login">Sign in to the owner portal</Link></p>}>
      <Alert>{loadError}</Alert>
      {rejection && <Alert>{`Why it was not approved: ${rejection}`}</Alert>}

      <form ref={formRef} onSubmit={onSubmit} className="au-form au-wizard" noValidate>
        <WizardSteps steps={STEPS} current={step} reached={reached} onPick={goTo} />

        <motion.div key={step} initial={{ opacity: 0, x: 14 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}>
          <FormSection n={step + 1} title={s.title} headRef={headRef}
            hint={step === 3 && resubmitToken ? `${s.hint} Leave one empty to keep the photo you sent before.` : s.hint}>

            {step === 0 && (<>
              <div className="au-grid2">
                <Field label="Travels name" icon="bus" error={err('travelsName')}>
                  <input type="text" name="travelsName" {...bad('travelsName')} placeholder="e.g. Sri Murugan Travels" value={form.travelsName}
                    onChange={set('travelsName')} maxLength={255} required />
                </Field>
                <Field label="Contact person" icon="user" error={err('contactName')}>
                  <input type="text" name="contactName" {...bad('contactName')} placeholder="Full name" value={form.contactName} autoComplete="name"
                    onChange={set('contactName')} maxLength={255} required />
                </Field>
                <Field label="Email" icon="mail" error={err('email')}>
                  <input type="email" name="email" {...bad('email')} placeholder="owner@example.com" value={form.email} autoComplete="email"
                    onChange={set('email')} required />
                </Field>
                <Field label="Mobile number" icon="phone" error={err('phone')}>
                  <input type="tel" name="phone" {...bad('phone')} inputMode="numeric" placeholder="10 digits" maxLength={10} value={form.phone} autoComplete="tel"
                    onChange={(e) => setValue('phone', e.target.value.replace(/\D/g, ''))} required />
                </Field>
              </div>
              <fieldset className={`au-fieldset${badField === 'cityIds' ? ' is-bad' : ''}`}>
                <legend className="au-label">Cities you serve</legend>
                <div className="au-chips">
                  {cities.map((c) => (
                    <label key={c.id} className="au-chip">
                      <input type="checkbox" name="cityIds" checked={form.cityIds.includes(c.id)} onChange={() => toggleCity(c.id)} />
                      <Icon name="check" size={16} />
                      {c.name}
                    </label>
                  ))}
                  {!cities.length && !loadError && <span className="au-help">Loading cities…</span>}
                </div>
                {cities.length > 0 && !err('cityIds') && (
                  <p className="au-chips-meta" aria-live="polite">{chosen ? `${chosen} selected` : 'Tap every city you run trips from'}</p>
                )}
                <FieldError>{err('cityIds')}</FieldError>
              </fieldset>
            </>)}

            {step === 1 && (
              <div className="au-grid2">
                <Field label={panHint ? `PAN (on file: ${panHint})` : 'PAN'} icon="id" error={err('panNumber')}>
                  <input type="text" name="panNumber" {...bad('panNumber')} placeholder="ABCDE1234F" maxLength={10} value={form.panNumber} autoCapitalize="characters"
                    onChange={(e) => setValue('panNumber', e.target.value.toUpperCase())} required />
                </Field>
                <Field label="Aadhaar number" icon="id" error={err('aadhaarNumber')}>
                  <input type="text" name="aadhaarNumber" {...bad('aadhaarNumber')} inputMode="numeric" placeholder="12 digits" maxLength={12} value={form.aadhaarNumber}
                    onChange={(e) => setValue('aadhaarNumber', e.target.value.replace(/\D/g, ''))} required />
                </Field>
              </div>
            )}

            {step === 2 && (
              <div className="au-grid2">
                <Field label="Account holder name" icon="user" error={err('bankAccountHolder')}>
                  <input type="text" name="bankAccountHolder" {...bad('bankAccountHolder')} placeholder="As printed on the passbook" value={form.bankAccountHolder}
                    onChange={set('bankAccountHolder')} required />
                </Field>
                <Field label="Bank name" icon="card" error={err('bankName')}>
                  <input type="text" name="bankName" {...bad('bankName')} placeholder="e.g. State Bank of India" value={form.bankName}
                    onChange={set('bankName')} required />
                </Field>
                <Field label="Account number" icon="card" error={err('bankAccountNumber')}>
                  <input type="text" name="bankAccountNumber" {...bad('bankAccountNumber')} inputMode="numeric" placeholder="9 to 18 digits" maxLength={18} value={form.bankAccountNumber}
                    onChange={(e) => setValue('bankAccountNumber', e.target.value.replace(/\D/g, ''))} required />
                </Field>
                <Field label="IFSC" icon="id" error={err('bankIfsc')}>
                  <input type="text" name="bankIfsc" {...bad('bankIfsc')} placeholder="SBIN0001234" maxLength={11} value={form.bankIfsc} autoCapitalize="characters"
                    onChange={(e) => setValue('bankIfsc', e.target.value.toUpperCase())} required />
                </Field>
                <div className="au-span2">
                  <Field label="UPI ID (customers pay you here)" icon="qr" error={err('upiId')}>
                    <input type="text" name="upiId" {...bad('upiId')} placeholder="name@bank" value={form.upiId} autoCapitalize="none"
                      onChange={set('upiId')} required />
                  </Field>
                </div>
              </div>
            )}

            {step === 3 && (<>
              <div className="pc au-docs">
                {DOCUMENTS.map((d) => (
                  <DocTile key={d.key} name={d.key} label={d.label} icon={d.icon} file={docs[d.key]}
                    missing={badField === d.key}
                    onChange={(f) => { setDocs((p) => ({ ...p, [d.key]: f })); clearIf(d.key) }} />
                ))}
              </div>
              <FieldError>{DOCUMENTS.some((d) => d.key === badField) ? error : ''}</FieldError>
              <label className={`au-consent${badField === 'aadhaarConsent' ? ' is-bad' : ''}`}>
                <input type="checkbox" name="aadhaarConsent" checked={form.aadhaarConsent}
                  onChange={(e) => setValue('aadhaarConsent', e.target.checked)} />
                <span>{CONSENT}</span>
              </label>
              <FieldError>{err('aadhaarConsent')}</FieldError>
            </>)}
          </FormSection>
        </motion.div>

        {/* Field problems show under the field; this is for the rest, such as a server error. */}
        {!badField && <Alert>{error}</Alert>}
        <div className="au-wiz-nav">
          {step > 0 && (
            <button type="button" className="au-btn2" onClick={() => goTo(step - 1)}>
              <Icon name="back" size={18} />Back
            </button>
          )}
          <Submit loading={loading} loadingText="Sending...">
            {last ? (resubmitToken ? 'Send for review again' : 'Submit application') : `Next: ${STEPS[step + 1].title}`}
          </Submit>
        </div>
      </form>
    </AuthShell>
  )
}
