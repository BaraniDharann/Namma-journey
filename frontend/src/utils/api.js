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
  const token = localStorage.getItem('nj_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      const isAuthRequest = err.config?.url?.includes('/auth/')
      if (!isAuthRequest) {
        localStorage.removeItem('nj_token')
        localStorage.removeItem('nj_user')
        window.location.href = '/login'
      }
    }
    // Show user-friendly error toast (skip 401 redirects and silent requests)
    const isAuthRedirect = err.response?.status === 401 && !err.config?.url?.includes('/auth/')
    const isSilent = err.config?._silent
    if (!isAuthRedirect && !isSilent) {
      const serverMsg = err.response?.data?.error || err.response?.data?.message || ''
      const friendlyMap = {
        'Review already submitted': 'You have already submitted a review for this booking',
        'Booking not found': 'This booking could not be found',
        'Unauthorized': 'You are not authorized to perform this action',
        'Driver not found': 'Driver information is currently unavailable',
      }
      const friendly = Object.entries(friendlyMap).find(([k]) => serverMsg.includes(k))?.[1]
      const message = friendly
        || (err.response?.status === 403 ? 'Access denied. Please log in with the correct account'
          : err.response?.status === 404 ? 'The requested information was not found'
          : err.response?.status === 409 ? 'This action has already been completed'
          : err.response?.status >= 500 ? 'Our servers are experiencing issues. Please try again shortly'
          : err.code === 'ECONNABORTED' ? 'The request took too long. Please check your connection and try again'
          : !err.response ? 'Unable to reach the server. Please check your internet connection'
          : serverMsg || 'Something went wrong. Please try again')
      toast.error(message, { duration: 4000, id: `api-error-${err.config?.url}` })
    }
    return Promise.reject(err)
  }
)

// --- In-memory GET cache with TTL ---
const cache = new Map()
const CACHE_TTL = 30000 // 30 seconds

function cachedGet(url, ttl = CACHE_TTL, silent = false) {
  const now = Date.now()
  const cached = cache.get(url)
  if (cached && now - cached.time < ttl) {
    return Promise.resolve(cached.data)
  }
  return api.get(url, silent ? { _silent: true } : {}).then((res) => {
    cache.set(url, { data: res, time: now })
    return res
  })
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
export const driverBookingAction = (driverId, bookingId, action) => api.post(`/driver/${driverId}/bookings/${bookingId}/action`, { action })
export const endTrip = (driverId, bookingId) => api.post(`/driver/${driverId}/bookings/${bookingId}/end-trip`)
export const markCashReceived = (driverId, bookingId, data) => api.post(`/driver/${driverId}/bookings/${bookingId}/cash-payment`, data)
export const startTrip = (driverId, bookingId) => api.post(`/driver/${driverId}/bookings/${bookingId}/start-trip`)
export const uploadEndTripPhoto = (driverId, bookingId, photoFile) => {
  const formData = new FormData()
  formData.append('photo', photoFile)
  return api.post(`/driver/${driverId}/bookings/${bookingId}/end-trip-photo`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const getDriverLocation = (bookingId) => api.get(`/driver/location/${bookingId}`)
export const updateDriverLocation = (data) => api.post(`/driver/location/update`, data)

// Owner APIs
export const getOwnerBookings = () => cachedGet('/owner/bookings')
export const getAllReviews = () => cachedGet('/owner/reviews')
export const getPendingPayments = () => cachedGet('/owner/payments/pending')
export const verifyPayment = (paymentId) => api.post(`/owner/payments/${paymentId}/verify`)
export const assignDriver = (bookingId, driverId) => api.post(`/owner/bookings/${bookingId}/assign-driver`, { driverId })
export const setPricing = (pricePerKm, ownerId) => api.post(`/owner/pricing/set?pricePerKm=${pricePerKm}&ownerId=${ownerId}`)
export const setHourlyPricing = (pricePerHour, ownerId) => api.post(`/owner/pricing/set-hourly?pricePerHour=${pricePerHour}&ownerId=${ownerId}`)
export const getDriverTripPhoto = (bookingId) => api.get(`/owner/bookings/${bookingId}/driver-photo`)
export const getCurrentPricing = () => cachedGet('/owner/pricing/current', 60000)
export const getDailyRevenue = (date) => api.get(`/owner/revenue/daily?date=${date}`)
export const getMonthlyRevenue = (year, month) => api.get(`/owner/revenue/monthly?year=${year}&month=${month}`)
export const getYearlyRevenue = (year) => api.get(`/owner/revenue/yearly?year=${year}`)
export const createDriver = (formData) => api.post('/owner/drivers', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const getOwnerDrivers = () => cachedGet('/owner/drivers')
export const getOwnerDriverById = (driverId) => api.get(`/owner/drivers/${driverId}`)
export const getDriverProfile = (driverId) => api.get(`/driver/${driverId}/profile`)
export const toggleDriverAvailability = (driverId, status) => api.put(`/driver/${driverId}/availability`, { status })
export const updateUserProfile = (userId, data) => api.put(`/user/${userId}/profile`, data)

// Public APIs (silent — landing page has fallbacks)
export const getPublicReviews = () => cachedGet('/public/reviews', 60000, true)
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
