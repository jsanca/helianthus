import {
  LinkOutlined,
  SendOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'
import { ApiError, type ApiClient } from '../api/client'
import { appConfig } from '../config'
import type { Catalog, OperationSelection } from '../types/catalog'
import { ParameterInput } from './ParameterInput'
import { ResultViewer, type ExecutionResult } from './ResultViewer'

type ResultFormat = 'json' | 'csv' | 'xml' | 'html'

interface ParameterValues {
  [key: string]: string | number | boolean | null
}

interface OperationRunnerProps {
  catalog: Catalog
  selection: OperationSelection
  api: ApiClient
}

export function OperationRunner({ catalog, selection, api }: OperationRunnerProps) {
  const operation = catalog.operations[selection.operationId]
  const configuration = operation.configurations[selection.configurationId]
  const [format, setFormat] = useState<ResultFormat>('json')
  const [parameterValues, setParameterValues] = useState<ParameterValues>({})
  const [result, setResult] = useState<ExecutionResult | null>(null)
  const [loading, setLoading] = useState(false)

  const parameters = operation.parameters || []

  const requestUrl = useMemo(() => {
    const path = `/api/op/${encodeURIComponent(selection.operationId)}/${encodeURIComponent(
      selection.configurationId,
    )}.${format}`
    const query = new URLSearchParams()
    Object.entries(parameterValues).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        query.append(key, String(value))
      }
    })
    const queryString = query.toString()
    return `${appConfig.apiBaseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }, [format, parameterValues, selection])

  const updateParameter = (name: string, value: string | number | boolean | null) => {
    setParameterValues((prev) => ({ ...prev, [name]: value }))
  }

  const execute = async () => {
    setLoading(true)
    setResult(null)
    const startedAt = performance.now()

    try {
      const params = new URLSearchParams()
      Object.entries(parameterValues).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== '') {
          params.append(key, String(value))
        }
      })
      const { response, url } = await api.execute(
        selection.operationId,
        selection.configurationId,
        format,
        params,
      )
      const body = await response.text()
      setResult({
        body,
        contentType: response.headers.get('content-type') ?? '',
        durationMs: Math.round(performance.now() - startedAt),
        format,
        ok: response.ok,
        status: response.status,
        statusText: response.statusText,
        url,
      })
    } catch (error) {
      const apiError = error instanceof ApiError ? error : null
      setResult({
        body:
          apiError?.body ||
          (error instanceof Error
            ? error.message
            : 'The request could not be completed.'),
        contentType: 'text/plain',
        durationMs: Math.round(performance.now() - startedAt),
        format,
        ok: false,
        status: apiError?.status ?? 0,
        statusText:
          apiError?.kind === 'unauthenticated'
            ? 'Unauthenticated'
            : apiError?.kind === 'forbidden'
              ? 'Forbidden'
              : 'Network error',
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
            <Typography.Title level={2}>
              {operation.label || selection.operationId}
            </Typography.Title>
            <Tag>{selection.configurationId}</Tag>
          </Space>
          {operation.description && (
            <Typography.Paragraph type="secondary">
              {operation.description}
            </Typography.Paragraph>
          )}
        </div>
        <div className="datasource-chip">
          <Typography.Text type="secondary">Datasource</Typography.Text>
          <Typography.Text strong>{operation.datasource ?? 'default'}</Typography.Text>
        </div>
      </div>

      <Row gutter={[20, 20]}>
        <Col xs={24} xl={15}>
          <Card title="Parameters">
            {parameters.length === 0 ? (
              <Typography.Text type="secondary">
                This operation has no parameters.
              </Typography.Text>
            ) : (
              <Form layout="vertical">
                {parameters.map((param) => (
                  <Form.Item
                    key={param.name}
                    label={
                      <Space direction="vertical" size={0}>
                        <Typography.Text strong>
                          {param.label || param.name}
                          {param.required && <span style={{ color: '#ff4d4f' }}> *</span>}
                        </Typography.Text>
                        {param.description && (
                          <Typography.Text type="secondary" style={{ fontSize: '12px' }}>
                            {param.description}
                          </Typography.Text>
                        )}
                      </Space>
                    }
                  >
                    <ParameterInput
                      parameter={param}
                      value={parameterValues[param.name] ?? null}
                      onChange={(value) => updateParameter(param.name, value)}
                    />
                  </Form.Item>
                ))}
              </Form>
            )}
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
              API base: {appConfig.apiBaseUrl}
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
