import React, { createContext, useContext, useState, useEffect } from 'react'

const AuthContext = createContext(null)

// Decode a JWT payload and return its claims (or null if the token is malformed).
// We're not verifying the signature here — the server is the source of truth for trust.
// This is purely to drop *obviously* expired tokens on hydration so the UI doesn't show
// a logged-in shell that will 401 on the first API call.
function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    return JSON.parse(atob(padded))
  } catch {
    return null
  }
}

function isTokenExpired(token) {
  const claims = decodeJwtPayload(token)
  if (!claims || typeof claims.exp !== 'number') return false
  // exp is seconds-since-epoch per RFC 7519.
  return claims.exp * 1000 <= Date.now()
}

// localStorage can throw SecurityError (storage partitioning/blocked 3rd-party storage,
// a document mid-teardown, etc). Uncaught, that would abort the hydration effect below
// before setLoading(false) runs, leaving ProtectedRoute stuck on its loading screen forever.
function safeStorage(fn, fallback) {
  try {
    return fn()
  } catch {
    return fallback
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [token, setToken] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    try {
      const savedToken = safeStorage(() => localStorage.getItem('nj_token'), null)
      const savedUser = safeStorage(() => localStorage.getItem('nj_user'), null)
      if (savedToken && savedUser) {
        if (isTokenExpired(savedToken)) {
          // Stale session — clear it so ProtectedRoute kicks the user to /login instead of
          // briefly rendering the dashboard and then 401-ing on the first XHR.
          safeStorage(() => localStorage.removeItem('nj_token'))
          safeStorage(() => localStorage.removeItem('nj_user'))
        } else {
          setToken(savedToken)
          try {
            setUser(JSON.parse(savedUser))
          } catch {
            safeStorage(() => localStorage.removeItem('nj_user'))
          }
        }
      }
    } finally {
      setLoading(false)
    }
  }, [])

  // Listen for the api.js interceptor's "auth-expired" signal so we clear in-memory state too,
  // not just localStorage. Without this the user object in context outlives the token by one tick.
  useEffect(() => {
    const onAuthExpired = () => { setToken(null); setUser(null) }
    window.addEventListener('nj_auth_expired', onAuthExpired)
    return () => window.removeEventListener('nj_auth_expired', onAuthExpired)
  }, [])

  const login = (authData) => {
    if (!authData?.token || !authData?.role) {
      throw new Error('Login response missing token or role')
    }
    const userData = {
      userId: authData.userId,
      role: authData.role,
      name: authData.name,
      email: authData.email,
      mobile: authData.mobile,
      message: authData.message
    }
    setToken(authData.token)
    setUser(userData)
    safeStorage(() => localStorage.setItem('nj_token', authData.token))
    safeStorage(() => localStorage.setItem('nj_user', JSON.stringify(userData)))
  }

  const updateUser = (updates) => {
    const updated = { ...user, ...updates }
    setUser(updated)
    safeStorage(() => localStorage.setItem('nj_user', JSON.stringify(updated)))
  }

  const logout = () => {
    setToken(null)
    setUser(null)
    safeStorage(() => localStorage.removeItem('nj_token'))
    safeStorage(() => localStorage.removeItem('nj_user'))
  }

  return (
    <AuthContext.Provider value={{ user, token, login, logout, updateUser, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
