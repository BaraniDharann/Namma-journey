import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import Icon from '../dash/Icon'

/**
 * Shared frame for every sign-in screen: a photo side that says who this door is for, and a
 * light, softly shaded form card. Each role keeps one tint (traveller orange, driver blue,
 * owner violet) so people can tell at a glance which login they are on.
 */
const ROLES = {
  user: {
    label: 'Traveller', icon: 'suitcase', to: '/login',
    photo: '/images/indian-hindu-temple-singapore.jpg',
    line: 'Temples, hills and coastlines, with a verified driver at the wheel.',
    points: [['route', 'Book a trip in under a minute'], ['shield', 'Verified, rated drivers'], ['wallet', 'Pay by UPI when you are ready']],
  },
  driver: {
    label: 'Driver', icon: 'wheel', to: '/driver/login',
    photo: '/images/beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg',
    line: 'Your trips, your route and your earnings in one place.',
    points: [['calendar', 'See new requests the moment they land'], ['nav', 'Live trip tracking'], ['rupee', 'Weekly earnings at a glance']],
  },
  owner: {
    label: 'Owner', icon: 'crown', to: '/owner/login',
    photo: '/images/palace-king-mahal-kingdom-shiva.jpg',
    line: 'Run the fleet, verify payments and watch the business grow.',
    points: [['chart', 'Revenue and booking analytics'], ['car', 'Fleet status in real time'], ['qr', 'Verify UPI payments fast']],
  },
}

const ease = [0.16, 1, 0.3, 1]

export default function AuthShell({ role = 'user', title, sub, children, footer }) {
  const r = ROLES[role]
  return (
    <div className={`au au-${role}`}>
      <aside className="au-side" aria-hidden="true">
        <img src={r.photo} alt="" />
        <div className="au-side-shade" />
        <motion.div className="au-side-card" initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, ease, delay: 0.15 }}>
          <span className="au-side-chip"><Icon name={r.icon} size={22} /></span>
          <p className="au-side-line">{r.line}</p>
          <ul>
            {r.points.map(([ic, text]) => (
              <li key={text}><span className="au-tic"><Icon name={ic} size={16} /></span>{text}</li>
            ))}
          </ul>
        </motion.div>
      </aside>

      <main className="au-main">
        <motion.div className="au-card" initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, ease }}>
          <div className="au-top">
            <Link to="/" className="au-brand">
              <span className="au-brand-mark"><Icon name="car" size={20} weight="fill" /></span>
              <span>Namma <b>Journey</b></span>
            </Link>
            <span className="au-role"><Icon name={r.icon} size={14} />{r.label}</span>
          </div>

          <h1 className="au-title">{title}</h1>
          {sub && <p className="au-sub">{sub}</p>}

          {children}

          {footer}
          <RoleSwitch current={role} />
        </motion.div>
      </main>
    </div>
  )
}

/** The other two doors, as tinted cards. */
function RoleSwitch({ current }) {
  const others = Object.entries(ROLES).filter(([k]) => k !== current)
  return (
    <nav className="au-switch" aria-label="Other logins">
      <span className="au-switch-l">Signing in as someone else?</span>
      <div className="au-switch-row">
        {others.map(([k, o]) => (
          <Link key={k} to={o.to} className={`au-door au-door-${k}`}>
            <span className="au-door-ic"><Icon name={o.icon} size={18} /></span>
            <span>{o.label} login</span>
            <Icon name="arrow" size={14} />
          </Link>
        ))}
      </div>
    </nav>
  )
}

/** A labelled input with a leading icon. `aside` sits at the right of the label row. */
export function Field({ label, icon, aside, children }) {
  return (
    <label className="au-field">
      <span className="au-label-row"><span className="au-label">{label}</span>{aside}</span>
      <span className="au-input">
        {icon && <Icon name={icon} size={18} className="au-input-ic" />}
        {children}
      </span>
    </label>
  )
}

/** Password input with a show/hide toggle. It stays type="password" until asked. */
export function PasswordInput(props) {
  const [show, setShow] = useState(false)
  return (
    <>
      <input {...props} type={show ? 'text' : 'password'} />
      <button type="button" className="au-eye" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}>
        <Icon name={show ? 'eye-off' : 'eye-open'} size={18} />
      </button>
    </>
  )
}

export function Alert({ tone = 'error', children }) {
  if (!children) return null
  return (
    <div className={`au-alert au-alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon name={tone === 'error' ? 'alert' : 'check'} size={18} />
      <span>{children}</span>
    </div>
  )
}

export function Submit({ loading, loadingText, disabled, children }) {
  return (
    <button type="submit" disabled={loading || disabled} className="au-submit">
      {loading ? <><span className="spinner au-spin" />{loadingText}</> : <>{children}<Icon name="arrow" size={18} /></>}
    </button>
  )
}

export function TextButton({ onClick, children, back }) {
  return (
    <button type="button" className={back ? 'au-back' : 'au-textbtn'} onClick={onClick}>
      {back && <Icon name="back" size={14} />}{children}
    </button>
  )
}
