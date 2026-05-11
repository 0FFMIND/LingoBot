import { useState, useCallback } from 'react';
import { vocabularyService } from '../services';
import { useTokenUsageStore } from '../stores';
import { 
  VocabularyCardDTO, 
  VocabularyCategory,
  VocabularyDifficulty,
} from '../types';

export interface UseVocabularyResult {
  currentVocabularyCard: VocabularyCardDTO | null;
  vocabularyCardLoading: boolean;
  vocabularyCategory: VocabularyCategory;
  vocabularyDifficulty: VocabularyDifficulty;
  userMeaningInput: string;
  userSentenceInput: string;
  setVocabularyCategory: (category: VocabularyCategory) => void;
  setVocabularyDifficulty: (difficulty: VocabularyDifficulty) => void;
  setUserMeaningInput: (value: string) => void;
  setUserSentenceInput: (value: string) => void;
  loadCurrentCard: () => Promise<void>;
  getPrevCard: () => Promise<void>;
  getNextCard: () => Promise<void>;
  generateNextCard: () => Promise<void>;
  regenerateCard: () => Promise<void>;
  saveUserMeaning: (cardId: number, meaning: string) => Promise<void>;
  saveUserSentence: (cardId: number, sentence: string) => Promise<void>;
  markAsCompleted: (cardId: number) => Promise<void>;
}

export function useVocabulary(
  conversationId: number | null,
  isAuthenticated: boolean
): UseVocabularyResult {
  const [currentVocabularyCard, setCurrentVocabularyCard] = useState<VocabularyCardDTO | null>(null);
  const [vocabularyCardLoading, setVocabularyCardLoading] = useState(false);
  const [vocabularyCategory, setVocabularyCategory] = useState<VocabularyCategory>('cefr');
  const [vocabularyDifficulty, setVocabularyDifficulty] = useState<VocabularyDifficulty>('b2');
  const [userMeaningInput, setUserMeaningInput] = useState('');
  const [userSentenceInput, setUserSentenceInput] = useState('');

  const loadCurrentCard = useCallback(async () => {
    if (!isAuthenticated || !conversationId) return;

    setVocabularyCardLoading(true);
    try {
      const existingCard = await vocabularyService.getCurrentCard(conversationId);
      if (existingCard) {
        setCurrentVocabularyCard(existingCard);
        setUserMeaningInput(existingCard.userMeaningGuess || '');
        setUserSentenceInput(existingCard.userSentence || '');
      }
      try {
        const totalCount = await vocabularyService.getCardCount(conversationId);
        const usage = useTokenUsageStore.getState().usageByConversation[conversationId];
        const currentLocalCount = usage?.wordCardsCount ?? 0;
        if (totalCount > currentLocalCount) {
          for (let i = currentLocalCount; i < totalCount; i++) {
            useTokenUsageStore.getState().recordWordCard(conversationId);
          }
        }
      } catch (e) {
        console.log('同步词汇卡计数失败:', e);
      }
    } catch (e) {
      console.log('没有找到已存在的词汇卡:', e);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, [isAuthenticated, conversationId]);

  const getPrevCard = useCallback(async () => {
    if (!isAuthenticated || !conversationId || !currentVocabularyCard) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyService.getPrevCard(
        conversationId,
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
  }, [isAuthenticated, conversationId, currentVocabularyCard]);

  const getNextCard = useCallback(async () => {
    if (!isAuthenticated || !conversationId || !currentVocabularyCard) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyService.getNextCard(
        conversationId,
        currentVocabularyCard.position,
        vocabularyDifficulty.toUpperCase()
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput(card.userMeaningGuess || '');
        setUserSentenceInput(card.userSentence || '');
      }
    } catch (error) {
      console.error('获取下一个单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, [isAuthenticated, conversationId, currentVocabularyCard]);

  const generateNextCard = useCallback(async () => {
    if (!isAuthenticated || !conversationId) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyService.generateNextCard(
        conversationId,
        vocabularyDifficulty.toUpperCase()
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput('');
        setUserSentenceInput('');
        useTokenUsageStore.getState().recordWordCard(conversationId);
        console.log('✅ 已记录新词汇卡到本地状态，conversationId:', conversationId);
      }
    } catch (error) {
      console.error('生成新单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, [isAuthenticated, conversationId, vocabularyDifficulty]);

  const regenerateCard = useCallback(async () => {
    if (!isAuthenticated || !conversationId) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyService.regenerateCard(
        conversationId,
        vocabularyDifficulty.toUpperCase()
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput('');
        setUserSentenceInput('');
      }
    } catch (error) {
      console.error('重新生成单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, [isAuthenticated, conversationId, vocabularyDifficulty]);

  const saveUserMeaning = useCallback(async (cardId: number, meaning: string) => {
    try {
      const updated = await vocabularyService.updateUserMeaning(cardId, meaning);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('保存用户意思失败:', error);
    }
  }, []);

  const saveUserSentence = useCallback(async (cardId: number, sentence: string) => {
    try {
      const updated = await vocabularyService.updateUserSentence(cardId, sentence);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('保存用户造句失败:', error);
    }
  }, []);

  const markAsCompleted = useCallback(async (cardId: number) => {
    try {
      const updated = await vocabularyService.markAsCompleted(cardId);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('标记词汇卡完成失败:', error);
    }
  }, []);

  return {
    currentVocabularyCard,
    vocabularyCardLoading,
    vocabularyCategory,
    vocabularyDifficulty,
    userMeaningInput,
    userSentenceInput,
    setVocabularyCategory,
    setVocabularyDifficulty,
    setUserMeaningInput,
    setUserSentenceInput,
    loadCurrentCard,
    getPrevCard,
    getNextCard,
    generateNextCard,
    regenerateCard,
    saveUserMeaning,
    saveUserSentence,
    markAsCompleted,
  };
}
