import React, { useState, useEffect } from 'react';
import { authUtils } from '../api';
import { UserDTO } from '../types';
import AdminLogin from './AdminLogin';
import AdminDashboard from './AdminDashboard';

const AdminPage: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserDTO | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    const { token, user } = authUtils.initializeAuth();
    if (token && user && user.role === 'ROLE_ADMIN') {
      setIsAuthenticated(true);
      setCurrentUser(user);
    }
    setInitializing(false);
  }, []);

  const handleLoginSuccess = (user: UserDTO) => {
    setIsAuthenticated(true);
    setCurrentUser(user);
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setCurrentUser(null);
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
        <AdminLogin onLoginSuccess={handleLoginSuccess} />
      ) : (
        currentUser && (
          <AdminDashboard currentUser={currentUser} onLogout={handleLogout} />
        )
      )}
    </div>
  );
};

export default AdminPage;
