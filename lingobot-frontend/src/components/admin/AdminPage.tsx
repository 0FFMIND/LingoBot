import React, { useState, useEffect } from 'react';
import { authUtils, authApi } from '../../api';
import { UserDTO } from '../../types';
import AdminLogin from './AdminLogin';
import AdminDashboard from './AdminDashboard';

const AdminPage: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserDTO | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    const init = async () => {
      try {
        const devRes = await fetch('/api/admin/dev-check');
        const isDev = await devRes.json();
        if (isDev) {
          setIsAuthenticated(true);
          setCurrentUser({
            id: 0,
            username: 'DevUser',
            email: 'dev@localhost',
            role: 'ROLE_ADMIN',
            createdAt: new Date().toISOString()
          });
          setInitializing(false);
          return;
        }
      } catch {
        // dev-check 失败则回退到正常认证流程
      }

      const token = authUtils.initializeAuth();
      if (token) {
        authApi.getCurrentUser()
          .then((user) => {
            if (user.role === 'ROLE_ADMIN') {
              authUtils.setUser(user);
              setIsAuthenticated(true);
              setCurrentUser(user);
            }
          })
          .catch(() => {
            authUtils.clearAuth();
          })
          .finally(() => setInitializing(false));
      } else {
        setInitializing(false);
      }
    };

    init();
  }, []);

  const handleLoginSuccess = (user: UserDTO) => {
    setIsAuthenticated(true);
    setCurrentUser(user);
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setCurrentUser(null);
  };

  const handleBackToChat = () => {
    window.location.href = '/';
  };

  if (initializing) {
    return (
      <div className="admin-page">
        <div className="admin-loading-full">
          <div className="loading-spinner"></div>
          <p>加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      {!isAuthenticated ? (
        <div className="auth-modal-overlay">
          <div className="auth-modal">
            <div className="auth-modal-header">
              <h2>管理员登录</h2>
              <button className="auth-close-btn" onClick={handleBackToChat}>
                ×
              </button>
            </div>
            <AdminLogin onLoginSuccess={handleLoginSuccess} />
          </div>
        </div>
      ) : (
        currentUser && (
          <AdminDashboard currentUser={currentUser} onLogout={handleLogout} />
        )
      )}
    </div>
  );
};

export default AdminPage;
