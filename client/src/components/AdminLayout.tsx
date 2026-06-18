import {
  ApiOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons'
import { Button, Layout, Space, Tag, Tooltip, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useMemo, useState } from 'react'
import type { SessionMode } from '../App'
import { mockCatalog } from '../data/mockCatalog'
import type { OperationSelection } from '../types/catalog'
import { OperationRunner } from './OperationRunner'

const { Header, Sider, Content } = Layout

interface AdminLayoutProps {
  mode: SessionMode
  onLogout: () => void
}

export function AdminLayout({ mode, onLogout }: AdminLayoutProps) {
  const [collapsed, setCollapsed] = useState(false)
  const [selection, setSelection] = useState<OperationSelection>({
    operationId: 'all-products',
    configurationId: 'default',
  })

  const treeData = useMemo<DataNode[]>(
    () => [
      {
        key: 'operations',
        title: 'Operations',
        icon: <ApiOutlined />,
        selectable: false,
        children: Object.entries(mockCatalog.operations).map(
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
        children: Object.entries(mockCatalog.datasources).map(([id, datasource]) => ({
          key: `datasource:${id}`,
          title: `${id} · ${datasource.type}`,
          selectable: false,
          isLeaf: true,
        })),
      },
    ],
    [],
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
          className="catalog-tree"
          treeData={treeData}
          showIcon
          blockNode
          defaultExpandAll
          selectedKeys={[`${selection.operationId}:${selection.configurationId}`]}
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
            <Typography.Title level={4}>{mockCatalog.app?.name}</Typography.Title>
          </div>
          <Space size="middle">
            <Tag color={mode === 'admin' ? 'green' : 'gold'}>
              {mode === 'admin' ? 'Admin mode' : 'Guest mode'}
            </Tag>
            <Tooltip title="Return to login">
              <Button type="text" icon={<LogoutOutlined />} onClick={onLogout}>
                Sign out
              </Button>
            </Tooltip>
          </Space>
        </Header>

        <Content className="app-content">
          <OperationRunner
            key={`${selection.operationId}:${selection.configurationId}`}
            catalog={mockCatalog}
            selection={selection}
          />
        </Content>
      </Layout>
    </Layout>
  )
}
