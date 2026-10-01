import React, { useState, useEffect, useRef, useCallback } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getNotifications, getUnreadCount, markNotificationAsRead, markAllNotificationsAsRead } from '../utils/api'
import Icon, { navIcon, navTint } from './dash/Icon'
import { TipLayer } from './dash/ui'

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

const ROLE_LABEL = { ROLE_USER: 'Traveller', ROLE_DRIVER: 'Driver', ROLE_OWNER: 'Owner' }

/**
 * The Postcard shell every owner, driver and traveller page renders inside: teal rail with
 * line icons (mapped from each page's routes, so pages keep passing their nav items as is),
 * a top bar with notifications, and a drawer on narrow screens.
 */
export default function DashboardLayout({ children, navItems, role }) {
  const { logout, user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [railOpen, setRailOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef(null)

  const roleLabel = ROLE_LABEL[role] || 'Traveller'
  const displayName = user?.name || user?.email || roleLabel
  const current = navItems.find((n) => n.path === location.pathname)

  const handleLogout = () => { logout(); navigate('/') }

  const fetchNotifications = useCallback(async () => {
    if (!user?.userId || !user?.role) return
    try {
      const recipientId = user.role === 'ROLE_OWNER' ? 'owner' : String(user.userId)
      const [notifRes, countRes] = await Promise.all([
        getNotifications(recipientId, user.role),
        getUnreadCount(recipientId, user.role),
      ])
      setNotifications(notifRes.data || [])
      setUnreadCount(countRes.data?.count ?? countRes.data ?? 0)
    } catch {
      /* already surfaced by the api error toast */
    }
  }, [user?.userId, user?.role])

  useEffect(() => {
    fetchNotifications()
    const interval = setInterval(fetchNotifications, 10000)
    return () => clearInterval(interval)
  }, [fetchNotifications])

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
    if (!user?.userId || !user?.role) return
    try {
      const recipientId = user.role === 'ROLE_OWNER' ? 'owner' : String(user.userId)
      await markAllNotificationsAsRead(recipientId, user.role)
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

        <nav className="pc-nav">
          {navItems.map((item) => {
            const on = location.pathname === item.path
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
              onClick={() => setDropdownOpen((prev) => !prev)}
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

        <main className="pc-main dashboard-content">
          {children}
        </main>
      </div>
      <TipLayer />
    </div>
  )
}
