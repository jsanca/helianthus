import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  CopyOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileTextOutlined,
  TableOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  message,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo } from 'react'

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

interface ResultFrameData {
  schema?: {
    columns: Array<{ name: string; type?: string; nullable?: boolean }>
  }
  rows?: Array<Record<string, unknown>>
  metadata?: {
    rowCount?: number
    durationMs?: number
  }
}

interface GridData {
  columns: Array<{ name: string; type?: string }>
  rows: Array<Record<string, unknown>>
  rowCount?: number
}

const formatCell = (value: unknown) => {
  if (value === null || value === undefined) {
    return <Typography.Text type="secondary">null</Typography.Text>
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function GridView({ grid }: { grid: GridData | null }) {
  if (!grid || grid.columns.length === 0) {
    return (
      <Alert
        type="info"
        showIcon
        message="Table view not available"
        description="This response does not contain a recognizable tabular structure."
      />
    )
  }

  const columns: ColumnsType<Record<string, unknown> & { key: number }> =
    grid.columns.map((column) => ({
      title: (
        <Space direction="vertical" size={0}>
          <span>{column.name}</span>
          {column.type && (
            <Typography.Text type="secondary" className="column-type">
              {column.type}
            </Typography.Text>
          )}
        </Space>
      ),
      dataIndex: column.name,
      key: column.name,
      ellipsis: true,
      render: formatCell,
    }))

  return (
    <Table
      className="result-table"
      columns={columns}
      dataSource={grid.rows.map((row, index) => ({ key: index, ...row }))}
      pagination={{
        pageSize: 50,
        showSizeChanger: true,
        showTotal: (total) => `${total} row${total === 1 ? '' : 's'}`,
      }}
      size="small"
      scroll={{ x: 'max-content' }}
    />
  )
}

function parseCsv(source: string): GridData | null {
  const records: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false

  for (let index = 0; index < source.length; index += 1) {
    const character = source[index]
    if (quoted) {
      if (character === '"' && source[index + 1] === '"') {
        field += '"'
        index += 1
      } else if (character === '"') {
        quoted = false
      } else {
        field += character
      }
    } else if (character === '"') {
      quoted = true
    } else if (character === ',') {
      row.push(field)
      field = ''
    } else if (character === '\n') {
      row.push(field.replace(/\r$/, ''))
      records.push(row)
      row = []
      field = ''
    } else {
      field += character
    }
  }

  if (field || row.length) {
    row.push(field.replace(/\r$/, ''))
    records.push(row)
  }
  if (records.length === 0) return null

  const headers = records[0]
  return {
    columns: headers.map((name) => ({ name })),
    rows: records.slice(1).map((values) =>
      Object.fromEntries(headers.map((header, index) => [header, values[index] ?? ''])),
    ),
    rowCount: Math.max(0, records.length - 1),
  }
}

function parseXml(source: string): { pretty: string; grid: GridData | null } {
  try {
    const document = new DOMParser().parseFromString(source, 'application/xml')
    if (document.querySelector('parsererror')) throw new Error('Invalid XML')

    const serialized = new XMLSerializer()
      .serializeToString(document)
      .replace(/(>)(<)(\/*)/g, '$1\n$2$3')
    let depth = 0
    const pretty = serialized
      .split('\n')
      .map((line) => {
        const trimmed = line.trim()
        if (/^<\//.test(trimmed)) depth = Math.max(0, depth - 1)
        const indented = `${'  '.repeat(depth)}${trimmed}`
        if (
          /^<[^!?/][^>]*>$/.test(trimmed) &&
          !/<\/[^>]+>$/.test(trimmed) &&
          !/\/>$/.test(trimmed)
        ) {
          depth += 1
        }
        return indented
      })
      .join('\n')

    const rowElements = Array.from(document.querySelectorAll('rows > row'))
    if (rowElements.length === 0) return { pretty, grid: null }

    const columnNames = Array.from(
      new Set(
        rowElements.flatMap((row) =>
          Array.from(row.children).map((element) => element.tagName),
        ),
      ),
    )
    const rows = rowElements.map((row) =>
      Object.fromEntries(
        columnNames.map((name) => [name, row.querySelector(`:scope > ${CSS.escape(name)}`)?.textContent ?? '']),
      ),
    )
    const declaredCount = Number(
      document.querySelector('metadata > rowCount')?.textContent,
    )
    return {
      pretty,
      grid: {
        columns: columnNames.map((name) => ({ name })),
        rows,
        rowCount: Number.isFinite(declaredCount) ? declaredCount : rows.length,
      },
    }
  } catch {
    return { pretty: source, grid: null }
  }
}

function prettyHtml(source: string) {
  try {
    const document = new DOMParser().parseFromString(source, 'text/html')
    return `<!DOCTYPE html>\n${document.documentElement.outerHTML}`.replace(
      /(>)(<)(\/*)/g,
      '$1\n$2$3',
    )
  } catch {
    return source
  }
}

function sourceExtension(format: ExecutionResult['format']) {
  return format === 'json'
    ? 'json'
    : format === 'csv'
      ? 'csv'
      : format === 'xml'
        ? 'xml'
        : 'html'
}

export function ResultViewer({ result }: ResultViewerProps) {
  const [messageApi, contextHolder] = message.useMessage()

  const parsed = useMemo(() => {
    if (!result.ok) return { source: result.body, grid: null, parseError: false }

    if (result.format === 'json') {
      try {
        const json = JSON.parse(result.body) as ResultFrameData
        return {
          source: JSON.stringify(json, null, 2),
          grid:
            json.schema?.columns && json.rows
              ? {
                  columns: json.schema.columns,
                  rows: json.rows,
                  rowCount: json.metadata?.rowCount ?? json.rows.length,
                }
              : null,
          parseError: false,
          serverDurationMs: json.metadata?.durationMs,
        }
      } catch {
        return { source: result.body, grid: null, parseError: true }
      }
    }
    if (result.format === 'csv') {
      return { source: result.body, grid: parseCsv(result.body), parseError: false }
    }
    if (result.format === 'xml') {
      const xml = parseXml(result.body)
      return { source: xml.pretty, grid: xml.grid, parseError: xml.grid === null }
    }
    return {
      source: prettyHtml(result.body),
      grid: null,
      parseError: false,
    }
  }, [result])

  const filename = useMemo(() => {
    const path = new URL(result.url).pathname
    const operation = path.split('/').pop()?.replace(/\.[^.]+$/, '') ?? 'result'
    return `${operation}.${sourceExtension(result.format)}`
  }, [result.format, result.url])

  const copySource = async () => {
    try {
      await navigator.clipboard.writeText(parsed.source)
      void messageApi.success('Response copied')
    } catch {
      void messageApi.error('Could not copy the response')
    }
  }

  const downloadSource = () => {
    const blob = new Blob([result.body], {
      type: result.contentType || 'text/plain;charset=utf-8',
    })
    const href = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = href
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(href)
  }

  const sourceLabel = result.format.toUpperCase()
  const items = result.ok
    ? result.format === 'html'
      ? [
          {
            key: 'preview',
            label: (
              <Space>
                <EyeOutlined />
                Preview
              </Space>
            ),
            children: (
              <iframe
                className="html-preview"
                title="Operation HTML response"
                sandbox=""
                srcDoc={result.body}
              />
            ),
          },
          {
            key: 'source',
            label: (
              <Space>
                <CodeOutlined />
                HTML source
              </Space>
            ),
            children: <pre className="code-result">{parsed.source}</pre>,
          },
        ]
      : [
          {
            key: 'table',
            label: (
              <Space>
                <TableOutlined />
                Table
              </Space>
            ),
            children: <GridView grid={parsed.grid} />,
          },
          {
            key: 'source',
            label: (
              <Space>
                <FileTextOutlined />
                {sourceLabel} source
              </Space>
            ),
            children: <pre className="code-result">{parsed.source || '(empty response)'}</pre>,
          },
        ]
    : []

  return (
    <div className="result-viewer">
      {contextHolder}
      <div className="result-meta">
        <Space wrap>
          <Tag
            color={result.ok ? 'success' : 'error'}
            icon={result.ok ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          >
            {result.status || '—'} {result.statusText}
          </Tag>
          <Tag color="blue">{sourceLabel}</Tag>
          <Tag>{result.durationMs} ms round trip</Tag>
          {parsed.serverDurationMs !== undefined && (
            <Tag>{parsed.serverDurationMs} ms query</Tag>
          )}
          {parsed.grid?.rowCount !== undefined && (
            <Tag color="geekblue">
              {parsed.grid.rowCount} row{parsed.grid.rowCount === 1 ? '' : 's'}
            </Tag>
          )}
          {parsed.grid?.columns.length !== undefined && (
            <Tag>{parsed.grid.columns.length} columns</Tag>
          )}
          {result.contentType && <Tag>{result.contentType.split(';')[0]}</Tag>}
        </Space>
        <Space>
          <Tooltip title="Copy response source">
            <Button icon={<CopyOutlined />} onClick={() => void copySource()}>
              Copy
            </Button>
          </Tooltip>
          <Tooltip title={`Download ${filename}`}>
            <Button icon={<DownloadOutlined />} onClick={downloadSource}>
              Download
            </Button>
          </Tooltip>
        </Space>
      </div>

      <Typography.Text className="result-url" type="secondary" copyable={{ text: result.url }}>
        {result.url}
      </Typography.Text>

      {!result.ok && (
        <>
          <Alert
            type="error"
            showIcon
            message={
              result.status === 0
                ? 'Backend unavailable'
                : result.status === 401
                  ? 'Authentication required'
                  : result.status === 403
                    ? 'Permission denied'
                    : 'Request failed'
            }
            description={
              result.status === 0
                ? 'Check that the API is running and that VITE_API_BASE_URL is correct.'
                : result.status === 401
                  ? 'Sign in with Keycloak and try the operation again.'
                  : result.status === 403
                    ? 'Your Keycloak roles do not permit this operation or configuration.'
                    : `The backend returned ${result.status} ${result.statusText}.`
            }
          />
          <pre className="code-result">{result.body || '(empty response)'}</pre>
        </>
      )}

      {parsed.parseError && result.ok && (
        <Alert
          type="warning"
          showIcon
          message={`${sourceLabel} could not be converted to a table`}
          description={`The formatted ${sourceLabel} source remains available.`}
        />
      )}

      {result.ok && (
        <Tabs
          className="result-tabs"
          defaultActiveKey={result.format === 'html' ? 'preview' : 'table'}
          items={items}
        />
      )}
    </div>
  )
}
