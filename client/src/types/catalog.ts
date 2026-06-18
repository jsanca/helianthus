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
  label?: string
  description?: string
  parameters?: ParameterDefinitionWithMetadata[]
  configurations: Record<string, ConfigurationDefinition>
}

export interface ParameterDefinitionWithMetadata {
  name: string
  type: string
  required?: boolean
  label?: string
  description?: string
  placeholder?: string
  input?: InputDefinition
}

export interface InputDefinition {
  kind: 'text' | 'number' | 'select' | 'boolean' | 'date'
  options?: string[]
  min?: number
  max?: number
  step?: number
}

export interface ConfigurationDefinition {
  label?: string
  description?: string
  pipeline?: Array<Record<string, unknown>>
}

export interface OperationSelection {
  operationId: string
  configurationId: string
}
