import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import { Alert, Space, Tag, Typography } from 'antd'

export interface ExecutionResult {
  body: string
  contentType: string
  durationMs: number
  format: 'json' | 'csv' | 'xml' | 'html'
  ok: boolean
  status: number
  statusText: string
  url: string
}

interface ResultViewerProps {
  result: ExecutionResult
}

export function ResultViewer({ result }: ResultViewerProps) {
  let displayBody = result.body
  let jsonParseError = false

  if (result.format === 'json' && result.body) {
    try {
      displayBody = JSON.stringify(JSON.parse(result.body), null, 2)
    } catch {
      jsonParseError = true
    }
  }

  return (
    <div className="result-viewer">
      <div className="result-meta">
        <Space wrap>
          <Tag
            color={result.ok ? 'success' : 'error'}
            icon={result.ok ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          >
            {result.status || '—'} {result.statusText}
          </Tag>
          <Tag>{result.durationMs} ms</Tag>
          {result.contentType && <Tag>{result.contentType}</Tag>}
        </Space>
        <Typography.Text type="secondary" copyable={{ text: result.url }}>
          {result.url}
        </Typography.Text>
      </div>

      {!result.ok && (
        <Alert
          type="error"
          showIcon
          message={result.status === 0 ? 'Backend unavailable' : 'Request failed'}
          description={
            result.status === 0
              ? 'Check that the API is running and that VITE_API_BASE_URL is correct.'
              : `The backend returned ${result.status} ${result.statusText}.`
          }
        />
      )}

      {jsonParseError && result.ok && (
        <Alert
          type="warning"
          showIcon
          message="The response was not valid JSON"
          description="Showing the response as plain text."
        />
      )}

      {result.format === 'html' && result.ok ? (
        <iframe
          className="html-preview"
          title="Operation HTML response"
          sandbox=""
          srcDoc={result.body}
        />
      ) : (
        <pre className="code-result">{displayBody || '(empty response)'}</pre>
      )}
    </div>
  )
}
