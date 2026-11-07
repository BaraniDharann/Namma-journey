import React, { useState, useEffect, useLayoutEffect, useRef, useCallback } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getLatestNotifications, getUnreadCountCached, invalidateCache, markNotificationAsRead, markAllNotificationsAsRead } from '../utils/api'
import useOwnerMe, { clearOwnerMe } from '../hooks/useOwnerMe'
import { visibleNav } from './owner/ownerNav'
import { createCountPoller } from './notificationPoll'
import Icon, { navIcon, navTint } from './dash/Icon'
import { TipLayer } from './dash/ui'
import ApplicationGate from './driver/ApplicationGate'
import TelegramGate from './driver/TelegramGate'
import DriverDispatch from './driver/DriverDispatch'
import useOwnerFleet from '../hooks/useOwnerFleet'
import { ownerMenu, isBusPath } from '../utils/ownerFleet'
import { userMenu } from '../utils/userMenu'

function timeAgo(dateStr) {
  const now = new Date()
  const date = new Date(dateStr)
  const seconds = Math.floor((now - date) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} hour${hours > 1 ? 's' : ''} ago`
  const days = Math.floor(hours / 24)
  return `${days} day${days > 1 ? 's' : ''} ago`
}

const BOOKING_ROUTES = {
  ROLE_USER: '/user/bookings',
  ROLE_DRIVER: '/driver/bookings',
  ROLE_OWNER: '/owner/bookings',
}

const ROLE_LABEL = { ROLE_USER: 'Traveller', ROLE_DRIVER: 'Driver', ROLE_OWNER: 'Owner', ROLE_ADMIN: 'Admin' }

// Every page renders its own copy of this shell, so a sidebar click mounts a fresh one. These
// carry the bits a fresh shell would otherwise reset (and visibly blink) across that remount.
let lastUnread = 0
let lastNavScroll = 0

/**
 * The Postcard shell every owner, driver and traveller page renders inside: teal rail with
 * line icons (mapped from each page's routes, so pages keep passing their nav items as is),
 * a top bar with notifications, and a drawer on narrow screens.
 */
export default function DashboardLayout({ children, navItems: pageNavItems, role }) {
  const { logout, user } = useAuth()
  // Owners see car items, bus items or both, by what they run (chosen on their Profile page).
  const fleetType = useOwnerFleet(role === 'ROLE_OWNER' ? (user?.userId ?? 'owner') : null)
  const location = useLocation()
  const navigate = useNavigate()
  const [railOpen, setRailOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCountState] = useState(lastUnread)
  const setUnreadCount = useCallback((n) => { lastUnread = n; setUnreadCountState(n) }, [])
  const navRef = useRef(null)
  // Put the sidebar back where it was scrolled to, before paint, so long menus don't jump to the top.
  useLayoutEffect(() => {
    const nav = navRef.current
    if (!nav) return undefined
    nav.scrollTop = lastNavScroll
    const remember = () => { lastNavScroll = nav.scrollTop }
    nav.addEventListener('scroll', remember, { passive: true })
    return () => nav.removeEventListener('scroll', remember)
  }, [])
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef(null)

  // Platform screens (City rides, independent drivers) are for NJO-0001 only; other owners never see them.
  const owner = useOwnerMe(role === 'ROLE_OWNER')
  const navItems = role === 'ROLE_OWNER'
    ? ownerMenu(visibleNav(pageNavItems, !!owner?.platform), fleetType)
    : role === 'ROLE_USER' ? userMenu(pageNavItems) : pageNavItems

  const roleLabel = ROLE_LABEL[role] || 'Traveller'
  const displayName = user?.name || user?.email || roleLabel
  // The one Bus item stands for every bus screen (Buses, a trip, a route...), so it stays lit there.
  const isOn = (path) => location.pathname === path || (path === '/owner/bus' && isBusPath(location.pathname))
  const current = navItems.find((n) => isOn(n.path))

  const handleLogout = () => {
    lastUnread = 0
    lastNavScroll = 0
    clearOwnerMe()
    logout()
    navigate('/')
  }

  const userRole = user?.role
  // Admins have no notification feed; their alerts arrive by email.
  const recipientId = !user?.userId || userRole === 'ROLE_ADMIN' ? null
    : String(user.userId)
  const openRef = useRef(false)
  useEffect(() => { openRef.current = dropdownOpen }, [dropdownOpen])

  // The list is only fetched when someone is looking at it: on opening the panel, or when the
  // count moves while it is open. Polling it every 10 s was the biggest steady database load.
  const loadList = useCallback(async () => {
    if (!recipientId || !userRole) return
    try {
      const res = await getLatestNotifications(recipientId, userRole)
      setNotifications(res.data || [])
    } catch { /* silent: the bell just keeps its last list */ }
  }, [recipientId, userRole])

  useEffect(() => {
    if (!recipientId || !userRole) return undefined
    const poller = createCountPoller({
      fetchCount: () => getUnreadCountCached(recipientId, userRole).then((res) => res.data?.count ?? res.data ?? 0),
      onCount: (count, previous) => {
        setUnreadCount(count)
        if (previous !== null && count !== previous && openRef.current) loadList()
      },
    })
    poller.start()
    return () => poller.stop()
  }, [recipientId, userRole, loadList, setUnreadCount])

  const toggleDropdown = () => {
    if (!dropdownOpen) loadList()
    setDropdownOpen(!dropdownOpen)
  }

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) setDropdownOpen(false)
    }
    const handleKey = (e) => {
      if (e.key === 'Escape') { setDropdownOpen(false); setRailOpen(false) }
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKey)
    }
  }, [])

  // A route change from inside the drawer should close it.
  useEffect(() => { setRailOpen(false) }, [location.pathname])

  const handleNotificationClick = async (notif) => {
    if (!notif.read) {
      try {
        await markNotificationAsRead(notif.id)
        invalidateCache('/notifications')
        setNotifications((prev) => prev.map((n) => (n.id === notif.id ? { ...n, read: true } : n)))
        setUnreadCount((prev) => Math.max(0, prev - 1))
      } catch { /* already surfaced by the api error toast */ }
    }
    if (notif.bookingId && BOOKING_ROUTES[user?.role]) {
      setDropdownOpen(false)
      navigate(BOOKING_ROUTES[user.role])
    }
  }

  const handleMarkAllRead = async () => {
    if (!recipientId || !userRole) return
    try {
      await markAllNotificationsAsRead(recipientId, userRole)
      invalidateCache('/notifications')
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
      setUnreadCount(0)
    } catch { /* already surfaced by the api error toast */ }
  }

  return (
    <div className="pc pc-app" data-role={role}>
      {railOpen && <div className="pc-scrim" onClick={() => setRailOpen(false)} aria-hidden="true" />}

      <aside className={`pc-rail${railOpen ? ' is-open' : ''}`} aria-label="Main">
        <Link to="/" className="pc-logo">
          <span className="pc-logo-mark"><Icon name="car" size={19} /></span>
          <span><b>Namma</b><small>Journey</small></span>
        </Link>

        <div className="pc-role">
          <span className="pc-role-av">{String(displayName).trim().charAt(0).toUpperCase()}</span>
          <span><b title={displayName}>{displayName}</b><small>{roleLabel}</small></span>
        </div>

        <nav className="pc-nav" ref={navRef}>
          {navItems.map((item) => {
            const on = isOn(item.path)
            return (
              <Link key={item.path} to={item.path} className={on ? 'is-on' : undefined} aria-current={on ? 'page' : undefined}>
                <span className={`pc-navic pc-tint-${navTint(item.path)}`}><Icon name={navIcon(item.path)} size={18} /></span>
                <span>{item.label}</span>
              </Link>
            )
          })}
        </nav>

        <div className="pc-rail-foot">
          <button type="button" onClick={handleLogout}>
            <Icon name="logout" />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      <div className="pc-body">
        <header className="pc-top">
          <button type="button" className="pc-iconbtn pc-menu" onClick={() => setRailOpen((s) => !s)} aria-label="Open menu" aria-expanded={railOpen}>
            <Icon name="menu" />
          </button>
          <span className="pc-top-title">{current?.label || roleLabel}</span>
          <span className="pc-top-sp" />

          <div className="pc-notes" ref={dropdownRef}>
            <button
              type="button"
              className="pc-iconbtn notification-bell"
              onClick={toggleDropdown}
              aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
              aria-expanded={dropdownOpen}
            >
              <Icon name="bell" />
              {unreadCount > 0 && <span className="pc-count notification-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
            </button>
            {dropdownOpen && (
              <div className="pc-notes-panel notification-dropdown" role="dialog" aria-label="Notifications">
                <div className="pc-notes-hd">
                  <h3>Notifications</h3>
                  {unreadCount > 0 && <button type="button" onClick={handleMarkAllRead}>Mark all as read</button>}
                </div>
                <div className="pc-notes-list">
                  {notifications.length === 0 ? (
                    <div className="pc-notes-empty">You&apos;re all caught up.</div>
                  ) : (
                    notifications.map((notif) => (
                      <div
                        key={notif.id}
                        className={`pc-note${!notif.read ? ' is-unread' : ''}`}
                        onClick={() => handleNotificationClick(notif)}
                        onKeyDown={(e) => { if (e.key === 'Enter') handleNotificationClick(notif) }}
                        role="button"
                        tabIndex={0}
                      >
                        <span className="pc-note-dot" />
                        <div>
                          <b>{notif.title}</b>
                          <p>{notif.message}</p>
                          <small>{timeAgo(notif.createdAt || notif.timestamp)}</small>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>
          <span className="pc-rolechip">{roleLabel}</span>
        </header>

        <main key={location.pathname} className="pc-main dashboard-content pc-main-enter">
          {role === 'ROLE_DRIVER'
            ? (
              <ApplicationGate driverId={user?.userId}>
                <TelegramGate driverId={user?.userId}><DriverDispatch driverId={user?.userId}>{children}</DriverDispatch></TelegramGate>
              </ApplicationGate>
            )
            : children}
        </main>
      </div>
      <TipLayer />
    </div>
  )
}
