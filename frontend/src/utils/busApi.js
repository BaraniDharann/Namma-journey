import api from './api'
import { idempotencyHeaders } from './idempotency'

// Bus module calls. Owner screens change often and show live state, so nothing here is cached.

const OWNER = '/owner/bus'
const ADMIN = '/admin/bus'

// Uploads can be large phone photos; give them the same generous timeout as driver photos.
const upload = (url, fields) => {
  const form = new FormData()
  for (const [k, v] of Object.entries(fields)) if (v != null && v !== '') form.append(k, v)
  return api.post(url, form, { timeout: 60000 })
}

// Owner profile: which menus to show (cars, buses or both).
export const getOwnerMe = () => api.get('/owner/me', { _silent: true })
export const setFleetType = (fleetType) => api.put('/owner/fleet-type', { fleetType })

// Buses
export const getBuses = () => api.get(`${OWNER}/buses`)
export const getBus = (id) => api.get(`${OWNER}/buses/${id}`)
export const createBus = (data) => api.post(`${OWNER}/buses`, data)
export const updateBus = (id, data) => api.put(`${OWNER}/buses/${id}`, data)
export const saveBusSeats = (id, seats) => api.put(`${OWNER}/buses/${id}/seats`, { seats })
export const uploadBusDocument = (id, type, file, expiresOn) => upload(`${OWNER}/buses/${id}/documents/${type}`, { file, expiresOn })
export const uploadBusPhoto = (id, file) => upload(`${OWNER}/buses/${id}/photos`, { file })
export const deleteBusPhoto = (id, photoId) => api.delete(`${OWNER}/buses/${id}/photos/${photoId}`)
export const submitBus = (id) => api.post(`${OWNER}/buses/${id}/submit`)

// Places (towns). Search is public and silent: an empty list is the fallback.
export const searchBusPlaces = (q) => api.get('/bus/places', { params: { q }, _silent: true })
export const addBusPlace = (name, district) => api.post(`${OWNER}/places`, { name, district })

// Routes
export const getBusRoutes = () => api.get(`${OWNER}/routes`)
export const getBusRoute = (id) => api.get(`${OWNER}/routes/${id}`)
export const createBusRoute = (data) => api.post(`${OWNER}/routes`, data)
export const updateBusRoute = (id, data) => api.put(`${OWNER}/routes/${id}`, data)
export const publishBusRoute = (id) => api.post(`${OWNER}/routes/${id}/publish`)
export const pauseBusRoute = (id) => api.post(`${OWNER}/routes/${id}/pause`)

// Trips
export const getBusTrips = (from, to) => api.get(`${OWNER}/trips`, { params: { from, to } })
export const getBusTrip = (id) => api.get(`${OWNER}/trips/${id}`)
export const setTripCrew = (id, crew) => api.put(`${OWNER}/trips/${id}/crew`, crew)
export const blockTripSeats = (id, seats) => api.post(`${OWNER}/trips/${id}/blocks`, { seats })
export const unblockTripSeats = (id, seats) => api.post(`${OWNER}/trips/${id}/unblock`, { seats })
export const cancelBusTrip = (id, reason) => api.post(`${OWNER}/trips/${id}/cancel`, { reason })
export const getTripManifest = (id) => api.get(`${OWNER}/trips/${id}/manifest.csv`, { responseType: 'blob' })

// Admin
export const adminBuses = (status) => api.get(`${ADMIN}/buses`, { params: { status } })
export const adminBus = (id) => api.get(`${ADMIN}/buses/${id}`)
export const adminApproveBus = (id) => api.post(`${ADMIN}/buses/${id}/approve`)
export const adminRejectBus = (id, reason) => api.post(`${ADMIN}/buses/${id}/reject`, { reason })
export const adminSetBusCommission = (ownerId, percent) => api.put(`${ADMIN}/owners/${ownerId}/commission`, { percent })

// --- Customer (parts 2 and 3) ---------------------------------------------------------------
// The pages show their own errors, so these calls are silent (no toast from the API layer).

const silent = { _silent: true }
const keyed = (key) => ({ ...silent, headers: idempotencyHeaders(key) })

// Public: search and the seat map
export const searchBusTrips = (from, to, date) => api.get('/bus/search', { params: { from, to, date }, ...silent })
export const getBusSeatMap = (tripId, from, to) => api.get(`/bus/trips/${tripId}`, { params: { from, to }, ...silent })

// Signed-in traveller: hold, pay, tickets
export const holdBusSeats = (data, idempotencyKey) => api.post('/bus/holds', data, keyed(idempotencyKey))
export const payBusTicket = (id) => api.post(`/bus/tickets/${id}/pay`, null, silent)
export const verifyBusPayment = (id, data) => api.post(`/bus/tickets/${id}/verify`, data, silent)
export const releaseBusTicket = (id) => api.post(`/bus/tickets/${id}/release`, null, silent)
export const getBusTickets = () => api.get('/bus/tickets', silent)
export const getBusTicket = (id) => api.get(`/bus/tickets/${id}`, silent)
export const getBusCancelQuote = (id, seats) => api.get(`/bus/tickets/${id}/cancel-quote`, { params: { seats: seats.join(',') }, ...silent })
export const cancelBusSeats = (id, seats) => api.post(`/bus/tickets/${id}/cancel`, { seats }, silent)
export const reviewBusTicket = (id, rating, comment) => api.post(`/bus/tickets/${id}/review`, { rating, comment }, silent)

// Payouts
export const getOwnerBusPayouts = () => api.get(`${OWNER}/payouts`, silent)
export const adminBusPayouts = (status) => api.get(`${ADMIN}/payouts`, { params: { status }, ...silent })
export const adminMarkBusPayoutPaid = (id, utr) => api.post(`${ADMIN}/payouts/${id}/paid`, { utr }, silent)
