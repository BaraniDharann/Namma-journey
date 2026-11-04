// Which owner menu items to show for what the owner runs: cars, buses or both.

export const FLEET_TYPES = [
  ['CARS', 'Cars', 'Trips and City rides with your cars and drivers'],
  ['BUSES', 'Buses', 'Intercity bus seats sold on a seat map'],
  ['BOTH', 'Both', 'Cars and buses'],
]

export const BUS_NAV_ITEMS = [
  { path: '/owner/buses', icon: '', label: 'Buses' },
  { path: '/owner/bus-routes', icon: '', label: 'Bus routes' },
  { path: '/owner/bus-trips', icon: '', label: 'Bus trips' },
  { path: '/owner/bus-earnings', icon: '', label: 'Bus earnings' },
]

// Shown whatever the fleet: the home screen and the owner's own account.
const SHARED = new Set(['/owner/dashboard', '/owner/profile'])

const hasCars = (t) => t !== 'BUSES'
const hasBuses = (t) => t === 'BUSES' || t === 'BOTH'

/**
 * The owner rail for a fleet type. Pages keep passing their own (car) list; car items stay for
 * CARS/BOTH, bus items go in just before Profile for BUSES/BOTH. A missing type means CARS.
 */
export function ownerMenu(navItems, fleetType) {
  const type = fleetType || 'CARS'
  const isBus = (n) => BUS_NAV_ITEMS.some((b) => b.path === n.path)
  const base = navItems.filter((n) => !isBus(n) && (SHARED.has(n.path) || hasCars(type)))
  if (!hasBuses(type)) return base
  const at = base.findIndex((n) => n.path === '/owner/profile')
  const cut = at < 0 ? base.length : at
  return [...base.slice(0, cut), ...BUS_NAV_ITEMS, ...base.slice(cut)]
}
