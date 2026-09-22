import { Alert, Button, Empty, Layout, Spin, Typography } from 'antd'
import type { ApiClient, ApiError } from '../api/client'
import type { useAuth } from '../auth/AuthContext'
import type { ActiveCatalogSelection, Catalog } from '../types/catalog'
import { EntityBrowser } from './EntityBrowser'
import { OperationRunner } from './OperationRunner'

const { Content } = Layout

interface WorkspaceContentProps {
  api: ApiClient
  auth: ReturnType<typeof useAuth>
  catalog: Catalog | null
  catalogError: ApiError | null
  catalogLoading: boolean
  selection: ActiveCatalogSelection | null
}

export function WorkspaceContent({
  api,
  auth,
  catalog,
  catalogError,
  catalogLoading,
  selection,
}: WorkspaceContentProps) {
  return (
    <Content className="app-content">
      {catalogError && (
        <Alert
          className="catalog-alert"
          type={
            catalogError.kind === 'backend-down' ||
            catalogError.kind === 'forbidden'
              ? 'error'
              : 'warning'
          }
          showIcon
          message={
            catalogError.kind === 'unauthenticated'
              ? 'Sign in to load the live catalog'
              : catalogError.kind === 'forbidden'
                ? 'Catalog access forbidden'
                : catalogError.kind === 'backend-down'
                  ? 'Backend unavailable'
                  : 'Catalog unavailable'
          }
          description={
            auth.mode === 'guest'
              ? `${catalogError.message} Showing the local sample catalog. Protected executions still require sign-in.`
              : catalogError.message
          }
          action={
            catalogError.kind === 'unauthenticated' ? (
              <Button size="small" onClick={() => void auth.login()}>
                Sign in
              </Button>
            ) : undefined
          }
        />
      )}
      {catalogLoading ? (
        <div className="workspace-loading">
          <Spin size="large" />
          <Typography.Text>Loading catalog…</Typography.Text>
        </div>
      ) : catalog && selection?.kind === 'operation' ? (
        <OperationRunner
          key={`${selection.operationId}:${selection.configurationId}`}
          catalog={catalog}
          selection={selection}
          api={api}
        />
      ) : catalog && selection?.kind === 'entity' ? (
        <EntityBrowser
          key={selection.entityId}
          catalog={catalog}
          selection={selection}
          api={api}
        />
      ) : catalog && selection?.kind === 'configurations' ? (
        <Empty description="Configurations will appear here as administrative capabilities are added." />
      ) : (
        <Empty description="No catalog items are available for this account." />
      )}
    </Content>
  )
}
