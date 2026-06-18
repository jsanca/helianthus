import {
  ArrowRightOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { Alert, Button, Card, Typography } from 'antd'
import { useAuth } from '../auth/AuthContext'

export function LoginScreen() {
  const { login, continueAsGuest, error } = useAuth()
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
          <span>Secure sign-in is provided by Keycloak using authorization code + PKCE.</span>
        </div>
      </section>

      <Card className="login-card" bordered={false}>
        <Typography.Text className="eyebrow">Admin console</Typography.Text>
        <Typography.Title level={2}>Welcome back</Typography.Title>
        <Typography.Paragraph type="secondary">
          Sign in to load the operations available to your account.
        </Typography.Paragraph>

        {error && (
          <Alert
            className="login-alert"
            type="warning"
            showIcon
            message="Identity provider unavailable"
            description={error}
          />
        )}

        <Button
          type="primary"
          block
          icon={<ArrowRightOutlined />}
          onClick={() => void login()}
        >
          Sign in with Keycloak
        </Button>

        <div className="login-divider">
          <span>or</span>
        </div>

        <Button block onClick={continueAsGuest}>
          Continue as guest
        </Button>
        <Typography.Paragraph className="auth-note" type="secondary">
          Guest mode can check health, but protected catalog and operation requests may
          require sign-in.
        </Typography.Paragraph>
      </Card>
    </main>
  )
}
