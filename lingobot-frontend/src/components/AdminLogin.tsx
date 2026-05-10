import React, { useState } from 'react';
import { authApi, authUtils } from '../api';
import { UserDTO } from '../types';

interface AdminLoginProps {
  onLoginSuccess: (user: UserDTO) => void;
}

const AdminLogin: React.FC<AdminLoginProps> = ({ onLoginSuccess }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [codeButtonText, setCodeButtonText] = useState('获取验证码');
  const [codeButtonDisabled, setCodeButtonDisabled] = useState(false);

  const handleSendCode = async () => {
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

    setCodeButtonDisabled(true);
    setCodeButtonText('发送中...');
    setError('');

    try {
      await authApi.sendLoginCode({ email: email.trim(), password });
      setCodeButtonText('60秒后重新获取');
      
      let count = 60;
      const timer = setInterval(() => {
        count--;
        if (count > 0) {
          setCodeButtonText(`${count}秒后重新获取`);
        } else {
          clearInterval(timer);
          setCodeButtonText('获取验证码');
          setCodeButtonDisabled(false);
        }
      }, 1000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '发送失败');
      setCodeButtonText('获取验证码');
      setCodeButtonDisabled(false);
    }
  };

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

    if (!verificationCode.trim()) {
      setError('请输入验证码');
      return;
    }

    setLoading(true);

    try {
      const response = await authApi.loginWithCode({ 
        email: email.trim(), 
        password,
        verificationCode: verificationCode.trim()
      });
      
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
    <form onSubmit={handleSubmit} className="auth-form">
      {error && <div className="auth-error">{error}</div>}

      <div className="auth-field">
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

      <div className="auth-field">
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

      <div className="auth-field code-field">
        <div className="code-input-wrapper">
          <label htmlFor="admin-verification-code">登录验证码</label>
          <input
            id="admin-verification-code"
            type="text"
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value)}
            placeholder="请输入验证码"
            disabled={loading}
            maxLength={6}
          />
        </div>
        <button
          type="button"
          className="code-button"
          onClick={handleSendCode}
          disabled={loading || codeButtonDisabled}
        >
          {codeButtonText}
        </button>
      </div>

      <button type="submit" className="auth-submit-btn" disabled={loading}>
        {loading ? '登录中...' : '登录'}
      </button>

      <div className="auth-switch">
        <button type="button" onClick={handleBackToChat} className="auth-switch-btn">
          ← 返回聊天页面
        </button>
      </div>
    </form>
  );
};

export default AdminLogin;
