const trimTrailingSlash = (value: string) => value.replace(/\/$/, '')

export const appConfig = {
  apiBaseUrl: trimTrailingSlash(
    import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  ),
  keycloak: {
    url: trimTrailingSlash(
      import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
    ),
    realm: import.meta.env.VITE_KEYCLOAK_REALM || 'helianthus',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'helianthus-client',
  },
}
