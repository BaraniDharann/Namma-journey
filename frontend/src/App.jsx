import React, { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { motion } from 'framer-motion'
import { AuthProvider, useAuth } from './context/AuthContext'
import { CelebrationProvider } from './components/celebrate/Celebration'

// Lazy load all pages for faster initial load
const LandingPage     = lazy(() => import('./pages/LandingPage'))
const LoginPage       = lazy(() => import('./pages/LoginPage'))
const SignupPage      = lazy(() => import('./pages/SignupPage'))
const DriverLoginPage = lazy(() => import('./pages/DriverLoginPage'))
const DriverSignupPage = lazy(() => import('./pages/DriverSignupPage'))
const OwnerLoginPage  = lazy(() => import('./pages/OwnerLoginPage'))
const OwnerSignupPage = lazy(() => import('./pages/OwnerSignupPage'))
const OwnerChangePassword = lazy(() => import('./pages/owner/OwnerChangePassword'))
const AdminLoginPage  = lazy(() => import('./pages/AdminLoginPage'))
const AdminOwners     = lazy(() => import('./pages/admin/AdminOwners'))
const AdminOwnerDetail = lazy(() => import('./pages/admin/AdminOwnerDetail'))

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
const OwnerVehicles  = lazy(() => import('./pages/owner/OwnerVehicles'))
const OwnerPricing   = lazy(() => import('./pages/owner/OwnerPricing'))
const OwnerDriverApplications = lazy(() => import('./pages/owner/OwnerDriverApplications'))
const OwnerCities    = lazy(() => import('./pages/owner/OwnerCities'))
const OwnerPayments  = lazy(() => import('./pages/owner/OwnerPayments'))
const OwnerPayouts   = lazy(() => import('./pages/owner/OwnerPayouts'))
const OwnerReviews   = lazy(() => import('./pages/owner/OwnerReviews'))
const OwnerRevenue   = lazy(() => import('./pages/owner/OwnerRevenue'))
const OwnerProfile   = lazy(() => import('./pages/owner/OwnerProfile'))
const OwnerPackages  = lazy(() => import('./pages/owner/OwnerPackages'))
const OwnerPackageBookings = lazy(() => import('./pages/owner/OwnerPackageBookings'))
const PackageDetail  = lazy(() => import('./pages/PackageDetail'))
const SharedTripPage = lazy(() => import('./pages/SharedTripPage'))
const UserPackageBookings = lazy(() => import('./pages/user/UserPackageBookings'))

// Bus module: owner screens and the admin's bus approvals
const OwnerBuses          = lazy(() => import('./pages/owner/bus/OwnerBuses'))
const OwnerBusWizard      = lazy(() => import('./pages/owner/bus/OwnerBusWizard'))
const OwnerBusRoutes      = lazy(() => import('./pages/owner/bus/OwnerBusRoutes'))
const OwnerBusRouteWizard = lazy(() => import('./pages/owner/bus/OwnerBusRouteWizard'))
const OwnerBusTrips       = lazy(() => import('./pages/owner/bus/OwnerBusTrips'))
const OwnerBusTripDetail  = lazy(() => import('./pages/owner/bus/OwnerBusTripDetail'))
const AdminBusApprovals   = lazy(() => import('./pages/admin/AdminBusApprovals'))
const AdminBusDetail      = lazy(() => import('./pages/admin/AdminBusDetail'))
const AdminBusPayouts     = lazy(() => import('./pages/admin/AdminBusPayouts'))
const OwnerBusEarnings    = lazy(() => import('./pages/owner/bus/OwnerBusEarnings'))
// Bus module: customer search, seat map and tickets
const BusSearchPage       = lazy(() => import('./pages/bus/BusSearchPage'))
const BusTripPage         = lazy(() => import('./pages/bus/BusTripPage'))
const UserBusTickets      = lazy(() => import('./pages/user/UserBusTickets'))
const UserBusTicketDetail = lazy(() => import('./pages/user/UserBusTicketDetail'))

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
  if (role === 'ROLE_OWNER_SETUP') return '/owner/change-password'
  if (role === 'ROLE_ADMIN')  return '/admin/owners'
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
        <Route path="/driver/signup" element={user ? <Navigate to="/driver/dashboard" /> : <DriverSignupPage />} />
        <Route path="/owner/login"  element={user ? <Navigate to={getDashboard(user.role)} /> : <OwnerLoginPage />} />
        <Route path="/owner/signup" element={user ? <Navigate to={getDashboard(user.role)} /> : <OwnerSignupPage />} />
        <Route path="/owner/change-password" element={<ProtectedRoute role="ROLE_OWNER_SETUP"><OwnerChangePassword /></ProtectedRoute>} />
        <Route path="/admin/login"  element={user ? <Navigate to={getDashboard(user.role)} /> : <AdminLoginPage />} />
        <Route path="/admin/owners" element={<ProtectedRoute role="ROLE_ADMIN"><AdminOwners /></ProtectedRoute>} />
        <Route path="/admin/owners/:id" element={<ProtectedRoute role="ROLE_ADMIN"><AdminOwnerDetail /></ProtectedRoute>} />

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
        <Route path="/owner/vehicles"  element={<ProtectedRoute role="ROLE_OWNER"><OwnerVehicles /></ProtectedRoute>} />
        <Route path="/owner/pricing"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerPricing /></ProtectedRoute>} />
        <Route path="/owner/driver-applications" element={<ProtectedRoute role="ROLE_OWNER"><OwnerDriverApplications /></ProtectedRoute>} />
        <Route path="/owner/cities"    element={<ProtectedRoute role="ROLE_OWNER"><OwnerCities /></ProtectedRoute>} />
        <Route path="/owner/payments"  element={<ProtectedRoute role="ROLE_OWNER"><OwnerPayments /></ProtectedRoute>} />
        <Route path="/owner/payouts"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerPayouts /></ProtectedRoute>} />
        <Route path="/owner/reviews"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerReviews /></ProtectedRoute>} />
        <Route path="/owner/revenue"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerRevenue /></ProtectedRoute>} />
        <Route path="/owner/profile"   element={<ProtectedRoute role="ROLE_OWNER"><OwnerProfile /></ProtectedRoute>} />
        <Route path="/owner/packages" element={<ProtectedRoute role="ROLE_OWNER"><OwnerPackages /></ProtectedRoute>} />
        <Route path="/owner/package-bookings" element={<ProtectedRoute role="ROLE_OWNER"><OwnerPackageBookings /></ProtectedRoute>} />
        <Route path="/owner/buses"          element={<ProtectedRoute role="ROLE_OWNER"><OwnerBuses /></ProtectedRoute>} />
        <Route path="/owner/buses/:id"      element={<ProtectedRoute role="ROLE_OWNER"><OwnerBusWizard /></ProtectedRoute>} />
        <Route path="/owner/bus-routes"     element={<ProtectedRoute role="ROLE_OWNER"><OwnerBusRoutes /></ProtectedRoute>} />
        <Route path="/owner/bus-routes/:id" element={<ProtectedRoute role="ROLE_OWNER"><OwnerBusRouteWizard /></ProtectedRoute>} />
        <Route path="/owner/bus-trips"      element={<ProtectedRoute role="ROLE_OWNER"><OwnerBusTrips /></ProtectedRoute>} />
        <Route path="/owner/bus-trips/:id"  element={<ProtectedRoute role="ROLE_OWNER"><OwnerBusTripDetail /></ProtectedRoute>} />
        <Route path="/admin/bus-approvals"     element={<ProtectedRoute role="ROLE_ADMIN"><AdminBusApprovals /></ProtectedRoute>} />
        <Route path="/admin/bus-approvals/:id" element={<ProtectedRoute role="ROLE_ADMIN"><AdminBusDetail /></ProtectedRoute>} />
        <Route path="/admin/bus-payouts"      element={<ProtectedRoute role="ROLE_ADMIN"><AdminBusPayouts /></ProtectedRoute>} />
        <Route path="/owner/bus-earnings"     element={<ProtectedRoute role="ROLE_OWNER"><OwnerBusEarnings /></ProtectedRoute>} />
        <Route path="/user/bus-tickets"       element={<ProtectedRoute role="ROLE_USER"><UserBusTickets /></ProtectedRoute>} />
        <Route path="/user/bus-tickets/:id"   element={<ProtectedRoute role="ROLE_USER"><UserBusTicketDetail /></ProtectedRoute>} />
        {/* Public: anyone can search buses and see a seat map; booking asks for a login. */}
        <Route path="/bus/search"         element={<BusSearchPage />} />
        <Route path="/bus/trip/:tripId"   element={<BusTripPage />} />

        <Route path="/packages/:id" element={<PackageDetail />} />
        {/* Public: family opens this from a shared trip link or an SOS alert, without logging in. */}
        <Route path="/trip/:token" element={<SharedTripPage />} />
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
        <CelebrationProvider>
          <AppRoutes />
        </CelebrationProvider>
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
