import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as api from './api'
import { AuthContext, type AuthState } from './auth-context'

/** Margen para renovar antes de que caduque el token de acceso. */
const REFRESH_MARGIN_MS = 60_000

function fromTokens(t: api.TokenResponse): AuthState {
  return { status: 'authenticated', user: t.user, accessToken: t.accessToken, expiresAt: Date.parse(t.expiresAt) }
}

/**
 * Mantiene la sesión: al cargar intenta recuperarla con la cookie de refresco
 * y renueva el token de acceso poco antes de que caduque.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'checking' })

  useEffect(() => {
    let active = true
    api.refresh().then(
      (t) => active && setState(fromTokens(t)),
      () => active && setState({ status: 'anonymous' }),
    )
    return () => {
      active = false
    }
  }, [])

  const expiresAt = state.status === 'authenticated' ? state.expiresAt : null
  useEffect(() => {
    if (expiresAt === null) return
    const delay = Math.max(expiresAt - Date.now() - REFRESH_MARGIN_MS, 5_000)
    const timer = setTimeout(() => {
      api.refresh().then(
        (t) => setState(fromTokens(t)),
        () => setState({ status: 'anonymous' }),
      )
    }, delay)
    return () => clearTimeout(timer)
  }, [expiresAt])

  const login = useCallback(async (email: string, password: string) => {
    setState(fromTokens(await api.login(email, password)))
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } catch {
      // Sin conexión o API caída: la sesión se cierra igual en este navegador (el token de acceso se descarta
      // y el de refresco caducará). No se propaga el error para no dejar una promesa rechazada sin manejar.
    } finally {
      setState({ status: 'anonymous' })
    }
  }, [])

  const value = useMemo(() => ({ state, login, logout }), [state, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
