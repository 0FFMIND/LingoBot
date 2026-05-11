import { authUtils, httpClient } from './httpClient';
import { VocabularyCardDTO, CreateVocabularyCardRequest, UserDTO } from '../types';

const refreshCurrentUserBalance = async (): Promise<void> => {
  try {
    const user = await httpClient.get<UserDTO>('/auth/me');
    authUtils.setUser(user);
    window.dispatchEvent(new CustomEvent('auth:balance-updated', {
      detail: { user },
    }));
  } catch (error) {
    console.warn('刷新账户余额失败:', error);
  }
};

export const vocabularyService = {
  getCardById: async (cardId: number): Promise<VocabularyCardDTO> => {
    return httpClient.get<VocabularyCardDTO>(`/vocabulary/cards/${cardId}`);
  },

  getAllCards: async (conversationId: number): Promise<VocabularyCardDTO[]> => {
    return httpClient.get<VocabularyCardDTO[]>(`/vocabulary/conversations/${conversationId}/cards`);
  },

  getCurrentCard: async (conversationId: number): Promise<VocabularyCardDTO | null> => {
    return httpClient.get<VocabularyCardDTO | null>(`/vocabulary/conversations/${conversationId}/current`);
  },

  getNextCard: async (conversationId: number, currentPosition?: number, level?: string): Promise<VocabularyCardDTO | null> => {
    const params = new URLSearchParams();
    if (currentPosition !== undefined) {
      params.set('currentPosition', String(currentPosition));
    }
    if (level) {
      params.set('level', level);
    }
    const query = params.toString();
    return httpClient.get<VocabularyCardDTO | null>(
      `/vocabulary/conversations/${conversationId}/next${query ? `?${query}` : ''}`
    );
  },

  getPrevCard: async (conversationId: number, currentPosition?: number): Promise<VocabularyCardDTO | null> => {
    const url = currentPosition !== undefined
      ? `/vocabulary/conversations/${conversationId}/prev?currentPosition=${currentPosition}`
      : `/vocabulary/conversations/${conversationId}/prev`;
    return httpClient.get<VocabularyCardDTO | null>(url);
  },

  generateNextCard: async (conversationId: number, level?: string): Promise<VocabularyCardDTO> => {
    const card = await httpClient.post<VocabularyCardDTO>(
      `/vocabulary/conversations/${conversationId}/generate`,
      level ? { level } : {}
    );
    await refreshCurrentUserBalance();
    return card;
  },

  regenerateCard: async (conversationId: number, level?: string): Promise<VocabularyCardDTO> => {
    const card = await httpClient.post<VocabularyCardDTO>(
      `/vocabulary/conversations/${conversationId}/regenerate`,
      level ? { level } : {}
    );
    await refreshCurrentUserBalance();
    return card;
  },

  createCard: async (conversationId: number, request: CreateVocabularyCardRequest): Promise<VocabularyCardDTO> => {
    return httpClient.post<VocabularyCardDTO>(
      `/vocabulary/cards?conversationId=${conversationId}`,
      request
    );
  },

  updateUserMeaning: async (cardId: number, userMeaning: string): Promise<VocabularyCardDTO> => {
    const card = await httpClient.put<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/meaning`,
      { userMeaning }
    );
    await refreshCurrentUserBalance();
    return card;
  },

  updateUserSentence: async (cardId: number, userSentence: string): Promise<VocabularyCardDTO> => {
    return httpClient.put<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/sentence`,
      { userSentence }
    );
  },

  updateAIFeedback: async (cardId: number, feedback: string): Promise<VocabularyCardDTO> => {
    return httpClient.put<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/feedback`,
      { feedback }
    );
  },

  markAsCompleted: async (cardId: number): Promise<VocabularyCardDTO> => {
    return httpClient.put<VocabularyCardDTO>(`/vocabulary/cards/${cardId}/complete`);
  },

  deleteAllCards: async (conversationId: number): Promise<void> => {
    return httpClient.delete<void>(`/vocabulary/conversations/${conversationId}/cards`);
  },

  getCardCount: async (conversationId: number): Promise<number> => {
    return httpClient.get<number>(`/vocabulary/conversations/${conversationId}/count`);
  },

  getMeaningCheckStatus: async (cardId: number): Promise<{
    cardId: number;
    word: string;
    userMeaningGuess: string;
    meaningCheckCompleted: boolean;
    meaningIsCorrect?: boolean;
    meaningCheckResult: string;
  }> => {
    return httpClient.get<{
      cardId: number;
      word: string;
      userMeaningGuess: string;
      meaningCheckCompleted: boolean;
      meaningIsCorrect?: boolean;
      meaningCheckResult: string;
    }>(`/vocabulary/cards/${cardId}/meaning-check`);
  },
};
