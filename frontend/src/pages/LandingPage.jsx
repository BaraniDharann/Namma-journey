import React, { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getPublicReviews, getPublicPackages, getPublicPricing, createBooking } from '../utils/api'
import Pagination, { usePagination } from '../components/Pagination'
import PlaceAutocomplete from '../components/PlaceAutocomplete'

const IMG = {
  temple: '/images/indian-hindu-temple-singapore.jpg',
  ancient: '/images/prasart-phimai-ancient-stone-thailand.jpg',
  garden: '/images/beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg',
  palace: '/images/palace-king-mahal-kingdom-shiva.jpg',
  backpacker: '/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg',
  mountain: '/images/city-view-from-mountain-hill (1).jpg',
  tourist: '/images/tourist-presenting-something.jpg',
  car: '/images/car image.jpeg',
}

const images = {
  hero: '/images/indian-city-buildings-scene.jpg',
  temple: '/images/indian-hindu-temple-singapore.jpg',
  palace: '/images/palace-king-mahal-kingdom-shiva.jpg',
  garden: '/images/beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg',
  mountain: '/images/city-view-from-mountain-hill (1).jpg',
  backpacker: '/images/backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg',
  ancient: '/images/prasart-phimai-ancient-stone-thailand.jpg',
  tourist: '/images/tourist-presenting-something.jpg',
  car: '/images/car image.jpeg',
  gps: '/images/GPS navigator-cuate.png'
}

const destinations = [
  { name: 'Tirupati', state: 'Andhra Pradesh', img: images.temple, tag: 'Temple' },
  { name: 'Hampi', state: 'Karnataka', img: images.ancient, tag: 'Heritage' },
  { name: 'Lodhi Garden', state: 'Delhi', img: images.garden, tag: 'Nature' },
  { name: 'Mahabalipuram', state: 'Tamil Nadu', img: images.palace, tag: 'Temple' },
  { name: 'Sunrise Point', state: 'Himachal', img: images.backpacker, tag: 'Adventure' },
  { name: 'Hill Station', state: 'Ooty', img: images.mountain, tag: 'Scenic' },
]

const features = [
  { icon: '🛕', title: 'Temple Trips', desc: 'Dedicated routes to all major temples across India with experienced drivers' },
  { icon: '🚗', title: 'Premium Cars', desc: 'AC & Non-AC vehicles for all group sizes, from sedans to SUVs' },
  { icon: '📍', title: 'Live Tracking', desc: 'Real-time GPS tracking for complete peace of mind during your journey' },
  { icon: '💳', title: 'Easy Payments', desc: 'Pay via UPI or cash — no hidden charges, transparent pricing' },
  { icon: '⭐', title: 'Verified Drivers', desc: 'All drivers are background-verified with valid license and Aadhaar' },
  { icon: '🔒', title: 'Safe & Secure', desc: 'Your safety is our priority with 24/7 support throughout the trip' },
]

const stats = [
  { value: '50K+', label: 'Happy Travellers', icon: '🧳' },
  { value: '1200+', label: 'Verified Drivers', icon: '🚗' },
  { value: '500+', label: 'Destinations', icon: '📍' },
  { value: '4.9★', label: 'Average Rating', icon: '⭐' },
]

const defaultReviews = [
  { userName: 'Rajesh Kumar', rating: 5, comment: 'Amazing service! The driver was very professional and the car was spotless. Tirupati trip was unforgettable.', fromPlace: 'Chennai', toPlace: 'Tirupati' },
  { userName: 'Priya Sharma', rating: 5, comment: 'Booked for a family trip to Rameshwaram. Very comfortable AC car, reasonable pricing. Will book again!', fromPlace: 'Madurai', toPlace: 'Rameshwaram' },
  { userName: 'Suresh Babu', rating: 4, comment: 'Good experience overall. Driver was on time and knew the route well. Smooth journey to Shirdi.', fromPlace: 'Pune', toPlace: 'Shirdi' },
  { userName: 'Anitha Reddy', rating: 5, comment: 'Best travel platform! Transparent pricing, no hidden charges. Our Kedarnath trip was perfectly organized.', fromPlace: 'Delhi', toPlace: 'Kedarnath' },
  { userName: 'Mohammed Faisal', rating: 4, comment: 'Very reliable service. The live tracking feature gave us peace of mind throughout the journey.', fromPlace: 'Hyderabad', toPlace: 'Srisailam' },
  { userName: 'Lakshmi Devi', rating: 5, comment: 'Wonderful experience! The driver was courteous and helpful. The car was clean and well-maintained.', fromPlace: 'Bangalore', toPlace: 'Mysore' },
]

// Hook to detect when elements scroll into view
function useScrollReveal() {
  const [visibleSections, setVisibleSections] = useState(new Set())
  const observers = useRef(new Map())

  const observe = (id, ref) => {
    if (!ref || observers.current.has(id)) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisibleSections(prev => new Set([...prev, id]))
          observer.disconnect()
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -50px 0px' }
    )
    observer.observe(ref)
    observers.current.set(id, observer)
  }

  return { visibleSections, observe }
}

// Animated counter component
function AnimatedCounter({ target, duration = 2000, isVisible }) {
  const [count, setCount] = useState(0)
  const numericTarget = parseInt(target.replace(/[^0-9]/g, ''))
  const suffix = target.replace(/[0-9]/g, '')

  useEffect(() => {
    if (!isVisible) return
    let start = 0
    const increment = numericTarget / (duration / 16)
    const timer = setInterval(() => {
      start += increment
      if (start >= numericTarget) {
        setCount(numericTarget)
        clearInterval(timer)
      } else {
        setCount(Math.floor(start))
      }
    }, 16)
    return () => clearInterval(timer)
  }, [isVisible, numericTarget, duration])

  return <span>{count.toLocaleString()}{suffix}</span>
}

export default function LandingPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [scrolled, setScrolled] = useState(false)
  const [activeSlide, setActiveSlide] = useState(0)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [reviews, setReviews] = useState([])
  const [packages, setPackages] = useState([])
  const [pkgCategory, setPkgCategory] = useState('ALL')
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 })
  const rotatingWords = ['Journey', 'Adventure', 'Pilgrimage', 'Escape', 'Discovery', 'Getaway', 'Expedition']
  const [wordIndex, setWordIndex] = useState(0)
  const [wordFade, setWordFade] = useState(true)
  const [qb, setQb] = useState({
    fromPlace: '', toPlace: '', fromLat: null, fromLon: null, toLat: null, toLon: null,
    fromDate: '', toDate: '', travelMembers: 1, acType: 'AC', bookingHours: 2
  })
  const [qbPricing, setQbPricing] = useState({ pricePerHour: 150 })
  const [qbLoading, setQbLoading] = useState(false)
  const [qbError, setQbError] = useState('')
  const [qbSuccess, setQbSuccess] = useState(null)
  const { visibleSections, observe } = useScrollReveal()
  const filteredPackages = packages.filter(p => pkgCategory === 'ALL' || p.category === pkgCategory)
  const { currentPage: pkgPage, totalPages: pkgTotalPages, paginatedItems: paginatedPackages, setCurrentPage: setPkgPage } = usePagination(filteredPackages, 8)
  const { currentPage: revPage, totalPages: revTotalPages, paginatedItems: paginatedReviews, setCurrentPage: setRevPage } = usePagination(reviews, 6)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    const timer = setInterval(() => setActiveSlide(p => (p + 1) % 3), 5000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    getPublicPackages().then(res => setPackages(res.data || [])).catch(() => {})
  }, [])

  useEffect(() => {
    getPublicPricing().then(res => { if (res.data) setQbPricing(res.data) }).catch(() => {})
  }, [])

  useEffect(() => {
    getPublicReviews()
      .then(res => {
        if (res.data && res.data.length > 0) {
          setReviews(res.data.map(r => ({
            userName: r.userName,
            rating: r.rating,
            comment: r.feedback || r.comment,
            fromPlace: r.fromPlace || '',
            toPlace: r.toPlace || '',
          })))
        } else {
          setReviews(defaultReviews)
        }
      })
      .catch(() => setReviews(defaultReviews))
  }, [])

  // Rotate hero highlight word
  useEffect(() => {
    const t = setInterval(() => {
      setWordFade(false)
      setTimeout(() => {
        setWordIndex(i => (i + 1) % rotatingWords.length)
        setWordFade(true)
      }, 350)
    }, 2200)
    return () => clearInterval(t)
  }, [])

  // Track mouse for parallax effect on hero
  useEffect(() => {
    const handleMouse = (e) => {
      setMousePos({ x: (e.clientX / window.innerWidth - 0.5) * 20, y: (e.clientY / window.innerHeight - 0.5) * 20 })
    }
    window.addEventListener('mousemove', handleMouse, { passive: true })
    return () => window.removeEventListener('mousemove', handleMouse)
  }, [])

  const heroSlides = [
    { title: 'Sacred Journeys', img: IMG.temple },
    { title: 'Explore Heritage', img: IMG.ancient },
    { title: 'Adventure Awaits', img: IMG.backpacker },
  ]

  const getDash = () => user?.role === 'ROLE_USER' ? '/user/dashboard' : user?.role === 'ROLE_DRIVER' ? '/driver/dashboard' : '/owner/dashboard'

  const sectionStyle = (id, delay = 0) => ({
    opacity: visibleSections.has(id) ? 1 : 0,
    transform: visibleSections.has(id) ? 'translateY(0)' : 'translateY(40px)',
    transition: `all 0.8s cubic-bezier(0.16, 1, 0.3, 1) ${delay}s`,
  })

  return (
    <div style={{ background: '#fff', minHeight: '100vh', width: '100%', overflowX: 'hidden', color: '#1e293b' }}>
      {/* Mobile Menu */}
      <div className={`mobile-menu ${mobileMenuOpen ? 'open' : ''}`}>
        <button onClick={() => setMobileMenuOpen(false)}
          style={{ position: 'absolute', top: 20, right: 24, fontSize: 28, background: 'none', border: 'none', cursor: 'pointer', color: '#1e293b' }}>✕</button>
        {['Home', 'Destinations', 'Packages', 'Features', 'Reviews', 'About'].map(item => (
          <a key={item} href={`#${item.toLowerCase()}`} onClick={() => setMobileMenuOpen(false)}
            style={{ color: '#1e293b', fontSize: 22, fontWeight: 700, fontFamily: 'Poppins', textDecoration: 'none' }}>{item}</a>
        ))}
        <div style={{ display: 'flex', gap: 12 }}>
          {user ? (
            <button onClick={() => { setMobileMenuOpen(false); navigate(getDash()) }} className="btn-primary">Dashboard</button>
          ) : (
            <><Link to="/login" className="btn-secondary" onClick={() => setMobileMenuOpen(false)}>Login</Link>
            <Link to="/signup" className="btn-primary" onClick={() => setMobileMenuOpen(false)}>Book Now</Link></>
          )}
        </div>
      </div>

      {/* Navbar */}
      <nav style={{
        position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50,
        background: scrolled ? 'rgba(255,255,255,0.97)' : 'transparent',
        borderBottom: scrolled ? '1px solid #e2e8f0' : 'none',
        backdropFilter: scrolled ? 'blur(12px)' : 'none',
        transition: 'all 0.3s ease', padding: '14px 0'
      }}>
        <div style={{ maxWidth: 1280, margin: '0 auto', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: 'linear-gradient(135deg,#f97316,#ea580c)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
            <span style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: scrolled ? '#0f172a' : '#fff' }}>Namma <span style={{ color: '#f97316' }}>Journey</span></span>
          </Link>
          <div style={{ display: 'flex', alignItems: 'center', gap: 32 }} className="nav-links-desktop">
            {['Home','Destinations','Features','Reviews','About'].map(item => (
              <a key={item} href={`#${item.toLowerCase()}`}
                style={{ fontSize: 14, fontWeight: 500, color: scrolled ? '#475569' : 'rgba(255,255,255,0.85)', textDecoration: 'none', transition: 'color 0.2s' }}
                onMouseEnter={e => e.target.style.color = '#f97316'}
                onMouseLeave={e => e.target.style.color = scrolled ? '#475569' : 'rgba(255,255,255,0.85)'}>{item}</a>
            ))}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {user ? (
              <button onClick={() => navigate(getDash())} className="btn-primary" style={{ fontSize: 14, padding: '9px 20px' }}>Dashboard</button>
            ) : (
              <>
                <Link to="/login" style={{ fontSize: 14, fontWeight: 600, color: scrolled ? '#f97316' : '#fff', textDecoration: 'none', padding: '9px 16px' }}>Login</Link>
                <Link to="/signup" className="btn-primary" style={{ fontSize: 14, padding: '9px 20px' }}>Book Now</Link>
              </>
            )}
            <button style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 22, color: scrolled ? '#1e293b' : '#fff', marginLeft: 4, display: 'none' }}
              onClick={() => setMobileMenuOpen(true)} className="nav-mobile-toggle">☰</button>
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section id="home" style={{ position: 'relative', minHeight: '100vh', display: 'flex', alignItems: 'center', overflow: 'hidden' }}>
        {heroSlides.map((slide, i) => (
          <div key={i} style={{
            position: 'absolute', inset: 0,
            opacity: activeSlide === i ? 1 : 0,
            transform: activeSlide === i ? `scale(1.05) translate(${mousePos.x * 0.3}px, ${mousePos.y * 0.3}px)` : 'scale(1.1)',
            transition: 'opacity 1.2s ease, transform 8s ease-out',
          }}>
            <img src={slide.img} alt={slide.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading={i === 0 ? 'eager' : 'lazy'} decoding="async" fetchpriority={i === 0 ? 'high' : 'low'} />
            <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(135deg, rgba(10,15,30,0.88) 0%, rgba(10,15,30,0.65) 60%, rgba(10,15,30,0.8) 100%)' }} />
          </div>
        ))}

        {/* Floating particles */}
        <div className="landing-particles">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="landing-particle" style={{
              left: `${15 + i * 15}%`,
              animationDelay: `${i * 0.8}s`,
              animationDuration: `${4 + i * 0.5}s`,
            }} />
          ))}
        </div>

        <div style={{ position: 'relative', maxWidth: 1280, margin: '0 auto', padding: '100px 24px 64px', width: '100%' }}>
          <div className="landing-hero-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 48, alignItems: 'center' }}>
            <div className="animate-fadeInUp landing-hero-content">
              <div style={{
                display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 20,
                background: 'rgba(249,115,22,0.15)', border: '1px solid rgba(249,115,22,0.3)',
                color: '#f97316', fontSize: 13, fontWeight: 600, marginBottom: 20,
                animation: 'glowPulse 3s ease-in-out infinite',
              }}>
                🛕 India's #1 Temple Travel Platform
              </div>
              <h1 style={{ fontFamily: 'Poppins', fontSize: 'clamp(2.5rem,6vw,4.5rem)', fontWeight: 900, color: '#fff', lineHeight: 1.1, marginBottom: 20 }}>
                Your Sacred<br />
                <span style={{
                  color: '#f97316',
                  backgroundImage: 'linear-gradient(90deg, #f97316, #fbbf24, #f97316)',
                  backgroundSize: '200% auto',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                  animation: 'gradientShift 3s linear infinite',
                  display: 'inline-block',
                  opacity: wordFade ? 1 : 0,
                  transform: wordFade ? 'translateY(0)' : 'translateY(8px)',
                  transition: 'opacity 0.35s ease, transform 0.35s ease',
                }} className="hero-glow-text">{rotatingWords[wordIndex]}</span><br />Awaits
              </h1>
              <p style={{ fontSize: 17, color: 'rgba(255,255,255,0.75)', lineHeight: 1.8, marginBottom: 20, maxWidth: 480 }}>
                Book AC/Non-AC cars for temple visits, devotional trips and long-distance travel across India. Verified drivers, transparent pricing.
              </p>

              <div className="hero-route-anim">
                <svg viewBox="0 0 520 70" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="routeGrad" x1="0" y1="0" x2="1" y2="0">
                      <stop offset="0%" stopColor="#f97316" />
                      <stop offset="50%" stopColor="#fbbf24" />
                      <stop offset="100%" stopColor="#f97316" />
                    </linearGradient>
                  </defs>
                  <path className="route-path" d="M 24 50 Q 130 0 260 50 T 496 50" />
                  <path className="route-progress" d="M 24 50 Q 130 0 260 50 T 496 50" strokeDasharray="8 10" style={{ animation: 'dashMove 1.2s linear infinite reverse', opacity: 0.55 }} />
                  <g className="pin" transform="translate(18, 44)">
                    <circle r="7" fill="#f97316" stroke="#fff" strokeWidth="2" />
                    <circle r="2.5" fill="#fff" />
                  </g>
                  <g className="pin pin-end" transform="translate(496, 44)">
                    <circle r="7" fill="#22c55e" stroke="#fff" strokeWidth="2" />
                    <circle r="2.5" fill="#fff" />
                  </g>
                </svg>
                <span className="car-emoji">🚗</span>
                <div className="route-labels">
                  <span>📍 Pickup</span>
                  <span style={{ color: '#fbbf24' }}>⛩️ Temple</span>
                  <span>🏁 Drop</span>
                </div>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginBottom: 40 }}>
                <Link to="/signup" className="btn-primary landing-btn-glow" style={{ fontSize: 15, padding: '13px 28px' }}>🚀 Book Your Ride</Link>
                <Link to="/driver/login" style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '13px 28px', borderRadius: 10, border: '2px solid rgba(255,255,255,0.4)', color: '#fff', fontSize: 15, fontWeight: 600, textDecoration: 'none', transition: 'all 0.3s' }}
                  onMouseEnter={e => { e.currentTarget.style.borderColor = '#f97316'; e.currentTarget.style.background = 'rgba(249,115,22,0.1)'; e.currentTarget.style.transform = 'translateY(-2px)' }}
                  onMouseLeave={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.4)'; e.currentTarget.style.background = 'transparent'; e.currentTarget.style.transform = 'translateY(0)' }}>
                  🚗 Drive With Us
                </Link>
              </div>
              <div className="landing-hero-stats" style={{ display: 'flex', flexWrap: 'wrap', gap: 28 }}>
                {stats.map(s => (
                  <div key={s.label} style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 22, fontWeight: 900, color: '#f97316' }}>{s.value}</div>
                    <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.5)', marginTop: 2 }}>{s.label}</div>
                  </div>
                ))}
              </div>
            </div>
            {/* Booking card with magnetic hover */}
            <div className="landing-booking-card" style={{
              background: 'rgba(255,255,255,0.97)', borderRadius: 20, padding: 28,
              boxShadow: '0 20px 60px rgba(0,0,0,0.25)',
              transition: 'transform 0.3s ease, box-shadow 0.3s ease',
              maxHeight: '85vh', overflowY: 'auto',
            }}
              onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-8px) scale(1.02)'; e.currentTarget.style.boxShadow = '0 30px 80px rgba(249,115,22,0.2)' }}
              onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0) scale(1)'; e.currentTarget.style.boxShadow = '0 20px 60px rgba(0,0,0,0.25)' }}
            >
              {qbSuccess ? (
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 40, marginBottom: 8 }}>🎉</div>
                  <h3 style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: '#0f172a', marginBottom: 12 }}>Booking Confirmed!</h3>
                  <div style={{ textAlign: 'left', marginBottom: 16 }}>
                    {[['From', qbSuccess.fromPlace], ['To', qbSuccess.toPlace], ['Hours', `${qbSuccess.bookingHours}h`], ['Amount', `₹${qbSuccess.totalAmount?.toLocaleString()}`]].map(([k,v]) => (
                      <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f1f5f9', fontSize: 13 }}>
                        <span style={{ color: '#64748b' }}>{k}</span>
                        <span style={{ fontWeight: 600, color: '#0F172A' }}>{v}</span>
                      </div>
                    ))}
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button onClick={() => navigate('/user/bookings')} className="btn-primary" style={{ flex: 1, padding: '10px', fontSize: 13, border: 'none', cursor: 'pointer' }}>View Bookings</button>
                    <button onClick={() => { setQbSuccess(null); setQb({ fromPlace:'', toPlace:'', fromLat:null, fromLon:null, toLat:null, toLon:null, fromDate:'', toDate:'', travelMembers:1, acType:'AC', bookingHours:2 }) }}
                      style={{ flex: 1, padding: '10px', fontSize: 13, borderRadius: 12, border: '2px solid #ffedd5', background: '#fff', color: '#f97316', fontWeight: 700, cursor: 'pointer' }}>New Booking</button>
                  </div>
                </div>
              ) : (
                <>
                  <h3 style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 18, color: '#0f172a', marginBottom: 16 }}>Quick Booking</h3>
                  {qbError && <div style={{ marginBottom: 12, padding: '10px 14px', borderRadius: 10, background: '#FEF2F2', border: '1px solid #FECACA', color: '#B91C1C', fontSize: 12, fontWeight: 600 }}>⚠️ {qbError}</div>}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {/* Route */}
                    <div>
                      <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>From (Pickup Location) *</label>
                      <PlaceAutocomplete
                        value={qb.fromPlace}
                        onChange={({ name, lat, lon }) => setQb(p => ({ ...p, fromPlace: name, fromLat: lat, fromLon: lon }))}
                        placeholder="Search your city, town or area..."
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>To (Destination) *</label>
                      <PlaceAutocomplete
                        value={qb.toPlace}
                        onChange={({ name, lat, lon }) => setQb(p => ({ ...p, toPlace: name, toLat: lat, toLon: lon }))}
                        placeholder="Search temple, city or destination..."
                      />
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 8 }}>
                        {['Tirupati Balaji', 'Shirdi Sai Baba', 'Vaishno Devi', 'Kedarnath', 'Badrinath'].map(t => (
                          <button key={t} type="button" onClick={() => setQb(p => ({ ...p, toPlace: t, toLat: null, toLon: null }))}
                            style={{ padding: '4px 10px', borderRadius: 16, fontSize: 11, fontWeight: 600, cursor: 'pointer', border: 'none', background: qb.toPlace === t ? '#f97316' : '#F1F5F9', color: qb.toPlace === t ? '#fff' : '#64748b', transition: 'all 0.15s' }}>
                            🛕 {t}
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Hours */}
                    <div>
                      <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>Select Hours *</label>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        {[1, 2, 3, 4, 5, 6, 8, 10, 12, 24].map(h => (
                          <button key={h} type="button" onClick={() => setQb(p => ({ ...p, bookingHours: h }))}
                            style={{ padding: '6px 12px', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer', border: 'none', background: qb.bookingHours === h ? 'linear-gradient(135deg,#f97316,#ea580c)' : '#f1f5f9', color: qb.bookingHours === h ? '#fff' : '#374151', transition: 'all 0.15s', minWidth: 42 }}>
                            {h}h
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Date & Members */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                      <div>
                        <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>From Date *</label>
                        <input type="date" className="input-field" value={qb.fromDate} min={new Date().toISOString().split('T')[0]} onChange={e => setQb(p => ({ ...p, fromDate: e.target.value }))} style={{ width: '100%' }} />
                      </div>
                      <div>
                        <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>To Date *</label>
                        <input type="date" className="input-field" value={qb.toDate} min={qb.fromDate || new Date().toISOString().split('T')[0]} onChange={e => setQb(p => ({ ...p, toDate: e.target.value }))} style={{ width: '100%' }} />
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                      <div>
                        <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>Members *</label>
                        <select className="input-field" value={qb.travelMembers} onChange={e => setQb(p => ({ ...p, travelMembers: Number(e.target.value) }))} style={{ width: '100%' }}>
                          {Array.from({ length: 20 }, (_, i) => i + 1).map(n => <option key={n} value={n}>{n} Person{n>1?'s':''}</option>)}
                        </select>
                      </div>
                      <div>
                        <label style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1, display: 'block', marginBottom: 6 }}>Vehicle *</label>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                          {['AC', 'NON_AC'].map(type => (
                            <button key={type} type="button" onClick={() => setQb(p => ({ ...p, acType: type }))}
                              style={{ padding: '9px', borderRadius: 10, fontSize: 12, fontWeight: 700, cursor: 'pointer', border: 'none', transition: 'all 0.15s', background: qb.acType === type ? 'linear-gradient(135deg,#f97316,#ea580c)' : '#f1f5f9', color: qb.acType === type ? '#fff' : '#374151' }}>
                              {type === 'AC' ? '❄️ AC' : '🌬️ Non-AC'}
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>

                    {/* Submit */}
                    <button
                      disabled={qbLoading}
                      onClick={async () => {
                        if (!user) {
                          navigate('/signup?redirect=/user/bookings/new')
                          return
                        }
                        if (!qb.fromLat || !qb.toLat) {
                          setQbError('Please select both locations from the dropdown suggestions.')
                          return
                        }
                        if (!qb.fromDate || !qb.toDate) {
                          setQbError('Please select both from and to dates.')
                          return
                        }
                        setQbLoading(true)
                        setQbError('')
                        try {
                          const res = await createBooking(user.userId, {
                            userName: user.name,
                            userPhone: user.mobile || '',
                            fromPlace: qb.fromPlace, toPlace: qb.toPlace,
                            fromLat: qb.fromLat, fromLon: qb.fromLon,
                            toLat: qb.toLat, toLon: qb.toLon,
                            fromDate: qb.fromDate, toDate: qb.toDate,
                            travelMembers: qb.travelMembers, acType: qb.acType,
                            bookingType: 'HOUR_BASED', bookingHours: qb.bookingHours,
                          })
                          setQbSuccess(res.data)
                        } catch (err) {
                          setQbError(err.response?.data?.message || Object.values(err.response?.data || {}).join(', ') || 'Booking failed')
                        } finally {
                          setQbLoading(false)
                        }
                      }}
                      className="btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '14px', fontSize: 15, cursor: qbLoading ? 'not-allowed' : 'pointer', border: 'none', opacity: qbLoading ? 0.7 : 1 }}>
                      {qbLoading ? 'Booking...' : '🚀 Confirm Booking'}
                    </button>
                    <p style={{ textAlign: 'center', fontSize: 12, color: '#94a3b8', margin: 0 }}>Free to sign up • No hidden charges</p>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
        <div style={{ position: 'absolute', bottom: 24, left: '50%', transform: 'translateX(-50%)', display: 'flex', gap: 8 }}>
          {heroSlides.map((_, i) => (
            <button key={i} onClick={() => setActiveSlide(i)} style={{ width: activeSlide === i ? 24 : 8, height: 8, borderRadius: 4, background: activeSlide === i ? '#f97316' : 'rgba(255,255,255,0.4)', border: 'none', cursor: 'pointer', transition: 'all 0.3s', padding: 0 }} />
          ))}
        </div>

        {/* Scroll indicator */}
        <div className="landing-scroll-indicator">
          <div className="landing-scroll-dot" />
        </div>
      </section>

      {/* Stats Bar */}
      <section style={{ background: 'linear-gradient(135deg, #0f172a, #1e1b4b)', padding: '0' }}>
        <div ref={el => observe('stats', el)} style={{ maxWidth: 1280, margin: '0 auto', padding: '40px 24px' }}>
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 24,
            ...sectionStyle('stats'),
          }}>
            {stats.map((s, i) => (
              <div key={s.label} style={{
                textAlign: 'center', padding: '20px', borderRadius: 16,
                background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
                backdropFilter: 'blur(8px)',
                transition: 'all 0.3s ease',
                transitionDelay: `${i * 0.1}s`,
              }}
                onMouseEnter={e => { e.currentTarget.style.background = 'rgba(249,115,22,0.15)'; e.currentTarget.style.borderColor = 'rgba(249,115,22,0.3)'; e.currentTarget.style.transform = 'translateY(-4px)' }}
                onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.05)'; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)'; e.currentTarget.style.transform = 'translateY(0)' }}
              >
                <div style={{ fontSize: 28, marginBottom: 8 }}>{s.icon}</div>
                <div style={{ fontSize: 28, fontWeight: 900, color: '#f97316', marginBottom: 4 }}>
                  <AnimatedCounter target={s.value} isVisible={visibleSections.has('stats')} />
                </div>
                <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.6)', fontWeight: 500 }}>{s.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* India Map Route Animation */}
      <section className="landing-section" style={{ padding: '80px 24px', background: '#ffffff' }}>
        <div ref={el => observe('india-map', el)} style={{ maxWidth: 1280, margin: '0 auto', textAlign: 'center', ...sectionStyle('india-map') }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 20, background: '#fff7ed', border: '1px solid #fed7aa', color: '#f97316', fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
            🗺️ Pan-India Coverage
          </div>
          <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.8rem)', fontWeight: 900, color: '#0f172a', marginBottom: 10 }}>
            Travel Across <span style={{ color: '#f97316' }}>Every State</span>
          </h2>
          <p style={{ color: '#64748b', fontSize: 15, marginBottom: 36 }}>From Kashmir to Kanyakumari — watch your journey unfold</p>
          <div className="india-map-marquee">
            <div className="india-map-marquee-track">
              {[
                ...[
                  { src: '/images/maps/map1.png', label: 'Ooty', tag: 'Hill Station', emoji: '⛰️' },
                  { src: '/images/maps/map2.png', label: 'Trichy', tag: 'Temple City', emoji: '🛕' },
                  { src: '/images/maps/map3.png', label: 'Kerala', tag: "God's Own Country", emoji: '🌴' },
                  { src: '/images/maps/map4.png', label: 'Tamil Nadu', tag: 'Heritage', emoji: '🏛️' },
                  { src: '/images/maps/map5.jfif', label: 'Coorg', tag: 'Coffee Hills', emoji: '☕' },
                  { src: '/images/maps/map6.jpg', label: 'South India', tag: 'Route Map', emoji: '🗺️' },
                  { src: '/images/india-3d-map.jfif', label: 'Bharat', tag: 'All India', emoji: '🇮🇳' },
                ],
                ...[
                  { src: '/images/maps/map1.png', label: 'Ooty', tag: 'Hill Station', emoji: '⛰️' },
                  { src: '/images/maps/map2.png', label: 'Trichy', tag: 'Temple City', emoji: '🛕' },
                  { src: '/images/maps/map3.png', label: 'Kerala', tag: "God's Own Country", emoji: '🌴' },
                  { src: '/images/maps/map4.png', label: 'Tamil Nadu', tag: 'Heritage', emoji: '🏛️' },
                  { src: '/images/maps/map5.jfif', label: 'Coorg', tag: 'Coffee Hills', emoji: '☕' },
                  { src: '/images/maps/map6.jpg', label: 'South India', tag: 'Route Map', emoji: '🗺️' },
                  { src: '/images/india-3d-map.jfif', label: 'Bharat', tag: 'All India', emoji: '🇮🇳' },
                ],
              ].map((item, i) => (
                <div key={i} className="india-map-card">
                  <div className="india-map-card-img" style={{ backgroundImage: `url(${item.src})` }} />
                  <div className="india-map-card-shine" />
                  <div className="india-map-card-badge">{item.emoji} {item.tag}</div>
                  <div className="india-map-card-overlay">
                    <div className="india-map-card-title">{item.label}</div>
                    <div className="india-map-card-cta">Explore Routes →</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Destinations */}
      <section id="destinations" className="landing-section" style={{ padding: '80px 24px', background: '#f8fafc' }}>
        <div style={{ maxWidth: 1280, margin: '0 auto' }}>
          <div ref={el => observe('dest-header', el)} style={{ textAlign: 'center', marginBottom: 48, ...sectionStyle('dest-header') }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 20, background: '#fff7ed', border: '1px solid #fed7aa', color: '#f97316', fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
              🗺️ Popular Destinations
            </div>
            <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.8rem)', fontWeight: 900, color: '#0f172a', marginBottom: 10 }}>
              Explore <span style={{ color: '#f97316' }}>Sacred India</span>
            </h2>
            <p style={{ color: '#64748b', fontSize: 15 }}>From ancient temples to scenic hill stations</p>
          </div>
          <div ref={el => observe('dest-grid', el)} className="landing-dest-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 20 }}>
            {destinations.map((dest, i) => (
              <div key={i} className="card landing-dest-card" style={{
                overflow: 'hidden', cursor: 'pointer',
                opacity: visibleSections.has('dest-grid') ? 1 : 0,
                transform: visibleSections.has('dest-grid') ? 'translateY(0)' : 'translateY(30px)',
                transition: `all 0.6s cubic-bezier(0.16, 1, 0.3, 1) ${i * 0.1}s`,
              }}
                onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-8px)'; e.currentTarget.style.boxShadow = '0 20px 40px rgba(0,0,0,0.12)' }}
                onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '' }}>
                <div style={{ position: 'relative', height: 180, overflow: 'hidden' }}>
                  <img src={dest.img} alt={dest.name} style={{ width: '100%', height: '100%', objectFit: 'cover', transition: 'transform 0.6s ease' }}
                    onMouseEnter={e => e.target.style.transform = 'scale(1.1)'}
                    onMouseLeave={e => e.target.style.transform = 'scale(1)'} loading="lazy" decoding="async" />
                  <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.4), transparent)', opacity: 0, transition: 'opacity 0.3s' }}
                    className="landing-dest-overlay" />
                  <span style={{ position: 'absolute', top: 10, right: 10, padding: '4px 10px', borderRadius: 20, background: '#f97316', color: '#fff', fontSize: 11, fontWeight: 700 }}>{dest.tag}</span>
                </div>
                <div style={{ padding: '16px 18px' }}>
                  <h3 style={{ fontWeight: 700, fontSize: 16, color: '#0f172a', marginBottom: 4 }}>{dest.name}</h3>
                  <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12 }}>📍 {dest.state}</p>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: 14, fontWeight: 700, color: '#f97316' }}>From ₹999</span>
                    <Link to="/signup" style={{ fontSize: 12, padding: '6px 14px', borderRadius: 8, background: '#fff7ed', color: '#f97316', border: '1px solid #fed7aa', fontWeight: 600, textDecoration: 'none', transition: 'all 0.2s' }}
                      onMouseEnter={e => { e.currentTarget.style.background = '#f97316'; e.currentTarget.style.color = '#fff' }}
                      onMouseLeave={e => { e.currentTarget.style.background = '#fff7ed'; e.currentTarget.style.color = '#f97316' }}
                    >Book Now →</Link>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Travel Packages */}
      {packages.length > 0 && (
        <section id="packages" style={{ padding: '80px 24px', background: 'linear-gradient(135deg, #faf5ff 0%, #f0f9ff 50%, #fff7ed 100%)' }}>
          <div style={{ maxWidth: 1280, margin: '0 auto' }}>
            <div ref={el => observe('pkg-header', el)} style={{ textAlign: 'center', marginBottom: 40, ...sectionStyle('pkg-header') }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 20, background: '#f5f3ff', border: '1px solid #ddd6fe', color: '#7c3aed', fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
                📦 Curated Packages
              </div>
              <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.8rem)', fontWeight: 900, color: '#0f172a', marginBottom: 10 }}>
                Explore Our <span style={{ color: '#7c3aed' }}>Travel Packages</span>
              </h2>
              <p style={{ color: '#64748b', fontSize: 15, maxWidth: 600, margin: '0 auto' }}>Handpicked packages for temples, honeymoons, hill stations & more — food, stay, transport all included</p>
            </div>

            {/* Category Filter */}
            <div ref={el => observe('pkg-filters', el)} style={{ display: 'flex', gap: 8, justifyContent: 'center', marginBottom: 32, flexWrap: 'wrap', ...sectionStyle('pkg-filters', 0.1) }}>
              {['ALL', 'TEMPLE', 'HONEYMOON', 'ADVENTURE', 'HILL_STATION', 'BEACH', 'HERITAGE', 'PILGRIMAGE', 'FAMILY', 'STATE_SPECIAL'].map(cat => (
                <button key={cat} onClick={() => { setPkgCategory(cat); setPkgPage(1); }} style={{
                  padding: '8px 18px', borderRadius: 20, border: 'none', fontWeight: 600, fontSize: 13, cursor: 'pointer',
                  background: pkgCategory === cat ? '#7c3aed' : '#fff', color: pkgCategory === cat ? '#fff' : '#64748b',
                  boxShadow: pkgCategory === cat ? '0 4px 12px rgba(124,58,237,0.3)' : '0 1px 4px rgba(0,0,0,0.06)',
                  transition: 'all 0.2s',
                }}>{cat === 'ALL' ? 'All' : cat === 'HILL_STATION' ? 'Hill Station' : cat === 'STATE_SPECIAL' ? 'State Special' : cat.charAt(0) + cat.slice(1).toLowerCase()}</button>
              ))}
            </div>

            {/* Package Cards */}
            <div ref={el => observe('pkg-grid', el)} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 24 }}>
              {paginatedPackages.map((pkg, i) => {
                  const catColors = { TEMPLE: '#f59e0b', HONEYMOON: '#ec4899', ADVENTURE: '#10b981', HILL_STATION: '#6366f1', BEACH: '#06b6d4', HERITAGE: '#8b5cf6', WILDLIFE: '#84cc16', PILGRIMAGE: '#f97316', FAMILY: '#3b82f6', STATE_SPECIAL: '#ef4444' }
                  const color = catColors[pkg.category] || '#6b7280'
                  const catEmojis = { TEMPLE: '🛕', HONEYMOON: '💑', ADVENTURE: '🏔️', HILL_STATION: '⛰️', BEACH: '🏖️', HERITAGE: '🏛️', WILDLIFE: '🦁', PILGRIMAGE: '🙏', FAMILY: '👨‍👩‍👧‍👦', STATE_SPECIAL: '🌟' }
                  return (
                    <Link to={`/packages/${pkg.id}`} key={pkg.id} style={{
                      textDecoration: 'none', color: 'inherit', borderRadius: 20, overflow: 'hidden', background: '#fff',
                      boxShadow: '0 4px 20px rgba(0,0,0,0.06)', border: '1px solid #e2e8f0',
                      opacity: visibleSections.has('pkg-grid') ? 1 : 0,
                      transform: visibleSections.has('pkg-grid') ? 'translateY(0)' : 'translateY(30px)',
                      transition: `all 0.6s cubic-bezier(0.16, 1, 0.3, 1) ${i * 0.08}s`,
                    }}
                      onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-8px)'; e.currentTarget.style.boxShadow = '0 20px 50px rgba(124,58,237,0.12)' }}
                      onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.06)' }}
                    >
                      <div style={{ height: 180, background: pkg.imageUrl ? `url(${pkg.imageUrl}) center/cover` : `linear-gradient(135deg, ${color}33, ${color}11)`, position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        {!pkg.imageUrl && <span style={{ fontSize: 56, opacity: 0.3 }}>{catEmojis[pkg.category] || '✈️'}</span>}
                        <div style={{ position: 'absolute', bottom: 12, left: 12, display: 'flex', gap: 6 }}>
                          <span style={{ padding: '4px 12px', background: color, color: '#fff', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{pkg.category?.replace('_', ' ')}</span>
                          <span style={{ padding: '4px 12px', background: 'rgba(255,255,255,0.95)', borderRadius: 20, fontSize: 11, fontWeight: 600, color: '#475569' }}>{pkg.state}</span>
                        </div>
                        <div style={{ position: 'absolute', top: 12, right: 12, padding: '6px 12px', background: 'rgba(0,0,0,0.6)', borderRadius: 20, color: '#fff', fontSize: 12, fontWeight: 700, backdropFilter: 'blur(4px)' }}>
                          {pkg.durationDays}D / {pkg.durationNights}N
                        </div>
                      </div>
                      <div style={{ padding: '16px 18px 20px' }}>
                        <h3 style={{ fontSize: 17, fontWeight: 800, color: '#1e293b', marginBottom: 6, fontFamily: 'Poppins' }}>{pkg.name}</h3>
                        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', lineHeight: 1.5 }}>{pkg.description}</p>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginBottom: 14 }}>
                          {pkg.foodIncluded && <span style={{ padding: '3px 8px', background: '#dcfce7', color: '#166534', borderRadius: 6, fontSize: 10, fontWeight: 600 }}>🍽️ Food</span>}
                          {pkg.accommodationIncluded && <span style={{ padding: '3px 8px', background: '#dbeafe', color: '#1e40af', borderRadius: 6, fontSize: 10, fontWeight: 600 }}>🏨 Stay</span>}
                          {pkg.transportIncluded && <span style={{ padding: '3px 8px', background: '#fef3c7', color: '#92400e', borderRadius: 6, fontSize: 10, fontWeight: 600 }}>🚗 Transport</span>}
                          {pkg.tollFree && <span style={{ padding: '3px 8px', background: '#f3e8ff', color: '#7c3aed', borderRadius: 6, fontSize: 10, fontWeight: 600 }}>🛣️ Toll Free</span>}
                          {pkg.guideIncluded && <span style={{ padding: '3px 8px', background: '#fce7f3', color: '#be185d', borderRadius: 6, fontSize: 10, fontWeight: 600 }}>🗣️ Guide</span>}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 14, fontSize: 12, color: '#94a3b8' }}>
                          📍 {pkg.placesIncluded?.slice(0, 3).join(' → ')}{pkg.placesIncluded?.length > 3 ? ` +${pkg.placesIncluded.length - 3} more` : ''}
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div>
                            <span style={{ fontSize: 24, fontWeight: 900, color: '#7c3aed' }}>₹{pkg.pricePerPerson?.toLocaleString()}</span>
                            <span style={{ fontSize: 12, color: '#94a3b8', marginLeft: 4 }}>/ person</span>
                          </div>
                          <span style={{ padding: '8px 20px', background: `linear-gradient(135deg, ${color}, ${color}cc)`, color: '#fff', borderRadius: 12, fontSize: 13, fontWeight: 700 }}>View Details →</span>
                        </div>
                      </div>
                    </Link>
                  )
                })}
            </div>
            <Pagination currentPage={pkgPage} totalPages={pkgTotalPages} onPageChange={setPkgPage} />
            {filteredPackages.length === 0 && (
              <div style={{ textAlign: 'center', padding: 40, color: '#94a3b8' }}>No packages in this category yet</div>
            )}
          </div>
        </section>
      )}

      {/* Features */}
      <section id="features" className="landing-section" style={{ padding: '80px 24px', background: '#fff' }}>
        <div style={{ maxWidth: 1280, margin: '0 auto' }}>
          <div ref={el => observe('feat-header', el)} style={{ textAlign: 'center', marginBottom: 48, ...sectionStyle('feat-header') }}>
            <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.8rem)', fontWeight: 900, color: '#0f172a', marginBottom: 10 }}>
              Why Choose <span style={{ color: '#f97316' }}>Namma Journey</span>
            </h2>
            <p style={{ color: '#64748b', fontSize: 15 }}>Everything you need for a perfect travel experience</p>
          </div>
          <div ref={el => observe('feat-grid', el)} className="landing-feat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 20 }}>
            {features.map((f, i) => (
              <div key={i} className="card" style={{
                padding: 24, cursor: 'default', position: 'relative', overflow: 'hidden',
                opacity: visibleSections.has('feat-grid') ? 1 : 0,
                transform: visibleSections.has('feat-grid') ? 'translateY(0) scale(1)' : 'translateY(20px) scale(0.95)',
                transition: `all 0.5s cubic-bezier(0.16, 1, 0.3, 1) ${i * 0.08}s`,
              }}
                onMouseEnter={e => { e.currentTarget.style.borderColor = '#fed7aa'; e.currentTarget.style.boxShadow = '0 12px 40px rgba(249,115,22,0.12)'; e.currentTarget.style.transform = 'translateY(-6px) scale(1.02)' }}
                onMouseLeave={e => { e.currentTarget.style.borderColor = '#e2e8f0'; e.currentTarget.style.boxShadow = 'none'; e.currentTarget.style.transform = 'translateY(0) scale(1)' }}>
                <div style={{
                  position: 'absolute', top: -20, right: -20, width: 80, height: 80, borderRadius: '50%',
                  background: 'radial-gradient(circle, rgba(249,115,22,0.08) 0%, transparent 70%)',
                  transition: 'all 0.4s ease',
                }} />
                <div style={{
                  fontSize: 36, marginBottom: 14,
                  display: 'inline-block',
                  transition: 'transform 0.3s ease',
                }}>{f.icon}</div>
                <h3 style={{ fontWeight: 700, fontSize: 16, color: '#0f172a', marginBottom: 8 }}>{f.title}</h3>
                <p style={{ fontSize: 14, color: '#64748b', lineHeight: 1.7 }}>{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* User Reviews */}
      <section id="reviews" className="landing-section" style={{ padding: '80px 24px', background: '#f8fafc' }}>
        <div style={{ maxWidth: 1280, margin: '0 auto' }}>
          <div ref={el => observe('rev-header', el)} style={{ textAlign: 'center', marginBottom: 48, ...sectionStyle('rev-header') }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 20, background: '#fff7ed', border: '1px solid #fed7aa', color: '#f97316', fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
              ⭐ Traveller Reviews
            </div>
            <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.8rem)', fontWeight: 900, color: '#0f172a', marginBottom: 10 }}>
              What Our <span style={{ color: '#f97316' }}>Travellers Say</span>
            </h2>
            <p style={{ color: '#64748b', fontSize: 15 }}>Real experiences from real travellers across India</p>
          </div>
          <div ref={el => observe('rev-grid', el)} className="landing-rev-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 20 }}>
            {paginatedReviews.map((review, i) => (
              <div key={i} className="card" style={{
                padding: 24, display: 'flex', flexDirection: 'column', gap: 14,
                opacity: visibleSections.has('rev-grid') ? 1 : 0,
                transform: visibleSections.has('rev-grid') ? 'translateY(0)' : 'translateY(30px)',
                transition: `all 0.6s cubic-bezier(0.16, 1, 0.3, 1) ${i * 0.1}s`,
              }}
                onMouseEnter={e => { e.currentTarget.style.borderColor = '#fed7aa'; e.currentTarget.style.boxShadow = '0 12px 40px rgba(249,115,22,0.1)'; e.currentTarget.style.transform = 'translateY(-4px)' }}
                onMouseLeave={e => { e.currentTarget.style.borderColor = '#e2e8f0'; e.currentTarget.style.boxShadow = 'none'; e.currentTarget.style.transform = 'translateY(0)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{ width: 44, height: 44, borderRadius: '50%', background: 'linear-gradient(135deg,#f97316,#ea580c)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 800, fontSize: 16, boxShadow: '0 4px 12px rgba(249,115,22,0.3)' }}>
                    {review.userName?.charAt(0)?.toUpperCase()}
                  </div>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: '#0f172a' }}>{review.userName}</div>
                    {(review.fromPlace && review.toPlace) && (
                      <div style={{ fontSize: 12, color: '#64748b' }}>
                        {review.fromPlace} → {review.toPlace}
                      </div>
                    )}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 2 }}>
                  {Array.from({ length: 5 }, (_, j) => (
                    <span key={j} style={{ fontSize: 16, color: j < review.rating ? '#f59e0b' : '#e2e8f0', transition: 'transform 0.2s', display: 'inline-block' }}
                      onMouseEnter={e => e.target.style.transform = 'scale(1.3)'}
                      onMouseLeave={e => e.target.style.transform = 'scale(1)'}>★</span>
                  ))}
                </div>
                <p style={{ fontSize: 14, color: '#475569', lineHeight: 1.7, flex: 1 }}>"{review.comment}"</p>
              </div>
            ))}
          </div>
          <Pagination currentPage={revPage} totalPages={revTotalPages} onPageChange={setRevPage} />
        </div>
      </section>

      {/* How it works */}
      <section id="about" className="landing-section" style={{ padding: '80px 24px', background: '#f8fafc' }}>
        <div style={{ maxWidth: 1280, margin: '0 auto' }}>
          <div ref={el => observe('howit', el)} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 48, alignItems: 'center' }}>
            <div style={sectionStyle('howit')}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 20, background: '#fff7ed', border: '1px solid #fed7aa', color: '#f97316', fontSize: 13, fontWeight: 600, marginBottom: 16 }}>
                📱 How It Works
              </div>
              <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.5rem)', fontWeight: 900, color: '#0f172a', marginBottom: 32 }}>
                Book in <span style={{ color: '#f97316' }}>3 Simple Steps</span>
              </h2>
              {[
                { step: '01', title: 'Create Account', desc: 'Sign up with your email, verify OTP and set your password in seconds' },
                { step: '02', title: 'Book Your Trip', desc: 'Enter pickup, destination, date and number of travellers. Choose AC or Non-AC' },
                { step: '03', title: 'Enjoy the Journey', desc: 'Our verified driver picks you up. Pay via UPI or cash after the trip' },
              ].map((item, i) => (
                <div key={i} style={{
                  display: 'flex', gap: 16, marginBottom: 28,
                  opacity: visibleSections.has('howit') ? 1 : 0,
                  transform: visibleSections.has('howit') ? 'translateX(0)' : 'translateX(-30px)',
                  transition: `all 0.6s cubic-bezier(0.16, 1, 0.3, 1) ${0.2 + i * 0.15}s`,
                }}>
                  <div style={{
                    flexShrink: 0, width: 44, height: 44, borderRadius: 12,
                    background: 'linear-gradient(135deg,#f97316,#ea580c)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#fff', fontWeight: 900, fontSize: 13,
                    boxShadow: '0 4px 14px rgba(249,115,22,0.3)',
                  }}>{item.step}</div>
                  <div>
                    <h4 style={{ fontWeight: 700, color: '#0f172a', marginBottom: 4 }}>{item.title}</h4>
                    <p style={{ fontSize: 14, color: '#64748b', lineHeight: 1.6 }}>{item.desc}</p>
                  </div>
                </div>
              ))}
              <Link to="/signup" className="btn-primary landing-btn-glow" style={{ padding: '13px 28px', fontSize: 15 }}>Get Started Free →</Link>
            </div>
            <div style={{
              position: 'relative',
              opacity: visibleSections.has('howit') ? 1 : 0,
              transform: visibleSections.has('howit') ? 'translateX(0) scale(1)' : 'translateX(30px) scale(0.95)',
              transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.3s',
            }}>
              <div style={{ borderRadius: 20, overflow: 'hidden', border: '1px solid #e2e8f0' }}>
                <img src={IMG.tourist} alt="Tourist" style={{ width: '100%', height: 400, objectFit: 'cover', objectPosition: 'top center', display: 'block', transition: 'transform 0.6s ease' }}
                  onMouseEnter={e => e.target.style.transform = 'scale(1.05)'}
                  onMouseLeave={e => e.target.style.transform = 'scale(1)'}
                  loading="lazy" decoding="async" />
              </div>
              <div className="animate-float" style={{ position: 'absolute', bottom: -16, left: 16, background: '#fff', borderRadius: 14, padding: '12px 16px', boxShadow: '0 8px 32px rgba(0,0,0,0.12)', display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 24 }}>🛕</span>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 13, color: '#0f172a' }}>Temple Trip Booked!</div>
                  <div style={{ fontSize: 12, color: '#f97316' }}>Tirupati • 2 days • ₹4,500</div>
                </div>
              </div>
              <div className="animate-float" style={{ position: 'absolute', top: -16, right: 16, background: '#fff', borderRadius: 14, padding: '10px 16px', boxShadow: '0 8px 32px rgba(0,0,0,0.12)', textAlign: 'center', animationDelay: '1s' }}>
                <div style={{ fontSize: 18, fontWeight: 900, color: '#f97316' }}>4.9★</div>
                <div style={{ fontSize: 11, color: '#64748b' }}>50K+ Reviews</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section style={{ padding: '80px 24px', background: '#fff' }}>
        <div ref={el => observe('cta', el)} style={{ maxWidth: 860, margin: '0 auto', textAlign: 'center', ...sectionStyle('cta') }}>
          <div className="landing-cta-box" style={{ borderRadius: 24, padding: '56px 40px', background: 'linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%)', position: 'relative', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', top: -40, right: -40, width: 200, height: 200, borderRadius: '50%', background: 'rgba(249,115,22,0.15)', filter: 'blur(40px)', animation: 'float 4s ease-in-out infinite' }} />
            <div style={{ position: 'absolute', bottom: -30, left: -30, width: 150, height: 150, borderRadius: '50%', background: 'rgba(59,130,246,0.1)', filter: 'blur(40px)', animation: 'float 5s ease-in-out infinite reverse' }} />
            <div style={{ position: 'relative' }}>
              <h2 style={{ fontFamily: 'Poppins', fontSize: 'clamp(1.8rem,4vw,2.8rem)', fontWeight: 900, color: '#fff', marginBottom: 12 }}>
                Ready for Your <span style={{ color: '#f97316' }}>Sacred Journey?</span>
              </h2>
              <p style={{ fontSize: 16, color: 'rgba(255,255,255,0.7)', marginBottom: 32 }}>Join 50,000+ travellers who trust Namma Journey</p>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, justifyContent: 'center' }}>
                <Link to="/signup" className="btn-primary landing-btn-glow" style={{ padding: '13px 32px', fontSize: 15 }}>🚀 Book Now — It's Free</Link>
                <Link to="/driver/login" style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '13px 32px', borderRadius: 10, border: '2px solid rgba(255,255,255,0.3)', color: '#fff', fontSize: 15, fontWeight: 600, textDecoration: 'none', transition: 'all 0.3s' }}
                  onMouseEnter={e => { e.currentTarget.style.borderColor = '#f97316'; e.currentTarget.style.background = 'rgba(249,115,22,0.15)' }}
                  onMouseLeave={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.3)'; e.currentTarget.style.background = 'transparent' }}
                >🚗 Become a Driver</Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer style={{ padding: '48px 24px 32px', background: '#0f172a', color: '#fff' }}>
        <div style={{ maxWidth: 1280, margin: '0 auto' }}>
          <div className="landing-footer-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 32, marginBottom: 40 }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: 'linear-gradient(135deg,#f97316,#ea580c)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>🚗</div>
                <span style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 16 }}>Namma Journey</span>
              </div>
              <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.5)', lineHeight: 1.7 }}>India's trusted platform for temple visits and devotional travel.</p>
            </div>
            {[
              { title: 'Quick Links', links: ['Home', 'Destinations', 'Packages', 'Features', 'Reviews', 'About'] },
              { title: 'For Users', links: ['Book a Ride', 'My Bookings', 'Payments', 'Reviews'] },
              { title: 'For Drivers', links: ['Join as Driver', 'Driver Login', 'Earnings', 'Support'] },
            ].map(col => (
              <div key={col.title}>
                <h4 style={{ fontWeight: 700, fontSize: 14, marginBottom: 14, color: '#fff' }}>{col.title}</h4>
                <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {col.links.map(link => (
                    <li key={link}><a href="#" style={{ fontSize: 13, color: 'rgba(255,255,255,0.5)', textDecoration: 'none', transition: 'color 0.2s' }}
                      onMouseEnter={e => e.target.style.color = '#f97316'}
                      onMouseLeave={e => e.target.style.color = 'rgba(255,255,255,0.5)'}>{link}</a></li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="landing-footer-bottom" style={{ borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: 24, display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.4)' }}>© 2024 Namma Journey. All rights reserved.</p>
            <div style={{ display: 'flex', gap: 8 }}>
              {['🐦', '📘', '📸', '▶️'].map((icon, i) => (
                <div key={i} style={{ width: 34, height: 34, borderRadius: '50%', background: 'rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', fontSize: 14, transition: 'all 0.3s' }}
                  onMouseEnter={e => { e.currentTarget.style.background = 'rgba(249,115,22,0.3)'; e.currentTarget.style.transform = 'translateY(-2px)' }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; e.currentTarget.style.transform = 'translateY(0)' }}>{icon}</div>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
