import {
  ApiOutlined,
  DatabaseOutlined,
  LoginOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Empty,
  Layout,
  Space,
  Spin,
  Tag,
  Tooltip,
  Tree,
  Typography,
} from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useEffect, useMemo, useState } from 'react'
import { useApi } from '../api/ApiContext'
import { ApiError, mapCatalogResponse } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { mockCatalog } from '../data/mockCatalog'
import type { Catalog, OperationSelection } from '../types/catalog'
import { HealthStatus } from './HealthStatus'
import { OperationRunner } from './OperationRunner'

const { Header, Sider, Content } = Layout

export function AdminLayout() {
  const auth = useAuth()
  const api = useApi()
  const [collapsed, setCollapsed] = useState(false)
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<ApiError | null>(null)
  const [selection, setSelection] = useState<OperationSelection | null>(null)

  useEffect(() => {
    let active = true
    void api
      .catalog()
      .then((response) => {
        if (!active) return
        const loadedCatalog = mapCatalogResponse(response)
        const firstOperation = Object.entries(loadedCatalog.operations)[0]
        setCatalog(loadedCatalog)
        setCatalogError(null)
        if (firstOperation) {
          setSelection({
            operationId: firstOperation[0],
            configurationId:
              Object.keys(firstOperation[1].configurations)[0] ?? 'default',
          })
        }
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
          const firstOperation = Object.entries(mockCatalog.operations)[0]
          if (firstOperation) {
            setSelection({
              operationId: firstOperation[0],
              configurationId:
                Object.keys(firstOperation[1].configurations)[0] ?? 'default',
            })
          }
        }
      })
      .finally(() => {
        if (active) setCatalogLoading(false)
      })

    return () => {
      active = false
    }
  }, [api, auth.mode])

  const treeData = useMemo<DataNode[]>(
    () =>
      catalog
        ? [
      {
        key: 'operations',
        title: 'Operations',
        icon: <ApiOutlined />,
        selectable: false,
        children: Object.entries(catalog.operations).map(
          ([operationId, operation]) => ({
            key: `operation:${operationId}`,
            title: operationId,
            selectable: false,
            children: Object.keys(operation.configurations).map((configurationId) => ({
              key: `${operationId}:${configurationId}`,
              title: configurationId,
              isLeaf: true,
            })),
          }),
        ),
      },
      {
        key: 'datasources',
        title: 'Configurations',
        icon: <DatabaseOutlined />,
        selectable: false,
        children: Object.entries(catalog.datasources).map(([id, datasource]) => ({
          key: `datasource:${id}`,
          title: `${id} · ${datasource.type}`,
          selectable: false,
          isLeaf: true,
        })),
      },
    ]
        : [],
    [catalog],
  )

  return (
    <Layout className="app-shell">
      <Sider
        width={280}
        collapsedWidth={76}
        collapsed={collapsed}
        className="app-sider"
        trigger={null}
      >
        <div className="sidebar-brand">
          <div className="brand-mark brand-mark-small">H</div>
          {!collapsed && (
            <div>
              <Typography.Text strong>Helianthus</Typography.Text>
              <Typography.Text>Admin UI</Typography.Text>
            </div>
          )}
        </div>

        {!collapsed && (
          <Typography.Text className="sidebar-label">Catalog</Typography.Text>
        )}
        <Tree
          key={Object.keys(catalog?.operations ?? {}).join(':')}
          className="catalog-tree"
          treeData={treeData}
          showIcon
          blockNode
          defaultExpandAll
          selectedKeys={
            selection
              ? [`${selection.operationId}:${selection.configurationId}`]
              : []
          }
          onSelect={(_, info) => {
            const key = String(info.node.key)
            if (key.startsWith('operation:') || key.startsWith('datasource:')) return
            const [operationId, configurationId] = key.split(':')
            setSelection({ operationId, configurationId })
          }}
          titleRender={(node) =>
            collapsed && node.key === 'operations' ? (
              <Tooltip title="Operations" placement="right">
                <span>{node.title as string}</span>
              </Tooltip>
            ) : (
              <span>{node.title as string}</span>
            )
          }
        />

        <Button
          className="collapse-button"
          type="text"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setCollapsed((value) => !value)}
        >
          {!collapsed && 'Collapse'}
        </Button>
      </Sider>

      <Layout>
        <Header className="app-header">
          <div>
            <Typography.Text type="secondary">Workspace</Typography.Text>
            <Typography.Title level={4}>
              {catalog?.app?.name ?? 'Helianthus API'}
            </Typography.Title>
          </div>
          <Space size="middle">
            <HealthStatus />
            <Tag
              color={
                auth.mode === 'admin'
                  ? 'green'
                  : auth.mode === 'guest'
                    ? 'gold'
                    : 'blue'
              }
              icon={<UserOutlined />}
            >
              {auth.mode === 'admin'
                ? 'Admin'
                : auth.mode === 'guest'
                  ? 'Guest'
                  : 'Authenticated'}
              {auth.username ? ` · ${auth.username}` : ''}
            </Tag>
            {auth.mode === 'guest' ? (
              <Tooltip title="Authenticate with Keycloak">
                <Button
                  type="text"
                  icon={<LoginOutlined />}
                  onClick={() => void auth.login()}
                >
                  Sign in
                </Button>
              </Tooltip>
            ) : (
              <Tooltip title="Sign out of Keycloak">
                <Button
                  type="text"
                  icon={<LogoutOutlined />}
                  onClick={() => void auth.logout()}
                >
                  Sign out
                </Button>
              </Tooltip>
            )}
            {auth.mode === 'guest' && (
              <Button type="text" onClick={() => void auth.logout()}>
                Exit guest
              </Button>
            )}
          </Space>
        </Header>

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
              <Typography.Text>Loading operations catalog…</Typography.Text>
            </div>
          ) : catalog && selection ? (
            <OperationRunner
              key={`${selection.operationId}:${selection.configurationId}`}
              catalog={catalog}
              selection={selection}
              api={api}
            />
          ) : (
            <Empty description="No operations are available for this account." />
          )}
        </Content>
      </Layout>
    </Layout>
  )
}
