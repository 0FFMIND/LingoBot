import { authUtils, httpClient } from './httpClient';
import {
  VocabularyCardDTO,

  UpdateLearningStateRequest,
  UserDTO,
  VocabularyStatsDTO,
  UserVocabularyDTO,
  PageResponseDTO,
  VocabularyCategory,
  VocabularyDifficulty,
  VocabularyStatus,
  VocabularySortBy,
} from '../types';

export type AIModifyVocabularyRequest = Partial<Pick<
  UserVocabularyDTO,
  'word' | 'phonetic' | 'partOfSpeech' | 'meaning' | 'example' | 'exampleTranslation' | 'synonyms' | 'category' | 'difficulty'
>> & { id: number };

type MeaningCheckStatus = {
  cardId: number;
  word: string;
  userMeaningGuess: string;
  meaningCheckCompleted: boolean;
  meaningIsCorrect?: boolean;
  meaningCheckResult: string;
  chineseSentenceForTranslation?: string;
};

type SentenceAnalysisStatus = {
  cardId: number;
  word: string;
  chineseSentenceForTranslation: string;
  userEnglishSentence: string;
  sentenceAnalysisCompleted: boolean;
  sentenceHasNewWord?: boolean;
  sentenceMeaningMatches?: boolean;
  sentenceAnalysis: string;
};

type VocabularyBatchGenerationResult = {
  revealedCards: VocabularyCardDTO[];
  hiddenCards: VocabularyCardDTO[];
  totalRevealed: number;
  totalHidden: number;
  totalCount: number;
};

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

  getAllCards: async (conversationPublicId: string): Promise<VocabularyCardDTO[]> => {
    return httpClient.get<VocabularyCardDTO[]>(`/vocabulary/conversations/${conversationPublicId}/cards`);
  },

  getCurrentCard: async (conversationPublicId: string): Promise<VocabularyCardDTO | null> => {
    return httpClient.get<VocabularyCardDTO | null>(`/vocabulary/conversations/${conversationPublicId}/current`);
  },

  getNextCard: async (
    conversationPublicId: string,
    currentPosition?: number,
    category?: VocabularyCategory,
    difficulty?: VocabularyDifficulty
  ): Promise<VocabularyCardDTO | null> => {
    const params = new URLSearchParams();
    if (currentPosition !== undefined) {
      params.set('currentPosition', String(currentPosition));
    }
    if (category) {
      params.set('category', category);
    }
    if (difficulty) {
      params.set('difficulty', difficulty);
    }
    const query = params.toString();
    return httpClient.get<VocabularyCardDTO | null>(
      `/vocabulary/conversations/${conversationPublicId}/next${query ? `?${query}` : ''}`
    );
  },

  getPrevCard: async (conversationPublicId: string, currentPosition?: number): Promise<VocabularyCardDTO | null> => {
    const url = currentPosition !== undefined
      ? `/vocabulary/conversations/${conversationPublicId}/prev?currentPosition=${currentPosition}`
      : `/vocabulary/conversations/${conversationPublicId}/prev`;
    return httpClient.get<VocabularyCardDTO | null>(url);
  },

  generateNextCard: async (
    conversationPublicId: string,
    category?: VocabularyCategory,
    difficulty?: VocabularyDifficulty
  ): Promise<VocabularyCardDTO> => {
    const batch = await httpClient.post<VocabularyBatchGenerationResult>(
      `/vocabulary/conversations/${conversationPublicId}/generate-batch`,
      { category, difficulty, batchSize: 10 }
    );

    const card = batch.revealedCards?.[0] ?? batch.hiddenCards?.[0];
    if (!card) {
      throw new Error('Batch vocabulary generation returned no cards');
    }

    await refreshCurrentUserBalance();
    return card;
  },



  updateUserMeaning: async (cardId: number, userMeaning: string): Promise<VocabularyCardDTO> => {
    const card = await httpClient.put<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/meaning`,
      { userMeaning }
    );
    await refreshCurrentUserBalance();
    return card;
  },

  updateUserEnglishSentence: async (cardId: number, userEnglishSentence: string): Promise<VocabularyCardDTO> => {
    return httpClient.put<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/english-sentence`,
      { userEnglishSentence }
    );
  },

  analyzeUserSentence: async (cardId: number): Promise<VocabularyCardDTO> => {
    const card = await httpClient.post<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/analyze-sentence`,
      {}
    );
    await refreshCurrentUserBalance();
    return card;
  },

  updateAIFeedback: async (cardId: number, aiFeedback: string): Promise<VocabularyCardDTO> => {
    return httpClient.put<VocabularyCardDTO>(
      `/vocabulary/cards/${cardId}/ai-feedback`,
      { aiFeedback }
    );
  },

  getSentenceAnalysisStatus: async (cardId: number): Promise<SentenceAnalysisStatus> => {
    const status = await httpClient.get<SentenceAnalysisStatus>(`/vocabulary/cards/${cardId}/sentence-analysis`);
    if (status && Object.keys(status).length > 0) {
      return status;
    }

    const card = await vocabularyService.getCardById(cardId);
    return {
      cardId: card.id,
      word: card.word,
      chineseSentenceForTranslation: card.chineseSentenceForTranslation || card.exampleTranslation || '',
      userEnglishSentence: card.userEnglishSentence || '',
      sentenceAnalysisCompleted: Boolean(card.sentenceAnalysisCompleted),
      sentenceHasNewWord: card.sentenceHasNewWord,
      sentenceMeaningMatches: card.sentenceMeaningMatches,
      sentenceAnalysis: card.sentenceAnalysis || '',
    };
  },

  markAsCompleted: async (cardId: number): Promise<VocabularyCardDTO> => {
    return httpClient.put<VocabularyCardDTO>(`/vocabulary/cards/${cardId}/complete`);
  },

  deleteAllCards: async (conversationPublicId: string): Promise<void> => {
    return httpClient.delete<void>(`/vocabulary/conversations/${conversationPublicId}/cards`);
  },

  getCardCount: async (conversationPublicId: string): Promise<number> => {
    return httpClient.get<number>(`/vocabulary/conversations/${conversationPublicId}/count`);
  },

  getMeaningCheckStatus: async (cardId: number): Promise<MeaningCheckStatus> => {
    const status = await httpClient.get<MeaningCheckStatus>(`/vocabulary/cards/${cardId}/meaning-check`);
    if (status && Object.keys(status).length > 0) {
      return status;
    }

    const card = await vocabularyService.getCardById(cardId);
    return {
      cardId: card.id,
      word: card.word,
      userMeaningGuess: card.userMeaningGuess || '',
      meaningCheckCompleted: Boolean(card.meaningCheckCompleted),
      meaningIsCorrect: card.meaningIsCorrect,
      meaningCheckResult: card.meaningCheckResult || '',
      chineseSentenceForTranslation: card.chineseSentenceForTranslation || card.exampleTranslation,
    };
  },

  getVocabularyStats: async (): Promise<VocabularyStatsDTO> => {
    return httpClient.get<VocabularyStatsDTO>('/user-vocabulary/stats');
  },

  getUserVocabularies: async (params: {
    status?: VocabularyStatus;
    filterType?: string;
    sortBy?: VocabularySortBy;
    search?: string;
    page?: number;
    size?: number;
  }): Promise<PageResponseDTO<UserVocabularyDTO>> => {
    const queryParams = new URLSearchParams();
    if (params.status) queryParams.set('status', params.status);
    if (params.filterType) queryParams.set('filterType', params.filterType);
    if (params.sortBy) queryParams.set('sortBy', params.sortBy);
    if (params.search) queryParams.set('search', params.search);
    if (params.page !== undefined) queryParams.set('page', String(params.page));
    if (params.size !== undefined) queryParams.set('size', String(params.size));
    
    const query = queryParams.toString();
    return httpClient.get<PageResponseDTO<UserVocabularyDTO>>(
      `/user-vocabulary/list${query ? `?${query}` : ''}`
    );
  },

  updateUserVocabulary: async (
    id: number,
    request: Partial<Pick<UserVocabularyDTO, 'word' | 'phonetic' | 'partOfSpeech' | 'meaning' | 'example' | 'exampleTranslation' | 'synonyms' | 'category' | 'difficulty'>>
  ): Promise<UserVocabularyDTO> => {
    return httpClient.put<UserVocabularyDTO>(`/user-vocabulary/${id}`, request);
  },

  aiModifyVocabulary: async (request: AIModifyVocabularyRequest): Promise<UserVocabularyDTO> => {
    const result = await httpClient.post<UserVocabularyDTO>('/user-vocabulary/ai-modify', request);
    await refreshCurrentUserBalance();
    return result;
  },

  updateLearningState: async (id: number, request: UpdateLearningStateRequest): Promise<UserVocabularyDTO> => {
    return httpClient.put<UserVocabularyDTO>(`/user-vocabulary/${id}/learning-state`, request);
  },

  deleteUserVocabulary: async (id: number): Promise<void> => {
    return httpClient.delete<void>(`/user-vocabulary/${id}`);
  },
};
