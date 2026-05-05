export interface ConversationDTO {
  id: number;
  title: string;
  learningMode?: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
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
