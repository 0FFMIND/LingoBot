import { useState, useEffect, useCallback } from 'react';
import Sidebar from './components/Sidebar';
import ChatWindow from './components/ChatWindow';
import AuthModal from './components/AuthModal';
import DeactivateModal from './components/DeactivateModal';
import InsufficientBalanceModal from './components/InsufficientBalanceModal';
import LogViewer from './components/LogViewer';
import AdminPage from './components/AdminPage';
import RightPanel from './components/RightPanel';
import AccountSettings from './components/AccountSettings';
import { ConversationDTO, MessageDTO, UserDTO, ModelType, MessageType, LearningMode, LEARNING_MODES, VocabularyCategory, VocabularyDifficulty, VocabularyCardDTO } from './types';
import { conversationApi, chatApi, authUtils, authApi, vocabularyApi } from './api';
import { usePreferences } from './hooks';
import './App.css';

type Route = 'chat' | 'log' | 'admin';
type MainView = 'chat' | 'settings';

function LearningModeSelector({
  learningMode,
  onLearningModeChange,
  disabled,
}: {
  learningMode: LearningMode;
  onLearningModeChange: (mode: LearningMode) => void;
  disabled?: boolean;
}) {
  const [showMenu, setShowMenu] = useState(false);

  const currentConfig = LEARNING_MODES[learningMode];

  return (
    <div className="learning-mode-selector">
      <button
        className="learning-mode-toggle-btn"
        onClick={() => !disabled && setShowMenu(!showMenu)}
        disabled={disabled}
        title="选择学习模式"
      >
        <span className="learning-mode-icon">{currentConfig.icon}</span>
        <span className="learning-mode-label">{currentConfig.label}</span>
        <span className={`dropdown-arrow ${showMenu ? 'open' : ''}`}>▼</span>
      </button>
      
      {showMenu && (
        <div className="learning-mode-menu">
          <div className="learning-mode-menu-title">选择学习模式</div>
          <div className="learning-mode-options">
            {(Object.keys(LEARNING_MODES) as LearningMode[]).map((mode) => {
              const config = LEARNING_MODES[mode];
              const isActive = learningMode === mode;
              
              return (
                <button
                  key={mode}
                  className={`learning-mode-option ${isActive ? 'active' : ''}`}
                  onClick={() => {
                    onLearningModeChange(mode);
                    setShowMenu(false);
                  }}
                >
                  <span className="learning-mode-option-icon">{config.icon}</span>
                  <div className="learning-mode-option-info">
                    <span className="learning-mode-option-name">{config.label}</span>
                    <span className="learning-mode-option-desc">{config.description}</span>
                  </div>
                  {isActive && <span className="check-mark">✓</span>}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function ChatApp() {
  const [conversations, setConversations] = useState<ConversationDTO[]>([]);
  const [currentConversation, setCurrentConversation] = useState<ConversationDTO | null>(null);
  const [messages, setMessages] = useState<MessageDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [streamingContent, setStreamingContent] = useState<string>('');
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [showDeactivateModal, setShowDeactivateModal] = useState(false);
  const [showInsufficientBalanceModal, setShowInsufficientBalanceModal] = useState(false);
  const [insufficientBalanceData, setInsufficientBalanceData] = useState<{
    message: string;
    currentBalance?: number;
    requiredCost?: number;
  }>({ message: '你的余额不足' });
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserDTO | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [mode, setMode] = useState<'chat' | 'agent'>('chat');
  const [globalLearningMode, setGlobalLearningMode] = useState<LearningMode>('chat');
  const [mainView, setMainView] = useState<MainView>('chat');
  
  const [conversationLearningModes, setConversationLearningModes] = useState<Record<number, LearningMode>>({});
  
  const [newConversationId, setNewConversationId] = useState<number | null>(null);
  const [showModeSelectorForNewChat, setShowModeSelectorForNewChat] = useState(false);
  
  const [agentStatus, setAgentStatus] = useState<{
    thinking: string;
    toolCalls: Array<{
      toolName: string;
      toolId: string;
      status: 'calling' | 'success' | 'error';
      result?: string;
      error?: string;
    }>;
  }>({
    thinking: '',
    toolCalls: [],
  });
  
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMoreConversations, setHasMoreConversations] = useState(true);
  const [loadingMoreConversations, setLoadingMoreConversations] = useState(false);
  const PAGE_SIZE = 20;

  const [currentVocabularyCard, setCurrentVocabularyCard] = useState<VocabularyCardDTO | null>(null);
  const [vocabularyCardLoading, setVocabularyCardLoading] = useState(false);
  const [userMeaningInput, setUserMeaningInput] = useState('');
  const [userSentenceInput, setUserSentenceInput] = useState('');

  const preferences = usePreferences(isAuthenticated);
  
  const chatModel = preferences.chatModel;
  const vocabularyCategory = preferences.vocabularyCategory;
  const vocabularyDifficulty = preferences.vocabularyDifficulty;
  const vocabularyModel = preferences.vocabularyModel;
  
  const setChatModel = preferences.setChatModel;
  const setVocabularyCategory = preferences.setVocabularyCategory;
  const setVocabularyDifficulty = preferences.setVocabularyDifficulty;
  const setVocabularyModel = preferences.setVocabularyModel;

  useEffect(() => {
    const handleGoogleOAuthCallback = () => {
      const urlParams = new URLSearchParams(window.location.search);
      const token = urlParams.get('token');
      const userId = urlParams.get('userId');
      const username = urlParams.get('username');
      const email = urlParams.get('email');
      const avatar = urlParams.get('avatar');
      
      if (token && userId && username) {
        const user: UserDTO = {
          id: parseInt(userId),
          username,
          email: email || undefined,
          avatar: avatar || undefined,
          createdAt: new Date().toISOString(),
        };
        
        authUtils.setAuth(token, user);
        
        urlParams.delete('token');
        urlParams.delete('userId');
        urlParams.delete('username');
        urlParams.delete('email');
        urlParams.delete('avatar');
        
        window.history.replaceState({}, '', window.location.pathname + (urlParams.toString() ? '?' + urlParams.toString() : ''));
        
        return { token, user };
      }
      return null;
    };
    
    const oauthResult = handleGoogleOAuthCallback();
    
    if (oauthResult) {
      setIsAuthenticated(true);
      setCurrentUser(oauthResult.user);
      setInitializing(false);
      return;
    }
    
    const token = authUtils.initializeAuth();
    if (token) {
      authApi.getCurrentUser()
        .then((fetchedUser) => {
          authUtils.setUser(fetchedUser);
          setIsAuthenticated(true);
          setCurrentUser(fetchedUser);
        })
        .catch(() => {
          authUtils.clearAuth();
          setIsAuthenticated(false);
          setCurrentUser(null);
        })
        .finally(() => setInitializing(false));
    } else {
      setIsAuthenticated(false);
      setCurrentUser(null);
      setInitializing(false);
    }
  }, []);

  const loadConversations = useCallback(async () => {
    if (!isAuthenticated) return;
    
    try {
      const pageResponse = await conversationApi.getByPage(0, PAGE_SIZE);
      const data = pageResponse.content;
      
      setConversations(data);
      setCurrentPage(0);
      setHasMoreConversations(pageResponse.hasNext);
      
      if (data.length > 0 && !currentConversation) {
        const currentConvFromBackend = await conversationApi.getCurrent();
        
        if (currentConvFromBackend) {
          const matchingConv = data.find(c => c.id === currentConvFromBackend.id);
          if (matchingConv) {
            setCurrentConversation(matchingConv);
            if (matchingConv.learningMode) {
              setGlobalLearningMode(matchingConv.learningMode as LearningMode);
            }
            if (matchingConv.learningMode === 'vocabulary') {
              loadVocabularyCardIfNeeded(matchingConv.id);
            }
            return;
          }
        }

        setCurrentConversation(data[0]);
        try {
          await conversationApi.setCurrent(data[0].id);
        } catch (e) {
          console.warn('保存当前对话到后端失败:', e);
        }
        if (data[0].learningMode) {
          setGlobalLearningMode(data[0].learningMode as LearningMode);
        }
        if (data[0].learningMode === 'vocabulary') {
          loadVocabularyCardIfNeeded(data[0].id);
        }
      }
    } catch (error) {
      console.error('加载对话列表失败:', error);
    }
  }, [isAuthenticated, currentConversation, PAGE_SIZE]);
  
  const loadMoreConversations = useCallback(async () => {
    if (!isAuthenticated || loadingMoreConversations || !hasMoreConversations) return;
    
    setLoadingMoreConversations(true);
    try {
      const nextPage = currentPage + 1;
      const pageResponse = await conversationApi.getByPage(nextPage, PAGE_SIZE);
      
      const existingIds = new Set(conversations.map(c => c.id));
      const newConversations = pageResponse.content.filter(c => !existingIds.has(c.id));
      
      if (newConversations.length > 0) {
        setConversations(prev => [...prev, ...newConversations]);
      }
      
      setCurrentPage(nextPage);
      setHasMoreConversations(pageResponse.hasNext);
    } catch (error) {
      console.error('加载更多对话失败:', error);
    } finally {
      setLoadingMoreConversations(false);
    }
  }, [isAuthenticated, loadingMoreConversations, hasMoreConversations, currentPage, conversations, PAGE_SIZE]);

  useEffect(() => {
    if (isAuthenticated) {
      loadConversations();
    } else {
      setConversations([]);
      setCurrentConversation(null);
      setMessages([]);
    }
  }, [isAuthenticated, loadConversations]);

  useEffect(() => {
    const handleLogin = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      setIsAuthenticated(true);
      if (customEvent.detail?.user) {
        setCurrentUser(customEvent.detail.user);
      }
    };

    const handleLogout = () => {
      setIsAuthenticated(false);
      setCurrentUser(null);
      setConversations([]);
      setCurrentConversation(null);
      setMessages([]);
      setConversationLearningModes({});
      setShowModeSelectorForNewChat(false);
      setNewConversationId(null);
    };

    const handleInsufficientBalance = (e: Event) => {
      const customEvent = e as CustomEvent<{
        message: string;
        currentBalance?: number;
        requiredCost?: number;
      }>;
      setInsufficientBalanceData({
        message: customEvent.detail?.message || '你的余额不足',
        currentBalance: customEvent.detail?.currentBalance,
        requiredCost: customEvent.detail?.requiredCost
      });
      setShowInsufficientBalanceModal(true);
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

  useEffect(() => {
    setMessages([]);
    setStreamingContent('');
    setCurrentVocabularyCard(null);
    setUserMeaningInput('');
    setUserSentenceInput('');
    
    if (currentConversation && isAuthenticated) {
      loadMessages(currentConversation.id);
    }
  }, [currentConversation, isAuthenticated]);

  useEffect(() => {
    if (!currentConversation || !isAuthenticated) {
      return;
    }

    let mode: LearningMode = 'chat';
    if (currentConversation.learningMode) {
      mode = currentConversation.learningMode as LearningMode;
    } else if (conversationLearningModes[currentConversation.id]) {
      mode = conversationLearningModes[currentConversation.id];
    } else {
      mode = globalLearningMode;
    }

    if (mode === 'vocabulary' && !currentVocabularyCard) {
      console.log('🔄 页面刷新后检测到词汇模式，尝试恢复词汇卡状态...');
      vocabularyApi.getCurrentCard(currentConversation.id)
        .then(existingCard => {
          if (existingCard) {
            setCurrentVocabularyCard(existingCard);
            setUserMeaningInput(existingCard.userMeaningGuess || '');
            setUserSentenceInput(existingCard.userSentence || '');
            console.log('✅ 已恢复词汇卡状态:', existingCard.word);
          } else {
            console.log('该对话没有词汇卡');
          }
        })
        .catch(e => {
          console.log('获取词汇卡失败:', e);
        });
    }
  }, [
    currentConversation, 
    isAuthenticated, 
    globalLearningMode, 
    conversationLearningModes,
    currentVocabularyCard
  ]);

  const loadMessages = async (conversationId: number) => {
    if (!isAuthenticated) return;
    
    try {
      const data = await chatApi.getMessages(conversationId);
      setMessages(data);
    } catch (error) {
      console.error('加载消息失败:', error);
    }
  };

  const createConversation = async (title: string) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    try {
      const newConversation = await conversationApi.create({ title });
      setConversations([newConversation, ...conversations]);
      setCurrentConversation(newConversation);
      
      setNewConversationId(newConversation.id);
      setShowModeSelectorForNewChat(true);
      setMainView('chat');
      
    } catch (error) {
      console.error('创建对话失败:', error);
      alert('创建对话失败');
    }
  };

  /**
   * 创建带有指定学习模式的新对话
   * 与 createConversation + handleLearningModeSelect 的流程不同，此函数直接一步完成
   * 适用于用户直接选择学习模式创建对话的场景
   */
  const createConversationWithMode = async (selectedMode: LearningMode) => {
    // 未登录时显示登录弹窗
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    try {
      // 获取学习模式配置，用于设置对话标题
      const modeConfig = LEARNING_MODES[selectedMode];
      // 创建新对话，标题使用学习模式的标签
      const newConversation = await conversationApi.create({ title: modeConfig.label });
      
      // 更新对话列表，将新对话放在最前面
      setConversations([newConversation, ...conversations]);
      // 设置当前对话为新创建的对话
      setCurrentConversation(newConversation);
      // 确保切换到聊天视图
      setMainView('chat');
      
      // 记录该对话的学习模式（本地状态）
      setConversationLearningModes((prev) => ({
        ...prev,
        [newConversation.id]: selectedMode,
      }));
      // 更新全局学习模式
      setGlobalLearningMode(selectedMode);

      // 更新对话列表中该对话的学习模式属性
      setConversations((prev) =>
        prev.map((c) => (c.id === newConversation.id ? { ...c, learningMode: selectedMode } : c))
      );

      // 如果当前对话就是新创建的对话，更新其学习模式
      if (currentConversation && currentConversation.id === newConversation.id) {
        setCurrentConversation({
          ...currentConversation,
          learningMode: selectedMode,
        });
      }
      
      // 将学习模式保存到后端数据库
      try {
        await conversationApi.updateLearningMode(newConversation.id, selectedMode);
      } catch (e) {
        console.error('❌ 保存学习模式到后端失败:', e);
      }

      // ==================== 词汇模式处理 ====================
      // 如果选择的是词汇学习模式，需要生成第一张词汇卡
      if (selectedMode === 'vocabulary') {
        setLoading(true);

        try {
          // 调用词汇卡生成接口，为新对话生成第一张词汇卡
          const newCard = await vocabularyApi.generateNextCard(
            newConversation.id,
            vocabularyDifficulty.toUpperCase()
          );

          // 如果成功生成词汇卡，更新相关状态
          if (newCard) {
            setCurrentVocabularyCard(newCard);
            setUserMeaningInput('');
            setUserSentenceInput('');
          }

          // 刷新消息列表和对话列表（词汇卡生成可能会创建系统消息）
          loadMessages(newConversation.id);
          loadConversations();

        } catch (error) {
          console.error('生成词汇卡失败:', error);
          alert('生成词汇卡失败: ' + (error instanceof Error ? error.message : '未知错误'));
        } finally {
          setLoading(false);
        }
      }
      
    } catch (error) {
      console.error('创建对话失败:', error);
      alert('创建对话失败');
    }
  };

  const handleLearningModeSelect = async (selectedMode: LearningMode) => {
    if (newConversationId !== null && showModeSelectorForNewChat) {
      const conversationId = newConversationId;
      
      setConversationLearningModes((prev) => ({
        ...prev,
        [conversationId]: selectedMode,
      }));
      setGlobalLearningMode(selectedMode);
      setShowModeSelectorForNewChat(false);
      setNewConversationId(null);

      setConversations((prev) =>
        prev.map((c) => (c.id === conversationId ? { ...c, learningMode: selectedMode } : c))
      );

      if (currentConversation && currentConversation.id === conversationId) {
        setCurrentConversation({
          ...currentConversation,
          learningMode: selectedMode,
        });
      }
      
      try {
        await conversationApi.updateLearningMode(conversationId, selectedMode);
      } catch (e) {
        console.error('❌ 保存学习模式到后端失败:', e);
      }

      // ==================== 词汇模式初始化逻辑 ====================
      // 当用户选择词汇模式时，检查是否已有词汇卡（恢复之前的学习进度）
      if (selectedMode === 'vocabulary') {
        setLoading(true);
        
        try {
          // 尝试获取当前对话已存在的词汇卡
          // 这是为了恢复用户之前的学习进度
          let existingCard: VocabularyCardDTO | null = null;
          try {
            existingCard = await vocabularyApi.getCurrentCard(conversationId);
          } catch (e) {
            console.log('没有找到已存在的词汇卡:', e);
          }
          
          if (existingCard) {
            // 找到已存在的词汇卡，直接恢复状态
            setCurrentVocabularyCard(existingCard);
            setUserMeaningInput(existingCard.userMeaningGuess || '');
            setUserSentenceInput(existingCard.userSentence || '');
          } else {
            // 没有找到已存在的词汇卡，生成第一张词汇卡
            console.log('正在生成第一张词汇卡... conversationId:', conversationId);
            const newCard = await vocabularyApi.generateNextCard(
              conversationId,
              vocabularyDifficulty.toUpperCase()
            );

            // 如果成功生成词汇卡，更新相关状态
            if (newCard) {
              setCurrentVocabularyCard(newCard);
              setUserMeaningInput('');
              setUserSentenceInput('');
            }

            // 刷新消息列表和对话列表（词汇卡生成可能会创建系统消息）
            loadMessages(conversationId);
            loadConversations();
          }
        } catch (error) {
          console.error('词汇卡初始化失败:', error);
        } finally {
          setLoading(false);
        }
      }
    }
  };

  const getCurrentLearningMode = (): LearningMode => {
    if (currentConversation && conversationLearningModes[currentConversation.id]) {
      return conversationLearningModes[currentConversation.id];
    }
    if (currentConversation?.learningMode) {
      return currentConversation.learningMode as LearningMode;
    }
    return globalLearningMode;
  };

  const isNewChatWaitingForMode = () => {
    return showModeSelectorForNewChat && 
           currentConversation?.id === newConversationId && 
           messages.length === 0;
  };

  const deleteConversation = async (id: number) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    try {
      await conversationApi.delete(id);
      setConversations(conversations.filter((c) => c.id !== id));
      setConversationLearningModes((prev) => {
        const updated = { ...prev };
        delete updated[id];
        return updated;
      });
      if (currentConversation?.id === id) {
        const remaining = conversations.filter((c) => c.id !== id);
        const newCurrent = remaining.length > 0 ? remaining[0] : null;
        setCurrentConversation(newCurrent);
        
        try {
          if (newCurrent) {
            await conversationApi.setCurrent(newCurrent.id);
          } else {
            await conversationApi.setCurrent(null);
          }
        } catch (e) {
          console.warn('更新当前对话到后端失败:', e);
        }
        
        if (newCurrent?.learningMode) {
          setGlobalLearningMode(newCurrent.learningMode as LearningMode);
        }
        
        if (newConversationId === id) {
          setShowModeSelectorForNewChat(false);
          setNewConversationId(null);
        }
      }
    } catch (error) {
      console.error('删除对话失败:', error);
      alert('删除对话失败');
    }
  };

  const loadVocabularyCardIfNeeded = useCallback(async (conversationId: number) => {
    setVocabularyCardLoading(true);
    try {
      const existingCard = await vocabularyApi.getCurrentCard(conversationId);
      if (existingCard) {
        setCurrentVocabularyCard(existingCard);
        setUserMeaningInput(existingCard.userMeaningGuess || '');
        setUserSentenceInput(existingCard.userSentence || '');
        console.log('✅ 已恢复词汇卡状态:', existingCard.word);
      } else {
        console.log('该对话没有词汇卡');
        setCurrentVocabularyCard(null);
      }
    } catch (e) {
      console.log('获取词汇卡失败:', e);
      setCurrentVocabularyCard(null);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, []);

  const handleSelectConversation = (conversation: ConversationDTO) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }
    setCurrentConversation(conversation);
    setShowModeSelectorForNewChat(false);
    setNewConversationId(null);
    setMainView('chat');
    
    setCurrentVocabularyCard(null);
    setUserMeaningInput('');
    setUserSentenceInput('');
    
    try {
      conversationApi.setCurrent(conversation.id);
    } catch (e) {
      console.warn('保存当前对话到后端失败:', e);
    }
    
    let selectedMode: LearningMode = 'chat';
    if (conversation.learningMode) {
      selectedMode = conversation.learningMode as LearningMode;
    } else if (conversationLearningModes[conversation.id]) {
      selectedMode = conversationLearningModes[conversation.id];
    }
    setGlobalLearningMode(selectedMode);
    
    if (selectedMode === 'vocabulary') {
      loadVocabularyCardIfNeeded(conversation.id);
    }
  };

  const currentLearningMode = getCurrentLearningMode();
  const isWaitingForMode = isNewChatWaitingForMode();

  const sendMessage = async (content: string) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading || isWaitingForMode) return;

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: currentConversation.id,
      content,
      role: 'user',
      timestamp: new Date().toISOString(),
    };

    setMessages([...messages, tempUserMessage]);

    try {
      await chatApi.sendMessageStream(
        {
          conversationId: currentConversation.id,
          content,
          mode,
          model: chatModel,
          learningMode: currentLearningMode,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('流式消息错误:', error);
          setMessages((prev) => prev.slice(0, -1));
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('发送消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('发送消息失败:', error);
      setMessages((prev) => prev.slice(0, -1));
      setStreamingContent('');
      setLoading(false);
      alert('发送消息失败');
    }
  };

  const sendMessageWithIntent = async (content: string, intent: string, currentWord: string) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading || isWaitingForMode) return;

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const messageContent = `[intent:${intent}][current_word:${currentWord}] ${content}`;

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: currentConversation.id,
      content: messageContent,
      role: 'user',
      timestamp: new Date().toISOString(),
    };

    setMessages([...messages, tempUserMessage]);

    try {
      const response = await chatApi.sendVocabularySentenceMessage({
        conversationId: currentConversation.id,
        content: messageContent,
        mode,
        model: vocabularyModel,
        learningMode: currentLearningMode,
        intent: intent as any,
        currentWord,
        vocabularyCategory,
        vocabularyDifficulty,
      });

      loadMessages(currentConversation.id);
      setLoading(false);
      loadConversations();

      if (currentLearningMode === 'vocabulary') {
        if (intent === 'make_sentence' && currentVocabularyCard && response?.content) {
          vocabularyApi.updateAIFeedback(currentVocabularyCard.id, response.content)
            .then(updatedCard => {
              if (updatedCard) setCurrentVocabularyCard(updatedCard);
            })
            .catch(e => {
              console.error('保存AI反馈失败:', e);
              vocabularyApi.getCurrentCard(currentConversation.id)
                .then(card => { if (card) setCurrentVocabularyCard(card); })
                .catch(err => console.error('重新加载词汇卡失败:', err));
            });
        } else {
          vocabularyApi.getCurrentCard(currentConversation.id)
            .then(updatedCard => {
              if (updatedCard) setCurrentVocabularyCard(updatedCard);
            })
            .catch(e => console.error('重新加载词汇卡失败:', e));
        }
      }
    } catch (error) {
      console.error('发送消息失败:', error);
      setMessages((prev) => prev.slice(0, -1));
      setLoading(false);
      alert('发送消息失败');
    }
  };

  const sendAudioMessage = async (audioData: string, audioFormat: string, duration: number) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading || isWaitingForMode) return;

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: currentConversation.id,
      content: '',
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: 'audio' as MessageType,
      audioData,
      audioFormat,
      audioDuration: duration,
    };

    setMessages([...messages, tempUserMessage]);

    try {
      await chatApi.sendMessageStream(
        {
          conversationId: currentConversation.id,
          content: '',
          mode,
          model: chatModel,
          learningMode: currentLearningMode,
          messageType: 'audio' as MessageType,
          audioData,
          audioFormat,
          audioDuration: duration,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('流式消息错误:', error);
          setMessages((prev) => prev.slice(0, -1));
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('发送消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('发送语音消息失败:', error);
      setMessages((prev) => prev.slice(0, -1));
      setStreamingContent('');
      setLoading(false);
      alert('发送消息失败');
    }
  };

  const sendImageMessage = async (content: string, imageData: string, imageFormat: string) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading || isWaitingForMode) return;

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: currentConversation.id,
      content: content || '',
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: 'image' as MessageType,
      imageData,
      imageFormat,
    };

    setMessages([...messages, tempUserMessage]);

    try {
      await chatApi.sendMessageStream(
        {
          conversationId: currentConversation.id,
          content: content || '',
          mode,
          model: chatModel,
          learningMode: currentLearningMode,
          messageType: 'image' as MessageType,
          imageData,
          imageFormat,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('流式消息错误:', error);
          setMessages((prev) => prev.slice(0, -1));
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('发送消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('发送图片消息失败:', error);
      setMessages((prev) => prev.slice(0, -1));
      setStreamingContent('');
      setLoading(false);
      alert('发送消息失败');
    }
  };

  const handlePrevWord = async () => {
    if (!currentConversation || !currentVocabularyCard) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyApi.getPrevCard(
        currentConversation.id,
        currentVocabularyCard.position
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput(card.userMeaningGuess || '');
        setUserSentenceInput(card.userSentence || '');
      }
    } catch (error) {
      console.error('获取上一个单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  };

  const handleNextWord = async () => {
    if (!currentConversation || !currentVocabularyCard) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyApi.getNextCard(
        currentConversation.id,
        currentVocabularyCard.position
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput(card.userMeaningGuess || '');
        setUserSentenceInput(card.userSentence || '');
      } else {
        const newCard = await vocabularyApi.generateNextCard(
          currentConversation.id,
          vocabularyDifficulty.toUpperCase()
        );
        if (newCard) {
          setCurrentVocabularyCard(newCard);
          setUserMeaningInput('');
          setUserSentenceInput('');
        }
      }
    } catch (error) {
      console.error('获取下一个单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  };

  const handleRegenerateWord = async () => {
    if (!currentConversation) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyApi.regenerateCard(
        currentConversation.id,
        vocabularyDifficulty.toUpperCase()
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput('');
        setUserSentenceInput('');
      }
    } catch (error) {
      console.error('重新生成单词失败:', error);
      alert('重新生成单词失败: ' + (error instanceof Error ? error.message : '未知错误'));
    } finally {
      setVocabularyCardLoading(false);
    }
  };

  const saveVocabularyUserMeaning = async (cardId: number, meaning: string) => {
    try {
      const updated = await vocabularyApi.updateUserMeaning(cardId, meaning);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('保存用户意思失败:', error);
    }
    pollMeaningCheckResult(cardId);
  };

  const pollMeaningCheckResult = async (cardId: number) => {
    const maxAttempts = 20;
    const intervalMs = 1500;
    for (let i = 0; i < maxAttempts; i++) {
      await new Promise(resolve => setTimeout(resolve, intervalMs));
      try {
        const result = await vocabularyApi.getMeaningCheckStatus(cardId);
        if (result.meaningCheckCompleted) {
          setCurrentVocabularyCard(prev =>
            prev && prev.id === cardId
              ? { ...prev, meaningCheckCompleted: true, meaningIsCorrect: result.meaningIsCorrect, meaningCheckResult: result.meaningCheckResult }
              : prev
          );
          return;
        }
      } catch (e) {
        console.warn('轮询释义检查结果失败:', e);
      }
    }
  };

  const saveVocabularyUserSentence = async (cardId: number, sentence: string) => {
    try {
      const updated = await vocabularyApi.updateUserSentence(cardId, sentence);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('保存用户造句失败:', error);
    }
  };

  const markVocabularyCardComplete = async (cardId: number) => {
    try {
      const updated = await vocabularyApi.markAsCompleted(cardId);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('标记词汇卡完成失败:', error);
    }
  };

  const retryMessage = async (assistantMessageId: number) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading) return;

    const assistantMessageIndex = messages.findIndex((m) => m.id === assistantMessageId);
    
    if (assistantMessageIndex === -1) {
      alert('找不到要重试的消息');
      return;
    }

    const assistantMessage = messages[assistantMessageIndex];
    
    if (assistantMessage.role !== 'assistant') {
      alert('只能重试AI助手的消息');
      return;
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, assistantMessageIndex);
    setMessages(updatedMessages);

    try {
      await chatApi.retryMessageStream(
        {
          conversationId: currentConversation.id,
          assistantMessageId,
          model: chatModel,
          mode,
          learningMode: currentLearningMode,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('重试消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('重试消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('重试消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('重试消息失败');
    }
  };

  const retryMessageWithModel = async (assistantMessageId: number, targetModel: ModelType) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading) return;

    const assistantMessageIndex = messages.findIndex((m) => m.id === assistantMessageId);
    
    if (assistantMessageIndex === -1) {
      alert('找不到要重试的消息');
      return;
    }

    const assistantMessage = messages[assistantMessageIndex];
    
    if (assistantMessage.role !== 'assistant') {
      alert('只能重试AI助手的消息');
      return;
    }

    if (targetModel !== chatModel) {
      setChatModel(targetModel);
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, assistantMessageIndex);
    setMessages(updatedMessages);

    try {
      await chatApi.retryMessageStream(
        {
          conversationId: currentConversation.id,
          assistantMessageId,
          model: targetModel,
          mode,
          learningMode: currentLearningMode,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('重试消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('重试消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('重试消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('重试消息失败');
    }
  };

  const editAudioMessage = async (
    userMessageId: number, 
    newContent: string, 
    audioData?: string, 
    audioFormat?: string, 
    audioDuration?: number
  ) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading) return;

    const userMessageIndex = messages.findIndex((m) => m.id === userMessageId);
    
    if (userMessageIndex === -1) {
      alert('找不到要编辑的消息');
      return;
    }

    const userMessage = messages[userMessageIndex];
    
    if (userMessage.role !== 'user') {
      alert('只能编辑用户消息');
      return;
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, userMessageIndex + 1).map(m => 
      m.id === userMessageId 
        ? { 
            ...m, 
            content: newContent,
            audioData: audioData || m.audioData,
            audioFormat: audioFormat || m.audioFormat,
            audioDuration: audioDuration || m.audioDuration,
          } 
        : m
    );
    setMessages(updatedMessages);

    const finalAudioData = audioData || userMessage.audioData;
    const finalAudioFormat = audioFormat || userMessage.audioFormat;
    const finalAudioDuration = audioDuration || userMessage.audioDuration;

    try {
      await chatApi.sendMessageStream(
        {
          conversationId: currentConversation.id,
          content: newContent,
          mode,
          model: chatModel,
          learningMode: currentLearningMode,
          messageType: finalAudioData ? 'audio' as MessageType : 'text' as MessageType,
          audioData: finalAudioData,
          audioFormat: finalAudioFormat,
          audioDuration: finalAudioDuration,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('编辑消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('编辑消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('编辑消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('编辑消息失败');
    }
  };

  const editMessage = async (userMessageId: number, newContent: string) => {
    if (!isAuthenticated) {
      setShowAuthModal(true);
      return;
    }

    if (!currentConversation || loading) return;

    const userMessageIndex = messages.findIndex((m) => m.id === userMessageId);
    
    if (userMessageIndex === -1) {
      alert('找不到要编辑的消息');
      return;
    }

    const userMessage = messages[userMessageIndex];
    
    if (userMessage.role !== 'user') {
      alert('只能编辑用户消息');
      return;
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, userMessageIndex + 1).map(m => 
      m.id === userMessageId ? { ...m, content: newContent } : m
    );
    setMessages(updatedMessages);

    try {
      await chatApi.editMessageStream(
        {
          conversationId: currentConversation.id,
          userMessageId,
          newContent,
        },
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          loadMessages(currentConversation.id);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          loadConversations();
        },
        (error) => {
          console.error('编辑消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('编辑消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('编辑消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('编辑消息失败');
    }
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setConversations([]);
    setCurrentConversation(null);
    setMessages([]);
    setConversationLearningModes({});
    setShowModeSelectorForNewChat(false);
    setNewConversationId(null);
  };

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
    handleLogout();
  };

  const handleDeactivateClick = () => {
    setShowDeactivateModal(true);
  };

  const handleDeactivateSuccess = () => {
    setShowDeactivateModal(false);
    handleLogout();
  };

  const handleOpenSettings = () => {
    setMainView('settings');
  };

  const handleCloseSettings = () => {
    setMainView('chat');
  };

  const learningConfig = LEARNING_MODES[currentLearningMode];

  return (
    <div className="app english-coach-app">
      <Sidebar
        conversations={conversations}
        currentConversation={currentConversation}
        onSelectConversation={handleSelectConversation}
        onCreateConversation={createConversation}
        onDeleteConversation={deleteConversation}
        onLoginClick={() => setShowAuthModal(true)}
        onLogout={handleLogoutComplete}
        onDeactivate={handleDeactivateClick}
        onOpenSettings={handleOpenSettings}
        onLoadMore={loadMoreConversations}
        disabled={!isAuthenticated}
        learningMode={currentLearningMode}
        onLearningModeChange={setGlobalLearningMode}
        conversationLearningModes={conversationLearningModes}
        loadingMore={loadingMoreConversations}
        hasMore={hasMoreConversations}
      />
      
      <div className="main-content-wrapper">
        {mainView === 'chat' ? (
          <ChatWindow
            conversation={currentConversation}
            messages={messages}
            onSendMessage={sendMessage}
            onSendAudioMessage={sendAudioMessage}
            onSendImageMessage={sendImageMessage}
            onRetryMessage={retryMessage}
            onRetryWithModel={retryMessageWithModel}
            onEditMessage={editMessage}
            onEditAudioMessage={editAudioMessage}
            onSendWithIntent={sendMessageWithIntent}
            loading={loading}
            streamingContent={streamingContent}
            disabled={!isAuthenticated}
            mode={mode}
            onModeChange={setMode}
            model={chatModel}
            onModelChange={setChatModel}
            agentStatus={agentStatus}
            learningMode={currentLearningMode}
            onLearningModeSelect={handleLearningModeSelect}
            showModeSelector={isWaitingForMode}
            vocabularyCategory={vocabularyCategory}
            onVocabularyCategoryChange={setVocabularyCategory}
            vocabularyDifficulty={vocabularyDifficulty}
            onVocabularyDifficultyChange={setVocabularyDifficulty}
            vocabularyModel={vocabularyModel}
            onVocabularyModelChange={setVocabularyModel}
            currentVocabularyCard={currentVocabularyCard}
            vocabularyCardLoading={vocabularyCardLoading}
            onPrevWord={handlePrevWord}
            onNextWord={handleNextWord}
            onRegenerateWord={handleRegenerateWord}
            onSaveVocabularyMeaning={saveVocabularyUserMeaning}
            onSaveVocabularySentence={saveVocabularyUserSentence}
            onCreateConversationWithMode={createConversationWithMode}
          />
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
        onClose={() => setShowInsufficientBalanceModal(false)}
        message={insufficientBalanceData.message}
        currentBalance={insufficientBalanceData.currentBalance}
        requiredCost={insufficientBalanceData.requiredCost}
      />
    </div>
  );
}

function LogPage() {
  return (
    <div className="log-page">
      <div className="log-page-header">
        <h1>📋 后端日志查看器</h1>
        <button 
          className="back-to-chat-btn"
          onClick={() => {
            window.history.pushState({}, '', '/');
            window.dispatchEvent(new PopStateEvent('popstate'));
          }}
        >
          ← 返回聊天
        </button>
      </div>
      <LogViewer fullPage={true} />
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
