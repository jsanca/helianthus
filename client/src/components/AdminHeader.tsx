import {
  LoginOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Button, Layout, Space, Tag, Tooltip, Typography } from 'antd'
import type { useAuth } from '../auth/AuthContext'
import type { Catalog } from '../types/catalog'
import { HealthStatus } from './HealthStatus'

const { Header } = Layout

interface AdminHeaderProps {
  auth: ReturnType<typeof useAuth>
  catalog: Catalog | null
}

export function AdminHeader({ auth, catalog }: AdminHeaderProps) {
  return (
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
  )
}
