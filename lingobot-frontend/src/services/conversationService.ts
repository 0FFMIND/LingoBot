import { httpClient } from './httpClient';
import { ConversationDTO, CreateConversationRequest, ConversationPageResponse, ContextStatusDTO } from '../types';

export interface CompactResult {
  executed: boolean;
  reason?: string;
  beforeTokens?: number;
  afterTokens?: number;
  savedTokens?: number;
  compactedCardCount?: number;
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

  getById: async (id: number): Promise<ConversationDTO> => {
    return httpClient.get<ConversationDTO>(`/conversations/${id}`);
  },

  getCurrent: async (): Promise<ConversationDTO | null> => {
    return httpClient.get<ConversationDTO | null>('/conversations/current');
  },

  setCurrent: async (conversationId: number | null): Promise<ConversationDTO | null> => {
    return httpClient.put<ConversationDTO | null>('/conversations/current', { conversationId });
  },

  updateTitle: async (id: number, title: string): Promise<ConversationDTO> => {
    return httpClient.put<ConversationDTO>(`/conversations/${id}`, { title });
  },

  updateLearningMode: async (id: number, learningMode: string): Promise<ConversationDTO> => {
    return httpClient.put<ConversationDTO>(`/conversations/${id}/learning-mode`, { learningMode });
  },

  delete: async (id: number): Promise<void> => {
    return httpClient.delete<void>(`/conversations/${id}`);
  },

  getContextStatus: async (conversationId: number): Promise<ContextStatusDTO> => {
    return httpClient.get<ContextStatusDTO>(`/context/status/${conversationId}`);
  },

  executeCompact: async (conversationId: number): Promise<CompactResult> => {
    return httpClient.post<CompactResult>(`/context/compact/${conversationId}`);
  },
};
