import { httpClient, handleResponse, API_BASE, getAuthHeaders } from './httpClient';
import { 
  MessageDTO, 
  ChatRequest, 
  EditMessageRequest, 
  RetryMessageRequest,
  StreamEvent 
} from '../types';

export const chatService = {
  sendMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    console.log('📤 发送消息请求:', request);
    const response = await httpClient.postRaw('/chat', request);
    const result = await response.json();
    console.log('📥 收到消息响应:', result);
    return result.data;
  },

  sendOnetimeMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    console.log('📤 发送一次性消息请求:', request);
    const response = await httpClient.postRaw('/chat/onetime', request);
    const result = await response.json();
    console.log('📥 收到一次性消息响应:', result);
    return result.data;
  },

  sendVocabularyMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    console.log('📤 发送词汇消息请求:', request);
    const response = await httpClient.postRaw('/chat/vocabulary', request);
    const result = await response.json();
    console.log('📥 收到词汇消息响应:', result);
    return result.data;
  },

  sendVocabularySentenceMessage: async (request: ChatRequest): Promise<MessageDTO> => {
    console.log('📤 发送词汇造句消息请求:', request);
    const response = await httpClient.postRaw('/chat/vocabulary', request);
    const result = await response.json();
    console.log('📥 收到词汇造句消息响应:', result);
    return result.data;
  },

  getMessages: async (conversationPublicId: string): Promise<MessageDTO[]> => {
    return httpClient.get<MessageDTO[]>(`/chat/conversations/${conversationPublicId}/messages`);
  },

  retryMessage: async (conversationPublicId: string, assistantMessageId: number): Promise<MessageDTO> => {
    return httpClient.post<MessageDTO>(`/chat/retry/${conversationPublicId}/${assistantMessageId}`);
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
    const response = await fetch(`${API_BASE}/chat/retry/stream`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
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
      
      if (done) {
        break;
      }

      const decoded = decoder.decode(value, { stream: true });
      buffer += decoded;
      
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.slice('data:'.length).trim();
          
          if (jsonStr) {
            try {
              const event: StreamEvent = JSON.parse(jsonStr);
              
              if (event.type === 'content') {
                onChunk(event.content);
              } else if (event.type === 'done') {
                if (event.message) {
                  console.log('AI 助手响应:', event.message.content);
                  onDone(event.message);
                }
                return;
              } else if (event.type === 'error') {
                console.error('收到错误事件:', event.content);
                onError(event.content);
                return;
              } else if (event.type === 'thinking') {
                console.log('🤔 思考中:', event.content);
                onThinking?.(event.content);
              } else if (event.type === 'tool_call') {
                console.log('🔧 调用工具:', event.toolName, 'id:', event.toolId);
                onToolCall?.(event.toolName || '', event.toolId || '');
              } else if (event.type === 'tool_result') {
                console.log('📋 工具结果:', event.toolName, 'success:', event.toolSuccess);
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
    }
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

    console.log('📤 [API] 发送消息请求:', request);

    const response = await fetch(`${API_BASE}/chat/stream`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
    });

    console.log('📥 [API] 响应状态:', response.status);

    try {
      await handleResponse(response);
    } catch (error) {
      console.error('❌ [API] 请求失败:', error);
      onError(error instanceof Error ? error.message : '请求失败');
      return;
    }

    if (!response.body) {
      console.error('❌ [API] 响应体为空');
      onError('响应体为空');
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      
      if (done) {
        break;
      }

      const decoded = decoder.decode(value, { stream: true });
      buffer += decoded;
      
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.slice('data:'.length).trim();
          
          if (jsonStr) {
            try {
              const event: StreamEvent = JSON.parse(jsonStr);
              
              if (event.type === 'content') {
                onChunk(event.content);
              } else if (event.type === 'done') {
                if (event.message) {
                  console.log('✅ [API] AI 响应完成:', event.message);
                  onDone(event.message);
                }
                return;
              } else if (event.type === 'error') {
                console.error('❌ [API] 错误:', event.content);
                onError(event.content);
                return;
              } else if (event.type === 'thinking') {
                console.log('🤔 思考中:', event.content);
                onThinking?.(event.content);
              } else if (event.type === 'tool_call') {
                console.log('🔧 调用工具:', event.toolName, 'id:', event.toolId);
                onToolCall?.(event.toolName || '', event.toolId || '');
              } else if (event.type === 'tool_result') {
                console.log('📋 工具结果:', event.toolName, 'success:', event.toolSuccess);
                onToolResult?.(
                  event.toolName || '', 
                  event.toolId || '', 
                  event.toolSuccess || false, 
                  event.content, 
                  event.toolError
                );
              }
            } catch (e) {
              console.error('❌ [API] 解析事件失败:', e, '原始数据:', jsonStr);
            }
          }
        }
      }
    }
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

    console.log('📤 [API] 发送一次性流式消息请求:', request);

    const response = await fetch(`${API_BASE}/chat/onetime/stream`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
    });

    console.log('📥 [API] 一次性流式响应状态:', response.status);

    try {
      await handleResponse(response);
    } catch (error) {
      console.error('❌ [API] 一次性流式请求失败:', error);
      onError(error instanceof Error ? error.message : '请求失败');
      return;
    }

    if (!response.body) {
      console.error('❌ [API] 一次性流式响应体为空');
      onError('响应体为空');
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      
      if (done) {
        break;
      }

      const decoded = decoder.decode(value, { stream: true });
      buffer += decoded;
      
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.slice('data:'.length).trim();
          
          if (jsonStr) {
            try {
              const event: StreamEvent = JSON.parse(jsonStr);
              
              if (event.type === 'content') {
                onChunk(event.content);
              } else if (event.type === 'done') {
                if (event.message) {
                  console.log('✅ [API] 一次性流式 AI 响应完成:', event.message);
                  onDone(event.message);
                }
                return;
              } else if (event.type === 'error') {
                console.error('❌ [API] 一次性流式错误:', event.content);
                onError(event.content);
                return;
              } else if (event.type === 'thinking') {
                console.log('🤔 思考中:', event.content);
                onThinking?.(event.content);
              } else if (event.type === 'tool_call') {
                console.log('🔧 调用工具:', event.toolName, 'id:', event.toolId);
                onToolCall?.(event.toolName || '', event.toolId || '');
              } else if (event.type === 'tool_result') {
                console.log('📋 工具结果:', event.toolName, 'success:', event.toolSuccess);
                onToolResult?.(
                  event.toolName || '', 
                  event.toolId || '', 
                  event.toolSuccess || false, 
                  event.content, 
                  event.toolError
                );
              }
            } catch (e) {
              console.error('❌ [API] 一次性流式解析事件失败:', e);
            }
          }
        }
      }
    }
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

    console.log('📤 [API] 发送词汇流式消息请求:', request);

    const response = await fetch(`${API_BASE}/chat/vocabulary/stream`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
    });

    console.log('📥 [API] 词汇流式响应状态:', response.status);

    try {
      await handleResponse(response);
    } catch (error) {
      console.error('❌ [API] 词汇流式请求失败:', error);
      onError(error instanceof Error ? error.message : '请求失败');
      return;
    }

    if (!response.body) {
      console.error('❌ [API] 词汇流式响应体为空');
      onError('响应体为空');
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      
      if (done) {
        break;
      }

      const decoded = decoder.decode(value, { stream: true });
      buffer += decoded;
      
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.slice('data:'.length).trim();
          
          if (jsonStr) {
            try {
              const event: StreamEvent = JSON.parse(jsonStr);
              
              if (event.type === 'content') {
                onChunk(event.content);
              } else if (event.type === 'done') {
                if (event.message) {
                  console.log('✅ [API] 词汇流式 AI 响应完成:', event.message);
                  onDone(event.message);
                }
                return;
              } else if (event.type === 'error') {
                console.error('❌ [API] 词汇流式错误:', event.content);
                onError(event.content);
                return;
              } else if (event.type === 'thinking') {
                console.log('🤔 思考中:', event.content);
                onThinking?.(event.content);
              } else if (event.type === 'tool_call') {
                console.log('🔧 调用工具:', event.toolName, 'id:', event.toolId);
                onToolCall?.(event.toolName || '', event.toolId || '');
              } else if (event.type === 'tool_result') {
                console.log('📋 工具结果:', event.toolName, 'success:', event.toolSuccess);
                onToolResult?.(
                  event.toolName || '', 
                  event.toolId || '', 
                  event.toolSuccess || false, 
                  event.content, 
                  event.toolError
                );
              }
            } catch (e) {
              console.error('❌ [API] 词汇流式解析事件失败:', e);
            }
          }
        }
      }
    }
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

    const response = await fetch(`${API_BASE}/chat/edit/stream`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
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
      
      if (done) {
        break;
      }

      const decoded = decoder.decode(value, { stream: true });
      buffer += decoded;
      
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.slice('data:'.length).trim();
          
          if (jsonStr) {
            try {
              const event: StreamEvent = JSON.parse(jsonStr);
              
              if (event.type === 'content') {
                onChunk(event.content);
              } else if (event.type === 'done') {
                if (event.message) {
                  console.log('AI 助手响应:', event.message.content);
                  onDone(event.message);
                }
                return;
              } else if (event.type === 'error') {
                console.error('收到错误事件:', event.content);
                onError(event.content);
                return;
              } else if (event.type === 'thinking') {
                console.log('🤔 思考中:', event.content);
                onThinking?.(event.content);
              } else if (event.type === 'tool_call') {
                console.log('🔧 调用工具:', event.toolName, 'id:', event.toolId);
                onToolCall?.(event.toolName || '', event.toolId || '');
              } else if (event.type === 'tool_result') {
                console.log('📋 工具结果:', event.toolName, 'success:', event.toolSuccess);
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
    }
  },
};
