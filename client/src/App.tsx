import { useState } from 'react'
import { AdminLayout } from './components/AdminLayout'
import { LoginScreen } from './components/LoginScreen'

export type SessionMode = 'guest' | 'admin'

function App() {
  const [mode, setMode] = useState<SessionMode | null>(null)

  if (!mode) {
    return <LoginScreen onLogin={setMode} />
  }

  return <AdminLayout mode={mode} onLogout={() => setMode(null)} />
}

export default App
