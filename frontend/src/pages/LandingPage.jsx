import React, { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { getPublicPackages, getPublicReviews } from '../utils/api'
import Pagination, { usePagination } from '../components/Pagination'
import QuickBooking from '../components/journey/QuickBooking'
import MemoryAlbum from '../components/drive/MemoryAlbum'
import CarAssemblyLoader, { shouldPlayIntro } from '../components/brand/CarAssemblyLoader'
import Icon from '../components/dash/Icon'
import { fleet, tripTypes } from '../data/journeyData'
import { BIOMES } from '../landing/biomes'
import { useIsCompact, useOnScreen } from '../components/motion/primitives'

// The 3D road is its own chunk: text and booking never wait for three.js.
const RoadWorld = lazy(() => import('../components/drive/RoadWorld'))

const EASE = [0.16, 1, 0.3, 1]

const NAV_ITEMS = [
  { label: 'The drive', href: '#drive' },
  { label: 'Our cars', href: '#fleet' },
  { label: 'Packages', href: '#packages' },
  { label: 'Why us', href: '#features' },
  { label: 'Reviews', href: '#reviews' },
]

const FEATURES = [
  { icon: 'temple', title: 'Temple trips', desc: 'Dedicated routes to every major temple in India, with drivers who know the roads and the rituals.' },
  { icon: 'car', title: 'Premium cars', desc: 'AC and non-AC vehicles for every group size: hatchbacks, sedans, SUVs and tempo travellers.' },
  { icon: 'pin', title: 'Live tracking', desc: 'Follow the car on the map in real time. Share the link so family can watch the journey too.' },
  { icon: 'wallet', title: 'Clean pricing', desc: 'UPI or cash, quoted up front. No surge, no hidden tolls, no surprises at the end of the trip.' },
  { icon: 'star', title: 'Verified drivers', desc: 'Every driver is background-checked with a valid licence and Aadhaar on file before their first ride.' },
  { icon: 'shield', title: 'Safe all the way', desc: '24/7 support on every trip, an SOS button in the app, and insurance on every kilometre.' },
]

const STATS = [
  { icon: 'users', value: '50K+', label: 'Happy travellers' },
  { icon: 'car', value: '1,200+', label: 'Verified drivers' },
  { icon: 'pin', value: '500+', label: 'Destinations' },
  { icon: 'star', value: '4.9', label: 'Average rating' },
]

const STEPS = [
  { title: 'Create your account', desc: 'Sign up with email, verify the OTP and set a password. Under a minute.' },
  { title: 'Plan the route', desc: 'Pickup, destination, dates, travellers, AC or non-AC. The fare is quoted before you confirm.' },
  { title: 'Drive', desc: 'A verified driver picks you up. Track the car live and settle by UPI or cash at the end.' },
]

const DEFAULT_REVIEWS = [
  { userName: 'Rajesh Kumar', rating: 5, comment: 'Amazing service. The driver was professional and the car spotless. The Tirupati trip was unforgettable.', fromPlace: 'Chennai', toPlace: 'Tirupati' },
  { userName: 'Priya Sharma', rating: 5, comment: 'Booked for a family trip to Rameshwaram. Comfortable AC car, reasonable pricing. Will book again.', fromPlace: 'Madurai', toPlace: 'Rameshwaram' },
  { userName: 'Suresh Babu', rating: 4, comment: 'Driver was on time and knew the route well. Smooth journey to Shirdi throughout.', fromPlace: 'Pune', toPlace: 'Shirdi' },
  { userName: 'Anitha Reddy', rating: 5, comment: 'Transparent pricing, no hidden charges. Our Kedarnath trip was perfectly organised.', fromPlace: 'Delhi', toPlace: 'Kedarnath' },
  { userName: 'Mohammed Faisal', rating: 4, comment: 'Very reliable. The live tracking gave us peace of mind for the whole drive.', fromPlace: 'Hyderabad', toPlace: 'Srisailam' },
  { userName: 'Lakshmi Devi', rating: 5, comment: 'The driver was courteous and helpful, and the car was clean and well maintained.', fromPlace: 'Bangalore', toPlace: 'Mysore' },
]

const PKG_CATEGORIES = ['ALL', 'TEMPLE', 'HONEYMOON', 'ADVENTURE', 'HILL_STATION', 'BEACH', 'HERITAGE', 'PILGRIMAGE', 'FAMILY', 'STATE_SPECIAL']
const CAT_ICON = { TEMPLE: 'temple', PILGRIMAGE: 'temple', HERITAGE: 'temple', HILL_STATION: 'map', ADVENTURE: 'map', BEACH: 'map', FAMILY: 'users', HONEYMOON: 'sparkle', STATE_SPECIAL: 'star' }
const TRIP_ICON = { 'Temple & pilgrimage': 'temple', Outstation: 'route', 'Hourly rental': 'clock', 'Hill stations': 'map', 'Weddings & events': 'sparkle', 'Corporate travel': 'card' }
const catLabel = (c) => (c === 'ALL' ? 'All' : c.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' '))

function hasWebGL() {
  try {
    const c = document.createElement('canvas')
    return !!(window.WebGLRenderingContext && (c.getContext('webgl2') || c.getContext('webgl')))
  } catch {
    return false
  }
}

/* ─────────────────────────  navigation  ───────────────────────── */

function Nav({ user, onDashboard }) {
  const [stuck, setStuck] = useState(false)
  const [sheet, setSheet] = useState(false)
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
      <nav className={`ws-nav${stuck ? ' is-stuck' : ''}`}>
        <Link to="/" className="ws-logo">
          <span className="ws-logo-mark"><Icon name="car" size={18} /></span>
          <span>Namma <b>Journey</b></span>
        </Link>
        <div className="ws-nav-links">
          {NAV_ITEMS.map((item) => (
            <button key={item.href} type="button" onClick={() => go(item.href)}>{item.label}</button>
          ))}
        </div>
        <div className="ws-nav-actions">
          {user ? (
            <button type="button" className="ws-btn ws-btn-sm" onClick={onDashboard}>Dashboard</button>
          ) : (
            <>
              <Link to="/login" className="ws-nav-login">Login</Link>
              <Link to="/signup" className="ws-btn ws-btn-sm">Book now</Link>
            </>
          )}
          <button type="button" className="ws-burger" onClick={() => setSheet(true)} aria-label="Open menu"><Icon name="menu" /></button>
        </div>
      </nav>
      <AnimatePresence>
        {sheet && (
          <motion.div
            className="ws-sheet"
            initial={{ opacity: 0, clipPath: 'circle(0% at 92% 6%)' }}
            animate={{ opacity: 1, clipPath: 'circle(150% at 92% 6%)' }}
            exit={{ opacity: 0, clipPath: 'circle(0% at 92% 6%)' }}
            transition={{ duration: 0.5, ease: EASE }}
          >
            <button type="button" className="ws-sheet-x" onClick={() => setSheet(false)} aria-label="Close menu"><Icon name="x" size={22} /></button>
            {NAV_ITEMS.map((item) => (
              <button key={item.href} type="button" onClick={() => go(item.href)}>{item.label}</button>
            ))}
            <div className="ws-sheet-actions">
              {user ? (
                <button type="button" className="ws-btn" onClick={() => { setSheet(false); onDashboard() }}>Dashboard</button>
              ) : (
                <>
                  <Link to="/login" className="ws-btn ws-btn-ghost" onClick={() => setSheet(false)}>Login</Link>
                  <Link to="/signup" className="ws-btn" onClick={() => setSheet(false)}>Book now</Link>
                </>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

/* ─────────────────────────  the hero: the car on the road  ───────────────────────── */

/** Painted fallback for reduced motion / no WebGL: the valley as a still. */
function StillSky() {
  return (
    <div className="rw-still" aria-hidden="true">
      <span className="rw-still-hills" />
      <span className="rw-still-road" />
    </div>
  )
}

/** The ready prop holds the 3D world back until the intro has finished, so the loader animates on a free main thread. */
function Hero({ ready }) {
  const reduced = useReducedMotion()
  const compact = useIsCompact('(max-width: 820px)')
  const [webgl] = useState(() => hasWebGL())
  const showScene = webgl && !reduced && ready
  const host = useRef(null)
  const onScreen = useOnScreen(host, '0px')
  const [place, setPlace] = useState(0)
  const go = (id) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  // On phones the form would cover the whole road, so it follows the scene instead.
  const booking = <QuickBooking />
  return (
    <>
    <section id="drive" ref={host} className="rw-hero" aria-labelledby="rw-title">
      <div className="rw-stage">
        {showScene ? (
          <Suspense fallback={<StillSky />}>
            <RoadWorld active={onScreen} lite={compact} onPlace={setPlace} />
          </Suspense>
        ) : (
          <StillSky />
        )}
      </div>
      <div className="rw-scrim" aria-hidden="true" />
      <div className="ws-wrap rw-grid">
        <motion.div
          className="rw-copy"
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, ease: EASE, delay: 0.1 }}
        >
          <h1 id="rw-title">Leave the traffic.<br /><em>Keep the journey.</em></h1>
          <p>Verified drivers, AC or non-AC cars and fares quoted up front, to temples, hills and coastlines.</p>
          <div className="rw-ctas">
            <button type="button" className="ws-btn" onClick={() => go('book')}><Icon name="car" />Book a ride</button>
            <button type="button" className="ws-btn rw-btn-light" onClick={() => go('fleet')}>Our cars<Icon name="arrow" /></button>
          </div>
        </motion.div>
        {!compact && (
          <motion.div
            id="book"
            className="rw-book"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: EASE, delay: 0.25 }}
          >
            {booking}
          </motion.div>
        )}
      </div>
      {showScene && (
        <div className="rw-place" aria-live="polite">
          <Icon name="pin" size={15} />
          <AnimatePresence mode="wait" initial={false}>
            <motion.span key={place} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.4, ease: EASE }}>
              Now passing <b>{BIOMES[place].name}</b>
            </motion.span>
          </AnimatePresence>
        </div>
      )}
    </section>
    {compact && (
      <section id="book" className="rw-book-m" aria-label="Book a ride">
        <div className="ws-wrap">{booking}</div>
      </section>
    )}
    </>
  )
}

/* ─────────────────────────  after the drive  ───────────────────────── */

function SectionHead({ title, children }) {
  return (
    <motion.div
      className="ws-head"
      initial={{ opacity: 0, y: 22 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.4 }}
      transition={{ duration: 0.7, ease: EASE }}
    >
      <h2>{title}</h2>
      {children && <p>{children}</p>}
    </motion.div>
  )
}

const rise = {
  hidden: { opacity: 0, y: 24 },
  show: (i = 0) => ({ opacity: 1, y: 0, transition: { duration: 0.6, delay: i * 0.06, ease: EASE } }),
}

function Fleet() {
  return (
    <section id="fleet" className="ws-sec ws-cream">
      <div className="ws-wrap">
        <SectionHead title="Pick the car, we'll bring the driver">
          Every vehicle is serviced, insured and driven by someone we&apos;ve verified in person.
          Rates below are all-inclusive: fuel, tolls and driver bata.
        </SectionHead>
        <div className="ws-fleet">
          {fleet.map((car, i) => (
            <motion.article key={car.id} className={`ws-car${car.popular ? ' is-popular' : ''}`} variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} custom={i}>
              {car.popular && <span className="ws-car-tag">Most booked</span>}
              <span className="ws-car-ic"><Icon name="car" size={26} /></span>
              <h3>{car.name}</h3>
              <p className="ws-car-eg">{car.examples}</p>
              <div className="ws-car-specs"><span><b>{car.seats}</b> seats</span><span><b>{car.bags}</b> bags</span></div>
              <div className="ws-car-rate"><b>₹{car.rate}</b><span>/ km</span></div>
              <p className="ws-car-best">{car.best}</p>
            </motion.article>
          ))}
        </div>
        <div className="ws-trips">
          {tripTypes.map((t) => (
            <div key={t.label} className="ws-trip">
              <span className="ws-trip-ic"><Icon name={TRIP_ICON[t.label] || 'route'} size={18} /></span>
              <span><b>{t.label}</b><small>{t.note}</small></span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default function LandingPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [intro, setIntro] = useState(() => shouldPlayIntro())
  const [packages, setPackages] = useState([])
  const [reviews, setReviews] = useState(DEFAULT_REVIEWS)
  const [category, setCategory] = useState('ALL')

  useEffect(() => {
    getPublicPackages().then((res) => setPackages(res.data || [])).catch(() => {})
    getPublicReviews()
      .then((res) => {
        if (res.data?.length) {
          setReviews(res.data.map((r) => ({
            userName: r.userName, rating: r.rating, comment: r.feedback || r.comment,
            fromPlace: r.fromPlace || '', toPlace: r.toPlace || '',
          })))
        }
      })
      .catch(() => {})
  }, [])

  const filtered = useMemo(() => packages.filter((p) => category === 'ALL' || p.category === category), [packages, category])
  const pkgPage = usePagination(filtered, 6)
  const revPage = usePagination(reviews, 6)
  const dashboardPath = user?.role === 'ROLE_DRIVER' ? '/driver/dashboard' : user?.role === 'ROLE_OWNER' ? '/owner/dashboard' : '/user/dashboard'
  const endIntro = useCallback(() => setIntro(false), [])

  return (
    <div className="ws">
      {intro && <CarAssemblyLoader onDone={endIntro} />}
      <Nav user={user} onDashboard={() => navigate(dashboardPath)} />

      <Hero ready={!intro} />

      <MemoryAlbum />

      <section className="ws-arrive" aria-label="You've arrived">
        <div className="ws-wrap">
          <SectionHead title="You've arrived. Rested.">
            That is the whole idea: you sit back, a verified driver takes the wheel, and the trip
            becomes the holiday.
          </SectionHead>
          <div className="ws-stats">
            {STATS.map((s, i) => (
              <motion.div key={s.label} className="ws-stat" variants={rise} initial="hidden" whileInView="show" viewport={{ once: true }} custom={i}>
                <Icon name={s.icon} size={18} />
                <b>{s.value}</b>
                <span>{s.label}</span>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <Fleet />

      {packages.length > 0 && (
        <section id="packages" className="ws-sec">
          <div className="ws-wrap">
            <SectionHead title="Someone else can plan it">
              Handpicked trips for temples, honeymoons and hill stations, with food, stay and transport already sorted.
            </SectionHead>
            <div className="ws-filters" role="group" aria-label="Package category">
              {PKG_CATEGORIES.map((c) => (
                <button key={c} type="button" aria-pressed={category === c} className="ws-filter" onClick={() => { setCategory(c); pkgPage.setCurrentPage(1) }}>
                  {catLabel(c)}
                </button>
              ))}
            </div>
            <motion.div layout className="ws-pkgs">
              <AnimatePresence mode="popLayout">
                {pkgPage.paginatedItems.map((pkg, i) => (
                  <motion.div key={pkg.id} layout initial={{ opacity: 0, y: 26 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.5, delay: i * 0.05, ease: EASE }}>
                    <Link to={`/packages/${pkg.id}`} className="ws-pkg">
                      <div className="ws-pkg-img" style={pkg.imageUrl ? { backgroundImage: `url(${pkg.imageUrl})` } : undefined}>
                        {!pkg.imageUrl && <Icon name={CAT_ICON[pkg.category] || 'package'} size={42} />}
                        <span className="ws-pkg-dur">{pkg.durationDays}D / {pkg.durationNights}N</span>
                      </div>
                      <div className="ws-pkg-body">
                        <div className="ws-pkg-tags"><span>{catLabel(pkg.category || 'ALL')}</span>{pkg.state && <span>{pkg.state}</span>}</div>
                        <h3>{pkg.name}</h3>
                        <p>{pkg.description}</p>
                        <div className="ws-pkg-incl">
                          {pkg.foodIncluded && <span>Food</span>}
                          {pkg.accommodationIncluded && <span>Stay</span>}
                          {pkg.transportIncluded && <span>Transport</span>}
                          {pkg.guideIncluded && <span>Guide</span>}
                        </div>
                        {pkg.placesIncluded?.length > 0 && (
                          <div className="ws-pkg-places"><Icon name="route" size={14} />{pkg.placesIncluded.slice(0, 3).join(' → ')}{pkg.placesIncluded.length > 3 ? ` +${pkg.placesIncluded.length - 3} more` : ''}</div>
                        )}
                        <div className="ws-pkg-foot">
                          <span><b>₹{Math.round(Number(pkg.pricePerPerson) || 0).toLocaleString('en-IN')}</b> / person</span>
                          <span className="ws-pkg-go">View<Icon name="arrow" size={15} /></span>
                        </div>
                      </div>
                    </Link>
                  </motion.div>
                ))}
              </AnimatePresence>
            </motion.div>
            <Pagination currentPage={pkgPage.currentPage} totalPages={pkgPage.totalPages} onPageChange={pkgPage.setCurrentPage} />
            {filtered.length === 0 && <p className="ws-none">No packages in this category yet.</p>}
          </div>
        </section>
      )}

      <section id="features" className="ws-sec ws-teal">
        <div className="ws-wrap">
          <SectionHead title="Built for the long drive" />
          <div className="ws-feats">
            {FEATURES.map((f, i) => (
              <motion.div key={f.title} className="ws-feat" variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} custom={i}>
                <span className="ws-feat-ic"><Icon name={f.icon} size={20} /></span>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <section id="about" className="ws-sec">
        <div className="ws-wrap ws-how">
          <div>
            <SectionHead title="Three steps to the first kilometre" />
            <ol className="ws-steps">
              {STEPS.map((s, i) => (
                <motion.li key={s.title} variants={rise} initial="hidden" whileInView="show" viewport={{ once: true }} custom={i}>
                  <span className="ws-step-n">{i + 1}</span>
                  <div><h3>{s.title}</h3><p>{s.desc}</p></div>
                </motion.li>
              ))}
            </ol>
            <Link to="/signup" className="ws-btn">Get started free<Icon name="arrow" /></Link>
          </div>
          <div className="ws-how-media">
            <img src="/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg" alt="Traveller at a sunrise viewpoint" loading="lazy" decoding="async" />
            <div className="ws-how-chip"><Icon name="temple" /><div><b>Temple trip booked</b><small>Tirupati · 2 days · ₹4,500</small></div></div>
          </div>
        </div>
      </section>

      <section id="reviews" className="ws-sec ws-cream">
        <div className="ws-wrap">
          <SectionHead title="What the road says back" />
          <div className="ws-revs">
            {revPage.paginatedItems.map((r, i) => (
              <motion.figure key={`${r.userName}-${i}`} className="ws-rev" variants={rise} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} custom={i}>
                <div className="ws-rev-stars" aria-label={`${r.rating} out of 5 stars`}>
                  {Array.from({ length: 5 }, (_, j) => <Icon key={j} name="star" size={15} className={j < r.rating ? 'is-on' : ''} />)}
                </div>
                <blockquote>“{r.comment}”</blockquote>
                <figcaption>
                  <span className="ws-rev-av">{r.userName?.charAt(0)?.toUpperCase()}</span>
                  <span><b>{r.userName}</b>{r.fromPlace && r.toPlace && <small>{r.fromPlace} → {r.toPlace}</small>}</span>
                </figcaption>
              </motion.figure>
            ))}
          </div>
          <Pagination currentPage={revPage.currentPage} totalPages={revPage.totalPages} onPageChange={revPage.setCurrentPage} />
        </div>
      </section>

      <section className="ws-sec">
        <div className="ws-wrap">
          <div className="ws-cta">
            <h2>Your journey is one tap away</h2>
            <p>Join 50,000+ travellers who let Namma Journey do the driving.</p>
            <div className="ws-cta-actions">
              <Link to="/signup" className="ws-btn">Book now, it&apos;s free<Icon name="arrow" /></Link>
              <Link to="/driver/login" className="ws-btn ws-btn-light"><Icon name="car" />Become a driver</Link>
            </div>
          </div>
        </div>
      </section>

      <footer className="ws-footer">
        <div className="ws-wrap">
          <div className="ws-footer-grid">
            <div>
              <Link to="/" className="ws-logo"><span className="ws-logo-mark"><Icon name="car" size={18} /></span><span>Namma <b>Journey</b></span></Link>
              <p>India&apos;s trusted platform for booking rides to any destination, and the long-haul routes that carry on from there.</p>
            </div>
            {[
              { title: 'The journey', links: NAV_ITEMS },
              { title: 'For travellers', links: [
                { label: 'Book a ride', href: '/user/bookings/new' }, { label: 'My bookings', href: '/user/bookings' },
                { label: 'Payments', href: '/user/payments' }, { label: 'Reviews', href: '#reviews' },
              ] },
              { title: 'For drivers', links: [
                { label: 'Join as driver', href: '/driver/login' }, { label: 'Driver login', href: '/driver/login' },
                { label: 'Earnings', href: '/driver/dashboard' }, { label: 'Support', href: '#about' },
              ] },
            ].map((col) => (
              <div key={col.title}>
                <h4>{col.title}</h4>
                {col.links.map((l) => (
                  <button
                    key={l.label}
                    type="button"
                    onClick={() => (l.href.startsWith('#') ? document.querySelector(l.href)?.scrollIntoView({ behavior: 'smooth' }) : navigate(l.href))}
                  >
                    {l.label}
                  </button>
                ))}
              </div>
            ))}
          </div>
          <div className="ws-footer-bottom">
            <p>
              © {new Date().getFullYear()} Namma Journey. All rights reserved. · Built by{' '}
              <a href="https://www.linkedin.com/in/barani-dharan-16b452253/" target="_blank" rel="noopener noreferrer">Barani T</a>
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}
