import React, { useState, useEffect, useRef, useCallback } from 'react';
import { ConversationDTO, UserDTO, LearningMode, LEARNING_MODES } from '../../types';
import { authUtils } from '../../api';
import ChangePasswordModal from '../auth/ChangePasswordModal';
import DeleteConversationModal from './modals/DeleteConversationModal';
import CircularProgress from '../common/CircularProgress';
import ContextStatusTooltip from './ContextStatusTooltip';

interface SidebarProps {
  conversations: ConversationDTO[];
  currentConversation: ConversationDTO | null;
  onSelectConversation: (conversation: ConversationDTO) => void;
  onCreateConversation: (title?: string) => void;
  onDeleteConversation: (publicId: string) => void;
  onLoginClick: () => void;
  onLogout: () => void;
  onDeactivate: () => void;
  onOpenSettings: () => void;
  onOpenVocabularyManager?: () => void;
  onLoadMore?: () => void;
  onManualCompact?: (publicId: string) => void;
  disabled?: boolean;
  learningMode?: LearningMode;
  onLearningModeChange?: (mode: LearningMode) => void;
  conversationLearningModes?: Record<string, LearningMode>;
  loadingMore?: boolean;
  hasMore?: boolean;
  isCompacting?: boolean;
  compactingConversationPublicId?: string | null;
}

const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  currentConversation,
  onSelectConversation,
  onCreateConversation,
  onDeleteConversation,
  onLoginClick,
  onLogout,
  onDeactivate,
  onOpenSettings,
  onOpenVocabularyManager,
  onLoadMore,
  onManualCompact,
  disabled = false,
  learningMode = 'chat',
  onLearningModeChange,
  conversationLearningModes = {},
  loadingMore = false,
  hasMore = false,
  isCompacting = false,
  compactingConversationPublicId = null,
}) => {
  const [user, setUser] = useState<UserDTO | null>(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const [showChangePasswordModal, setShowChangePasswordModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deletingConversationPublicId, setDeletingConversationPublicId] = useState<string | null>(null);
  const [deletingConversationTitle, setDeletingConversationTitle] = useState('');

  const conversationListRef = useRef<HTMLDivElement>(null);

  const handleScroll = useCallback(() => {
    if (!conversationListRef.current || !onLoadMore || loadingMore || !hasMore) return;

    const { scrollTop, scrollHeight, clientHeight } = conversationListRef.current;
    const scrollThreshold = 50;

    if (scrollTop + clientHeight >= scrollHeight - scrollThreshold) {
      onLoadMore();
    }
  }, [onLoadMore, loadingMore, hasMore]);

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

    const handleAvatarUpdated = () => {
      const currentUser = authUtils.getUser();
      setUser(currentUser);
    };

    const handleUsernameUpdated = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      setUser(customEvent.detail.user);
    };

    const handleBalanceUpdated = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      if (customEvent.detail?.user) {
        setUser(customEvent.detail.user);
      }
    };

    window.addEventListener('auth:login', handleLogin);
    window.addEventListener('auth:logout', handleLogout);
    window.addEventListener('auth:avatar-updated', handleAvatarUpdated);
    window.addEventListener('auth:username-updated', handleUsernameUpdated);
    window.addEventListener('auth:balance-updated', handleBalanceUpdated);

    return () => {
      window.removeEventListener('auth:login', handleLogin);
      window.removeEventListener('auth:logout', handleLogout);
      window.removeEventListener('auth:avatar-updated', handleAvatarUpdated);
      window.removeEventListener('auth:username-updated', handleUsernameUpdated);
      window.removeEventListener('auth:balance-updated', handleBalanceUpdated);
    };
  }, []);

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

  useEffect(() => {
    const listElement = conversationListRef.current;
    if (listElement && onLoadMore) {
      listElement.addEventListener('scroll', handleScroll);
      return () => listElement.removeEventListener('scroll', handleScroll);
    }
  }, [handleScroll, onLoadMore]);

  const toggleDropdown = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowDropdown(!showDropdown);
  };

  const handleNewChatClick = () => {
    if (!disabled) {
      onCreateConversation();
    }
  };

  const handleKnowledgeBaseClick = () => {
    if (onOpenVocabularyManager && !disabled) {
      onOpenVocabularyManager();
    }
  };

  const handleSearchClick = () => {
    alert('搜索功能开发中...');
  };

  const handleSelect = (conversation: ConversationDTO) => {
    if (!disabled) {
      onSelectConversation(conversation);
    }
  };

  const handleDelete = (e: React.MouseEvent, publicId: string) => {
    e.stopPropagation();
    if (disabled) return;

    const conversation = conversations.find(c => c.publicId === publicId);
    if (conversation) {
      setDeletingConversationPublicId(publicId);
      setDeletingConversationTitle(conversation.title);
      setShowDeleteModal(true);
    }
  };

  const handleConfirmDelete = () => {
    if (deletingConversationPublicId !== null) {
      onDeleteConversation(deletingConversationPublicId);
    }
    setShowDeleteModal(false);
    setDeletingConversationPublicId(null);
    setDeletingConversationTitle('');
  };

  const handleCancelDelete = () => {
    setShowDeleteModal(false);
    setDeletingConversationPublicId(null);
    setDeletingConversationTitle('');
  };

  const handleChangePasswordClick = () => {
    setShowDropdown(false);
    setShowChangePasswordModal(true);
  };

  const getConversationLearningMode = (conversation: ConversationDTO): LearningMode => {
    if (conversation.learningMode && LEARNING_MODES[conversation.learningMode as LearningMode]) {
      return conversation.learningMode as LearningMode;
    }
    return conversationLearningModes[conversation.publicId] || 'chat';
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays === 0) {
      return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    } else if (diffDays === 1) {
      return '昨天';
    } else if (diffDays < 7) {
      return date.toLocaleDateString('zh-CN', { weekday: 'short' });
    } else {
      return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
    }
  };

  return (
    <div className={`sidebar-english ${disabled ? 'disabled' : ''}`}>
      <div className="sidebar-header-english">
        <div className="brand-section">
          <div className="brand-icon">
            <div className="icon-circle">
              <span className="robot-emoji">🤖</span>
            </div>
          </div>
          <div className="brand-text">
            <div className="brand-name">英语学习助手</div>
            <div className="brand-subtitle">AI English Coach ✨</div>
          </div>
        </div>
      </div>

      <div className="sidebar-menu-section">
        <button
          className="sidebar-menu-item"
          onClick={handleNewChatClick}
          disabled={disabled}
        >
          <span className="menu-item-icon">📝</span>
          <span className="menu-item-text">新建学习对话</span>
        </button>

        <button
          className="sidebar-menu-item"
          onClick={handleKnowledgeBaseClick}
          disabled={disabled}
        >
          <span className="menu-item-icon">📚</span>
          <span className="menu-item-text">单词卡管理</span>
        </button>

        <button
          className="sidebar-menu-item"
          onClick={handleSearchClick}
          disabled={disabled}
        >
          <span className="menu-item-icon">🔍</span>
          <span className="menu-item-text">搜索</span>
        </button>
      </div>

      <div className="conversation-history-header">
        <div className="history-title">
          <span className="history-icon">📋</span>
          <span>对话历史</span>
        </div>
      </div>

      <div className="conversation-list-english compact" ref={conversationListRef}>
        {conversations.length === 0 ? (
          <div className="empty-state-english compact">
            <div className="empty-icon">💬</div>
            <p>暂无学习对话</p>
            <p className="empty-hint">{disabled ? '请先登录后开始学习' : '点击上方按钮开始新的学习之旅'}</p>
          </div>
        ) : (
          conversations.map((conversation) => {
            const convLearningMode = getConversationLearningMode(conversation);
            const modeConfig = LEARNING_MODES[convLearningMode];

            const hasContextStatus = conversation.contextStatus !== undefined;

            return (
              <div
                key={conversation.publicId}
                className={`conversation-item-english compact ${
                  currentConversation?.publicId === conversation.publicId ? 'active' : ''
                } ${disabled ? 'disabled' : ''}`}
                onClick={() => handleSelect(conversation)}
              >
                <div className="conversation-info-english compact">
                  <span className="conversation-icon" title={modeConfig?.label}>
                    {modeConfig?.icon || '💬'}
                  </span>
                  <div className="conversation-text-wrapper">
                    <h3 className="conversation-title-english compact">{conversation.title}</h3>
                    <div className="conversation-meta-row">
                      <p className="conversation-meta-english">
                        <span className="mode-badge" style={{ fontSize: '10px', opacity: 0.7 }}>
                          {modeConfig?.label || '普通聊天'}
                        </span>
                        <span style={{ marginLeft: '8px' }}>{formatDate(conversation.updatedAt)}</span>
                      </p>
                      {hasContextStatus && conversation.contextStatus && (
                        <ContextStatusTooltip
                          status={conversation.contextStatus}
                          onManualCompact={onManualCompact}
                          publicId={conversation.publicId}
                          isCompacting={isCompacting}
                          compactingConversationPublicId={compactingConversationPublicId}
                        >
                          <div className="conversation-progress-inline">
                            <CircularProgress
                              percentage={conversation.contextStatus.tokenRatio * 100}
                              size={12}
                              strokeWidth={2}
                              showPercentage={false}
                              onDoubleClick={() => {
                                if (onManualCompact && !isCompacting) {
                                  onManualCompact(conversation.publicId);
                                }
                              }}
                            />
                          </div>
                        </ContextStatusTooltip>
                      )}
                    </div>
                  </div>
                </div>

                <button
                  className={`delete-btn-english compact ${disabled ? 'disabled' : ''}`}
                  onClick={(e) => handleDelete(e, conversation.publicId)}
                  disabled={disabled}
                  title="删除对话"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                  </svg>
                </button>
              </div>
            );
          })
        )}

        {loadingMore && (
          <div className="load-more-indicator">
            <div className="loading-spinner"></div>
            <span>加载中...</span>
          </div>
        )}
      </div>

      <div className="sidebar-user-section-english">
        {user ? (
          <div className="user-info-english compact" onClick={toggleDropdown}>
            <div className="user-avatar-english compact">
              {user.avatar ? (
                <img src={user.avatar} alt={user.username} className="avatar-image" />
              ) : (
                <span className="avatar-text">{user.username.charAt(0).toUpperCase()}</span>
              )}
            </div>
            <div className="user-details-english">
              <span className="user-name-english">{user.username}</span>
              <span className="user-status-english compact">
                <span className="membership-text">已登录</span>
              </span>
            </div>
            <span className={`dropdown-arrow-english ${showDropdown ? 'open' : ''}`}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </span>
          </div>
        ) : (
          <button className="login-btn-english compact" onClick={onLoginClick}>
            <span className="login-icon-english">👤</span>
            <span className="login-text-english">点击登录</span>
          </button>
        )}

        {showDropdown && user && (
          <div className="user-dropdown-english compact">
            <div className="dropdown-item-english" onClick={() => {
              setShowDropdown(false);
              onOpenSettings();
            }}>
              <span className="dropdown-icon">⚙️</span>
              <span>账户设置</span>
            </div>
            <div className="dropdown-item-english" onClick={onLogout}>
              <span className="dropdown-icon">🚪</span>
              <span>退出登录</span>
            </div>
          </div>
        )}
      </div>

      <ChangePasswordModal
        isOpen={showChangePasswordModal}
        onClose={() => setShowChangePasswordModal(false)}
      />
      <DeleteConversationModal
        isOpen={showDeleteModal}
        conversationTitle={deletingConversationTitle}
        onClose={handleCancelDelete}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
};

export default Sidebar;
