import { httpClient, handleResponse, API_BASE, getAuthHeaders } from './httpClient';
import { 
  MessageDTO, 
  ChatRequest, 
  EditMessageRequest, 
  RetryMessageRequest,
  StreamEvent 
} from '../types';

interface SseStreamCallbacks {
  onChunk: (chunk: string) => void;
  onDone: (message: MessageDTO) => void;
  onError: (error: string) => void;
  onThinking?: (content: string) => void;
  onToolCall?: (toolName: string, toolId: string) => void;
  onToolResult?: (toolName: string, toolId: string, success: boolean, result: string, error?: string) => void;
}

async function fetchSseStream(path: string, body: unknown, callbacks: SseStreamCallbacks): Promise<void> {
  const { onChunk, onDone, onError, onThinking, onToolCall, onToolResult } = callbacks;

  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(body),
  });

  try {
    await handleResponse(response);
  } catch (error) {
    onError(error instanceof Error ? error.message : '请求失败');
    return;
  }

  if (!response.body) {
    onError('响应体为空');
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const jsonStr = line.slice('data:'.length).trim();
      if (!jsonStr) continue;

      try {
        const event: StreamEvent = JSON.parse(jsonStr);

        if (event.type === 'content') {
          onChunk(event.content);
        } else if (event.type === 'done') {
          if (event.message) {
            onDone(event.message);
          }
          return;
        } else if (event.type === 'error') {
          onError(event.content);
          return;
        } else if (event.type === 'thinking') {
          onThinking?.(event.content);
        } else if (event.type === 'tool_call') {
          onToolCall?.(event.toolName || '', event.toolId || '');
        } else if (event.type === 'tool_result') {
          onToolResult?.(
            event.toolName || '',
            event.toolId || '',
            event.toolSuccess || false,
            event.content,
            event.toolError
          );
        }
      } catch (e) {
        console.error('解析 SSE 事件失败:', e);
      }
    }
  }
}

export const chatService = {
  sendMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    const response = await httpClient.postRaw('/chat', request);
    const result = await response.json();
    return result.data;
  },

  sendOnetimeMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    const response = await httpClient.postRaw('/chat/onetime', request);
    const result = await response.json();
    return result.data;
  },

  sendVocabularyMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    const response = await httpClient.postRaw('/chat/vocabulary', request);
    const result = await response.json();
    return result.data;
  },

  sendVocabularySentenceMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    const response = await httpClient.postRaw('/chat/vocabulary', request);
    const result = await response.json();
    return result.data;
  },

  getMessages: async (conversationPublicId: string): Promise<MessageDTO[]> => {
    return httpClient.get<MessageDTO[]>(`/chat/conversations/${conversationPublicId}/messages`);
  },

  retryMessage: async (conversationPublicId: string, assistantMessageId: number): Promise<MessageDTO> => {
    return httpClient.post<MessageDTO>(`/chat/retry/${conversationPublicId}/${assistantMessageId}`);
  },

  sendMessageStream: async (
    request: ChatRequest,
    onChunk: (chunk: string) => void,
    onDone: (message: MessageDTO) => void,
    onError: (error: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolName: string, toolId: string) => void,
    onToolResult?: (toolName: string, toolId: string, success: boolean, result: string, error?: string) => void
  ): Promise<void> => {
    return fetchSseStream('/chat/stream', request, { onChunk, onDone, onError, onThinking, onToolCall, onToolResult });
  },

  sendOnetimeMessageStream: async (
    request: ChatRequest,
    onChunk: (chunk: string) => void,
    onDone: (message: MessageDTO) => void,
    onError: (error: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolName: string, toolId: string) => void,
    onToolResult?: (toolName: string, toolId: string, success: boolean, result: string, error?: string) => void
  ): Promise<void> => {
    return fetchSseStream('/chat/onetime/stream', request, { onChunk, onDone, onError, onThinking, onToolCall, onToolResult });
  },

  sendVocabularyMessageStream: async (
    request: ChatRequest,
    onChunk: (chunk: string) => void,
    onDone: (message: MessageDTO) => void,
    onError: (error: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolName: string, toolId: string) => void,
    onToolResult?: (toolName: string, toolId: string, success: boolean, result: string, error?: string) => void
  ): Promise<void> => {
    return fetchSseStream('/chat/vocabulary/stream', request, { onChunk, onDone, onError, onThinking, onToolCall, onToolResult });
  },

  editMessageStream: async (
    request: EditMessageRequest,
    onChunk: (chunk: string) => void,
    onDone: (message: MessageDTO) => void,
    onError: (error: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolName: string, toolId: string) => void,
    onToolResult?: (toolName: string, toolId: string, success: boolean, result: string, error?: string) => void
  ): Promise<void> => {
    return fetchSseStream('/chat/edit/stream', request, { onChunk, onDone, onError, onThinking, onToolCall, onToolResult });
  },

  retryMessageStream: async (
    request: RetryMessageRequest,
    onChunk: (chunk: string) => void,
    onDone: (message: MessageDTO) => void,
    onError: (error: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolName: string, toolId: string) => void,
    onToolResult?: (toolName: string, toolId: string, success: boolean, result: string, error?: string) => void
  ): Promise<void> => {
    return fetchSseStream('/chat/retry/stream', request, { onChunk, onDone, onError, onThinking, onToolCall, onToolResult });
  },
};
