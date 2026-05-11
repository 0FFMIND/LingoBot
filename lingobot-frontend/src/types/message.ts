import { ModelType } from './preferences';

export type MessageType = 'text' | 'audio' | 'image' | 'chat' | 'vocabulary';

export interface MessageDTO {
  id: number;
  conversationId: number;
  content: string;
  role: 'user' | 'assistant';
  timestamp: string;
  messageType?: MessageType;
  audioData?: string;
  audioFormat?: string;
  audioDuration?: number;
  imageData?: string;
  imageFormat?: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
}

export interface AudioAttachment {
  id: string;
  url: string;
  title: string;
  type: string;
  description?: string;
  durationSeconds?: number;
}

export interface ImageAttachment {
  id: string;
  url: string;
  data?: string;
  format?: string;
  alt?: string;
}

export type IntentType = 'answer_meaning' | 'make_sentence' | 'view_meaning' | 'view_example' | 'next_word';

export type ExecutionMode = 'loop' | 'onetime';

export interface ChatRequest {
  conversationId?: number;
  content: string;
  mode?: 'chat' | 'agent';
  model?: ModelType;
  messageType?: MessageType;
  executionMode?: ExecutionMode;
  audioData?: string;
  audioFormat?: string;
  audioDuration?: number;
  imageData?: string;
  imageFormat?: string;
  learningMode?: string;
  intent?: IntentType;
  currentWord?: string;
  vocabularyCategory?: string;
  vocabularyDifficulty?: string;
}

export interface AudioRecordingState {
  isRecording: boolean;
  duration: number;
  audioBlob: Blob | null;
  audioUrl: string | null;
}

export interface EditMessageRequest {
  conversationId: number;
  userMessageId: number;
  newContent: string;
}

export interface RetryMessageRequest {
  conversationId: number;
  assistantMessageId: number;
  model?: ModelType;
  mode?: 'chat' | 'agent';
  learningMode?: string;
  vocabularyCategory?: string;
  vocabularyDifficulty?: string;
}

export interface StreamEvent {
  type: 'content' | 'done' | 'error' | 'thinking' | 'tool_call' | 'tool_result';
  content: string;
  done: boolean;
  message?: MessageDTO;
  toolName?: string;
  toolId?: string;
  toolSuccess?: boolean;
  toolError?: string;
}

export interface StreamingMessage {
  id: number;
  conversationId: number;
  content: string;
  role: 'assistant';
  isStreaming: boolean;
  timestamp?: string;
}
