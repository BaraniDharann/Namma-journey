import React, { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion, useScroll, useSpring } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { getPublicPackages, getPublicReviews } from '../utils/api'
import Pagination, { usePagination } from '../components/Pagination'
import JourneyHero from '../components/journey/JourneyHero'
import JourneyStage from '../components/journey/JourneyStage'
import QuickBooking from '../components/journey/QuickBooking'
import { fleet, tripTypes } from '../data/journeyData'
import {
  Counter,
  EASE,
  Reveal,
  Stagger,
  StaggerItem,
  TiltCard,
} from '../components/motion/primitives'

const NAV_ITEMS = [
  { label: 'Journey', href: '#journey' },
  { label: 'Our cars', href: '#fleet' },
  { label: 'Packages', href: '#packages' },
  { label: 'Why us', href: '#features' },
  { label: 'Reviews', href: '#reviews' },
]

const FEATURES = [
  { icon: '🛕', title: 'Temple trips', desc: 'Dedicated routes to every major temple in India, with drivers who know the roads and the rituals.' },
  { icon: '🚗', title: 'Premium cars', desc: 'AC and non-AC vehicles for every group size — hatchbacks, sedans, SUVs and tempo travellers.' },
  { icon: '📍', title: 'Live tracking', desc: 'Follow the car on the map in real time. Share the link so family can watch the journey too.' },
  { icon: '💳', title: 'Clean pricing', desc: 'UPI or cash, quoted up front. No surge, no hidden tolls, no surprises at the end of the trip.' },
  { icon: '⭐', title: 'Verified drivers', desc: 'Every driver is background-checked with a valid licence and Aadhaar on file before their first ride.' },
  { icon: '🔒', title: 'Safe all the way', desc: '24/7 support on every trip, an SOS button in the app, and insurance on every kilometre.' },
]

const STATS = [
  { icon: '🧳', value: 50, suffix: 'K+', label: 'Happy travellers' },
  { icon: '🚗', value: 1200, suffix: '+', label: 'Verified drivers' },
  { icon: '📍', value: 500, suffix: '+', label: 'Destinations' },
  { icon: '⭐', value: 4.9, suffix: '★', label: 'Average rating', decimals: 1 },
]

const STEPS = [
  { step: '01', title: 'Create your account', desc: 'Sign up with email, verify the OTP and set a password. Under a minute.' },
  { step: '02', title: 'Plan the route', desc: 'Pickup, destination, dates, travellers, AC or non-AC. The fare is quoted before you confirm.' },
  { step: '03', title: 'Drive', desc: 'A verified driver picks you up. Track the car live and settle by UPI or cash at the end.' },
]

const DEFAULT_REVIEWS = [
  { userName: 'Rajesh Kumar', rating: 5, comment: 'Amazing service. The driver was professional and the car spotless — the Tirupati trip was unforgettable.', fromPlace: 'Chennai', toPlace: 'Tirupati' },
  { userName: 'Priya Sharma', rating: 5, comment: 'Booked for a family trip to Rameshwaram. Comfortable AC car, reasonable pricing. Will book again.', fromPlace: 'Madurai', toPlace: 'Rameshwaram' },
  { userName: 'Suresh Babu', rating: 4, comment: 'Driver was on time and knew the route well. Smooth journey to Shirdi throughout.', fromPlace: 'Pune', toPlace: 'Shirdi' },
  { userName: 'Anitha Reddy', rating: 5, comment: 'Transparent pricing, no hidden charges. Our Kedarnath trip was perfectly organised.', fromPlace: 'Delhi', toPlace: 'Kedarnath' },
  { userName: 'Mohammed Faisal', rating: 4, comment: 'Very reliable. The live tracking gave us peace of mind for the whole drive.', fromPlace: 'Hyderabad', toPlace: 'Srisailam' },
  { userName: 'Lakshmi Devi', rating: 5, comment: 'The driver was courteous and helpful, and the car was clean and well maintained.', fromPlace: 'Bangalore', toPlace: 'Mysore' },
]

const PKG_CATEGORIES = ['ALL', 'TEMPLE', 'HONEYMOON', 'ADVENTURE', 'HILL_STATION', 'BEACH', 'HERITAGE', 'PILGRIMAGE', 'FAMILY', 'STATE_SPECIAL']
const CAT_COLORS = { TEMPLE: '#f59e0b', HONEYMOON: '#ec4899', ADVENTURE: '#10b981', HILL_STATION: '#6366f1', BEACH: '#06b6d4', HERITAGE: '#8b5cf6', WILDLIFE: '#84cc16', PILGRIMAGE: '#f97316', FAMILY: '#3b82f6', STATE_SPECIAL: '#ef4444' }
const CAT_EMOJI = { TEMPLE: '🛕', HONEYMOON: '💑', ADVENTURE: '🏔️', HILL_STATION: '⛰️', BEACH: '🏖️', HERITAGE: '🏛️', WILDLIFE: '🦁', PILGRIMAGE: '🙏', FAMILY: '👨‍👩‍👧‍👦', STATE_SPECIAL: '🌟' }

const catLabel = (c) =>
  c === 'ALL' ? 'All' : c.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ')

/* ─────────────────────────  navigation  ───────────────────────── */

function Nav({ user, onDashboard }) {
  const [stuck, setStuck] = useState(false)
  const [sheet, setSheet] = useState(false)
  const { scrollYProgress } = useScroll()
  const bar = useSpring(scrollYProgress, { stiffness: 140, damping: 24, restDelta: 0.001 })

  useEffect(() => {
    const onScroll = () => setStuck(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const go = (href) => {
    setSheet(false)
    document.querySelector(href)?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <>
      <nav className={`jn${stuck ? ' is-stuck' : ''}`}>
        <div className="jp-wrap jn-inner">
          <Link to="/" className="jn-logo">
            <span className="jn-mark">🚗</span>
            <span className="jn-name">Namma <b>Journey</b></span>
          </Link>

          <div className="jn-links">
            {NAV_ITEMS.map((item) => (
              <button key={item.href} type="button" className="jn-link" onClick={() => go(item.href)}>
                {item.label}
              </button>
            ))}
          </div>

          <div className="jn-actions">
            {user ? (
              <button type="button" className="jn-cta" onClick={onDashboard}>Dashboard</button>
            ) : (
              <>
                <Link to="/login" className="jn-ghost">Login</Link>
                <Link to="/signup" className="jn-cta">Book now</Link>
              </>
            )}
            <button type="button" className="jn-burger" onClick={() => setSheet(true)} aria-label="Open menu">☰</button>
          </div>
        </div>
        <motion.span className="jn-scrollbar" style={{ scaleX: bar }} />
      </nav>

      <AnimatePresence>
        {sheet && (
          <motion.div
            className="jn-sheet"
            initial={{ opacity: 0, clipPath: 'circle(0% at 92% 6%)' }}
            animate={{ opacity: 1, clipPath: 'circle(150% at 92% 6%)' }}
            exit={{ opacity: 0, clipPath: 'circle(0% at 92% 6%)' }}
            transition={{ duration: 0.5, ease: EASE }}
          >
            <button type="button" className="jn-sheet-close" onClick={() => setSheet(false)} aria-label="Close menu">✕</button>
            {NAV_ITEMS.map((item, i) => (
              <motion.button
                key={item.href}
                type="button"
                onClick={() => go(item.href)}
                initial={{ opacity: 0, y: 26 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.12 + i * 0.06, duration: 0.5, ease: EASE }}
              >
                {item.label}
              </motion.button>
            ))}
            <motion.div
              initial={{ opacity: 0, y: 26 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.44, duration: 0.5, ease: EASE }}
              style={{ display: 'flex', gap: 12, marginTop: 18 }}
            >
              {user ? (
                <button type="button" className="jn-cta" onClick={() => { setSheet(false); onDashboard() }}>Dashboard</button>
              ) : (
                <>
                  <Link to="/login" className="jn-ghost" style={{ display: 'inline-block' }} onClick={() => setSheet(false)}>Login</Link>
                  <Link to="/signup" className="jn-cta" onClick={() => setSheet(false)}>Book now</Link>
                </>
              )}
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

/* ─────────────────────────  the cars  ───────────────────────── */

function FleetSection() {
  return (
    <section id="fleet" className="jp-section jp-cream">
      <div className="jp-wrap">
        <div style={{ textAlign: 'center', marginBottom: 44 }}>
          <Reveal><span className="jp-eyebrow">🚘 The garage</span></Reveal>
          <Reveal delay={0.06}>
            <h2 className="jp-h2">Pick the car, <em>we'll bring the driver</em></h2>
          </Reveal>
          <Reveal delay={0.12}>
            <p className="jp-lead" style={{ margin: '0 auto' }}>
              Every vehicle is serviced, insured and driven by someone we've verified in person.
              Rates below are all-inclusive — fuel, tolls and driver bata.
            </p>
          </Reveal>
        </div>

        <Stagger className="fleet-grid" stagger={0.09}>
          {fleet.map((car) => (
            <StaggerItem key={car.id} className={`fleet-card${car.popular ? ' is-popular' : ''}`}>
              {car.popular && <span className="fleet-tag">Most booked</span>}
              <span className="fleet-emoji">{car.emoji}</span>
              <h3>{car.name}</h3>
              <p className="fleet-eg">{car.examples}</p>
              <div className="fleet-specs">
                <div><b>{car.seats}</b> seats</div>
                <div><b>{car.bags}</b> bags</div>
              </div>
              <div className="fleet-rate">
                <b>₹{car.rate}</b> <span>/ km</span>
                <p className="fleet-best">{car.best}</p>
              </div>
            </StaggerItem>
          ))}
        </Stagger>

        <Stagger className="trip-types" stagger={0.06}>
          {tripTypes.map((t) => (
            <StaggerItem key={t.label} className="trip-type">
              <span className="trip-type-icon">{t.icon}</span>
              <span>
                <b>{t.label}</b>
                <span>{t.note}</span>
              </span>
            </StaggerItem>
          ))}
        </Stagger>
      </div>
    </section>
  )
}

/* ─────────────────────────  page  ───────────────────────── */

export default function LandingPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const [packages, setPackages] = useState([])
  const [reviews, setReviews] = useState(DEFAULT_REVIEWS)
  const [category, setCategory] = useState('ALL')

  useEffect(() => {
    getPublicPackages().then((res) => setPackages(res.data || [])).catch(() => {})
    getPublicReviews()
      .then((res) => {
        if (res.data?.length) {
          setReviews(res.data.map((r) => ({
            userName: r.userName,
            rating: r.rating,
            comment: r.feedback || r.comment,
            fromPlace: r.fromPlace || '',
            toPlace: r.toPlace || '',
          })))
        }
      })
      .catch(() => {})
  }, [])

  const filtered = packages.filter((p) => category === 'ALL' || p.category === category)
  const pkgPage = usePagination(filtered, 8)
  const revPage = usePagination(reviews, 6)

  const dashboardPath =
    user?.role === 'ROLE_DRIVER' ? '/driver/dashboard'
    : user?.role === 'ROLE_OWNER' ? '/owner/dashboard'
    : '/user/dashboard'

  const startJourney = useCallback(() => {
    document.getElementById('journey')?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  return (
    <div className="journey-page">
      <Nav user={user} onDashboard={() => navigate(dashboardPath)} />

      <JourneyHero onStart={startJourney}>
        <QuickBooking />
      </JourneyHero>

      {/* ── the drive itself ── */}
      <JourneyStage />

      {/* ── numbers ── */}
      <section className="jp-section" style={{ paddingTop: 78, paddingBottom: 78 }}>
        <div className="jp-wrap">
          <Stagger className="jp-grid jp-grid-4" stagger={0.08}>
            {STATS.map((s) => (
              <StaggerItem key={s.label} className="stat-tile">
                <span className="stat-tile-icon">{s.icon}</span>
                <b>
                  <Counter value={s.value} suffix={s.suffix} decimals={s.decimals || 0} />
                </b>
                <span>{s.label}</span>
              </StaggerItem>
            ))}
          </Stagger>
        </div>
      </section>

      <FleetSection />

      {/* ── packages ── */}
      {packages.length > 0 && (
        <section id="packages" className="jp-section">
          <div className="jp-wrap">
            <div style={{ textAlign: 'center', marginBottom: 34 }}>
              <Reveal><span className="jp-eyebrow">📦 Curated packages</span></Reveal>
              <Reveal delay={0.06}>
                <h2 className="jp-h2">Someone else can <em>plan it</em></h2>
              </Reveal>
              <Reveal delay={0.12}>
                <p className="jp-lead" style={{ margin: '0 auto' }}>
                  Handpicked trips for temples, honeymoons and hill stations — food, stay and
                  transport already sorted.
                </p>
              </Reveal>
            </div>

            <Reveal delay={0.06} className="jp-filters">
              {PKG_CATEGORIES.map((c) => (
                <button
                  key={c}
                  type="button"
                  className={`jp-filter${category === c ? ' is-on' : ''}`}
                  onClick={() => { setCategory(c); pkgPage.setCurrentPage(1) }}
                >
                  {catLabel(c)}
                </button>
              ))}
            </Reveal>

            <motion.div layout className="jp-grid jp-grid-3">
              <AnimatePresence mode="popLayout">
                {pkgPage.paginatedItems.map((pkg, i) => {
                  const color = CAT_COLORS[pkg.category] || '#6b7280'
                  return (
                    <motion.div
                      key={pkg.id}
                      layout
                      initial={{ opacity: 0, y: 30, scale: 0.96 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: -16, scale: 0.96 }}
                      transition={{ duration: 0.5, delay: i * 0.05, ease: EASE }}
                    >
                      <TiltCard max={7} style={{ borderRadius: 20 }}>
                        <Link to={`/packages/${pkg.id}`} className="pkg">
                          <div
                            className="pkg-img"
                            style={{
                              backgroundImage: pkg.imageUrl ? `url(${pkg.imageUrl})` : undefined,
                              background: pkg.imageUrl ? undefined : `linear-gradient(140deg, ${color}44, ${color}11)`,
                            }}
                          >
                            {!pkg.imageUrl && (
                              <span style={{ fontSize: 54, opacity: 0.4 }}>{CAT_EMOJI[pkg.category] || '✈️'}</span>
                            )}
                            <div className="pkg-tags">
                              <span className="pkg-tag" style={{ background: color }}>{pkg.category?.replace('_', ' ')}</span>
                              <span className="pkg-tag" style={{ background: 'rgba(255,255,255,.14)' }}>{pkg.state}</span>
                            </div>
                            <span className="pkg-dur">{pkg.durationDays}D / {pkg.durationNights}N</span>
                          </div>
                          <div className="pkg-body">
                            <h3>{pkg.name}</h3>
                            <p className="pkg-desc">{pkg.description}</p>
                            <div className="pkg-incl">
                              {pkg.foodIncluded && <span>🍽️ Food</span>}
                              {pkg.accommodationIncluded && <span>🏨 Stay</span>}
                              {pkg.transportIncluded && <span>🚗 Transport</span>}
                              {pkg.tollFree && <span>🛣️ Toll free</span>}
                              {pkg.guideIncluded && <span>🗣️ Guide</span>}
                            </div>
                            <div className="pkg-places">
                              📍 {pkg.placesIncluded?.slice(0, 3).join(' → ')}
                              {pkg.placesIncluded?.length > 3 ? ` +${pkg.placesIncluded.length - 3} more` : ''}
                            </div>
                            <div className="pkg-foot">
                              <span className="pkg-price">
                                <b>₹{pkg.pricePerPerson?.toLocaleString('en-IN')}</b>
                                <span>/ person</span>
                              </span>
                              <span className="pkg-go" style={{ background: `linear-gradient(135deg, ${color}, ${color}bb)` }}>
                                View →
                              </span>
                            </div>
                          </div>
                        </Link>
                      </TiltCard>
                    </motion.div>
                  )
                })}
              </AnimatePresence>
            </motion.div>

            <Pagination
              currentPage={pkgPage.currentPage}
              totalPages={pkgPage.totalPages}
              onPageChange={pkgPage.setCurrentPage}
            />
            {filtered.length === 0 && (
              <p style={{ textAlign: 'center', padding: 40, color: '#94a3b8' }}>
                No packages in this category yet.
              </p>
            )}
          </div>
        </section>
      )}

      {/* ── features ── */}
      <section id="features" className="jp-section">
        <div className="jp-wrap">
          <div style={{ textAlign: 'center', marginBottom: 44 }}>
            <Reveal><span className="jp-eyebrow">Why Namma Journey</span></Reveal>
            <Reveal delay={0.06}>
              <h2 className="jp-h2">Built for the <em>long drive</em></h2>
            </Reveal>
          </div>
          <Stagger className="jp-grid jp-grid-3" stagger={0.07}>
            {FEATURES.map((f) => (
              <StaggerItem key={f.title} className="feat">
                <span className="feat-icon">{f.icon}</span>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
              </StaggerItem>
            ))}
          </Stagger>
        </div>
      </section>

      {/* ── how it works ── */}
      <section id="about" className="jp-section">
        <div className="jp-wrap how-grid">
          <div>
            <Reveal><span className="jp-eyebrow">📱 How it works</span></Reveal>
            <Reveal delay={0.06}>
              <h2 className="jp-h2">Three steps to the <em>first kilometre</em></h2>
            </Reveal>
            <Stagger stagger={0.12} delay={0.1} style={{ marginTop: 28 }}>
              {STEPS.map((s) => (
                <StaggerItem key={s.step} className="step">
                  <span className="step-num">{s.step}</span>
                  <div>
                    <h4>{s.title}</h4>
                    <p>{s.desc}</p>
                  </div>
                </StaggerItem>
              ))}
            </Stagger>
            <Reveal delay={0.2}>
              <Link to="/signup" className="btn-journey" style={{ marginTop: 8 }}>
                <span className="btn-journey-shine" />
                Get started free →
              </Link>
            </Reveal>
          </div>

          <Reveal x={40} y={0} delay={0.1}>
            <TiltCard max={8} className="how-media">
              <img
                src="/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg"
                alt="Traveller at a sunrise viewpoint"
                loading="lazy"
                decoding="async"
              />
              <span className="how-media-wash" aria-hidden />
              <motion.div
                className="how-chip how-chip-a"
                animate={{ y: [0, -9, 0] }}
                transition={{ duration: 3.4, repeat: Infinity, ease: 'easeInOut' }}
              >
                <span>🛕</span>
                <div>
                  <b>Temple trip booked</b>
                  <i>Tirupati · 2 days · ₹4,500</i>
                </div>
              </motion.div>
              <motion.div
                className="how-chip how-chip-b"
                animate={{ y: [0, -9, 0] }}
                transition={{ duration: 3.4, repeat: Infinity, ease: 'easeInOut', delay: 1.1 }}
              >
                <b>4.9★</b>
                <i>50K+ reviews</i>
              </motion.div>
            </TiltCard>
          </Reveal>
        </div>
      </section>

      {/* ── reviews ── */}
      <section id="reviews" className="jp-section jp-cream">
        <div className="jp-wrap">
          <div style={{ textAlign: 'center', marginBottom: 44 }}>
            <Reveal><span className="jp-eyebrow">⭐ Traveller reviews</span></Reveal>
            <Reveal delay={0.06}>
              <h2 className="jp-h2">What the <em>road says back</em></h2>
            </Reveal>
          </div>
          <Stagger className="jp-grid jp-grid-3" stagger={0.08}>
            {revPage.paginatedItems.map((r, i) => (
              <StaggerItem key={`${r.userName}-${i}`} className="rev">
                <div className="rev-top">
                  <span className="rev-av">{r.userName?.charAt(0)?.toUpperCase()}</span>
                  <div>
                    <div className="rev-name">{r.userName}</div>
                    {r.fromPlace && r.toPlace && (
                      <div className="rev-route">{r.fromPlace} → {r.toPlace}</div>
                    )}
                  </div>
                </div>
                <div className="rev-stars">
                  {Array.from({ length: 5 }, (_, j) => (
                    <span key={j} style={{ color: j < r.rating ? '#fbbf24' : 'rgba(255,255,255,.16)' }}>★</span>
                  ))}
                </div>
                <p className="rev-text">“{r.comment}”</p>
              </StaggerItem>
            ))}
          </Stagger>
          <Pagination
            currentPage={revPage.currentPage}
            totalPages={revPage.totalPages}
            onPageChange={revPage.setCurrentPage}
          />
        </div>
      </section>

      {/* ── final CTA ── */}
      <section className="jp-section">
        <div className="jp-wrap">
          <Reveal>
            <div className="jp-cta">
              <h2 className="jp-h2" style={{ marginTop: 0 }}>
                Your journey is <em>one tap away</em>
              </h2>
              <p className="jp-lead" style={{ margin: '0 auto 30px' }}>
                Join 50,000+ travellers who let Namma Journey do the driving.
              </p>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, justifyContent: 'center' }}>
                <Link to="/signup" className="btn-journey">
                  <span className="btn-journey-shine" />
                  🚀 Book now — it's free
                </Link>
                <Link to="/driver/login" className="btn-outline">
                  🚗 Become a driver
                </Link>
              </div>
            </div>
          </Reveal>
        </div>
      </section>

      {/* ── footer ── */}
      <footer className="jp-footer">
        <div className="jp-wrap">
          <div className="jp-footer-grid">
            <div>
              <Link to="/" className="jn-logo" style={{ marginBottom: 14 }}>
                <span className="jn-mark">🚗</span>
                <span className="jn-name">Namma <b>Journey</b></span>
              </Link>
              <p style={{ fontSize: 13, color: '#64748b', lineHeight: 1.75, marginTop: 12 }}>
                India's trusted platform for booking rides to any destination — and the long-haul
                routes that carry on from there.
              </p>
            </div>
            {[
              { title: 'The journey', links: NAV_ITEMS.map((n) => ({ label: n.label, href: n.href })) },
              { title: 'For travellers', links: [
                { label: 'Book a ride', href: '/user/bookings/new' },
                { label: 'My bookings', href: '/user/bookings' },
                { label: 'Payments', href: '/user/payments' },
                { label: 'Reviews', href: '#reviews' },
              ] },
              { title: 'For drivers', links: [
                { label: 'Join as driver', href: '/driver/login' },
                { label: 'Driver login', href: '/driver/login' },
                { label: 'Earnings', href: '/driver/dashboard' },
                { label: 'Support', href: '#about' },
              ] },
            ].map((col) => (
              <div key={col.title}>
                <h4>{col.title}</h4>
                {col.links.map((l) => (
                  <a
                    key={l.label}
                    onClick={() => {
                      if (l.href.startsWith('#')) document.querySelector(l.href)?.scrollIntoView({ behavior: 'smooth' })
                      else navigate(l.href)
                    }}
                  >
                    {l.label}
                  </a>
                ))}
              </div>
            ))}
          </div>
          <div className="jp-footer-bottom">
            <p>
              © {new Date().getFullYear()} Namma Journey. All rights reserved. · Built by{' '}
              <a
                href="https://www.linkedin.com/in/barani-dharan-16b452253/"
                target="_blank"
                rel="noopener noreferrer"
                style={{ display: 'inline', color: '#f97316', fontWeight: 600 }}
              >
                Barani T
              </a>
            </p>
            <div className="jp-social">
              {[
                { label: 'Twitter', href: 'https://twitter.com/nammajourney', icon: '🐦' },
                { label: 'Facebook', href: 'https://www.facebook.com/nammajourney', icon: '📘' },
                { label: 'Instagram', href: 'https://www.instagram.com/nammajourney', icon: '📸' },
                { label: 'YouTube', href: 'https://www.youtube.com/@nammajourney', icon: '▶️' },
              ].map((s) => (
                <a key={s.label} href={s.href} target="_blank" rel="noopener noreferrer" aria-label={s.label} title={s.label}>
                  {s.icon}
                </a>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
