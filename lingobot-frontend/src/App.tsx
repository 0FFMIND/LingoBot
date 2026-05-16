import { useState, useEffect, useCallback } from 'react';
import Sidebar from './components/chat/Sidebar';
import ChatWindow from './components/chat/ChatWindow';
import AuthModal from './components/auth/AuthModal';
import DeactivateModal from './components/auth/DeactivateModal';
import InsufficientBalanceModal from './components/chat/modals/InsufficientBalanceModal';
import AdminPage from './components/admin/AdminPage';
import LogPage from './components/admin/LogPage';
import RightPanel from './components/chat/RightPanel';
import AccountSettings from './components/auth/AccountSettings';
import VocabularyManager from './components/vocabulary/VocabularyManager';
import { UserDTO, ConversationDTO } from './types';
import { authApi, conversationApi } from './api';
import { useAuthStore, useChatStore, useConversationStore } from './stores';
import './App.css';

type Route = 'chat' | 'log' | 'admin';
type MainView = 'chat' | 'settings' | 'vocabulary-manager';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const extractPublicIdFromPath = (path: string): string | null => {
  if (path.startsWith('/v/')) {
    const publicId = path.slice(3).split('/')[0];
    if (UUID_REGEX.test(publicId)) {
      return publicId;
    }
  }
  return null;
};

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
    startNewChatWithModeSelection,
    reset: resetConversations,
  } = useConversationStore();

  const {
    isCompacting,
    compactingConversationPublicId,
    manualCompact,
    reset: resetChat,
  } = useChatStore();

  const [initialPathConversationLoaded, setInitialPathConversationLoaded] = useState(false);

  const syncUrlWithConversation = useCallback((conversation: ConversationDTO | null) => {
    if (!conversation) {
      if (window.location.pathname !== '/' && !window.location.pathname.startsWith('/admin') && !window.location.pathname.startsWith('/log')) {
        window.history.pushState({}, '', '/');
      }
      return;
    }

    const currentPublicId = extractPublicIdFromPath(window.location.pathname);
    if (currentPublicId !== conversation.publicId) {
      window.history.pushState({}, '', `/v/${conversation.publicId}`);
    }
  }, []);

  const loadConversationFromPath = useCallback(async () => {
    const publicId = extractPublicIdFromPath(window.location.pathname);
    if (!publicId || !isAuthenticated) {
      return;
    }

    const existingConv = conversations.find(c => c.publicId === publicId);
    if (existingConv) {
      selectConversation(existingConv);
      return;
    }

    try {
      const conv = await conversationApi.getByPublicId(publicId);
      selectConversation(conv);
    } catch (e) {
      console.error('Failed to load conversation from URL:', e);
      window.history.pushState({}, '', '/');
    }
  }, [conversations, isAuthenticated, selectConversation]);

  useEffect(() => {
    initAuth();
  }, []);

  useEffect(() => {
    if (isAuthenticated && !initialPathConversationLoaded) {
      setInitialPathConversationLoaded(true);
      loadConversationFromPath();
    }
  }, [isAuthenticated, initialPathConversationLoaded, loadConversationFromPath]);

  useEffect(() => {
    if (!initialPathConversationLoaded) return;
    loadConversationFromPath();
  }, [initialPathConversationLoaded, loadConversationFromPath]);

  useEffect(() => {
    if (initialPathConversationLoaded && mainView === 'chat') {
      syncUrlWithConversation(currentConversation);
    }
  }, [currentConversation, mainView, initialPathConversationLoaded, syncUrlWithConversation]);

  useEffect(() => {
    const handlePopState = () => {
      if (isAuthenticated) {
        loadConversationFromPath();
      }
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [isAuthenticated, loadConversationFromPath]);

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

  const handleOpenVocabularyManager = () => {
    setMainView('vocabulary-manager');
  };

  const handleCloseVocabularyManager = () => {
    setMainView('chat');
  };

  const currentLearningMode = getCurrentLearningMode();

  return (
    <div className="app english-coach-app">
      <Sidebar
        conversations={conversations}
        currentConversation={currentConversation}
        onSelectConversation={selectConversation}
        onCreateConversation={startNewChatWithModeSelection}
        onDeleteConversation={deleteConversation}
        onLoginClick={() => setShowAuthModal(true)}
        onLogout={handleLogoutComplete}
        onDeactivate={handleDeactivateClick}
        onOpenSettings={handleOpenSettings}
        onOpenVocabularyManager={handleOpenVocabularyManager}
        onLoadMore={loadMoreConversations}
        onManualCompact={manualCompact}
        disabled={!isAuthenticated}
        learningMode={currentLearningMode}
        onLearningModeChange={(mode) => useConversationStore.setState({ globalLearningMode: mode })}
        conversationLearningModes={conversationLearningModes}
        loadingMore={loadingMoreConversations}
        hasMore={hasMoreConversations}
        isCompacting={isCompacting}
        compactingConversationPublicId={compactingConversationPublicId}
      />

      <div className="main-content-wrapper">
        {mainView === 'chat' ? (
          <ChatWindow />
        ) : mainView === 'settings' ? (
          <AccountSettings
            onClose={handleCloseSettings}
            onLogout={handleLogoutComplete}
          />
        ) : (
          <VocabularyManager
            onBack={handleCloseVocabularyManager}
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
