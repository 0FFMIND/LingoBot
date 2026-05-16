export interface ContextStatusDTO {
  currentTokens: number;
  maxTokens: number;
  tokenRatio: number;
  wordCardsTotal: number;
  wordCardsSinceCompact: number;
  wordCardThreshold: number;
  shouldCompact: boolean;
  compactReason?: string;
  compactedCount: number;
}

export interface ConversationDTO {
  publicId: string;
  title: string;
  learningMode?: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
  contextStatus?: ContextStatusDTO;
}

export interface CreateConversationRequest {
  title: string;
}

export interface PageResponseDTO<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ConversationPageResponse extends PageResponseDTO<ConversationDTO> {}
