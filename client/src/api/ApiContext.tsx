import { createContext, useContext, useMemo, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiClient } from './client'

const ApiContext = createContext<ApiClient | null>(null)

export function ApiProvider({ children }: { children: ReactNode }) {
  const { getToken } = useAuth()
  const client = useMemo(() => new ApiClient(getToken), [getToken])
  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>
}

// This hook intentionally shares the provider module.
// eslint-disable-next-line react-refresh/only-export-components
export function useApi() {
  const context = useContext(ApiContext)
  if (!context) throw new Error('useApi must be used inside ApiProvider')
  return context
}
