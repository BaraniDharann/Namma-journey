// The traveller rail. Each user page passes its own list; the bus tickets item is added here so
// those pages need no change. It goes just before Profile.

export const BUS_TICKETS_ITEM = { path: '/user/bus-tickets', icon: '', label: 'Bus tickets' }

export function userMenu(navItems) {
  const items = navItems || []
  if (items.some((n) => n.path === BUS_TICKETS_ITEM.path)) return items
  const at = items.findIndex((n) => n.path === '/user/profile')
  const cut = at < 0 ? items.length : at
  return [...items.slice(0, cut), BUS_TICKETS_ITEM, ...items.slice(cut)]
}

/** The rail for the bus pages (same items as the other traveller pages). */
export const userNavItems = userMenu([
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
])
