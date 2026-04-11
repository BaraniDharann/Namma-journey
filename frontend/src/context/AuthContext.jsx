import React, { createContext, useContext, useState, useEffect } from 'react'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [token, setToken] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const savedToken = localStorage.getItem('nj_token')
    const savedUser = localStorage.getItem('nj_user')
    if (savedToken && savedUser) {
      setToken(savedToken)
      setUser(JSON.parse(savedUser))
    }
    setLoading(false)
  }, [])

  const login = (authData) => {
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
    localStorage.setItem('nj_token', authData.token)
    localStorage.setItem('nj_user', JSON.stringify(userData))
  }

  const updateUser = (updates) => {
    const updated = { ...user, ...updates }
    setUser(updated)
    localStorage.setItem('nj_user', JSON.stringify(updated))
  }

  const logout = () => {
    setToken(null)
    setUser(null)
    localStorage.removeItem('nj_token')
    localStorage.removeItem('nj_user')
  }

  return (
    <AuthContext.Provider value={{ user, token, login, logout, updateUser, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
