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
    queryRef: string | null
    datasource: string | null
    label?: string | null
    description?: string | null
    parameters: Array<{
      name: string
      type: string
      required: boolean
      label?: string | null
      description?: string | null
      placeholder?: string | null
      input?: {
        kind: 'text' | 'number' | 'select' | 'boolean' | 'date'
        options?: string[] | null
        min?: number | null
        max?: number | null
        step?: number | null
      } | null
    }>
    configurations: Array<{
      name: string
      label?: string | null
      description?: string | null
      pipeline?: unknown
    }>
  }>
  entities?: Array<{
    name: string
    label?: string | null
    description?: string | null
    datasource: string
    table: string
    primaryKey: string[]
    fields: string[]
    security?: {
      read?: { roles: string[] } | null
      write?: { roles: string[] } | null
    } | null
  }>
}

export interface ResultFrameResponse {
  schema?: {
    columns?: Array<{ name: string; type?: string; nullable?: boolean }>
  }
  rows?: Array<Record<string, unknown>>
  metadata?: {
    rowCount?: number
    durationMs?: number
  }
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

  async listEntity(
    entityId: string,
    params: URLSearchParams,
  ): Promise<ResultFrameResponse> {
    const query = params.toString()
    const response = await this.request(
      `/api/entities/${encodeURIComponent(entityId)}.json${query ? `?${query}` : ''}`,
      {
        headers: { Accept: 'application/json' },
      },
    )
    return response.json() as Promise<ResultFrameResponse>
  }

  async getEntityRecord(
    entityId: string,
    primaryKey: string | number,
  ): Promise<ResultFrameResponse> {
    const response = await this.request(
      `/api/entities/${encodeURIComponent(entityId)}/${encodeURIComponent(
        String(primaryKey),
      )}.json`,
      {
        headers: { Accept: 'application/json' },
      },
    )
    return response.json() as Promise<ResultFrameResponse>
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
        label: operation.label ?? undefined,
        description: operation.description ?? undefined,
        parameters: operation.parameters.map((parameter) => ({
          ...parameter,
          label: parameter.label ?? undefined,
          description: parameter.description ?? undefined,
          placeholder: parameter.placeholder ?? undefined,
          input: parameter.input
            ? {
                ...parameter.input,
                options: parameter.input.options ?? undefined,
                min: parameter.input.min ?? undefined,
                max: parameter.input.max ?? undefined,
                step: parameter.input.step ?? undefined,
              }
            : undefined,
        })),
        configurations: Object.fromEntries(
          operation.configurations.map((configuration) => [
            configuration.name,
            {
              label: configuration.label ?? undefined,
              description: configuration.description ?? undefined,
              pipeline: Array.isArray(configuration.pipeline)
                ? configuration.pipeline
                : [],
            },
          ]),
        ),
      },
    ]),
  ),
  entities: Object.fromEntries(
    (response.entities ?? []).map((entity) => [
      entity.name,
      {
        label: entity.label ?? undefined,
        description: entity.description ?? undefined,
        datasource: entity.datasource,
        table: entity.table,
        primaryKey: entity.primaryKey,
        fields: entity.fields,
        security: entity.security ?? undefined,
      },
    ]),
  ),
})
