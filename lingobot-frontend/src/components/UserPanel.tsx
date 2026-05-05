import React, { useEffect, useState } from 'react';
import { authUtils, authApi } from '../api';
import { UserDTO } from '../types';

interface UserPanelProps {
  onLoginClick: () => void;
  onLogout: () => void;
}

const UserPanel: React.FC<UserPanelProps> = ({ onLoginClick, onLogout }) => {
  const [user, setUser] = useState<UserDTO | null>(null);
  const [showDropdown, setShowDropdown] = useState(false);

  useEffect(() => {
    const checkAuth = () => {
      const currentUser = authUtils.getUser();
      setUser(currentUser);
    };

    checkAuth();

    const handleLogin = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      setUser(customEvent.detail.user);
      setShowDropdown(false);
    };

    const handleLogout = () => {
      setUser(null);
      setShowDropdown(false);
    };

    const handleUsernameUpdated = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      setUser(customEvent.detail.user);
    };

    window.addEventListener('auth:login', handleLogin);
    window.addEventListener('auth:logout', handleLogout);
    window.addEventListener('auth:username-updated', handleUsernameUpdated);

    return () => {
      window.removeEventListener('auth:login', handleLogin);
      window.removeEventListener('auth:logout', handleLogout);
      window.removeEventListener('auth:username-updated', handleUsernameUpdated);
    };
  }, []);

  const handleLogout = () => {
    authApi.logout();
    onLogout();
  };

  const toggleDropdown = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowDropdown(!showDropdown);
  };

  useEffect(() => {
    const handleClickOutside = () => {
      setShowDropdown(false);
    };

    if (showDropdown) {
      document.addEventListener('click', handleClickOutside);
    }

    return () => {
      document.removeEventListener('click', handleClickOutside);
    };
  }, [showDropdown]);

  if (!user) {
    return (
      <div className="user-panel">
        <button 
          className="login-prompt-btn" 
          onClick={onLoginClick}
        >
          <span className="login-icon">👤</span>
          <span className="login-text">点击登录</span>
        </button>
      </div>
    );
  }

  return (
    <div className="user-panel">
      <div className="user-info" onClick={toggleDropdown}>
        <div className="user-avatar">
          {user.username.charAt(0).toUpperCase()}
        </div>
        <div className="user-details">
          <span className="user-name">{user.username}</span>
          <span className="user-status">已登录</span>
        </div>
        <span className={`dropdown-arrow ${showDropdown ? 'open' : ''}`}>▼</span>
      </div>

      {showDropdown && (
        <div className="user-dropdown">
          <div className="dropdown-item" onClick={handleLogout}>
            <span>🚪</span>
            <span>退出登录</span>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserPanel;
