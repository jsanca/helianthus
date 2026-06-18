import { Spin, Typography } from 'antd'
import { useAuth } from './auth/AuthContext'
import { AdminLayout } from './components/AdminLayout'
import { LoginScreen } from './components/LoginScreen'

function App() {
  const auth = useAuth()

  if (auth.status === 'initializing') {
    return (
      <main className="auth-loading">
        <div className="brand-mark">H</div>
        <Spin size="large" />
        <Typography.Text>Connecting to Helianthus…</Typography.Text>
      </main>
    )
  }

  if (!auth.mode) {
    return <LoginScreen />
  }

  return <AdminLayout />
}

export default App
