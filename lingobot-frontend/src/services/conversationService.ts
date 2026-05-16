import { httpClient } from './httpClient';
import { ConversationDTO, CreateConversationRequest, ConversationPageResponse, ContextStatusDTO } from '../types';

export interface CompactResult {
  executed: boolean;
  reason?: string;
  beforeTokens?: number;
  afterTokens?: number;
  savedTokens?: number;
  compactedCardCount?: number;
  compactedCardsCount?: number;
  recentCardsCount?: number;
  totalCompactedCards?: number;
  compactBatch?: number;
}

export const conversationService = {
  getAll: async (): Promise<ConversationDTO[]> => {
    return httpClient.get<ConversationDTO[]>('/conversations');
  },

  getByPage: async (page: number = 0, size: number = 20): Promise<ConversationPageResponse> => {
    return httpClient.get<ConversationPageResponse>(`/conversations/page?page=${page}&size=${size}`);
  },

  create: async (request: CreateConversationRequest): Promise<ConversationDTO> => {
    return httpClient.post<ConversationDTO>('/conversations', request);
  },

  getByPublicId: async (publicId: string): Promise<ConversationDTO> => {
    return httpClient.get<ConversationDTO>(`/conversations/${publicId}`);
  },

  getCurrent: async (): Promise<ConversationDTO | null> => {
    return httpClient.get<ConversationDTO | null>('/conversations/current');
  },

  setCurrent: async (publicId: string | null): Promise<ConversationDTO | null> => {
    return httpClient.put<ConversationDTO | null>('/conversations/current', { publicId });
  },

  updateTitle: async (publicId: string, title: string): Promise<ConversationDTO> => {
    return httpClient.put<ConversationDTO>(`/conversations/${publicId}`, { title });
  },

  updateLearningMode: async (publicId: string, learningMode: string): Promise<ConversationDTO> => {
    return httpClient.put<ConversationDTO>(`/conversations/${publicId}/learning-mode`, { learningMode });
  },

  updateVocabularyIntent: async (publicId: string, vocabularyIntent: string): Promise<ConversationDTO> => {
    return httpClient.put<ConversationDTO>(`/conversations/${publicId}/vocabulary-intent`, { vocabularyIntent });
  },

  delete: async (publicId: string): Promise<void> => {
    return httpClient.delete<void>(`/conversations/${publicId}`);
  },

  getContextStatus: async (publicId: string): Promise<ContextStatusDTO> => {
    return httpClient.get<ContextStatusDTO>(`/context/status/${publicId}`);
  },

  executeCompact: async (publicId: string): Promise<CompactResult> => {
    return httpClient.post<CompactResult>(`/context/compact/${publicId}`);
  },
};
