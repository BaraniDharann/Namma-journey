import React, { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { motion } from 'framer-motion'
import { AuthProvider, useAuth } from './context/AuthContext'

// Lazy load all pages for faster initial load
const LandingPage     = lazy(() => import('./pages/LandingPage'))
const LoginPage       = lazy(() => import('./pages/LoginPage'))
const SignupPage      = lazy(() => import('./pages/SignupPage'))
const DriverLoginPage = lazy(() => import('./pages/DriverLoginPage'))
const OwnerLoginPage  = lazy(() => import('./pages/OwnerLoginPage'))

const UserDashboard  = lazy(() => import('./pages/user/UserDashboard'))
const UserBookings   = lazy(() => import('./pages/user/UserBookings'))
const NewBooking     = lazy(() => import('./pages/user/NewBooking'))
const UserPayments   = lazy(() => import('./pages/user/UserPayments'))
const UserProfile    = lazy(() => import('./pages/user/UserProfile'))

const DriverDashboard = lazy(() => import('./pages/driver/DriverDashboard'))
const DriverBookings  = lazy(() => import('./pages/driver/DriverBookings'))
const DriverProfile   = lazy(() => import('./pages/driver/DriverProfile'))

const OwnerDashboard = lazy(() => import('./pages/owner/OwnerDashboard'))
const OwnerBookings  = lazy(() => import('./pages/owner/OwnerBookings'))
const OwnerDrivers   = lazy(() => import('./pages/owner/OwnerDrivers'))
const OwnerPayments  = lazy(() => import('./pages/owner/OwnerPayments'))
const OwnerReviews   = lazy(() => import('./pages/owner/OwnerReviews'))
const OwnerRevenue   = lazy(() => import('./pages/owner/OwnerRevenue'))
const OwnerProfile   = lazy(() => import('./pages/owner/OwnerProfile'))
const OwnerPackages  = lazy(() => import('./pages/owner/OwnerPackages'))
const OwnerPackageBookings = lazy(() => import('./pages/owner/OwnerPackageBookings'))
const PackageDetail  = lazy(() => import('./pages/PackageDetail'))
const UserPackageBookings = lazy(() => import('./pages/user/UserPackageBookings'))

function PageLoader() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', background: '#fff' }}>
      <div style={{ textAlign: 'center' }}>
        <div
          className="spinner"
          style={{ margin: '0 auto 12px', borderColor: '#ffedd5', borderTopColor: '#f97316' }}
        />
        <p style={{ color: '#94a3b8', fontSize: 14 }}>Starting the engine…</p>
      </div>
    </div>
  )
}

/** Every route fades up on arrival — one shared entrance for the whole app. */
function PageFade({ children }) {
  const { pathname } = useLocation()
  return (
    <motion.div
      key={pathname}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
    >
      {children}
    </motion.div>
  )
}

function ProtectedRoute({ children, role }) {
  const { user, loading } = useAuth()
  if (loading) return <PageLoader />
  if (!user) return <Navigate to="/login" replace />
  if (role && user.role !== role) return <Navigate to="/" replace />
  return children
}

function getDashboard(role) {
  if (role === 'ROLE_USER')   return '/user/dashboard'
  if (role === 'ROLE_DRIVER') return '/driver/dashboard'
  if (role === 'ROLE_OWNER')  return '/owner/dashboard'
  return '/'
}

function AppRoutes() {
  const { user } = useAuth()
  return (
    <Suspense fallback={<PageLoader />}>
      <PageFade>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login"        element={user ? <Navigate to={getDashboard(user.role)} /> : <LoginPage />} />
        <Route path="/signup"       element={user ? <Navigate to="/user/dashboard" /> : <SignupPage />} />
        <Route path="/driver/login" element={user ? <Navigate to="/driver/dashboard" /> : <DriverLoginPage />} />
        <Route path="/owner/login"  element={user ? <Navigate to="/owner/dashboard" /> : <OwnerLoginPage />} />

        <Route path="/user/dashboard"    element={<ProtectedRoute role="ROLE_USER"><UserDashboard /></ProtectedRoute>} />
        <Route path="/user/bookings"     element={<ProtectedRoute role="ROLE_USER"><UserBookings /></ProtectedRoute>} />
        <Route path="/user/bookings/new" element={<ProtectedRoute role="ROLE_USER"><NewBooking /></ProtectedRoute>} />
        <Route path="/user/payments"     element={<ProtectedRoute role="ROLE_USER"><UserPayments /></ProtectedRoute>} />
        <Route path="/user/profile"      element={<ProtectedRoute role="ROLE_USER"><UserProfile /></ProtectedRoute>} />
        <Route path="/user/package-bookings" element={<ProtectedRoute role="ROLE_USER"><UserPackageBookings /></ProtectedRoute>} />

        <Route path="/driver/dashboard" element={<ProtectedRoute role="ROLE_DRIVER"><DriverDashboard /></ProtectedRoute>} />
        <Route path="/driver/bookings"  element={<ProtectedRoute role="ROLE_DRIVER"><DriverBookings /></ProtectedRoute>} />
        <Route path="/driver/profile"   element={<ProtectedRoute role="ROLE_DRIVER"><DriverProfile /></ProtectedRoute>} />

        <Route path="/owner/dashboard" element={<ProtectedRoute role="ROLE_OWNER"><OwnerDashboard /></ProtectedRoute>} />
        <Route path="/owner/bookings"  element={<ProtectedRoute role="ROLE_OWNER"><OwnerBookings /></ProtectedRoute>} />
        <Route path="/owner/drivers"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerDrivers /></ProtectedRoute>} />
        <Route path="/owner/payments"  element={<ProtectedRoute role="ROLE_OWNER"><OwnerPayments /></ProtectedRoute>} />
        <Route path="/owner/reviews"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerReviews /></ProtectedRoute>} />
        <Route path="/owner/revenue"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerRevenue /></ProtectedRoute>} />
        <Route path="/owner/profile"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerProfile /></ProtectedRoute>} />
        <Route path="/owner/packages" element={<ProtectedRoute role="ROLE_OWNER"><OwnerPackages /></ProtectedRoute>} />
        <Route path="/owner/package-bookings" element={<ProtectedRoute role="ROLE_OWNER"><OwnerPackageBookings /></ProtectedRoute>} />

        <Route path="/packages/:id" element={<PackageDetail />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </PageFade>
    </Suspense>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
        <Toaster
          position="top-right"
          toastOptions={{
            error: { style: { background: '#fef2f2', color: '#991b1b', border: '1px solid #fecaca' } },
            success: { style: { background: '#f0fdf4', color: '#166534', border: '1px solid #bbf7d0' } },
          }}
        />
      </BrowserRouter>
    </AuthProvider>
  )
}
