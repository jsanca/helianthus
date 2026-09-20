import {
  ApiOutlined,
  DatabaseOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { Button, Layout, Tooltip, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useMemo } from 'react'
import type {
  ActiveCatalogSelection,
  Catalog,
  EntityDefinition,
  OperationDefinition,
} from '../types/catalog'

const { Sider } = Layout

interface CatalogSidebarProps {
  catalog: Catalog | null
  collapsed: boolean
  selection: ActiveCatalogSelection | null
  onCollapseChange: (collapsed: boolean) => void
  onSelectionChange: (selection: ActiveCatalogSelection) => void
}

interface CatalogSectionProps {
  icon: DataNode['icon']
  sectionKey: string
  title: string
  children: DataNode[]
}

function CatalogSection({
  children,
  icon,
  sectionKey,
  title,
}: CatalogSectionProps): DataNode {
  return {
    key: sectionKey,
    title,
    icon,
    selectable: false,
    children,
  }
}

function OperationsTree(operations: OperationDefinition[]): DataNode {
  return CatalogSection({
    sectionKey: 'operations',
    title: 'Operations',
    icon: <ApiOutlined />,
    children: operations.map((operation) => ({
      key: `operation:${operation.name}`,
      title: operation.name,
      selectable: false,
      children: Object.entries(operation.configurations).map(
        ([configurationId, configuration]) => ({
          key: `operation:${operation.name}:${configurationId}`,
          title: configuration.label || configurationId,
          isLeaf: true,
        }),
      ),
    })),
  })
}

function EntitiesTree(entities: EntityDefinition[]): DataNode {
  return CatalogSection({
    sectionKey: 'entities',
    title: 'Entities',
    icon: <DatabaseOutlined />,
    children: entities.map((entity) => ({
      key: `entity:${entity.name}`,
      title: entity.name,
      isLeaf: true,
    })),
  })
}

function ConfigurationsLink(): DataNode {
  return {
    key: 'configurations',
    title: 'Configurations',
    icon: <SettingOutlined />,
    isLeaf: true,
  }
}

function selectedKeys(selection: ActiveCatalogSelection | null) {
  if (selection?.kind === 'operation') {
    return [`operation:${selection.operationId}:${selection.configurationId}`]
  }
  if (selection?.kind === 'entity') return [`entity:${selection.entityId}`]
  if (selection?.kind === 'configurations') return ['configurations']
  return []
}

export function CatalogSidebar({
  catalog,
  collapsed,
  selection,
  onCollapseChange,
  onSelectionChange,
}: CatalogSidebarProps) {
  const treeData = useMemo<DataNode[]>(
    () =>
      catalog
        ? [
            OperationsTree(catalog.operations),
            EntitiesTree(catalog.entities),
            ConfigurationsLink(),
          ]
        : [],
    [catalog],
  )

  const treeKey = useMemo(
    () =>
      [
        catalog?.operations.map((operation) => operation.name).join(':') ?? '',
        catalog?.operations
          .flatMap((operation) =>
            Object.keys(operation.configurations).map(
              (configurationId) => `${operation.name}:${configurationId}`,
            ),
          )
          .join(':') ?? '',
        catalog?.entities.map((entity) => entity.name).join(':') ?? '',
      ].join('|'),
    [catalog],
  )

  return (
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
        key={treeKey}
        className="catalog-tree"
        treeData={treeData}
        showIcon
        blockNode
        defaultExpandAll
        selectedKeys={selectedKeys(selection)}
        onSelect={(_, info) => {
          const key = String(info.node.key)
          if (key === 'configurations') {
            onSelectionChange({ kind: 'configurations' })
            return
          }
          if (key.startsWith('operation:')) {
            const [, operationId, configurationId] = key.split(':')
            if (!operationId || !configurationId) return
            const operation = catalog?.operations.find(
              (candidate) => candidate.name === operationId,
            )
            if (!operation) return
            onSelectionChange({
              kind: 'operation',
              operationId,
              configurationId,
            })
            return
          }
          if (key.startsWith('entity:')) {
            onSelectionChange({
              kind: 'entity',
              entityId: key.replace(/^entity:/, ''),
            })
          }
        }}
        titleRender={(node) =>
          collapsed &&
          ['operations', 'entities', 'configurations'].includes(String(node.key)) ? (
            <Tooltip title={String(node.title)} placement="right">
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
        onClick={() => onCollapseChange(!collapsed)}
      >
        {!collapsed && 'Collapse'}
      </Button>
    </Sider>
  )
}
