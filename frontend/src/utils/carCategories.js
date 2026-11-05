// Trips are booked and priced by car type (CarCategory on the server). City rides use dash/vehicles instead.
export const CAR_CATEGORIES = [
  { value: 'HATCHBACK', label: 'Hatchback', seats: 4 },
  { value: 'SEDAN', label: 'Sedan', seats: 4 },
  { value: 'SUV', label: 'SUV', seats: 7 },
  { value: 'TEMPO_TRAVELLER', label: 'Tempo Traveller', seats: 12 },
]

export const categoryLabel = (value) =>
  CAR_CATEGORIES.find((c) => c.value === value)?.label || value || 'Car'

/** The fare a rate gives: perHour × hours when hours are given, otherwise perKm × km. The server has the final say. */
export function estimateTripPrice(rate, { km, hours } = {}) {
  if (!rate) return 0
  const total = hours ? Number(rate.perHour) * Number(hours) : Number(rate.perKm) * Number(km || 0)
  return Number.isFinite(total) ? Math.round(total * 100) / 100 : 0
}
