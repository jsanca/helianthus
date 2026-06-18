export interface Catalog {
  app?: {
    name?: string
  }
  datasources: Record<string, DatasourceDefinition>
  queries: Record<string, QueryDefinition>
  operations: Record<string, OperationDefinition>
}

export interface DatasourceDefinition {
  type: string
}

export interface QueryDefinition {
  datasource: string
  sql: string
  parameters?: Record<string, ParameterDefinition>
}

export interface ParameterDefinition {
  name?: string
  type: string
  required?: boolean
}

export interface OperationDefinition {
  queryRef?: string
  query?: string
  datasource?: string
  parameters?: ParameterDefinition[]
  configurations: Record<string, ConfigurationDefinition>
}

export interface ConfigurationDefinition {
  pipeline?: Array<Record<string, unknown>>
}

export interface OperationSelection {
  operationId: string
  configurationId: string
}
