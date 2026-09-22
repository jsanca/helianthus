export interface Catalog {
  app?: {
    name?: string
  }
  datasources: Record<string, DatasourceDefinition>
  queries: Record<string, QueryDefinition>
  operations: OperationDefinition[]
  entities: EntityDefinition[]
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
  name: string
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

export interface EntityDefinition {
  name: string
  label?: string
  description?: string
  datasource: string
  table: string
  primaryKey: string[]
  fields: string[]
  security?: EntitySecurityDefinition
}

export interface EntitySecurityDefinition {
  read?: EntityRoleDefinition
  write?: EntityRoleDefinition
}

export interface EntityRoleDefinition {
  roles: string[]
}

export interface EntitySelection {
  entityId: string
}

export type ActiveCatalogSelection =
  | ({ kind: 'operation' } & OperationSelection)
  | ({ kind: 'entity' } & EntitySelection)
  | { kind: 'configurations' }
