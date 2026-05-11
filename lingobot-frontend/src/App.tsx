import { useState, useEffect } from 'react';
import Sidebar from './features/chat/components/Sidebar';
import ChatWindow from './features/chat/components/ChatWindow';
import AuthModal from './features/auth/components/AuthModal';
import DeactivateModal from './features/auth/components/DeactivateModal';
import InsufficientBalanceModal from './features/chat/components/InsufficientBalanceModal';
import AdminPage from './features/admin/components/AdminPage';
import LogPage from './features/admin/components/LogPage';
import RightPanel from './features/chat/components/RightPanel';
import AccountSettings from './features/auth/components/AccountSettings';
import { UserDTO } from './types';
import { authApi } from './api';
import { useAuthStore, useChatStore, useConversationStore } from './stores';
import './App.css';

type Route = 'chat' | 'log' | 'admin';

function ChatApp() {
  const {
    isAuthenticated,
    currentUser,
    initializing,
    showAuthModal,
    showDeactivateModal,
    showInsufficientBalanceModal,
    insufficientBalanceData,
    initAuth,
    setCurrentUser,
    setShowAuthModal,
    setShowDeactivateModal,
    closeInsufficientBalance,
    showInsufficientBalance,
    logout,
  } = useAuthStore();

  const {
    conversations,
    currentConversation,
    mainView,
    loadingMoreConversations,
    hasMoreConversations,
    conversationLearningModes,
    getCurrentLearningMode,
    loadConversations,
    loadMoreConversations,
    selectConversation,
    createConversation,
    deleteConversation,
    setMainView,
    reset: resetConversations,
  } = useConversationStore();

  const {
    isCompacting,
    compactingConversationId,
    manualCompact,
    reset: resetChat,
  } = useChatStore();

  // Initialize auth on mount
  useEffect(() => {
    initAuth();
  }, []);

  // Load conversations when authenticated
  useEffect(() => {
    if (isAuthenticated) {
      loadConversations();
    } else {
      resetConversations();
      resetChat();
    }
  }, [isAuthenticated]);

  // Listen for global events
  useEffect(() => {
    const handleLogin = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      useAuthStore.setState({ isAuthenticated: true });
      if (customEvent.detail?.user) {
        setCurrentUser(customEvent.detail.user);
      }
    };

    const handleLogout = () => {
      logout();
      resetConversations();
      resetChat();
    };

    const handleInsufficientBalance = (e: Event) => {
      const customEvent = e as CustomEvent<{
        message: string;
        currentBalance?: number;
        requiredCost?: number;
      }>;
      showInsufficientBalance({
        message: customEvent.detail?.message || '你的余额不足',
        currentBalance: customEvent.detail?.currentBalance,
        requiredCost: customEvent.detail?.requiredCost,
      });
    };

    const handleBalanceUpdated = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      if (customEvent.detail?.user) {
        setCurrentUser(customEvent.detail.user);
      }
    };

    window.addEventListener('auth:login', handleLogin);
    window.addEventListener('auth:logout', handleLogout);
    window.addEventListener('balance:insufficient', handleInsufficientBalance);
    window.addEventListener('auth:balance-updated', handleBalanceUpdated);

    return () => {
      window.removeEventListener('auth:login', handleLogin);
      window.removeEventListener('auth:logout', handleLogout);
      window.removeEventListener('balance:insufficient', handleInsufficientBalance);
      window.removeEventListener('auth:balance-updated', handleBalanceUpdated);
    };
  }, []);

  if (initializing) {
    return (
      <div className="app">
        <div className="loading-screen">
          <div className="loading-spinner"></div>
          <p>加载中...</p>
        </div>
      </div>
    );
  }

  const handleLogoutComplete = () => {
    authApi.logout();
    logout();
    resetConversations();
    resetChat();
  };

  const handleDeactivateClick = () => {
    setShowDeactivateModal(true);
  };

  const handleDeactivateSuccess = () => {
    setShowDeactivateModal(false);
    handleLogoutComplete();
  };

  const handleOpenSettings = () => {
    setMainView('settings');
  };

  const handleCloseSettings = () => {
    setMainView('chat');
  };

  const currentLearningMode = getCurrentLearningMode();

  return (
    <div className="app english-coach-app">
      <Sidebar
        conversations={conversations}
        currentConversation={currentConversation}
        onSelectConversation={selectConversation}
        onCreateConversation={createConversation}
        onDeleteConversation={deleteConversation}
        onLoginClick={() => setShowAuthModal(true)}
        onLogout={handleLogoutComplete}
        onDeactivate={handleDeactivateClick}
        onOpenSettings={handleOpenSettings}
        onLoadMore={loadMoreConversations}
        onManualCompact={manualCompact}
        disabled={!isAuthenticated}
        learningMode={currentLearningMode}
        onLearningModeChange={(mode) => useConversationStore.setState({ globalLearningMode: mode })}
        conversationLearningModes={conversationLearningModes}
        loadingMore={loadingMoreConversations}
        hasMore={hasMoreConversations}
        isCompacting={isCompacting}
        compactingConversationId={compactingConversationId}
      />

      <div className="main-content-wrapper">
        {mainView === 'chat' ? (
          <ChatWindow />
        ) : (
          <AccountSettings
            onClose={handleCloseSettings}
            onLogout={handleLogoutComplete}
          />
        )}
      </div>

      <RightPanel />

      <AuthModal
        isOpen={showAuthModal}
        onClose={() => setShowAuthModal(false)}
        onSuccess={() => {
          loadConversations();
        }}
      />

      <DeactivateModal
        isOpen={showDeactivateModal}
        user={currentUser}
        onClose={() => setShowDeactivateModal(false)}
        onSuccess={handleDeactivateSuccess}
      />

      <InsufficientBalanceModal
        isOpen={showInsufficientBalanceModal}
        onClose={closeInsufficientBalance}
        message={insufficientBalanceData.message}
        currentBalance={insufficientBalanceData.currentBalance}
        requiredCost={insufficientBalanceData.requiredCost}
      />
    </div>
  );
}

function App() {
  const [currentRoute, setCurrentRoute] = useState<Route>('chat');

  useEffect(() => {
    const checkPath = () => {
      const path = window.location.pathname;
      if (path === '/admin' || path.startsWith('/admin/')) {
        setCurrentRoute('admin');
      } else if (path === '/log' || path === '/logs') {
        setCurrentRoute('log');
      } else {
        setCurrentRoute('chat');
      }
    };

    checkPath();
    window.addEventListener('popstate', checkPath);
    return () => window.removeEventListener('popstate', checkPath);
  }, []);

  if (currentRoute === 'admin') {
    return <AdminPage />;
  }

  if (currentRoute === 'log') {
    return <LogPage />;
  }

  return <ChatApp />;
}

export default App;
