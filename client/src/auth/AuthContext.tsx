import Keycloak, { type KeycloakTokenParsed } from 'keycloak-js'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { appConfig } from '../config'

export type SessionMode = 'guest' | 'admin' | 'user'
export type AuthStatus = 'initializing' | 'ready' | 'unavailable'

interface HelianthusToken extends KeycloakTokenParsed {
  preferred_username?: string
  name?: string
  realm_access?: {
    roles: string[]
  }
}

interface AuthContextValue {
  status: AuthStatus
  authenticated: boolean
  mode: SessionMode | null
  username: string | null
  roles: string[]
  error: string | null
  login: () => Promise<void>
  logout: () => Promise<void>
  continueAsGuest: () => void
  getToken: () => Promise<string | undefined>
}

const AuthContext = createContext<AuthContextValue | null>(null)

const keycloak = new Keycloak(appConfig.keycloak)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('initializing')
  const [guest, setGuest] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [token, setToken] = useState<HelianthusToken | undefined>()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    keycloak.onAuthSuccess = () => {
      if (!active) return
      setAuthenticated(true)
      setToken(keycloak.tokenParsed as HelianthusToken | undefined)
      setGuest(false)
    }
    keycloak.onAuthLogout = () => {
      if (!active) return
      setAuthenticated(false)
      setToken(undefined)
    }
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).then((refreshed) => {
        if (active && refreshed) {
          setToken(keycloak.tokenParsed as HelianthusToken | undefined)
        }
      })
    }

    void keycloak
      .init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        silentCheckSsoFallback: false,
      })
      .then((isAuthenticated) => {
        if (!active) return
        setAuthenticated(isAuthenticated)
        setToken(keycloak.tokenParsed as HelianthusToken | undefined)
        setStatus('ready')
      })
      .catch(() => {
        if (!active) return
        setError(
          `Keycloak is unavailable at ${appConfig.keycloak.url}. Guest mode remains available.`,
        )
        setStatus('unavailable')
      })

    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async () => {
    setError(null)
    try {
      await keycloak.login({ redirectUri: window.location.href })
    } catch {
      setError('Unable to redirect to Keycloak. Check the identity provider.')
      setStatus('unavailable')
    }
  }, [])

  const logout = useCallback(async () => {
    if (keycloak.authenticated) {
      await keycloak.logout({ redirectUri: window.location.origin })
      return
    }
    setGuest(false)
  }, [])

  const getToken = useCallback(async () => {
    if (!keycloak.authenticated) return undefined
    try {
      await keycloak.updateToken(30)
      return keycloak.token
    } catch {
      setError('Your session could not be refreshed. Please sign in again.')
      return keycloak.token
    }
  }, [])

  const roles = useMemo(() => token?.realm_access?.roles ?? [], [token])
  const mode: SessionMode | null = guest
    ? 'guest'
    : authenticated
      ? roles.includes('ADMIN')
        ? 'admin'
        : 'user'
      : null

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      authenticated,
      mode,
      username: token?.preferred_username ?? token?.name ?? null,
      roles,
      error,
      login,
      logout,
      continueAsGuest: () => setGuest(true),
      getToken,
    }),
    [authenticated, error, getToken, login, logout, mode, roles, status, token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// This hook intentionally shares the provider module.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
