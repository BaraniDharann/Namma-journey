import React, { useState, useEffect, useRef, useCallback } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getNotifications, getUnreadCount, markNotificationAsRead, markAllNotificationsAsRead } from '../utils/api'

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

export default function DashboardLayout({ children, navItems, role }) {
  const { logout, user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 1024)
  const dropdownRef = useRef(null)

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 1024)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const roleConfig = {
    ROLE_USER:   { label: 'Traveller', color: '#f97316', bg: '#fff7ed', icon: '🧳' },
    ROLE_DRIVER: { label: 'Driver',    color: '#3b82f6', bg: '#eff6ff', icon: '🚗' },
    ROLE_OWNER:  { label: 'Owner',     color: '#8b5cf6', bg: '#f5f3ff', icon: '👑' },
  }
  const rc = roleConfig[role] || roleConfig.ROLE_USER

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
    } catch (err) {
    }
  }, [user?.userId, user?.role])

  useEffect(() => {
    fetchNotifications()
    const interval = setInterval(fetchNotifications, 10000)
    return () => clearInterval(interval)
  }, [fetchNotifications])

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleNotificationClick = async (notif) => {
    if (!notif.read) {
      try {
        await markNotificationAsRead(notif.id)
        setNotifications(prev => prev.map(n => n.id === notif.id ? { ...n, read: true } : n))
        setUnreadCount(prev => Math.max(0, prev - 1))
      } catch (err) { /* ignore */ }
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
      setNotifications(prev => prev.map(n => ({ ...n, read: true })))
      setUnreadCount(0)
    } catch (err) { /* ignore */ }
  }

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }} className="gradient-bg-animated">
      {sidebarOpen && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.4)', zIndex: 20 }}
          onClick={() => setSidebarOpen(false)} />
      )}

      <aside style={{
        position: isMobile ? 'fixed' : 'static',
        top: 0, left: 0, bottom: 0,
        width: 240,
        background: '#fff',
        borderRight: '1px solid #e2e8f0',
        display: 'flex',
        flexDirection: 'column',
        zIndex: 30,
        transform: sidebarOpen || !isMobile ? 'translateX(0)' : 'translateX(-100%)',
        transition: 'transform 0.25s ease',
        flexShrink: 0,
      }}>
        <div style={{ padding: '20px 20px 16px', borderBottom: '1px solid #f1f5f9' }}>
          <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: 'linear-gradient(135deg,#f97316,#ea580c)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🚗</div>
            <div>
              <div style={{ fontFamily: 'Poppins', fontWeight: 800, fontSize: 16, color: '#0f172a', lineHeight: 1.2 }}>Namma</div>
              <div style={{ fontSize: 11, color: '#f97316', fontWeight: 600 }}>Journey</div>
            </div>
          </Link>
        </div>

        <div style={{ margin: '12px 16px', padding: '12px 14px', borderRadius: 12, background: rc.bg, border: `1px solid ${rc.color}20` }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: rc.color + '20', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>{rc.icon}</div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 13, color: '#0f172a' }}>{rc.label}</div>
            </div>
          </div>
        </div>

        <nav style={{ flex: 1, padding: '8px 12px', overflowY: 'auto' }}>
          {navItems.map(item => (
            <Link key={item.path} to={item.path}
              className={`sidebar-link ${location.pathname === item.path ? 'active' : ''}`}
              onClick={() => setSidebarOpen(false)}>
              <span style={{ fontSize: 18 }}>{item.icon}</span>
              <span>{item.label}</span>
            </Link>
          ))}
        </nav>

        <div style={{ padding: '12px', borderTop: '1px solid #f1f5f9' }}>
          <button onClick={handleLogout} className="sidebar-link" style={{ width: '100%', color: '#ef4444', background: 'none', border: 'none' }}>
            <span style={{ fontSize: 18 }}>🚪</span>
            <span>Logout</span>
          </button>
        </div>
      </aside>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minWidth: 0 }}>
        <header className="dashboard-topbar" style={{ background: '#fff', borderBottom: '1px solid #e2e8f0', padding: '0 24px', height: 60, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
          <button className="hamburger-btn" style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 22, color: '#64748b', display: 'block' }}
            onClick={() => setSidebarOpen(s => !s)}>☰</button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div className="notification-wrapper" ref={dropdownRef}>
              <button className="notification-bell" onClick={() => setDropdownOpen(prev => !prev)}>
                🔔
                {unreadCount > 0 && (
                  <span className="notification-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
                )}
              </button>
              {dropdownOpen && (
                <div className="notification-dropdown">
                  <div className="notification-header">
                    <h3>Notifications</h3>
                    {unreadCount > 0 && (
                      <button className="notification-mark-all" onClick={handleMarkAllRead}>
                        Mark all as read
                      </button>
                    )}
                  </div>
                  <div className="notification-list">
                    {notifications.length === 0 ? (
                      <div className="notification-empty">No notifications</div>
                    ) : (
                      notifications.map(notif => (
                        <div
                          key={notif.id}
                          className={`notification-item ${!notif.read ? 'unread' : ''}`}
                          onClick={() => handleNotificationClick(notif)}
                        >
                          {!notif.read && <div className="notification-unread-dot" />}
                          <div className="notification-content">
                            <div className="notification-title">{notif.title}</div>
                            <div className="notification-message">{notif.message}</div>
                            <div className="notification-time">{timeAgo(notif.createdAt || notif.timestamp)}</div>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>
            <div style={{ padding: '5px 12px', borderRadius: 20, background: rc.bg, color: rc.color, fontSize: 12, fontWeight: 700, border: `1px solid ${rc.color}30` }}>
              {rc.label}
            </div>
          </div>
        </header>

        <main className="dashboard-content" style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
          {children}
        </main>
      </div>
    </div>
  )
}
