import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * A traveller with no mobile number on file cannot be reached by the driver who takes their trip,
 * so booking is closed to them until they add one. Accounts created through Sign-in with Google
 * never collect a number — Google does not hand one over — so those travellers reach the booking
 * form with nothing in that field and would otherwise book a trip nobody can deliver.
 *
 * Two surfaces share the rule:
 *   - {@link MobileNumberPrompt} — the nudge on the dashboard, right after sign-in.
 *   - {@link MobileRequiredPanel} — the booking form standing in its own place, explaining why.
 *
 * Neither is the enforcement. The server refuses the booking outright (UserService.createBooking);
 * these only make the refusal something the traveller can understand and act on before they have
 * filled in a whole form.
 */

/** True when the signed-in traveller still owes us a contact number. */
export function needsMobileNumber(user) {
  return Boolean(user) && user.role === 'ROLE_USER' && !String(user.mobile || '').trim()
}

const ORANGE = '#f97316'

function PrimaryButton({ children, onClick, style }) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '12px 22px', borderRadius: 12, border: 'none', cursor: 'pointer',
        background: `linear-gradient(135deg, ${ORANGE}, #ea580c)`, color: '#fff',
        fontWeight: 700, fontSize: 14, boxShadow: '0 4px 16px rgba(249,115,22,0.28)',
        ...style,
      }}
    >
      {children}
    </button>
  )
}

/**
 * Modal nudge for the dashboard. Dismissible — badgering someone on every page load is its own
 * kind of broken — but dismissing it does not unlock booking, which is what `MobileRequiredPanel`
 * and the server are for.
 */
export function MobileNumberPrompt() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [dismissed, setDismissed] = useState(false)

  if (!needsMobileNumber(user) || dismissed) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-prompt-title"
      style={{
        position: 'fixed', inset: 0, zIndex: 60, display: 'flex',
        alignItems: 'center', justifyContent: 'center', padding: 20,
        background: 'rgba(15,23,42,0.45)',
      }}
      onClick={(e) => { if (e.target === e.currentTarget) setDismissed(true) }}
    >
      <div
        className="animate-fadeIn"
        style={{
          width: '100%', maxWidth: 440, background: '#fff', borderRadius: 20, padding: 28,
          boxShadow: '0 24px 60px rgba(15,23,42,0.22)', textAlign: 'center',
        }}
      >
        <div
          style={{
            width: 64, height: 64, borderRadius: '50%', margin: '0 auto 16px', fontSize: 30,
            background: 'linear-gradient(135deg,#fff7ed,#ffedd5)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          📱
        </div>

        <h2
          id="mobile-prompt-title"
          style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: '1.3rem', color: '#0F172A', marginBottom: 8 }}
        >
          Add your mobile number
        </h2>
        <p style={{ fontSize: 14, color: '#64748b', lineHeight: 1.6, marginBottom: 22 }}>
          We still need a number for your account. Your driver uses it to reach you on the day of
          the trip, so <strong style={{ color: '#0F172A' }}>booking stays closed until you add one</strong>.
        </p>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
          <PrimaryButton onClick={() => navigate('/user/profile')}>Update my profile</PrimaryButton>
          <button
            onClick={() => setDismissed(true)}
            style={{
              padding: '12px 22px', borderRadius: 12, border: '2px solid #e2e8f0', background: '#fff',
              color: '#64748b', fontWeight: 700, fontSize: 14, cursor: 'pointer',
            }}
          >
            Later
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * Stands in for the booking form. Says what is missing, why it matters and where to fix it,
 * instead of letting someone fill in dates and destinations only to be refused at the end.
 */
export function MobileRequiredPanel() {
  const navigate = useNavigate()

  return (
    <div
      style={{
        background: '#fff', borderRadius: 20, padding: '40px 28px', textAlign: 'center',
        border: '1px solid #ffedd5', boxShadow: '0 2px 12px rgba(0,0,0,0.03)',
      }}
    >
      <div
        style={{
          width: 72, height: 72, borderRadius: '50%', margin: '0 auto 18px', fontSize: 34,
          background: 'linear-gradient(135deg,#fff7ed,#ffedd5)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        📱
      </div>

      <h2 style={{ fontFamily: 'Poppins, sans-serif', fontWeight: 800, fontSize: '1.25rem', color: '#0F172A', marginBottom: 10 }}>
        Add your mobile number to book a trip
      </h2>
      <p style={{ fontSize: 14, color: '#64748b', lineHeight: 1.65, maxWidth: 420, margin: '0 auto 24px' }}>
        Your account does not have a mobile number yet. The driver assigned to your trip needs it to
        reach you, so we cannot take a booking without it. Add it to your profile and this form opens
        straight away.
      </p>

      <PrimaryButton onClick={() => navigate('/user/profile')}>Go to my profile</PrimaryButton>
    </div>
  )
}
