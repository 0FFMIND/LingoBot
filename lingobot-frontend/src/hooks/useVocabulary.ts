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
  userEnglishSentenceInput: string;
  setVocabularyCategory: (category: VocabularyCategory) => void;
  setVocabularyDifficulty: (difficulty: VocabularyDifficulty) => void;
  setUserMeaningInput: (value: string) => void;
  setUserEnglishSentenceInput: (value: string) => void;
  loadCurrentCard: () => Promise<void>;
  getPrevCard: () => Promise<void>;
  getNextCard: () => Promise<void>;

  saveUserMeaning: (cardId: number, meaning: string) => Promise<void>;
  saveUserEnglishSentence: (cardId: number, sentence: string) => Promise<void>;
  analyzeUserSentence: (cardId: number) => Promise<void>;
  markAsCompleted: (cardId: number) => Promise<void>;
}

export function useVocabulary(
  conversationPublicId: string | null,
  isAuthenticated: boolean
): UseVocabularyResult {
  const [currentVocabularyCard, setCurrentVocabularyCard] = useState<VocabularyCardDTO | null>(null);
  const [vocabularyCardLoading, setVocabularyCardLoading] = useState(false);
  const [vocabularyCategory, setVocabularyCategory] = useState<VocabularyCategory>('cefr');
  const [vocabularyDifficulty, setVocabularyDifficulty] = useState<VocabularyDifficulty>('b2');
  const [userMeaningInput, setUserMeaningInput] = useState('');
  const [userEnglishSentenceInput, setUserEnglishSentenceInput] = useState('');

  const loadCurrentCard = useCallback(async () => {
    if (!isAuthenticated || !conversationPublicId) return;

    setVocabularyCardLoading(true);
    try {
      const existingCard = await vocabularyService.getCurrentCard(conversationPublicId);
      if (existingCard) {
        setCurrentVocabularyCard(existingCard);
        setUserMeaningInput(existingCard.userMeaningGuess || '');
        setUserEnglishSentenceInput(existingCard.userEnglishSentence || '');
      }
      try {
        const totalCount = await vocabularyService.getCardCount(conversationPublicId);
        const usage = useTokenUsageStore.getState().usageByConversationPublicId[conversationPublicId];
        const currentLocalCount = usage?.wordCardsCount ?? 0;
        if (totalCount > currentLocalCount) {
          for (let i = currentLocalCount; i < totalCount; i++) {
            useTokenUsageStore.getState().recordWordCard(conversationPublicId);
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
  }, [isAuthenticated, conversationPublicId]);

  const getPrevCard = useCallback(async () => {
    if (!isAuthenticated || !conversationPublicId || !currentVocabularyCard) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyService.getPrevCard(
        conversationPublicId,
        currentVocabularyCard.position
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput(card.userMeaningGuess || '');
        setUserEnglishSentenceInput(card.userEnglishSentence || '');
      }
    } catch (error) {
      console.error('获取上一个单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, [isAuthenticated, conversationPublicId, currentVocabularyCard]);

  const getNextCard = useCallback(async () => {
    if (!isAuthenticated || !conversationPublicId || !currentVocabularyCard) return;

    setVocabularyCardLoading(true);
    try {
      const card = await vocabularyService.getNextCard(
        conversationPublicId,
        currentVocabularyCard.position,
        vocabularyCategory,
        vocabularyDifficulty
      );
      if (card) {
        setCurrentVocabularyCard(card);
        setUserMeaningInput(card.userMeaningGuess || '');
        setUserEnglishSentenceInput(card.userEnglishSentence || '');
      }
    } catch (error) {
      console.error('获取下一个单词失败:', error);
    } finally {
      setVocabularyCardLoading(false);
    }
  }, [isAuthenticated, conversationPublicId, currentVocabularyCard, vocabularyCategory, vocabularyDifficulty]);

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

  const saveUserEnglishSentence = useCallback(async (cardId: number, sentence: string) => {
    try {
      const updated = await vocabularyService.updateUserEnglishSentence(cardId, sentence);
      if (updated) {
        setCurrentVocabularyCard(updated);
      }
    } catch (error) {
      console.error('保存用户英文句子失败:', error);
    }
  }, []);

  const analyzeUserSentence = useCallback(async (cardId: number) => {
    try {
      await vocabularyService.analyzeUserSentence(cardId);
    } catch (error) {
      console.error('触发句子分析失败:', error);
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
    userEnglishSentenceInput,
    setVocabularyCategory,
    setVocabularyDifficulty,
    setUserMeaningInput,
    setUserEnglishSentenceInput,
    loadCurrentCard,
    getPrevCard,
    getNextCard,
    saveUserMeaning,
    saveUserEnglishSentence,
    analyzeUserSentence,
    markAsCompleted,
  };
}
