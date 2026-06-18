import {
  DeleteOutlined,
  LinkOutlined,
  PlusOutlined,
  SendOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Col,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'
import type { Catalog, OperationSelection } from '../types/catalog'
import { ResultViewer, type ExecutionResult } from './ResultViewer'

type ResultFormat = 'json' | 'csv' | 'xml' | 'html'

interface ParameterRow {
  id: number
  key: string
  value: string
}

interface OperationRunnerProps {
  catalog: Catalog
  selection: OperationSelection
}

const createEmptyRows = (count: number): ParameterRow[] =>
  Array.from({ length: count }, (_, index) => ({
    id: Date.now() + index,
    key: '',
    value: '',
  }))

const createParameterRows = (
  parameters: Array<{ name?: string }> = [],
): ParameterRow[] => {
  const rows = createEmptyRows(Math.max(5, parameters.length))
  parameters.forEach((parameter, index) => {
    rows[index].key = parameter.name ?? ''
  })
  return rows
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(
  /\/$/,
  '',
)

export function OperationRunner({ catalog, selection }: OperationRunnerProps) {
  const operation = catalog.operations[selection.operationId]
  const configuration = operation.configurations[selection.configurationId]
  const [format, setFormat] = useState<ResultFormat>('json')
  const [parameters, setParameters] = useState<ParameterRow[]>(() =>
    createParameterRows(operation.parameters),
  )
  const [result, setResult] = useState<ExecutionResult | null>(null)
  const [loading, setLoading] = useState(false)

  const requestUrl = useMemo(() => {
    const path = `/api/op/${encodeURIComponent(selection.operationId)}/${encodeURIComponent(
      selection.configurationId,
    )}.${format}`
    const query = new URLSearchParams()
    parameters.forEach((parameter) => {
      if (parameter.key.trim()) {
        query.append(parameter.key.trim(), parameter.value)
      }
    })
    const queryString = query.toString()
    return `${apiBaseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }, [format, parameters, selection])

  const updateParameter = (
    id: number,
    field: 'key' | 'value',
    value: string,
  ) => {
    setParameters((rows) =>
      rows.map((row) => (row.id === id ? { ...row, [field]: value } : row)),
    )
  }

  const execute = async () => {
    setLoading(true)
    setResult(null)
    const startedAt = performance.now()

    try {
      const response = await fetch(requestUrl, {
        headers: {
          Accept:
            format === 'json'
              ? 'application/json'
              : format === 'html'
                ? 'text/html'
                : format === 'csv'
                  ? 'text/csv'
                  : 'application/xml',
        },
      })
      const body = await response.text()
      setResult({
        body,
        contentType: response.headers.get('content-type') ?? '',
        durationMs: Math.round(performance.now() - startedAt),
        format,
        ok: response.ok,
        status: response.status,
        statusText: response.statusText,
        url: requestUrl,
      })
    } catch (error) {
      setResult({
        body: error instanceof Error ? error.message : 'The request could not be completed.',
        contentType: 'text/plain',
        durationMs: Math.round(performance.now() - startedAt),
        format,
        ok: false,
        status: 0,
        statusText: 'Network error',
        url: requestUrl,
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="runner">
      <div className="runner-heading">
        <div>
          <Typography.Text className="eyebrow">Operation runner</Typography.Text>
          <Space align="baseline" wrap>
            <Typography.Title level={2}>{selection.operationId}</Typography.Title>
            <Tag>{selection.configurationId}</Tag>
          </Space>
          <Typography.Paragraph type="secondary">
            Configure the request, choose a representation, and run it against the API.
          </Typography.Paragraph>
        </div>
        <div className="datasource-chip">
          <Typography.Text type="secondary">Datasource</Typography.Text>
          <Typography.Text strong>{operation.datasource ?? 'default'}</Typography.Text>
        </div>
      </div>

      <Row gutter={[20, 20]}>
        <Col xs={24} xl={15}>
          <Card
            title="Parameters"
            extra={
              <Button
                type="text"
                icon={<PlusOutlined />}
                onClick={() =>
                  setParameters((rows) => [...rows, ...createEmptyRows(1)])
                }
              >
                Add row
              </Button>
            }
          >
            <div className="parameter-header" aria-hidden="true">
              <span>Key</span>
              <span>Value</span>
              <span />
            </div>
            <div className="parameter-list">
              {parameters.map((parameter, index) => (
                <div className="parameter-row" key={parameter.id}>
                  <Input
                    aria-label={`Parameter ${index + 1} key`}
                    placeholder="parameter"
                    value={parameter.key}
                    onChange={(event) =>
                      updateParameter(parameter.id, 'key', event.target.value)
                    }
                  />
                  <Input
                    aria-label={`Parameter ${index + 1} value`}
                    placeholder="value"
                    value={parameter.value}
                    onChange={(event) =>
                      updateParameter(parameter.id, 'value', event.target.value)
                    }
                    onPressEnter={execute}
                  />
                  <Button
                    aria-label={`Remove parameter row ${index + 1}`}
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={parameters.length === 1}
                    onClick={() =>
                      setParameters((rows) =>
                        rows.filter((row) => row.id !== parameter.id),
                      )
                    }
                  />
                </div>
              ))}
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <Card title="Request" className="request-card">
            <label className="field-label" htmlFor="format-select">
              Response format
            </label>
            <Select
              id="format-select"
              value={format}
              onChange={setFormat}
              options={[
                { value: 'json', label: 'JSON' },
                { value: 'csv', label: 'CSV' },
                { value: 'xml', label: 'XML' },
                { value: 'html', label: 'HTML' },
              ]}
            />

            <div className="request-preview">
              <Space>
                <LinkOutlined />
                <Typography.Text strong>Request URL</Typography.Text>
              </Space>
              <Typography.Text code copyable={{ text: requestUrl }}>
                {requestUrl}
              </Typography.Text>
            </div>

            <Button
              type="primary"
              size="large"
              block
              icon={<SendOutlined />}
              loading={loading}
              onClick={execute}
            >
              Execute operation
            </Button>
            <Typography.Text className="api-base-note" type="secondary">
              API base: {apiBaseUrl}
            </Typography.Text>
          </Card>
        </Col>
      </Row>

      <Card className="result-card" title="Result">
        {loading ? (
          <div className="result-empty">
            <Spin size="large" />
            <Typography.Text type="secondary">Waiting for the operation…</Typography.Text>
          </div>
        ) : result ? (
          <ResultViewer result={result} />
        ) : (
          <Alert
            type="info"
            showIcon
            message="Ready to run"
            description={`The ${selection.configurationId} configuration has ${
              configuration.pipeline?.length ?? 0
            } pipeline step(s). Its response will appear here.`}
          />
        )}
      </Card>
    </div>
  )
}
