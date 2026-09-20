import { Layout } from 'antd'
import { useEffect, useState } from 'react'
import { useApi } from '../api/ApiContext'
import { ApiError, mapCatalogResponse } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { mockCatalog } from '../data/mockCatalog'
import type { ActiveCatalogSelection, Catalog } from '../types/catalog'
import { AdminHeader } from './AdminHeader'
import { CatalogSidebar } from './CatalogSidebar'
import { WorkspaceContent } from './WorkspaceContent'

const initialSelectionFor = (catalog: Catalog): ActiveCatalogSelection | null => {
  const firstOperation = catalog.operations[0]
  const firstEntity = catalog.entities[0]
  if (firstOperation) {
    return {
      kind: 'operation',
      operationId: firstOperation.name,
      configurationId: Object.keys(firstOperation.configurations)[0] ?? 'default',
    }
  }
  if (firstEntity) {
    return { kind: 'entity', entityId: firstEntity.name }
  }
  return null
}

export function AdminLayout() {
  const auth = useAuth()
  const api = useApi()
  const [collapsed, setCollapsed] = useState(false)
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<ApiError | null>(null)
  const [selection, setSelection] = useState<ActiveCatalogSelection | null>(null)

  useEffect(() => {
    let active = true
    void api
      .catalog()
      .then((response) => {
        if (!active) return
        const loadedCatalog = mapCatalogResponse(response)
        setCatalog(loadedCatalog)
        setCatalogError(null)
        setSelection(initialSelectionFor(loadedCatalog))
      })
      .catch((error: unknown) => {
        if (!active) return
        const apiError =
          error instanceof ApiError
            ? error
            : new ApiError('Catalog could not be loaded.', 'http')
        setCatalogError(apiError)
        if (auth.mode === 'guest') {
          setCatalog(mockCatalog)
          setSelection(initialSelectionFor(mockCatalog))
        }
      })
      .finally(() => {
        if (active) setCatalogLoading(false)
      })

    return () => {
      active = false
    }
  }, [api, auth.mode])

  return (
    <Layout className="app-shell">
      <CatalogSidebar
        catalog={catalog}
        collapsed={collapsed}
        selection={selection}
        onCollapseChange={setCollapsed}
        onSelectionChange={setSelection}
      />

      <Layout>
        <AdminHeader auth={auth} catalog={catalog} />
        <WorkspaceContent
          api={api}
          auth={auth}
          catalog={catalog}
          catalogError={catalogError}
          catalogLoading={catalogLoading}
          selection={selection}
        />
      </Layout>
    </Layout>
  )
}
