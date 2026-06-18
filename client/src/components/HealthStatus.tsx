import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Button, Space, Tag, Tooltip } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useApi } from '../api/ApiContext'

type HealthState = 'checking' | 'up' | 'down'

export function HealthStatus() {
  const api = useApi()
  const [state, setState] = useState<HealthState>('checking')

  const check = useCallback(async (showChecking = true) => {
    if (showChecking) setState('checking')
    try {
      const health = await api.health()
      setState(health.status === 'ok' ? 'up' : 'down')
    } catch {
      setState('down')
    }
  }, [api])

  useEffect(() => {
    let active = true
    void api
      .health()
      .then((health) => {
        if (active) setState(health.status === 'ok' ? 'up' : 'down')
      })
      .catch(() => {
        if (active) setState('down')
      })
    return () => {
      active = false
    }
  }, [api])

  return (
    <Space size={4}>
      <Tag
        color={state === 'up' ? 'success' : state === 'down' ? 'error' : 'default'}
        icon={
          state === 'up' ? (
            <CheckCircleOutlined />
          ) : state === 'down' ? (
            <CloseCircleOutlined />
          ) : (
            <LoadingOutlined />
          )
        }
      >
        Backend {state === 'up' ? 'online' : state === 'down' ? 'offline' : 'checking'}
      </Tag>
      <Tooltip title="Check backend health">
        <Button
          size="small"
          type="text"
          icon={<ReloadOutlined />}
          onClick={() => void check()}
          loading={state === 'checking'}
        />
      </Tooltip>
    </Space>
  )
}
