import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, type ApiClient } from '../api/client'
import type { Catalog, EntitySelection } from '../types/catalog'

interface EntityBrowserProps {
  api: ApiClient
  catalog: Catalog
  selection: EntitySelection
}

interface EntityFilters {
  field?: string
  value?: string
  search?: string
  orderBy?: string
  orderDir: 'asc' | 'desc'
}

const PAGE_SIZE = 100

const formatValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return <Typography.Text type="secondary">—</Typography.Text>
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export function EntityBrowser({ api, catalog, selection }: EntityBrowserProps) {
  const entity = catalog.entities[selection.entityId]
  const [filters, setFilters] = useState<EntityFilters>({ orderDir: 'asc' })
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([])
  const [schemaFields, setSchemaFields] = useState<string[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [offset, setOffset] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [selectedRecord, setSelectedRecord] =
    useState<Record<string, unknown> | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const primaryKey = entity?.primaryKey[0]
  const fields = schemaFields.length > 0 ? schemaFields : entity?.fields ?? []
  const hasWriteMetadata = Boolean(entity?.security?.write?.roles.length)
  const mutationUnavailableMessage = hasWriteMetadata
    ? 'Write roles exist in catalog metadata, but mutation endpoints are not available in the current backend contract.'
    : 'This entity does not advertise write access in catalog metadata.'

  const loadRecords = useCallback(async () => {
    if (!entity) return

    setLoading(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      params.set('limit', String(PAGE_SIZE))
      params.set('offset', String(offset))
      if (filters.orderBy) {
        params.set('orderBy', filters.orderBy)
        params.set('orderDir', filters.orderDir)
      }
      if (filters.field && filters.value) {
        params.set(filters.field, filters.value)
      }

      const result = await api.listEntity(selection.entityId, params)
      const nextRows = result.rows ?? []
      setRows(nextRows)
      setSchemaFields(
        result.schema?.columns?.map((column) => column.name).filter(Boolean) ??
          entity.fields,
      )
      setRowCount(result.metadata?.rowCount ?? nextRows.length)
    } catch (unknownError) {
      setRows([])
      setRowCount(0)
      setError(
        unknownError instanceof ApiError
          ? unknownError
          : new ApiError('Entity records could not be loaded.', 'http'),
      )
    } finally {
      setLoading(false)
    }
  }, [api, entity, filters, offset, selection.entityId])

  useEffect(() => {
    setFilters({ orderDir: 'asc' })
    setRows([])
    setSchemaFields([])
    setSelectedRecord(null)
    setOffset(0)
    setReloadToken((value) => value + 1)
  }, [selection.entityId])

  useEffect(() => {
    void loadRecords()
  }, [loadRecords, reloadToken])

  const visibleRows = useMemo(() => {
    const search = filters.search?.trim().toLowerCase()
    if (!search) return rows

    return rows.filter((row) =>
      fields.some((field) =>
        String(row[field] ?? '')
          .toLowerCase()
          .includes(search),
      ),
    )
  }, [fields, filters.search, rows])

  const columns: ColumnsType<Record<string, unknown>> = useMemo(
    () =>
      fields.map((field) => ({
        title: (
          <Space size={6}>
            <span>{field}</span>
            {entity?.primaryKey.includes(field) && <Tag color="gold">PK</Tag>}
          </Space>
        ),
        dataIndex: field,
        key: field,
        ellipsis: true,
        render: formatValue,
      })),
    [entity?.primaryKey, fields],
  )

  if (!entity) {
    return <Empty description="Select an entity to browse its records." />
  }

  return (
    <div className="entity-browser">
      <div className="runner-heading">
        <div>
          <Typography.Text className="eyebrow">Entity browser</Typography.Text>
          <Space align="baseline" wrap>
            <Typography.Title level={2}>
              {entity.label || selection.entityId}
            </Typography.Title>
            <Tag>{entity.table}</Tag>
          </Space>
          {entity.description && (
            <Typography.Paragraph type="secondary">
              {entity.description}
            </Typography.Paragraph>
          )}
        </div>
        <div className="datasource-chip">
          <Typography.Text type="secondary">Datasource</Typography.Text>
          <Typography.Text strong>{entity.datasource}</Typography.Text>
        </div>
      </div>

      <Card className="entity-toolbar-card">
        <Form layout="vertical">
          <div className="entity-toolbar">
            <Form.Item label="Search loaded records">
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="Search any visible field"
                value={filters.search}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    search: event.target.value,
                  }))
                }
              />
            </Form.Item>

            <Form.Item label="Backend filter field">
              <Select
                allowClear
                placeholder="Field"
                value={filters.field}
                onChange={(field) =>
                  setFilters((current) => ({ ...current, field }))
                }
                options={entity.fields.map((field) => ({
                  value: field,
                  label: field,
                }))}
              />
            </Form.Item>

            <Form.Item label="Backend filter value">
              <Input
                allowClear
                placeholder="Exact value"
                value={filters.value}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    value: event.target.value,
                  }))
                }
                onPressEnter={() => {
                  setOffset(0)
                  setReloadToken((value) => value + 1)
                }}
              />
            </Form.Item>

            <Form.Item label="Sort">
              <Space.Compact block>
                <Select
                  allowClear
                  placeholder="Order by"
                  value={filters.orderBy}
                  onChange={(orderBy) =>
                    setFilters((current) => ({ ...current, orderBy }))
                  }
                  options={entity.fields.map((field) => ({
                    value: field,
                    label: field,
                  }))}
                />
                <Select
                  className="order-dir-select"
                  value={filters.orderDir}
                  onChange={(orderDir) =>
                    setFilters((current) => ({ ...current, orderDir }))
                  }
                  options={[
                    { value: 'asc', label: 'Asc' },
                    { value: 'desc', label: 'Desc' },
                  ]}
                />
              </Space.Compact>
            </Form.Item>

            <Form.Item label="Actions">
              <Space wrap>
                <Button
                  type="primary"
                  icon={<ReloadOutlined />}
                  loading={loading}
                  onClick={() => {
                    setOffset(0)
                    setReloadToken((value) => value + 1)
                  }}
                >
                  Apply
                </Button>
                <Tooltip title={mutationUnavailableMessage}>
                  <Button icon={<PlusOutlined />} disabled>
                    Create
                  </Button>
                </Tooltip>
              </Space>
            </Form.Item>
          </div>
        </Form>
      </Card>

      {error && (
        <Alert
          className="catalog-alert"
          type={error.kind === 'forbidden' ? 'error' : 'warning'}
          showIcon
          message="Entity records unavailable"
          description={error.message}
        />
      )}

      <Card
        className="entity-table-card"
        title={
          <Space wrap>
            <span>Records</span>
            <Tag>{visibleRows.length} loaded</Tag>
            {primaryKey && <Tag color="blue">Primary key: {primaryKey}</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Button
              disabled={offset === 0 || loading}
              onClick={() => setOffset((value) => Math.max(0, value - PAGE_SIZE))}
            >
              Previous
            </Button>
            <Button
              disabled={rows.length < PAGE_SIZE || loading}
              onClick={() => setOffset((value) => value + PAGE_SIZE)}
            >
              Next
            </Button>
          </Space>
        }
      >
        {loading ? (
          <div className="result-empty">
            <Spin size="large" />
            <Typography.Text type="secondary">Loading entity records…</Typography.Text>
          </div>
        ) : (
          <Table
            className="result-table"
            columns={columns}
            dataSource={visibleRows.map((row, index) => ({
              ...row,
              key:
                primaryKey && row[primaryKey] !== undefined
                  ? String(row[primaryKey])
                  : `${offset}-${index}`,
            }))}
            locale={{
              emptyText: (
                <Empty description="No records match the current filters." />
              ),
            }}
            pagination={{
              pageSize: 25,
              showSizeChanger: true,
              showTotal: (total) => `${total} record${total === 1 ? '' : 's'}`,
            }}
            scroll={{ x: 'max-content' }}
            size="small"
            onRow={(record) => ({
              onClick: () => setSelectedRecord(record),
            })}
          />
        )}
        <Typography.Text className="api-base-note" type="secondary">
          Backend returned {rowCount} row{rowCount === 1 ? '' : 's'} for this page.
          Click a row to inspect the full record.
        </Typography.Text>
      </Card>

      <Drawer
        title={selectedRecord ? `${entity.label || selection.entityId} detail` : 'Detail'}
        open={Boolean(selectedRecord)}
        width={560}
        onClose={() => setSelectedRecord(null)}
        extra={
          <Space>
            <Tooltip title={mutationUnavailableMessage}>
              <Button icon={<EditOutlined />} disabled>
                Edit
              </Button>
            </Tooltip>
            <Tooltip title={mutationUnavailableMessage}>
              <Button danger icon={<DeleteOutlined />} disabled>
                Delete
              </Button>
            </Tooltip>
          </Space>
        }
      >
        {selectedRecord && (
          <Descriptions bordered column={1} size="small">
            {fields.map((field) => (
              <Descriptions.Item
                key={field}
                label={
                  <Space>
                    <span>{field}</span>
                    {entity.primaryKey.includes(field) && <Tag color="gold">PK</Tag>}
                  </Space>
                }
              >
                {formatValue(selectedRecord[field])}
              </Descriptions.Item>
            ))}
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}
