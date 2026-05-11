import React, { useState, useEffect, useCallback } from 'react';
import { adminApi, authUtils, redemptionApi } from '../../../api';
import { BlockedUserInfo, BlockedIpInfo, UserDTO, UserAdminDTO, RedemptionCodeDTO } from '../../../types';

interface AdminDashboardProps {
  currentUser: UserDTO;
  onLogout: () => void;
}

interface ResetPasswordModalProps {
  user: UserAdminDTO | null;
  onClose: () => void;
  onSuccess: (userId: number) => void;
}

interface DeleteUserModalProps {
  user: UserAdminDTO | null;
  onClose: () => void;
  onConfirm: () => void;
}

interface EditUsernameModalProps {
  user: UserAdminDTO | null;
  onClose: () => void;
  onSuccess: (userId: number) => void;
}

interface EditBalanceModalProps {
  user: UserAdminDTO | null;
  onClose: () => void;
  onSuccess: (userId: number) => void;
}

interface CreateRedemptionCodeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const DeleteUserModal: React.FC<DeleteUserModalProps> = ({ user, onClose, onConfirm }) => {
  if (!user) return null;

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div className="auth-modal-overlay" onClick={handleOverlayClick}>
      <div className="auth-modal delete-conversation-modal">
        <div className="delete-modal-header">
          <h2>删除用户？</h2>
        </div>

        <div className="delete-modal-content">
          <p className="delete-modal-message">
            确定要删除 <strong>"{user.email}"</strong> 吗？
          </p>
          <p className="delete-modal-hint">
            此操作将解锁该用户的所有限制并永久删除该用户账户，且不可恢复！
          </p>
        </div>

        <div className="delete-modal-actions">
          <button
            type="button"
            className="delete-modal-cancel-btn"
            onClick={onClose}
          >
            取消
          </button>
          <button
            type="button"
            className="delete-modal-delete-btn"
            onClick={onConfirm}
          >
            删除
          </button>
        </div>
      </div>
    </div>
  );
};

const EditUsernameModal: React.FC<EditUsernameModalProps> = ({ user, onClose, onSuccess }) => {
  const [newUsername, setNewUsername] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user) {
      setNewUsername(user.username);
      setError('');
    }
  }, [user]);

  if (!user) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!newUsername.trim()) {
      setError('请输入新用户名');
      return;
    }

    if (newUsername.trim().length < 2 || newUsername.trim().length > 50) {
      setError('用户名长度必须在2-50个字符之间');
      return;
    }

    setLoading(true);
    try {
      await adminApi.updateUserUsername(user.id, newUsername.trim());
      alert(`用户 "${user.email}" 的用户名已修改为 "${newUsername.trim()}"！`);
      onSuccess(user.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '修改失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="reset-password-modal-overlay" onClick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}>
      <div className="reset-password-modal">
        <div className="reset-password-header">
          <h3>修改用户名</h3>
          <button
            type="button"
            className="reset-password-close"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="reset-password-form">
          <div className="reset-password-user-info">
            <span>邮箱: </span>
            <strong>{user.email}</strong>
            <span className="reset-password-role-badge">
              {user.role === 'ROLE_ADMIN' ? '管理员' : '普通用户'}
            </span>
          </div>

          {error && <div className="reset-password-error">{error}</div>}

          <div className="reset-password-field">
            <label htmlFor="new-username">当前用户名</label>
            <input
              type="text"
              value={user.username}
              disabled
              className="disabled-input"
            />
          </div>

          <div className="reset-password-field">
            <label htmlFor="new-username">新用户名</label>
            <input
              id="new-username"
              type="text"
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
              placeholder="请输入新用户名（2-50个字符）"
              disabled={loading}
              autoComplete="off"
            />
          </div>

          <div className="reset-password-actions">
            <button
              type="button"
              className="reset-password-cancel-btn"
              onClick={onClose}
              disabled={loading}
            >
              取消
            </button>
            <button
              type="submit"
              className="reset-password-submit-btn"
              disabled={loading}
            >
              {loading ? '处理中...' : '确认修改'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const EditBalanceModal: React.FC<EditBalanceModalProps> = ({ user, onClose, onSuccess }) => {
  const [newBalance, setNewBalance] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user) {
      setNewBalance(user.balance.toString());
      setError('');
    }
  }, [user]);

  if (!user) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const balanceNum = parseFloat(newBalance);
    if (newBalance === '' || isNaN(balanceNum)) {
      setError('请输入有效的余额');
      return;
    }

    if (balanceNum < 0) {
      setError('余额不能为负数');
      return;
    }

    setLoading(true);
    try {
      await adminApi.updateUserBalance(user.id, balanceNum);
      alert(`用户 "${user.email}" 的余额已修改为 "${balanceNum}"！`);
      onSuccess(user.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '修改失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="reset-password-modal-overlay" onClick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}>
      <div className="reset-password-modal">
        <div className="reset-password-header">
          <h3>修改余额</h3>
          <button
            type="button"
            className="reset-password-close"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="reset-password-form">
          <div className="reset-password-user-info">
            <span>邮箱: </span>
            <strong>{user.email}</strong>
            <span className="reset-password-role-badge">
              {user.role === 'ROLE_ADMIN' ? '管理员' : '普通用户'}
            </span>
          </div>

          {error && <div className="reset-password-error">{error}</div>}

          <div className="reset-password-field">
            <label htmlFor="current-balance">当前余额</label>
            <input
              type="text"
              value={user.balance.toString() + ' 点'}
              disabled
              className="disabled-input"
            />
          </div>

          <div className="reset-password-field">
            <label htmlFor="new-balance">新余额</label>
            <input
              id="new-balance"
              type="number"
              min="0"
              step="0.01"
              value={newBalance}
              onChange={(e) => setNewBalance(e.target.value)}
              placeholder="请输入新余额"
              disabled={loading}
              autoComplete="off"
            />
          </div>

          <div className="reset-password-actions">
            <button
              type="button"
              className="reset-password-cancel-btn"
              onClick={onClose}
              disabled={loading}
            >
              取消
            </button>
            <button
              type="submit"
              className="reset-password-submit-btn"
              disabled={loading}
            >
              {loading ? '处理中...' : '确认修改'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const ResetPasswordModal: React.FC<ResetPasswordModalProps> = ({ user, onClose, onSuccess }) => {
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (!user) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!newPassword.trim()) {
      setError('请输入新密码');
      return;
    }

    if (newPassword.length < 6) {
      setError('密码长度至少为6个字符');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      await adminApi.resetUserPassword(user.id, newPassword);
      alert(`用户 "${user.email}" 的密码重置成功！`);
      onSuccess(user.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '密码重置失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="reset-password-modal-overlay" onClick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}>
      <div className="reset-password-modal">
        <div className="reset-password-header">
          <h3>重置用户密码</h3>
          <button
            type="button"
            className="reset-password-close"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="reset-password-form">
          <div className="reset-password-user-info">
            <span>邮箱: </span>
            <strong>{user.email}</strong>
            <span className="reset-password-role-badge">
              {user.role === 'ROLE_ADMIN' ? '管理员' : '普通用户'}
            </span>
          </div>

          {error && <div className="reset-password-error">{error}</div>}

          <div className="reset-password-field">
            <label htmlFor="new-password">新密码</label>
            <input
              id="new-password"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="请输入新密码（至少6个字符）"
              disabled={loading}
              autoComplete="new-password"
            />
          </div>

          <div className="reset-password-field">
            <label htmlFor="confirm-password">确认密码</label>
            <input
              id="confirm-password"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="请再次输入新密码"
              disabled={loading}
              autoComplete="new-password"
            />
          </div>

          <div className="reset-password-actions">
            <button
              type="button"
              className="reset-password-cancel-btn"
              onClick={onClose}
              disabled={loading}
            >
              取消
            </button>
            <button
              type="submit"
              className="reset-password-submit-btn"
              disabled={loading}
            >
              {loading ? '处理中...' : '确认重置'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const EXPIRE_OPTIONS = [
  { label: '永不过期', value: null },
  { label: '1秒（测试用）', value: 1 },
  { label: '1小时', value: 3600 },
  { label: '1天', value: 86400 },
  { label: '7天', value: 604800 },
] as const;

const CreateRedemptionCodeModal: React.FC<CreateRedemptionCodeModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [points, setPoints] = useState<string>('');
  const [expireOption, setExpireOption] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [createdCode, setCreatedCode] = useState<RedemptionCodeDTO | null>(null);

  useEffect(() => {
    if (isOpen) {
      setPoints('');
      setExpireOption(null);
      setError('');
      setCreatedCode(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setCreatedCode(null);

    const pointsNum = parseInt(points);
    if (!points || isNaN(pointsNum) || pointsNum < 1) {
      setError('请输入有效的点数（至少1点）');
      return;
    }

    setLoading(true);
    try {
      const result = await redemptionApi.createCode(
        pointsNum,
        expireOption !== null ? expireOption : undefined
      );
      setCreatedCode(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建失败');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (createdCode) {
      onSuccess();
    }
    onClose();
  };

  return (
    <div className="reset-password-modal-overlay" onClick={(e) => {
      if (e.target === e.currentTarget) handleClose();
    }}>
      <div className="reset-password-modal">
        <div className="reset-password-header">
          <h3>生成兑换码</h3>
          <button
            type="button"
            className="reset-password-close"
            onClick={handleClose}
          >
            ×
          </button>
        </div>

        {createdCode ? (
          <div className="redemption-created-section">
            <div className="redemption-success-icon">✓</div>
            <h4 className="redemption-created-title">兑换码创建成功！</h4>

            <div className="redemption-code-display">
              <label>兑换码</label>
              <div className="redemption-code-box">
                <code>{createdCode.code}</code>
              </div>
            </div>

            <div className="redemption-code-info">
              <div className="code-info-item">
                <span>点数:</span>
                <strong>{createdCode.points} 点</strong>
              </div>
              <div className="code-info-item">
                <span>创建时间:</span>
                <strong>{new Date(createdCode.createdAt).toLocaleString('zh-CN')}</strong>
              </div>
              {createdCode.expiresAt && (
                <div className="code-info-item">
                  <span>到期时间:</span>
                  <strong>{new Date(createdCode.expiresAt).toLocaleString('zh-CN')}</strong>
                </div>
              )}
              {!createdCode.expiresAt && (
                <div className="code-info-item">
                  <span>到期时间:</span>
                  <strong>永不过期</strong>
                </div>
              )}
            </div>

            <div className="reset-password-actions">
              <button
                type="button"
                className="reset-password-cancel-btn"
                onClick={handleClose}
              >
                关闭
              </button>
              <button
                type="button"
                className="reset-password-submit-btn"
                onClick={() => {
                  setCreatedCode(null);
                  setPoints('');
                }}
              >
                继续创建
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="reset-password-form">
            <div className="reset-password-user-info">
              <span>生成新的兑换码，用户可使用该码兑换对应点数</span>
            </div>

            {error && <div className="reset-password-error">{error}</div>}

            <div className="reset-password-field">
              <label htmlFor="points">兑换点数</label>
              <input
                id="points"
                type="number"
                min="1"
                value={points}
                onChange={(e) => setPoints(e.target.value)}
                placeholder="请输入点数（如：100）"
                disabled={loading}
                autoComplete="off"
              />
            </div>

            <div className="reset-password-field">
              <label htmlFor="expire-option">到期时间</label>
              <select
                id="expire-option"
                value={expireOption ?? ''}
                onChange={(e) => {
                  const value = e.target.value;
                  setExpireOption(value === '' ? null : parseInt(value));
                }}
                disabled={loading}
              >
                {EXPIRE_OPTIONS.map((option) => (
                  <option key={option.value ?? 'never'} value={option.value ?? ''}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="reset-password-actions">
              <button
                type="button"
                className="reset-password-cancel-btn"
                onClick={handleClose}
                disabled={loading}
              >
                取消
              </button>
              <button
                type="submit"
                className="reset-password-submit-btn"
                disabled={loading}
              >
                {loading ? '创建中...' : '生成兑换码'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

const AdminDashboard: React.FC<AdminDashboardProps> = ({ currentUser, onLogout }) => {
  const [blockedUsers, setBlockedUsers] = useState<BlockedUserInfo[]>([]);
  const [blockedIps, setBlockedIps] = useState<BlockedIpInfo[]>([]);
  const [allUsers, setAllUsers] = useState<UserAdminDTO[]>([]);
  const [redemptionCodes, setRedemptionCodes] = useState<RedemptionCodeDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'users' | 'ips' | 'manage' | 'redemption'>('manage');
  const [actionLoading, setActionLoading] = useState<number | string | null>(null);
  const [refreshInterval, setRefreshInterval] = useState<number | null>(null);
  const [resetPasswordUser, setResetPasswordUser] = useState<UserAdminDTO | null>(null);
  const [deleteUser, setDeleteUser] = useState<UserAdminDTO | null>(null);
  const [editUsernameUser, setEditUsernameUser] = useState<UserAdminDTO | null>(null);
  const [editBalanceUser, setEditBalanceUser] = useState<UserAdminDTO | null>(null);
  const [showCreateRedemptionModal, setShowCreateRedemptionModal] = useState(false);

  const loadData = useCallback(async () => {
    try {
      const [users, ips, allUsersList, codes] = await Promise.all([
        adminApi.getBlockedUsers(),
        adminApi.getBlockedIps(),
        adminApi.getAllUsers(),
        redemptionApi.getAllCodes()
      ]);
      setBlockedUsers(users);
      setBlockedIps(ips);
      setAllUsers(allUsersList);
      setRedemptionCodes(codes);
    } catch (error) {
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
    const interval = window.setInterval(loadData, 5000);
    setRefreshInterval(interval);
    return () => {
      if (refreshInterval) {
        window.clearInterval(refreshInterval);
      }
    };
  }, [loadData]);

  const handleUnlockUser = async (userId: number, username: string) => {
    if (!confirm(`确定要解锁用户 "${username}" 吗？`)) {
      return;
    }
    setActionLoading(userId);
    try {
      await adminApi.unlockUser(userId);
      await loadData();
    } catch (error) {
      console.error('解锁用户失败:', error);
      alert('解锁失败: ' + (error instanceof Error ? error.message : '未知错误'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleUnblockIp = async (ipAddress: string) => {
    if (!confirm(`确定要解除 IP "${ipAddress}" 的封锁吗？`)) {
      return;
    }
    setActionLoading(ipAddress);
    try {
      await adminApi.unblockIp(ipAddress);
      await loadData();
    } catch (error) {
      console.error('解除IP封锁失败:', error);
      alert('解除封锁失败: ' + (error instanceof Error ? error.message : '未知错误'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteUser = async (user: UserAdminDTO) => {
    if (user.isCurrentAdmin) {
      alert('不能删除当前登录的管理员账户！');
      return;
    }
    setDeleteUser(user);
  };

  const confirmDeleteUser = async () => {
    if (!deleteUser) return;

    setActionLoading(deleteUser.id);
    try {
      await adminApi.deleteUser(deleteUser.id);
      alert(`用户 "${deleteUser.email}" 已删除！`);
      setDeleteUser(null);
      await loadData();
    } catch (error) {
      console.error('删除用户失败:', error);
      alert('删除失败: ' + (error instanceof Error ? error.message : '未知错误'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleResetPassword = (user: UserAdminDTO) => {
    setResetPasswordUser(user);
  };

  const handleEditUsername = (user: UserAdminDTO) => {
    setEditUsernameUser(user);
  };

  const handleEditBalance = (user: UserAdminDTO) => {
    setEditBalanceUser(user);
  };

  const handleLogout = () => {
    authUtils.clearAuth();
    onLogout();
  };

  const formatTime = (timeStr: string) => {
    return new Date(timeStr).toLocaleString('zh-CN');
  };

  const formatDuration = (seconds: number) => {
    if (seconds < 60) {
      return `${seconds}秒`;
    } else if (seconds < 3600) {
      return `${Math.floor(seconds / 60)}分钟`;
    } else if (seconds < 86400) {
      return `${Math.floor(seconds / 3600)}小时${Math.floor((seconds % 3600) / 60)}分钟`;
    } else {
      return `${Math.floor(seconds / 86400)}天${Math.floor((seconds % 86400) / 3600)}小时`;
    }
  };

  const getRoleDisplay = (role: string) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return '管理员';
      case 'ROLE_USER':
        return '普通用户';
      default:
        return role;
    }
  };

  const getRoleBadgeClass = (role: string) => {
    return role === 'ROLE_ADMIN' ? 'role-badge-admin' : 'role-badge-user';
  };

  const handleDeleteRedemptionCode = async (codeId: number, codeValue: string) => {
    if (!confirm(`确定要删除兑换码 "${codeValue}" 吗？\n删除后该兑换码将无法使用。`)) {
      return;
    }

    setActionLoading(codeId);
    try {
      await redemptionApi.deleteCode(codeId);
      alert('删除成功！');
      setRedemptionCodes(prev => prev.filter(c => c.id !== codeId));
    } catch (err) {
      const message = err instanceof Error ? err.message : '删除失败';
      alert(message);
    } finally {
      setActionLoading(null);
    }
  };

  const usedCount = redemptionCodes.filter(c => c.isUsed).length;
  const unusedCount = redemptionCodes.length - usedCount;

  return (
    <div className="admin-dashboard">
      <div className="admin-header">
        <div className="admin-header-left">
          <h1>管理面板</h1>
          <span className="admin-status-badge">
            用户总数: {allUsers.length} | 锁定用户: {blockedUsers.length} | 封锁IP: {blockedIps.length}
          </span>
        </div>
        <div className="admin-header-right">
          <span className="admin-user-info">
            管理员: {currentUser.username}
          </span>
          <button onClick={handleLogout} className="admin-logout-btn">
            退出登录
          </button>
        </div>
      </div>

      <div className="admin-tabs">
        <button
          className={`admin-tab-btn ${activeTab === 'manage' ? 'active' : ''}`}
          onClick={() => setActiveTab('manage')}
        >
          用户管理 ({allUsers.length})
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'redemption' ? 'active' : ''}`}
          onClick={() => setActiveTab('redemption')}
        >
          兑换码管理 ({redemptionCodes.length})
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          锁定用户 ({blockedUsers.length})
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'ips' ? 'active' : ''}`}
          onClick={() => setActiveTab('ips')}
        >
          封锁IP ({blockedIps.length})
        </button>
      </div>

      <div className="admin-content">
        {loading ? (
          <div className="admin-loading">
            <div className="loading-spinner"></div>
            <p>加载中...</p>
          </div>
        ) : (
          <>
            {activeTab === 'manage' && (
              <div className="admin-table-container">
                {allUsers.length === 0 ? (
                  <div className="admin-empty-state">
                    <div className="admin-empty-icon">👥</div>
                    <h3>暂无用户</h3>
                    <p>系统中还没有任何用户</p>
                  </div>
                ) : (
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>用户ID</th>
                        <th>邮箱</th>
                        <th>用户名</th>
                        <th>角色</th>
                        <th>余额</th>
                        <th>注册时间</th>
                        <th>状态</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {allUsers.map((user) => (
                        <tr key={user.id} className={user.isCurrentAdmin ? 'current-admin-row' : ''}>
                          <td>{user.id}</td>
                          <td className="admin-email">
                            {user.email}
                            {user.isCurrentAdmin && (
                              <span className="current-admin-indicator">(当前登录)</span>
                            )}
                          </td>
                          <td className="admin-username">
                            {user.username}
                          </td>
                          <td>
                            <span className={`role-badge ${getRoleBadgeClass(user.role)}`}>
                              {getRoleDisplay(user.role)}
                            </span>
                          </td>
                          <td>
                            <span className="balance-badge">
                              {user.balance ?? 0} 点
                            </span>
                          </td>
                          <td>{user.createdAt ? formatTime(user.createdAt) : '-'}</td>
                          <td>
                            {blockedUsers.some(u => u.userId === user.id) ? (
                              <span className="status-badge-locked">已锁定</span>
                            ) : (
                              <span className="status-badge-normal">正常</span>
                            )}
                          </td>
                          <td>
                            <div className="user-actions">
                              <button
                                className="admin-action-btn admin-edit-username-btn"
                                onClick={() => handleEditUsername(user)}
                                disabled={actionLoading === user.id}
                              >
                                修改名称
                              </button>
                              <button
                                className="admin-action-btn admin-edit-balance-btn"
                                onClick={() => handleEditBalance(user)}
                                disabled={actionLoading === user.id}
                              >
                                修改余额
                              </button>
                              <button
                                className="admin-action-btn admin-reset-password-btn"
                                onClick={() => handleResetPassword(user)}
                                disabled={actionLoading === user.id}
                              >
                                重置密码
                              </button>
                              {!user.isCurrentAdmin && (
                                <button
                                  className="admin-action-btn admin-delete-btn"
                                  onClick={() => handleDeleteUser(user)}
                                  disabled={actionLoading === user.id}
                                >
                                  {actionLoading === user.id ? '处理中...' : '删除'}
                                </button>
                              )}
                              {blockedUsers.some(u => u.userId === user.id) && (
                                <button
                                  className="admin-unlock-btn"
                                  onClick={() => {
                                    const blockedUser = blockedUsers.find(u => u.userId === user.id);
                                    if (blockedUser) {
                                      handleUnlockUser(user.id, user.username);
                                    }
                                  }}
                                  disabled={actionLoading === user.id}
                                >
                                  解锁
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            )}

            {activeTab === 'redemption' && (
              <div className="admin-table-container">
                <div className="redemption-toolbar">
                  <div className="redemption-stats">
                    <span className="stat-item">
                      <span className="stat-label">总计:</span>
                      <span className="stat-value">{redemptionCodes.length}</span>
                    </span>
                    <span className="stat-item">
                      <span className="stat-label">已使用:</span>
                      <span className="stat-value used">{usedCount}</span>
                    </span>
                    <span className="stat-item">
                      <span className="stat-label">未使用:</span>
                      <span className="stat-value unused">{unusedCount}</span>
                    </span>
                  </div>
                  <button
                    className="create-redemption-btn"
                    onClick={() => setShowCreateRedemptionModal(true)}
                  >
                    + 生成兑换码
                  </button>
                </div>

                {redemptionCodes.length === 0 ? (
                  <div className="admin-empty-state">
                    <div className="admin-empty-icon">🎁</div>
                    <h3>暂无兑换码</h3>
                    <p>点击上方按钮生成新的兑换码</p>
                  </div>
                ) : (
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>兑换码</th>
                        <th>点数</th>
                        <th>状态</th>
                        <th>创建时间</th>
                        <th>到期时间</th>
                        <th>使用者</th>
                        <th>使用时间</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {redemptionCodes.map((code) => {
                        const isExpired = code.isExpired && !code.isUsed;
                        return (
                          <tr key={code.id} className={isExpired ? 'expired-code-row' : ''}>
                            <td className="redemption-code-cell">
                              <code className="code-text">{code.code}</code>
                            </td>
                            <td>
                              <span className="points-badge">{code.points} 点</span>
                            </td>
                            <td>
                              {code.isUsed ? (
                                <span className="status-badge-used">已使用</span>
                              ) : isExpired ? (
                                <span className="status-badge-expired">已过期</span>
                              ) : (
                                <span className="status-badge-unused">未使用</span>
                              )}
                            </td>
                            <td>{formatTime(code.createdAt)}</td>
                            <td>
                              {code.expiresAt ? (
                                <span className={isExpired ? 'expired-time' : ''}>
                                  {formatTime(code.expiresAt)}
                                </span>
                              ) : (
                                <span>永不过期</span>
                              )}
                            </td>
                            <td className="admin-username">
                              {code.usedByUsername || '-'}
                            </td>
                            <td>
                              {code.usedAt ? formatTime(code.usedAt) : '-'}
                            </td>
                            <td>
                              <div className="redemption-actions">
                                {!code.isUsed && !isExpired && (
                                  <button
                                    className="delete-redemption-btn"
                                    onClick={() => handleDeleteRedemptionCode(code.id, code.code)}
                                    title="删除兑换码"
                                    disabled={actionLoading === code.id}
                                  >
                                    {actionLoading === code.id ? '删除中...' : '删除'}
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            )}

            {activeTab === 'users' && (
              <div className="admin-table-container">
                {blockedUsers.length === 0 ? (
                  <div className="admin-empty-state">
                    <div className="admin-empty-icon">✓</div>
                    <h3>暂无锁定用户</h3>
                    <p>所有用户状态正常</p>
                  </div>
                ) : (
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>用户ID</th>
                        <th>用户名</th>
                        <th>锁定时间</th>
                        <th>解锁时间</th>
                        <th>剩余时间</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {blockedUsers.map((user) => (
                        <tr key={user.userId}>
                          <td>{user.userId}</td>
                          <td className="admin-username">{user.username}</td>
                          <td>{formatTime(user.blockedAt)}</td>
                          <td>{formatTime(user.expiresAt)}</td>
                          <td>
                            <span className="admin-time-remaining">
                              {formatDuration(user.remainingSeconds)}
                            </span>
                          </td>
                          <td>
                            <button
                              className="admin-unlock-btn"
                              onClick={() => handleUnlockUser(user.userId, user.username)}
                              disabled={actionLoading === user.userId}
                            >
                              {actionLoading === user.userId ? '处理中...' : '解锁'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            )}

            {activeTab === 'ips' && (
              <div className="admin-table-container">
                {blockedIps.length === 0 ? (
                  <div className="admin-empty-state">
                    <div className="admin-empty-icon">✓</div>
                    <h3>暂无封锁IP</h3>
                    <p>所有IP访问正常</p>
                  </div>
                ) : (
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>IP地址</th>
                        <th>封锁时间</th>
                        <th>解锁时间</th>
                        <th>剩余时间</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {blockedIps.map((ip) => (
                        <tr key={ip.ipAddress}>
                          <td className="admin-ip">{ip.ipAddress}</td>
                          <td>{formatTime(ip.blockedAt)}</td>
                          <td>{formatTime(ip.expiresAt)}</td>
                          <td>
                            <span className="admin-time-remaining">
                              {formatDuration(ip.remainingSeconds)}
                            </span>
                          </td>
                          <td>
                            <button
                              className="admin-unlock-btn"
                              onClick={() => handleUnblockIp(ip.ipAddress)}
                              disabled={actionLoading === ip.ipAddress}
                            >
                              {actionLoading === ip.ipAddress ? '处理中...' : '解除封锁'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            )}
          </>
        )}
      </div>

      <div className="admin-footer">
        <p>数据每 5 秒自动刷新</p>
        <button onClick={loadData} className="admin-refresh-btn">
          手动刷新
        </button>
      </div>

      {resetPasswordUser && (
        <ResetPasswordModal
          user={resetPasswordUser}
          onClose={() => {
            setResetPasswordUser(null);
          }}
          onSuccess={() => loadData()}
        />
      )}

      {deleteUser && (
        <DeleteUserModal
          user={deleteUser}
          onClose={() => setDeleteUser(null)}
          onConfirm={confirmDeleteUser}
        />
      )}

      {editUsernameUser && (
        <EditUsernameModal
          user={editUsernameUser}
          onClose={() => {
            setEditUsernameUser(null);
          }}
          onSuccess={() => loadData()}
        />
      )}

      {editBalanceUser && (
        <EditBalanceModal
          user={editBalanceUser}
          onClose={() => {
            setEditBalanceUser(null);
          }}
          onSuccess={() => loadData()}
        />
      )}

      <CreateRedemptionCodeModal
        isOpen={showCreateRedemptionModal}
        onClose={() => setShowCreateRedemptionModal(false)}
        onSuccess={() => loadData()}
      />
    </div>
  );
};

export default AdminDashboard;
