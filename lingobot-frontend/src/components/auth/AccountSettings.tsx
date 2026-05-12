import React, { useState, useEffect, useRef } from 'react';
import { UserDTO } from '../../types';
import { authUtils, authApi, redemptionApi } from '../../api';
import ChangePasswordModal from './ChangePasswordModal';
import DeactivateModal from './DeactivateModal';
import BalanceHistoryPanel from './BalanceHistoryPanel';

interface AccountSettingsProps {
  onClose: () => void;
  onLogout: () => void;
}

type SettingsTab = 'profile' | 'balance' | 'security';

const AccountSettings: React.FC<AccountSettingsProps> = ({ onClose, onLogout }) => {
  const [user, setUser] = useState<UserDTO | null>(null);
  const [activeTab, setActiveTab] = useState<SettingsTab>('profile');
  const [showChangePasswordModal, setShowChangePasswordModal] = useState(false);
  const [showDeactivateModal, setShowDeactivateModal] = useState(false);
  const [avatar, setAvatar] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [editingUsername, setEditingUsername] = useState(false);
  const [newUsername, setNewUsername] = useState('');
  const [usernameLoading, setUsernameLoading] = useState(false);

  const [balance, setBalance] = useState<number>(0);
  const [redemptionCode, setRedemptionCode] = useState('');
  const [redeemLoading, setRedeemLoading] = useState(false);
  const [redeemMessage, setRedeemMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const currentUser = await authApi.getCurrentUser();
        setUser(currentUser);
        setAvatar(currentUser.avatar || null);
        setBalance(currentUser.balance ?? 0);
      } catch (error) {
        console.error('获取用户信息失败:', error);
        const cachedUser = authUtils.getUser();
        setUser(cachedUser);
        setAvatar(cachedUser?.avatar || null);
        setBalance(cachedUser?.balance ?? 0);
      }
    };

    fetchUser();
  }, []);

  const fetchBalance = async () => {
    try {
      const fetchedUser = await authApi.getCurrentUser();
      const newBalance = fetchedUser.balance ?? 0;
      setBalance(newBalance);
      setUser(fetchedUser);

      const existingUser = authUtils.getUser();
      if (existingUser) {
        const updatedUser = { ...existingUser, balance: newBalance };
        authUtils.setAuth(authUtils.getToken()!, updatedUser);
        window.dispatchEvent(new CustomEvent('auth:balance-updated', {
          detail: { user: updatedUser }
        }));
      }
    } catch (error) {
      console.error('获取余额失败:', error);
    }
  };

  const handleRedeemCode = async () => {
    if (!redemptionCode.trim()) {
      setRedeemMessage({ type: 'error', text: '请输入兑换码' });
      setTimeout(() => setRedeemMessage(null), 3000);
      return;
    }

    setRedeemLoading(true);
    setRedeemMessage(null);

    try {
      const result = await redemptionApi.redeemCode(redemptionCode.trim());
      setRedeemMessage({ type: 'success', text: `兑换成功！获得 ${result.points} 点` });
      setRedemptionCode('');
      await fetchBalance();
      setTimeout(() => setRedeemMessage(null), 5000);
    } catch (error) {
      setRedeemMessage({ type: 'error', text: error instanceof Error ? error.message : '兑换失败，请重试' });
      setTimeout(() => setRedeemMessage(null), 3000);
    } finally {
      setRedeemLoading(false);
    }
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      alert('请选择图片文件');
      return;
    }

    const maxSize = 2 * 1024 * 1024;
    if (file.size > maxSize) {
      alert('图片大小不能超过2MB');
      return;
    }

    const reader = new FileReader();
    reader.onload = async (event) => {
      const result = event.target?.result as string;

      setLoading(true);
      setSaveMessage(null);

      try {
        await authApi.updateAvatar(result);
        setAvatar(result);
        setSaveMessage({ type: 'success', text: '头像更新成功！' });
        setTimeout(() => setSaveMessage(null), 3000);

        const currentUser = authUtils.getUser();
        if (currentUser) {
          authUtils.setAuth(authUtils.getToken()!, { ...currentUser, avatar: result });
        }
        window.dispatchEvent(new CustomEvent('auth:avatar-updated'));
      } catch (error) {
        setSaveMessage({ type: 'error', text: error instanceof Error ? error.message : '保存失败，请重试' });
        setTimeout(() => setSaveMessage(null), 3000);
      } finally {
        setLoading(false);
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleDeactivateClick = () => {
    setShowDeactivateModal(true);
  };

  const handleDeactivateSuccess = () => {
    setShowDeactivateModal(false);
    onLogout();
    onClose();
  };

  const handleEditUsernameClick = () => {
    setNewUsername(user?.username || '');
    setEditingUsername(true);
  };

  const handleCancelEdit = () => {
    setEditingUsername(false);
    setNewUsername('');
  };

  const handleSaveUsername = async () => {
    if (!newUsername.trim()) {
      setSaveMessage({ type: 'error', text: '昵称不能为空' });
      setTimeout(() => setSaveMessage(null), 3000);
      return;
    }

    if (newUsername.trim().length < 3 || newUsername.trim().length > 20) {
      setSaveMessage({ type: 'error', text: '昵称长度必须在3-20个字符之间' });
      setTimeout(() => setSaveMessage(null), 3000);
      return;
    }

    setUsernameLoading(true);
    setSaveMessage(null);

    try {
      const response = await authApi.updateUsername(newUsername.trim());
      setUser(prev => prev ? { ...prev, username: response.username } : null);
      setSaveMessage({ type: 'success', text: '昵称修改成功！' });
      setTimeout(() => setSaveMessage(null), 3000);
    } catch (error) {
      setSaveMessage({ type: 'error', text: error instanceof Error ? error.message : '保存失败，请重试' });
      setTimeout(() => setSaveMessage(null), 3000);
    } finally {
      setUsernameLoading(false);
      setEditingUsername(false);
    }
  };

  const getAvatarDisplay = () => {
    if (avatar) {
      return <img src={avatar} alt="头像" className="avatar-image" />;
    }
    if (user) {
      return <span className="avatar-text">{user.username.charAt(0).toUpperCase()}</span>;
    }
    return <span className="avatar-text">?</span>;
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  return (
    <div className="account-settings-page">
      <div className="settings-header">
        <h1 className="settings-title">个人设置</h1>
        <p className="settings-subtitle">管理您的个人信息和账户设置</p>
      </div>

      <div className="settings-tabs">
        <button
          className={`settings-tab ${activeTab === 'profile' ? 'active' : ''}`}
          onClick={() => setActiveTab('profile')}
        >
          <span className="tab-icon">👤</span>
          <span>个人信息</span>
        </button>
        <button
          className={`settings-tab ${activeTab === 'balance' ? 'active' : ''}`}
          onClick={() => setActiveTab('balance')}
        >
          <span className="tab-icon">💰</span>
          <span>账户余额</span>
        </button>
        <button
          className={`settings-tab ${activeTab === 'security' ? 'active' : ''}`}
          onClick={() => setActiveTab('security')}
        >
          <span className="tab-icon">🔒</span>
          <span>账户安全</span>
        </button>
      </div>

      <div className="settings-content">
        {activeTab === 'profile' && (
          <div className="profile-section">
            <div className="section-card">
              <div className="section-header">
                <h2 className="section-title">基本信息</h2>
              </div>

              <div className="avatar-section">
                <div className="avatar-display">
                  {getAvatarDisplay()}
                </div>
                <div className="avatar-info">
                  <p className="avatar-hint">支持 JPG、PNG 格式，大小不超过 2MB</p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    style={{ display: 'none' }}
                  />
                  <button
                    className="change-avatar-btn"
                    onClick={handleAvatarClick}
                    disabled={loading}
                  >
                    {loading ? '上传中...' : '更换头像'}
                  </button>
                </div>
              </div>

              {saveMessage && (
                <div className={`save-message ${saveMessage.type}`}>
                  {saveMessage.text}
                </div>
              )}

              <div className="info-grid">
                <div className="info-item">
                  <label className="info-label">邮箱</label>
                  <div className="info-value read-only">
                    {user?.email || '-'}
                    <span className="info-hint">（不可修改）</span>
                  </div>
                </div>
                <div className="info-item editable">
                  <label className="info-label">昵称</label>
                  {editingUsername ? (
                    <div className="info-edit-container">
                      <input
                        type="text"
                        className="info-edit-input"
                        value={newUsername}
                        onChange={(e) => setNewUsername(e.target.value)}
                        disabled={usernameLoading}
                        autoFocus
                      />
                      <div className="info-edit-buttons">
                        <button
                          className="info-edit-btn save"
                          onClick={handleSaveUsername}
                          disabled={usernameLoading}
                        >
                          {usernameLoading ? '保存中...' : '保存'}
                        </button>
                        <button
                          className="info-edit-btn cancel"
                          onClick={handleCancelEdit}
                          disabled={usernameLoading}
                        >
                          取消
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="info-value-container">
                      <div className="info-value">{user?.username || '-'}</div>
                      <button
                        className="edit-icon-btn"
                        onClick={handleEditUsernameClick}
                        disabled={loading || usernameLoading}
                      >
                        ✏️
                      </button>
                    </div>
                  )}
                </div>
                <div className="info-item">
                  <label className="info-label">注册时间</label>
                  <div className="info-value">
                    {user?.createdAt ? formatDate(user.createdAt) : '-'}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'balance' && (
          <div className="balance-section">
            <div className="section-card">
              <div className="section-header">
                <h2 className="section-title">账户余额</h2>
              </div>

              <div className="balance-display-section">
                <div className="balance-display">
                  <div className="balance-info">
                    <p className="balance-label">当前余额</p>
                    <div className="balance-value-wrapper">
                      <p className="balance-value">{balance}</p>
                      <p className="balance-unit">点</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="redeem-section">
                <h3 className="redeem-title">兑换码兑换</h3>
                <div className="redeem-form">
                  <input
                    type="text"
                    className="redeem-input"
                    placeholder="请输入兑换码（如：sk-xxx...）"
                    value={redemptionCode}
                    onChange={(e) => setRedemptionCode(e.target.value)}
                    disabled={redeemLoading}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        handleRedeemCode();
                      }
                    }}
                  />
                  <button
                    className="redeem-btn"
                    onClick={handleRedeemCode}
                    disabled={redeemLoading}
                  >
                    {redeemLoading ? '兑换中...' : '立即兑换'}
                  </button>
                </div>

                {redeemMessage && (
                  <div className={`redeem-message ${redeemMessage.type}`}>
                    {redeemMessage.text}
                  </div>
                )}
              </div>

              <BalanceHistoryPanel />
            </div>
          </div>
        )}

        {activeTab === 'security' && (
          <div className="security-section">
            <div className="section-card">
              <div className="section-header">
                <h2 className="section-title">账户安全</h2>
              </div>

              <div className="security-options">
                <div className="security-option">
                  <div className="security-option-icon">
                    🔑
                  </div>
                  <div className="security-option-info">
                    <h3 className="security-option-title">修改密码</h3>
                    <p className="security-option-desc">定期更换密码，保护账户安全</p>
                  </div>
                  <button
                    className="security-option-btn"
                    onClick={() => setShowChangePasswordModal(true)}
                  >
                    修改
                  </button>
                </div>

                <div className="security-option danger">
                  <div className="security-option-icon danger">
                    🗑️
                  </div>
                  <div className="security-option-info">
                    <h3 className="security-option-title">注销账户</h3>
                    <p className="security-option-desc">注销后，您的所有数据将被永久删除</p>
                  </div>
                  <button
                    className="security-option-btn danger"
                    onClick={handleDeactivateClick}
                  >
                    注销
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      <ChangePasswordModal
        isOpen={showChangePasswordModal}
        onClose={() => setShowChangePasswordModal(false)}
      />

      <DeactivateModal
        isOpen={showDeactivateModal}
        user={user}
        onClose={() => setShowDeactivateModal(false)}
        onSuccess={handleDeactivateSuccess}
      />
    </div>
  );
};

export default AccountSettings;
