import { useState, useEffect, useCallback } from 'react';
import { conversationService } from '../services';
import { ConversationDTO, LearningMode } from '../types';

export interface UseConversationResult {
  conversations: ConversationDTO[];
  currentConversation: ConversationDTO | null;
  loading: boolean;
  createConversation: (title: string) => Promise<ConversationDTO | null>;
  deleteConversation: (publicId: string) => Promise<void>;
  selectConversation: (conversation: ConversationDTO) => void;
  updateLearningMode: (publicId: string, mode: LearningMode) => Promise<void>;
  loadConversations: () => Promise<void>;
}

export function useConversation(isAuthenticated: boolean): UseConversationResult {
  const [conversations, setConversations] = useState<ConversationDTO[]>([]);
  const [currentConversation, setCurrentConversation] = useState<ConversationDTO | null>(null);
  const [loading, setLoading] = useState(false);

  const loadConversations = useCallback(async () => {
    if (!isAuthenticated) return;
    
    setLoading(true);
    try {
      const data = await conversationService.getAll();
      setConversations(data);
      
      if (data.length > 0 && !currentConversation) {
        const currentConvFromBackend = await conversationService.getCurrent();
        
        if (currentConvFromBackend) {
          const matchingConv = data.find(c => c.publicId === currentConvFromBackend.publicId);
          if (matchingConv) {
            setCurrentConversation(matchingConv);
            setLoading(false);
            return;
          }
        }
        
        setCurrentConversation(data[0]);
        try {
          await conversationService.setCurrent(data[0].publicId);
        } catch (e) {
          console.warn('保存当前对话到后端失败:', e);
        }
      }
    } catch (error) {
      console.error('加载对话列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated, currentConversation]);

  const createConversation = useCallback(async (title: string): Promise<ConversationDTO | null> => {
    if (!isAuthenticated) return null;

    try {
      const newConversation = await conversationService.create({ title });
      setConversations([newConversation, ...conversations]);
      setCurrentConversation(newConversation);
      
      try {
        await conversationService.setCurrent(newConversation.publicId);
      } catch (e) {
        console.warn('保存当前对话到后端失败:', e);
      }
      
      return newConversation;
    } catch (error) {
      console.error('创建对话失败:', error);
      alert('创建对话失败');
      return null;
    }
  }, [isAuthenticated, conversations]);

  const deleteConversation = useCallback(async (publicId: string) => {
    if (!isAuthenticated) return;

    try {
      await conversationService.delete(publicId);
      setConversations(conversations.filter((c) => c.publicId !== publicId));
      
      if (currentConversation?.publicId === publicId) {
        const remaining = conversations.filter((c) => c.publicId !== publicId);
        const newCurrent = remaining.length > 0 ? remaining[0] : null;
        setCurrentConversation(newCurrent);
        
        try {
          if (newCurrent) {
            await conversationService.setCurrent(newCurrent.publicId);
          } else {
            await conversationService.setCurrent(null);
          }
        } catch (e) {
          console.warn('更新当前对话到后端失败:', e);
        }
      }
    } catch (error) {
      console.error('删除对话失败:', error);
      alert('删除对话失败');
    }
  }, [isAuthenticated, conversations, currentConversation]);

  const selectConversation = useCallback((conversation: ConversationDTO) => {
    setCurrentConversation(conversation);
    try {
      conversationService.setCurrent(conversation.publicId);
    } catch (e) {
      console.warn('保存当前对话到后端失败:', e);
    }
  }, []);

  const updateLearningMode = useCallback(async (publicId: string, mode: LearningMode) => {
    try {
      await conversationService.updateLearningMode(publicId, mode);
      setConversations((prev) =>
        prev.map((c) => (c.publicId === publicId ? { ...c, learningMode: mode } : c))
      );
      if (currentConversation?.publicId === publicId) {
        setCurrentConversation({
          ...currentConversation,
          learningMode: mode,
        });
      }
    } catch (e) {
      console.error('保存学习模式到后端失败:', e);
    }
  }, [currentConversation]);

  useEffect(() => {
    if (isAuthenticated) {
      loadConversations();
    } else {
      setConversations([]);
      setCurrentConversation(null);
    }
  }, [isAuthenticated, loadConversations]);

  return {
    conversations,
    currentConversation,
    loading,
    createConversation,
    deleteConversation,
    selectConversation,
    updateLearningMode,
    loadConversations,
  };
}
