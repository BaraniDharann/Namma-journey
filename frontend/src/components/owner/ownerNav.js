// One owner menu for every owner page. City-ride drivers are approved automatically and never
// shown to owners: owners see only the drivers they add themselves.
// `platform` items are City-ride screens only NJO-0001 can
// open (the API answers 403 for any other owner), so DashboardLayout hides them unless /owner/me says platform.
export const OWNER_NAV = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/vehicles', icon: '', label: 'Vehicles' },
  { path: '/owner/pricing', icon: '', label: 'Pricing' },
  { path: '/owner/cities', icon: '', label: 'Cities', platform: true },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
]

export const visibleNav = (items, platform) => items.filter((item) => !item.platform || platform)
