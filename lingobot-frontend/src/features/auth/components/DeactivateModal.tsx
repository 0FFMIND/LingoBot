import React, { useState } from 'react';
import { UserDTO } from '../../../types';
import { authApi } from '../../../api';

interface DeactivateModalProps {
  isOpen: boolean;
  user: UserDTO | null;
  onClose: () => void;
  onSuccess: () => void;
}

const DeactivateModal: React.FC<DeactivateModalProps> = ({ isOpen, user, onClose, onSuccess }) => {
  const [inputUserId, setInputUserId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [step, setStep] = useState<'confirm' | 'input'>('confirm');

  const handleStartDeactivate = () => {
    setStep('input');
    setError('');
    setInputUserId('');
  };

  const handleConfirmDeactivate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!user) {
      setError('用户信息不存在');
      return;
    }

    const inputId = inputUserId.trim();
    if (!inputId) {
      setError('请输入用户ID');
      return;
    }

    if (inputId !== String(user.id)) {
      setError('用户ID不匹配，请重新输入');
      return;
    }

    setLoading(true);

    try {
      await authApi.deactivateAccount();
      onSuccess();
      onClose();
      resetForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : '注销失败');
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setInputUserId('');
    setError('');
    setStep('confirm');
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      handleClose();
    }
  };

  if (!isOpen || !user) return null;

  return (
    <div className="auth-modal-overlay" onClick={handleOverlayClick}>
      <div className="auth-modal deactivate-modal">
        <div className="auth-modal-header">
          <h2>注销账户</h2>
          <button className="auth-close-btn" onClick={handleClose}>
            ×
          </button>
        </div>

        {step === 'confirm' ? (
          <div className="deactivate-confirm">
            <div className="deactivate-warning">
              <span className="warning-icon">⚠️</span>
              <p className="warning-text">
                注销账户后，您的所有数据将被永久删除，包括：
              </p>
              <ul className="warning-list">
                <li>所有对话记录</li>
                <li>所有消息内容</li>
                <li>用户账户信息</li>
              </ul>
              <p className="warning-irreversible">
                此操作不可逆，下次使用相同用户名注册将是全新账户。
              </p>
            </div>

            <div className="deactivate-user-info">
              <p>当前用户：<strong>{user.username}</strong></p>
              <p>用户ID：<strong>{user.id}</strong></p>
            </div>

            <div className="deactivate-actions">
              <button
                type="button"
                className="auth-cancel-btn"
                onClick={handleClose}
              >
                取消
              </button>
              <button
                type="button"
                className="auth-deactivate-btn"
                onClick={handleStartDeactivate}
              >
                继续注销
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleConfirmDeactivate} className="auth-form">
            {error && <div className="auth-error">{error}</div>}

            <div className="deactivate-warning-small">
              <p>请输入您的用户ID <strong>{user.id}</strong> 以确认注销：</p>
            </div>

            <div className="auth-field">
              <label htmlFor="userId">用户ID</label>
              <input
                id="userId"
                type="text"
                value={inputUserId}
                onChange={(e) => setInputUserId(e.target.value)}
                placeholder="请输入用户ID"
                disabled={loading}
                autoFocus
              />
            </div>

            <div className="deactivate-actions">
              <button
                type="button"
                className="auth-cancel-btn"
                onClick={() => setStep('confirm')}
                disabled={loading}
              >
                返回
              </button>
              <button
                type="submit"
                className="auth-deactivate-confirm-btn"
                disabled={loading}
              >
                {loading ? '处理中...' : '确认注销'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default DeactivateModal;
