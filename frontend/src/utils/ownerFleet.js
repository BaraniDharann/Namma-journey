// Which owner menu items to show for what the owner runs: cars, buses or both.

export const FLEET_TYPES = [
  ['CARS', 'Cars', 'Trips and City rides with your cars and drivers'],
  ['BUSES', 'Buses', 'Intercity bus seats sold on a seat map'],
  ['BOTH', 'Both', 'Cars and buses'],
]

// One sidebar item for everything bus; its screens (Today, Buses, Routes, Trips, Expenses,
// Earnings) are tabs inside it (components/bus/BusTabs).
export const BUS_NAV_ITEMS = [
  { path: '/owner/bus', icon: '', label: 'Bus' },
]

/** True for every bus screen, so the one Bus item stays lit on Buses, a trip, a route... */
export const isBusPath = (path) => path === '/owner/bus' || /^\/owner\/bus(es|-[a-z-]+)(\/|$)/.test(path)

// Shown whatever the fleet: the home screen and the owner's own account.
const SHARED = new Set(['/owner/dashboard', '/owner/profile'])

const hasCars = (t) => t !== 'BUSES'
const hasBuses = (t) => t === 'BUSES' || t === 'BOTH'

/**
 * The owner rail for a fleet type. Pages keep passing their own (car) list; car items stay for
 * CARS/BOTH, and for BUSES/BOTH the one Bus item goes right after Dashboard, where it is seen
 * without scrolling. A missing type means CARS.
 */
export function ownerMenu(navItems, fleetType) {
  const type = fleetType || 'CARS'
  const base = navItems.filter((n) => !isBusPath(n.path) && (SHARED.has(n.path) || hasCars(type)))
  if (!hasBuses(type)) return base
  const at = base.findIndex((n) => n.path === '/owner/dashboard')
  const cut = at < 0 ? 0 : at + 1
  return [...base.slice(0, cut), ...BUS_NAV_ITEMS, ...base.slice(cut)]
}
