import React, { useState } from 'react';
import { authApi, authUtils } from '../api';
import { UserDTO } from '../types';

interface AdminLoginProps {
  onLoginSuccess: (user: UserDTO) => void;
}

const AdminLogin: React.FC<AdminLoginProps> = ({ onLoginSuccess }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email.trim()) {
      setError('请输入邮箱');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setError('请输入有效的邮箱地址');
      return;
    }

    if (!password) {
      setError('请输入密码');
      return;
    }

    setLoading(true);

    try {
      const response = await authApi.login({ email: email.trim(), password });
      
      if (response.role !== 'ROLE_ADMIN') {
        authUtils.clearAuth();
        setError('该账户不是管理员账户');
        return;
      }

      const user: UserDTO = {
        id: response.userId,
        username: response.username,
        role: response.role,
        createdAt: new Date().toISOString()
      };

      authUtils.setAuth(response.token, user);
      onLoginSuccess(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleBackToChat = () => {
    window.location.href = '/';
  };

  return (
    <form onSubmit={handleSubmit} className="admin-login-form">
      {error && <div className="admin-login-error">{error}</div>}

      <div className="admin-login-field">
        <label htmlFor="admin-email">邮箱</label>
        <input
          id="admin-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="请输入邮箱"
          disabled={loading}
          autoComplete="email"
        />
      </div>

      <div className="admin-login-field">
        <label htmlFor="admin-password">密码</label>
        <input
          id="admin-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="请输入密码"
          disabled={loading}
          autoComplete="current-password"
        />
      </div>

      <button type="submit" className="admin-login-submit-btn" disabled={loading}>
        {loading ? '登录中...' : '登录'}
      </button>

      <div className="admin-back-link">
        <button type="button" onClick={handleBackToChat}>
          ← 返回聊天页面
        </button>
      </div>
    </form>
  );
};

export default AdminLogin;
