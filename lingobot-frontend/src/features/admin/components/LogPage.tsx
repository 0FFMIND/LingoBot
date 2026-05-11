import { useState, useEffect } from 'react'
import { UserDTO } from '../../../types'
import { authUtils, authApi } from '../../../api'
import AdminLogin from './AdminLogin'
import LogViewer from './LogViewer'

function LogPage() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [currentUser, setCurrentUser] = useState<UserDTO | null>(null)
  const [initializing, setInitializing] = useState(true)

  useEffect(() => {
    const init = async () => {
      try {
        const devRes = await fetch('/api/logs/dev-check')
        const isDev = await devRes.json()
        if (isDev) {
          setIsAuthenticated(true)
          setInitializing(false)
          return
        }
      } catch {
        // dev-check 失败则回退到正常认证流程
      }

      const token = authUtils.initializeAuth()
      if (token) {
        authApi.getCurrentUser()
          .then((user) => {
            if (user.role === 'ROLE_ADMIN') {
              authUtils.setUser(user)
              setIsAuthenticated(true)
              setCurrentUser(user)
            }
          })
          .catch(() => {
            authUtils.clearAuth()
          })
          .finally(() => setInitializing(false))
      } else {
        setInitializing(false)
      }
    }

    init()
  }, [])

  const handleLoginSuccess = (user: UserDTO) => {
    setIsAuthenticated(true)
    setCurrentUser(user)
  }

  const handleLogout = () => {
    setIsAuthenticated(false)
    setCurrentUser(null)
  }

  if (initializing) {
    return (
      <div className="log-page">
        <div className="admin-loading-full">
          <div className="loading-spinner"></div>
          <p>加载中...</p>
        </div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <div className="admin-page">
        <div className="admin-login-container">
          <div className="admin-login-header">
            <h1>管理员登录</h1>
            <p>需要管理员权限才能查看日志</p>
          </div>
          <AdminLogin onLoginSuccess={handleLoginSuccess} />
        </div>
      </div>
    )
  }

  return (
    <div className="log-dashboard">
      <div className="log-header">
        <div className="log-header-left">
          <h1>📋 后端日志查看器</h1>
          <span className="admin-status-badge">
            实时日志监控
          </span>
        </div>
        <div className="admin-header-right">
          <span className="admin-user-info">
            管理员: {currentUser?.username}
          </span>
          <button
            className="admin-logout-btn"
            onClick={() => {
              authApi.logout()
              handleLogout()
            }}
          >
            退出登录
          </button>
          <button
            className="back-to-chat-btn-styled"
            onClick={() => {
              window.history.pushState({}, '', '/')
              window.dispatchEvent(new PopStateEvent('popstate'))
            }}
          >
            ← 返回聊天
          </button>
        </div>
      </div>
      <LogViewer fullPage={true} />
    </div>
  )
}

export default LogPage
