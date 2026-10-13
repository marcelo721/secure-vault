import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, ApiRequestError, setUnauthorizedHandler } from '../api/client'
import type { User } from '../api/types'

interface AuthContextValue {
  user: User | null
  /** true enquanto a sessão inicial (GET /auth/me) está sendo verificada. */
  loading: boolean
  /** true quando uma sessão existente expirou ou foi invalidada durante o uso. */
  sessionExpired: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)
  const userRef = useRef<User | null>(null)
  userRef.current = user

  useEffect(() => {
    // Sessão expirada em qualquer rota privada: limpa o estado local; o ProtectedRoute redireciona ao login.
    setUnauthorizedHandler(() => {
      if (userRef.current) setSessionExpired(true)
      setUser(null)
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  useEffect(() => {
    let active = true
    api
      .me()
      .then((me) => {
        if (active) setUser(me)
      })
      .catch((error: unknown) => {
        // 401 aqui é o estado normal de "não logado"; outros erros também deixam o usuário deslogado.
        if (!(error instanceof ApiRequestError) && import.meta.env.DEV) {
          console.warn('Falha ao verificar a sessão')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const loggedIn = await api.login(email, password)
    setSessionExpired(false)
    setUser(loggedIn)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      // Mesmo que a chamada falhe, encerra a sessão local.
      setSessionExpired(false)
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({ user, loading, sessionExpired, login, logout }),
    [user, loading, sessionExpired, login, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth deve ser usado dentro de AuthProvider')
  return context
}
