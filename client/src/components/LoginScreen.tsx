import {
  ArrowRightOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Button, Card, Form, Input, Typography } from 'antd'
import type { SessionMode } from '../App'

interface LoginScreenProps {
  onLogin: (mode: SessionMode) => void
}

export function LoginScreen({ onLogin }: LoginScreenProps) {
  return (
    <main className="login-shell">
      <section className="login-intro">
        <div className="brand-mark" aria-hidden="true">
          H
        </div>
        <Typography.Text className="eyebrow">Helianthus</Typography.Text>
        <Typography.Title>Put your operations within reach.</Typography.Title>
        <Typography.Paragraph>
          Browse the catalog, configure a request, and inspect the response from
          one focused workspace.
        </Typography.Paragraph>
        <div className="login-feature">
          <SafetyCertificateOutlined />
          <span>Authentication wiring is coming later. This screen is local only.</span>
        </div>
      </section>

      <Card className="login-card" bordered={false}>
        <Typography.Text className="eyebrow">Admin console</Typography.Text>
        <Typography.Title level={2}>Welcome back</Typography.Title>
        <Typography.Paragraph type="secondary">
          Use any credentials to enter the admin placeholder.
        </Typography.Paragraph>

        <Form layout="vertical" onFinish={() => onLogin('admin')} requiredMark={false}>
          <Form.Item
            label="Username"
            name="username"
            rules={[{ required: true, message: 'Enter a username' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="admin" autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="Password"
            name="password"
            rules={[{ required: true, message: 'Enter a password' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </Form.Item>
          <Button type="primary" htmlType="submit" block icon={<ArrowRightOutlined />}>
            Sign in
          </Button>
        </Form>

        <div className="login-divider">
          <span>or</span>
        </div>

        <Button block onClick={() => onLogin('guest')}>
          Continue as guest
        </Button>
        <Typography.Paragraph className="auth-note" type="secondary">
          No account is created and no credentials are sent.
        </Typography.Paragraph>
      </Card>
    </main>
  )
}
