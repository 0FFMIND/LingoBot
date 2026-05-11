import React, { useState } from 'react';
import { authApi } from '../../../api';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

type AuthMode = 'login' | 'register';

const AuthModal: React.FC<AuthModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [mode, setMode] = useState<AuthMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [loginCodeButtonText, setLoginCodeButtonText] = useState('获取验证码');
  const [loginCodeButtonDisabled, setLoginCodeButtonDisabled] = useState(false);
  const [codeButtonText, setCodeButtonText] = useState('获取验证码');
  const [codeButtonDisabled, setCodeButtonDisabled] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email.trim()) {
      setError('请输入邮箱');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('请输入有效的邮箱地址');
      return;
    }

    if (!password) {
      setError('请输入密码');
      return;
    }

    if (mode === 'login') {
      if (!verificationCode.trim()) {
        setError('请输入验证码');
        return;
      }
    }

    if (mode === 'register') {
      if (!verificationCode.trim()) {
        setError('请输入验证码');
        return;
      }
      if (password.length < 6) {
        setError('密码长度至少为6个字符');
        return;
      }
      if (password !== confirmPassword) {
        setError('两次输入的密码不一致');
        return;
      }
    }

    setLoading(true);

    try {
      if (mode === 'login') {
        await authApi.loginWithCode({
          email: email.trim(),
          password,
          verificationCode: verificationCode.trim()
        });
      } else {
        await authApi.registerWithCode({
          email: email.trim(),
          password,
          verificationCode: verificationCode.trim()
        });
      }
      onSuccess();
      onClose();
      resetForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSendLoginCode = async () => {
    if (!email.trim()) {
      setError('请先输入邮箱');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('请输入有效的邮箱地址');
      return;
    }

    if (!password) {
      setError('请先输入密码');
      return;
    }

    setLoginCodeButtonDisabled(true);
    setLoginCodeButtonText('发送中...');

    try {
      await authApi.sendLoginCode({ email: email.trim(), password });
      setLoginCodeButtonText('60秒后重新获取');

      let count = 60;
      const timer = setInterval(() => {
        count--;
        if (count > 0) {
          setLoginCodeButtonText(`${count}秒后重新获取`);
        } else {
          clearInterval(timer);
          setLoginCodeButtonText('获取验证码');
          setLoginCodeButtonDisabled(false);
        }
      }, 1000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '发送失败');
      setLoginCodeButtonText('获取验证码');
      setLoginCodeButtonDisabled(false);
    }
  };

  const handleSendCode = async () => {
    if (!email.trim()) {
      setError('请先输入邮箱');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('请输入有效的邮箱地址');
      return;
    }

    setCodeButtonDisabled(true);
    setCodeButtonText('发送中...');

    try {
      await authApi.sendVerificationCode(email.trim());
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

  const resetForm = () => {
    setEmail('');
    setPassword('');
    setConfirmPassword('');
    setVerificationCode('');
    setError('');
    setLoginCodeButtonText('获取验证码');
    setLoginCodeButtonDisabled(false);
    setCodeButtonText('获取验证码');
    setCodeButtonDisabled(false);
  };

  const switchMode = () => {
    setMode(mode === 'login' ? 'register' : 'login');
    resetForm();
  };

  if (!isOpen) return null;

  return (
    <div className="auth-modal-overlay">
      <div className="auth-modal">
        <div className="auth-modal-header">
          <h2>{mode === 'login' ? '登录' : '注册'}</h2>
          <button className="auth-close-btn" onClick={onClose}>
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          {error && <div className="auth-error">{error}</div>}

          <div className="auth-field">
            <label htmlFor="email">邮箱</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="请输入邮箱"
              disabled={loading}
              autoComplete="email"
            />
          </div>

          <div className="auth-field">
            <label htmlFor="password">密码</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              disabled={loading}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </div>

          {mode === 'login' && (
            <div className="auth-field code-field">
              <div className="code-input-wrapper">
                <label htmlFor="loginVerificationCode">登录验证码</label>
                <input
                  id="loginVerificationCode"
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
                onClick={handleSendLoginCode}
                disabled={loading || loginCodeButtonDisabled}
              >
                {loginCodeButtonText}
              </button>
            </div>
          )}

          {mode === 'register' && (
            <>
              <div className="auth-field">
                <label htmlFor="confirmPassword">确认密码</label>
                <input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="请再次输入密码"
                  disabled={loading}
                  autoComplete="new-password"
                />
              </div>
              <div className="auth-field code-field">
                <div className="code-input-wrapper">
                  <label htmlFor="verificationCode">验证码</label>
                  <input
                    id="verificationCode"
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
            </>
          )}

          <button type="submit" className="auth-submit-btn" disabled={loading}>
            {loading ? '处理中...' : mode === 'login' ? '登录' : '注册'}
          </button>
        </form>

        <div className="auth-switch">
          {mode === 'login' ? (
            <span>
              还没有账号？
              <button type="button" onClick={switchMode} className="auth-switch-btn">
                立即注册
              </button>
            </span>
          ) : (
            <span>
              已有账号？
              <button type="button" onClick={switchMode} className="auth-switch-btn">
                立即登录
              </button>
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

export default AuthModal;
