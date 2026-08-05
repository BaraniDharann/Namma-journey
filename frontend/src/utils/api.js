import axios from 'axios'
import toast from 'react-hot-toast'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
})

api.interceptors.request.use((config) => {
  // localStorage can throw SecurityError (storage partitioning, blocked 3rd-party storage);
  // an uncaught throw here would reject every outgoing request.
  let token = null
  try { token = localStorage.getItem('nj_token') } catch { /* treat as logged out */ }
  if (token) config.headers.Authorization = `Bearer ${token}`
  // The axios instance defaults to Content-Type: application/json. When the caller passes
  // FormData (multipart upload), that default suppresses axios's auto-boundary detection,
  // so Spring sees no parts. Drop the header for FormData and let axios + the browser pick
  // the right Content-Type (with boundary) themselves.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    if (config.headers && 'Content-Type' in config.headers) delete config.headers['Content-Type']
    if (config.headers && 'content-type' in config.headers) delete config.headers['content-type']
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      const isAuthRequest = err.config?.url?.includes('/auth/')
      if (!isAuthRequest) {
        try {
          localStorage.removeItem('nj_token')
          localStorage.removeItem('nj_user')
        } catch { /* storage unavailable — in-memory clear below still runs */ }
        // Notify AuthContext so in-memory user/token are cleared before the navigation;
        // otherwise the dashboard renders one stale frame before /login mounts.
        try { window.dispatchEvent(new Event('nj_auth_expired')) } catch { /* SSR safety */ }
        // Don't bounce the user mid-flow if they're already on a public page.
        if (!/^\/(login|signup|driver\/login|owner\/login|packages\/|$)/.test(window.location.pathname)) {
          window.location.href = '/login'
        }
      }
    }
    // Show user-friendly error toast (skip 401 redirects and silent requests)
    const isAuthRedirect = err.response?.status === 401 && !err.config?.url?.includes('/auth/')
    const isSilent = err.config?._silent
    if (!isAuthRedirect && !isSilent) {
      const status = err.response?.status
      const serverMsg = err.response?.data?.error || err.response?.data?.message || ''
      const friendlyMap = {
        'Review already submitted': 'You have already submitted a review for this booking',
        'Booking not found': 'This booking could not be found',
        'Unauthorized': 'You are not authorized to perform this action',
        'Driver not found': 'Driver information is currently unavailable',
        'Pricing not set': 'Pricing has not been configured yet',
      }
      const friendly = Object.entries(friendlyMap).find(([k]) => serverMsg.includes(k))?.[1]
      const message = friendly
        || (status === 400 ? (serverMsg || 'The information you entered is not valid. Please check and try again')
          : status === 403 ? 'Access denied. Please log in with the correct account'
          : status === 404 ? 'The requested information was not found'
          : status === 409 ? 'This action has already been completed'
          : status === 422 ? (serverMsg || 'Please check the details and try again')
          : status === 429 ? 'Too many requests. Please wait a moment and try again'
          : status >= 500 ? 'Our servers are temporarily unavailable. Please try again in a moment'
          : err.code === 'ECONNABORTED' ? 'The request took too long. Please check your connection and try again'
          : !err.response ? 'Unable to reach the server. Please check your internet connection'
          : serverMsg || 'Something went wrong. Please try again')
      // Coalesce repeated errors so a burst of failed calls (e.g. 12 monthly revenue requests)
      // shows a single toast instead of stacking. Use status-class as the toast id for 4xx/5xx.
      const toastId = status >= 500 ? 'api-error-server'
        : status === 401 ? 'api-error-auth'
        : status === 403 ? 'api-error-forbidden'
        : status === 404 ? 'api-error-notfound'
        : !err.response ? 'api-error-network'
        : `api-error-${err.config?.url}`
      toast.error(message, { duration: 4000, id: toastId })
    }
    return Promise.reject(err)
  }
)

// --- In-memory GET cache with TTL + in-flight dedup ---
const cache = new Map()
const inflight = new Map()
const CACHE_TTL = 30000 // 30 seconds

function cachedGet(url, ttl = CACHE_TTL, silent = false) {
  const now = Date.now()
  const cached = cache.get(url)
  if (cached && now - cached.time < ttl) {
    return Promise.resolve(cached.data)
  }
  const pending = inflight.get(url)
  if (pending) return pending
  const req = api.get(url, silent ? { _silent: true } : {})
    .then((res) => {
      cache.set(url, { data: res, time: now })
      inflight.delete(url)
      return res
    })
    .catch((err) => {
      inflight.delete(url)
      throw err
    })
  inflight.set(url, req)
  return req
}

export function invalidateCache(urlPattern) {
  for (const key of cache.keys()) {
    if (key.includes(urlPattern)) cache.delete(key)
  }
}

// --- Debounce utility ---
const debounceTimers = new Map()
function debouncedGet(url, delay = 300) {
  return new Promise((resolve, reject) => {
    const existing = debounceTimers.get(url)
    if (existing) clearTimeout(existing)
    debounceTimers.set(url, setTimeout(() => {
      debounceTimers.delete(url)
      api.get(url).then(resolve).catch(reject)
    }, delay))
  })
}

// Auth APIs
export const sendOtp = (email) => api.post('/auth/otp/send', { email })
export const userSignup = (data) => api.post('/auth/user/signup', data)
export const userLogin = (data) => api.post('/auth/user/login', data)
export const googleLogin = (token) => api.post('/auth/user/login', { loginType: 'GOOGLE', token })
export const driverLogin = (data) => api.post('/auth/driver/login', data)
export const ownerLogin = (data) => api.post('/auth/owner/login', data)
export const driverVerifyOtp = (data) => api.post('/auth/driver/verify-otp', data)
export const driverForgotPassword = (data) => api.post('/auth/driver/forgot-password', data)
export const driverRequestResetOtp = (mobile) => api.post('/auth/driver/request-reset-otp', { mobile })
export const ownerForgotPassword = (data) => api.post('/auth/owner/forgot-password', data)
export const userForgotPassword = (data) => api.post('/user/forgot-password', data)

// Place Search API (debounced to avoid flooding on keystrokes)
export const searchPlaces = (query) => debouncedGet(`/places/search?q=${encodeURIComponent(query)}`, 350)

// Route Preview API
export const getRoutePreview = (fromLat, fromLon, toLat, toLon) =>
  api.get(`/user/route-preview?fromLat=${fromLat}&fromLon=${fromLon}&toLat=${toLat}&toLon=${toLon}`)

// User APIs
export const createBooking = (userId, data) => api.post(`/user/${userId}/bookings`, data).then(res => { invalidateCache('bookings'); return res })
export const getUserBookings = (userId) => cachedGet(`/user/${userId}/bookings`)
export const getBookingById = (userId, bookingId) => api.get(`/user/${userId}/bookings/${bookingId}`)
export const updateBooking = (userId, bookingId, data) => api.put(`/user/${userId}/bookings/${bookingId}`, data).then(res => { invalidateCache('bookings'); return res })
export const deleteBooking = (userId, bookingId) => api.delete(`/user/${userId}/bookings/${bookingId}`).then(res => { invalidateCache('bookings'); return res })
export const confirmBooking = (userId, bookingId) => api.post(`/user/${userId}/bookings/${bookingId}/confirm`).then(res => { invalidateCache('bookings'); return res })
export const submitReview = (userId, bookingId, data) => api.post(`/user/${userId}/bookings/${bookingId}/reviews`, data).then(res => { invalidateCache('reviews'); return res })
export const initiatePayment = (userId, bookingId, data) => api.post(`/user/${userId}/bookings/${bookingId}/payment`, data).then(res => { invalidateCache('payments'); return res })
export const getUserPayments = (userId) => cachedGet(`/user/${userId}/payments`)
export const getTripSummary = (userId, bookingId) => api.get(`/user/${userId}/bookings/${bookingId}/summary`)

// Driver APIs
export const getDriverBookings = (driverId) => cachedGet(`/driver/${driverId}/bookings`)
export const driverBookingAction = (driverId, bookingId, action) => api.post(`/driver/${driverId}/bookings/${bookingId}/action`, { action }).then(res => { invalidateCache('bookings'); return res })
export const endTrip = (driverId, bookingId) => api.post(`/driver/${driverId}/bookings/${bookingId}/end-trip`).then(res => { invalidateCache('bookings'); invalidateCache('payments'); return res })
export const markCashReceived = (driverId, bookingId, data) => api.post(`/driver/${driverId}/bookings/${bookingId}/cash-payment`, data).then(res => { invalidateCache('bookings'); invalidateCache('payments'); return res })
export const startTrip = (driverId, bookingId) => api.post(`/driver/${driverId}/bookings/${bookingId}/start-trip`).then(res => { invalidateCache('bookings'); return res })
export const uploadEndTripPhoto = (driverId, bookingId, photoFile) => {
  const formData = new FormData()
  formData.append('photo', photoFile)
  // Don't set Content-Type — axios fills in the multipart boundary automatically when given FormData.
  return api.post(`/driver/${driverId}/bookings/${bookingId}/end-trip-photo`, formData, { timeout: 60000 })
    .then(res => { invalidateCache('bookings'); return res })
}
export const getDriverLocation = (bookingId) => api.get(`/driver/location/${bookingId}`)
export const updateDriverLocation = (data) => api.post(`/driver/location/update`, data)

// Owner APIs
export const getOwnerBookings = () => cachedGet('/owner/bookings')
export const getAllReviews = () => cachedGet('/owner/reviews')
export const getPendingPayments = () => cachedGet('/owner/payments/pending')
export const verifyPayment = (paymentId) => api.post(`/owner/payments/${paymentId}/verify`).then(res => { invalidateCache('payments'); invalidateCache('bookings'); invalidateCache('revenue'); return res })
export const assignDriver = (bookingId, driverId) => api.post(`/owner/bookings/${bookingId}/assign-driver`, { driverId }).then(res => { invalidateCache('bookings'); return res })
export const setPricing = (pricePerKm, ownerId) => api.post(`/owner/pricing/set?pricePerKm=${pricePerKm}&ownerId=${ownerId}`)
export const setHourlyPricing = (pricePerHour, ownerId) => api.post(`/owner/pricing/set-hourly?pricePerHour=${pricePerHour}&ownerId=${ownerId}`)
export const getDriverTripPhoto = (bookingId) => api.get(`/owner/bookings/${bookingId}/driver-photo`)
export const getCurrentPricing = () => cachedGet('/owner/pricing/current', 60000)
export const getDailyRevenue = (date) => api.get(`/owner/revenue/daily?date=${date}`)
export const getMonthlyRevenue = (year, month, opts = {}) => api.get(`/owner/revenue/monthly?year=${year}&month=${month}`, opts.silent ? { _silent: true } : {})
// All 12 months in one request — the chart used to fire getMonthlyRevenue twelve times per view.
export const getMonthlyRevenueSeries = (year, opts = {}) => api.get(`/owner/revenue/monthly-series?year=${year}`, opts.silent ? { _silent: true } : {})
export const getYearlyRevenue = (year) => api.get(`/owner/revenue/yearly?year=${year}`)
// Driver creation does multipart upload + DB writes + dispatches a welcome email — give it a
// longer timeout than the default 15s and let axios pick the multipart boundary itself.
export const createDriver = (formData) => api.post('/owner/drivers', formData, { timeout: 60000 })
  .then(res => { invalidateCache('drivers'); return res })
export const getOwnerDrivers = () => cachedGet('/owner/drivers')
export const getOwnerDriverById = (driverId) => api.get(`/owner/drivers/${driverId}`)
export const deleteOwnerDriver = (driverId) => api.delete(`/owner/drivers/${driverId}`)
  .then(res => { invalidateCache('drivers'); return res })
// Mints a one-time Telegram onboarding link. POST, not GET, because the response is a
// credential — whoever opens it can accept and reject that driver's trips until redeemed.
// Each call revokes the previous link, so it must never be issued speculatively on render.
export const createDriverTelegramLink = (driverId) => api.post(`/owner/drivers/${driverId}/telegram-link`)
  .then(res => { invalidateCache('drivers'); return res })
export const getDriverProfile = (driverId) => api.get(`/driver/${driverId}/profile`)
export const toggleDriverAvailability = (driverId, status) => api.put(`/driver/${driverId}/availability`, { status }).then(res => { invalidateCache('driver'); return res })
export const updateUserProfile = (userId, data) => api.put(`/user/${userId}/profile`, data).then(res => { invalidateCache('profile'); return res })

// Public APIs (silent — landing page has fallbacks)
export const getPublicReviews = () => cachedGet('/public/reviews', 5000, true)
export const getPublicPricing = () => cachedGet('/public/pricing', 60000, true)

// Travel Package APIs (Public)
export const getPublicPackages = (params = {}) => {
  const query = new URLSearchParams(params).toString()
  return cachedGet(`/public/packages${query ? '?' + query : ''}`, 60000, true)
}
export const getPublicPackageById = (id) => cachedGet(`/public/packages/${id}`, 60000)

// Travel Package APIs (User)
export const bookPackage = (userId, data) => api.post(`/user/${userId}/package-bookings`, data).then(res => { invalidateCache('package-bookings'); return res })
export const getUserPackageBookings = (userId) => cachedGet(`/user/${userId}/package-bookings`)
export const cancelPackageBooking = (userId, bookingId, reason) => api.post(`/user/${userId}/package-bookings/${bookingId}/cancel`, { reason }).then(res => { invalidateCache('package-bookings'); return res })

// Travel Package APIs (Owner)
export const getOwnerPackages = () => cachedGet('/owner/packages')
export const createPackage = (data, ownerId) => api.post(`/owner/packages?ownerId=${ownerId}`, data).then(res => { invalidateCache('packages'); return res })
export const updatePackage = (id, data) => api.put(`/owner/packages/${id}`, data).then(res => { invalidateCache('packages'); return res })
export const togglePackage = (id) => api.post(`/owner/packages/${id}/toggle`).then(res => { invalidateCache('packages'); return res })
export const deletePackage = (id) => api.delete(`/owner/packages/${id}`).then(res => { invalidateCache('packages'); return res })
export const getOwnerPackageBookings = (status) => {
  const query = status ? `?status=${status}` : ''
  return cachedGet(`/owner/package-bookings${query}`)
}
export const confirmPackageBooking = (bookingId) => api.post(`/owner/package-bookings/${bookingId}/confirm`).then(res => { invalidateCache('package-bookings'); return res })
export const cancelOwnerPackageBooking = (bookingId, reason) => api.post(`/owner/package-bookings/${bookingId}/cancel`, { reason }).then(res => { invalidateCache('package-bookings'); return res })
export const completePackageBooking = (bookingId) => api.post(`/owner/package-bookings/${bookingId}/complete`).then(res => { invalidateCache('package-bookings'); return res })

// Notification APIs
export const getNotifications = (recipientId, role) =>
  api.get(`/notifications?recipientId=${recipientId}&role=${role}`)

export const getUnreadCount = (recipientId, role) =>
  api.get(`/notifications/unread-count?recipientId=${recipientId}&role=${role}`)

export const markNotificationAsRead = (notificationId) =>
  api.put(`/notifications/${notificationId}/read`)

export const markAllNotificationsAsRead = (recipientId, role) =>
  api.put(`/notifications/read-all?recipientId=${recipientId}&role=${role}`)

export default api
