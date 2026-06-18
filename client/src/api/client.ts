import { appConfig } from '../config'
import type { Catalog } from '../types/catalog'

export type ApiErrorKind =
  | 'backend-down'
  | 'unauthenticated'
  | 'forbidden'
  | 'http'

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly kind: ApiErrorKind,
    public readonly status = 0,
    public readonly body = '',
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface HealthResponse {
  status: string
  service: string
}

export interface CatalogResponse {
  app: string | null
  formats: string[]
  operations: Array<{
    name: string
    type: string
    queryRef: string | null
    datasource: string | null
    parameters: Array<{
      name: string
      type: string
      required: boolean
    }>
    configurations: Array<{
      name: string
      pipeline?: unknown
    }>
  }>
}

type TokenProvider = () => Promise<string | undefined>

const errorFromResponse = async (response: Response) => {
  const body = await response.text()
  if (response.status === 401) {
    return new ApiError('Authentication is required.', 'unauthenticated', 401, body)
  }
  if (response.status === 403) {
    return new ApiError(
      'Your account does not have permission for this request.',
      'forbidden',
      403,
      body,
    )
  }
  return new ApiError(
    `Backend returned ${response.status} ${response.statusText}.`,
    'http',
    response.status,
    body,
  )
}

export class ApiClient {
  constructor(private readonly getToken: TokenProvider) {}

  buildUrl(path: string, params?: URLSearchParams) {
    const query = params?.toString()
    return `${appConfig.apiBaseUrl}${path}${query ? `?${query}` : ''}`
  }

  async request(path: string, init: RequestInit = {}) {
    const token = await this.getToken()
    const headers = new Headers(init.headers)
    if (token) headers.set('Authorization', `Bearer ${token}`)

    try {
      const response = await fetch(this.buildUrl(path), { ...init, headers })
      if (!response.ok) throw await errorFromResponse(response)
      return response
    } catch (error) {
      if (error instanceof ApiError) throw error
      throw new ApiError(
        `The Helianthus backend is unavailable at ${appConfig.apiBaseUrl}.`,
        'backend-down',
      )
    }
  }

  async health(): Promise<HealthResponse> {
    const response = await this.request('/health')
    return response.json() as Promise<HealthResponse>
  }

  async catalog(): Promise<CatalogResponse> {
    const response = await this.request('/api/admin/catalog', {
      headers: { Accept: 'application/json' },
    })
    return response.json() as Promise<CatalogResponse>
  }

  async execute(
    operationId: string,
    configurationId: string,
    format: string,
    params: URLSearchParams,
  ) {
    const path = `/api/op/${encodeURIComponent(operationId)}/${encodeURIComponent(
      configurationId,
    )}.${format}`
    const token = await this.getToken()
    const headers = new Headers({
      Accept:
        format === 'json'
          ? 'application/json'
          : format === 'html'
            ? 'text/html'
            : format === 'csv'
              ? 'text/csv'
              : 'application/xml',
    })
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const url = this.buildUrl(path, params)

    try {
      const response = await fetch(url, { headers })
      if (!response.ok) throw await errorFromResponse(response)
      return { response, url }
    } catch (error) {
      if (error instanceof ApiError) throw error
      throw new ApiError(
        `The Helianthus backend is unavailable at ${appConfig.apiBaseUrl}.`,
        'backend-down',
      )
    }
  }
}

export const mapCatalogResponse = (response: CatalogResponse): Catalog => ({
  app: { name: response.app ?? 'Helianthus API' },
  datasources: {},
  queries: {},
  operations: Object.fromEntries(
    response.operations.map((operation) => [
      operation.name,
      {
        queryRef: operation.queryRef ?? undefined,
        datasource: operation.datasource ?? undefined,
        parameters: operation.parameters,
        configurations: Object.fromEntries(
          operation.configurations.map((configuration) => [
            configuration.name,
            {
              pipeline: Array.isArray(configuration.pipeline)
                ? configuration.pipeline
                : [],
            },
          ]),
        ),
      },
    ]),
  ),
})
