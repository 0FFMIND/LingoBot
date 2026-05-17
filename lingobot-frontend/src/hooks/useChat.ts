import { useState, useCallback } from 'react';
import { chatService } from '../services';
import { useTokenUsageStore } from '../stores';
import { 
  MessageDTO, 
  LearningMode,
  VocabularyCategory,
  VocabularyDifficulty,
  ChatRequest,
  RetryMessageRequest,
  EditMessageRequest
} from '../types';

export interface AgentStatus {
  thinking: string;
  toolCalls: Array<{
    toolName: string;
    toolId: string;
    status: 'calling' | 'success' | 'error';
    result?: string;
    error?: string;
  }>;
}

export interface UseChatResult {
  messages: MessageDTO[];
  loading: boolean;
  streamingContent: string;
  agentStatus: AgentStatus;
  sendMessage: (content: string, options?: SendMessageOptions) => Promise<void>;
  sendAudioMessage: (audioData: string, audioFormat: string, duration: number) => Promise<void>;
  sendImageMessage: (content: string, imageData: string, imageFormat: string) => Promise<void>;
  sendMessageWithIntent: (content: string, intent: string, currentWord: string) => Promise<void>;
  retryMessage: (assistantMessageId: number) => Promise<void>;
  retryMessageWithModel: (assistantMessageId: number, targetModel: string) => Promise<void>;
  editMessage: (userMessageId: number, newContent: string) => Promise<void>;
  editAudioMessage: (
    userMessageId: number, 
    newContent: string, 
    audioData?: string, 
    audioFormat?: string, 
    audioDuration?: number
  ) => Promise<void>;
  loadMessages: () => Promise<void>;
}

export interface SendMessageOptions {
  mode?: 'chat' | 'agent';
  model?: string;
  learningMode?: LearningMode;
  vocabularyCategory?: VocabularyCategory;
  vocabularyDifficulty?: VocabularyDifficulty;
}

export function useChat(
  conversationPublicId: string | null,
  isAuthenticated: boolean,
  options: {
    mode: 'chat' | 'agent';
    model: string;
    learningMode: LearningMode;
    vocabularyCategory: VocabularyCategory;
    vocabularyDifficulty: VocabularyDifficulty;
  }
): UseChatResult {
  const [messages, setMessages] = useState<MessageDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [streamingContent, setStreamingContent] = useState('');
  const [agentStatus, setAgentStatus] = useState<AgentStatus>({
    thinking: '',
    toolCalls: [],
  });

  const { mode, model, learningMode, vocabularyCategory, vocabularyDifficulty } = options;

  const recordTokensFromMessage = useCallback((message: MessageDTO | undefined, convPublicId: string | null) => {
    if (!message || !convPublicId) return;

    const { promptTokens, completionTokens, totalTokens } = message;
    if (promptTokens !== undefined || completionTokens !== undefined || totalTokens !== undefined) {
      console.log('🔢 记录 Token 用量:', { convPublicId, promptTokens, completionTokens, totalTokens });
      useTokenUsageStore.getState().recordTokenUsage(
        convPublicId,
        promptTokens || 0,
        completionTokens || 0,
        totalTokens || 0
      );
    }
  }, []);

  const loadMessages = useCallback(async () => {
    if (!isAuthenticated || !conversationPublicId) return;
    
    try {
      const data = await chatService.getMessages(conversationPublicId);
      setMessages(data);
    } catch (error) {
      console.error('加载消息失败:', error);
    }
  }, [isAuthenticated, conversationPublicId]);

  const createBaseRequest = useCallback((): Partial<ChatRequest> => ({
    mode,
    model,
    learningMode,
    vocabularyCategory,
    vocabularyDifficulty,
  }), [mode, model, learningMode, vocabularyCategory, vocabularyDifficulty]);

  const sendMessageNonStream = useCallback(async (request: ChatRequest) => {
    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: 0,
      content: request.content,
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: request.messageType,
      audioData: request.audioData,
      audioFormat: request.audioFormat,
      audioDuration: request.audioDuration,
      imageData: request.imageData,
      imageFormat: request.imageFormat,
    };

    setMessages([...messages, tempUserMessage]);

    try {
      const response = await chatService.sendMessage(request);
      console.log('✅ 非流式消息响应:', response);
      recordTokensFromMessage(response, request.conversationPublicId || null);
      loadMessages();
      setLoading(false);
      setAgentStatus({ thinking: '', toolCalls: [] });
    } catch (error) {
      console.error('非流式消息错误:', error);
      setMessages((prev) => prev.slice(0, -1));
      setLoading(false);
      setAgentStatus({ thinking: '', toolCalls: [] });
      alert('发送消息失败: ' + (error instanceof Error ? error.message : '未知错误'));
    }
  }, [messages, loadMessages, recordTokensFromMessage]);

  const sendMessageStream = useCallback(async (request: ChatRequest) => {
    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: 0,
      content: request.content,
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: request.messageType,
      audioData: request.audioData,
      audioFormat: request.audioFormat,
      audioDuration: request.audioDuration,
      imageData: request.imageData,
      imageFormat: request.imageFormat,
    };

    setMessages([...messages, tempUserMessage]);

    try {
      await chatService.sendMessageStream(
        request,
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          recordTokensFromMessage(finalMessage, request.conversationPublicId || null);
          loadMessages();
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
        },
        (error) => {
          console.error('流式消息错误:', error);
          setMessages((prev) => prev.slice(0, -1));
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('发送消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('发送消息失败:', error);
      setMessages((prev) => prev.slice(0, -1));
      setStreamingContent('');
      setLoading(false);
      alert('发送消息失败');
    }
  }, [messages, loadMessages, recordTokensFromMessage]);

  const sendMessage = useCallback(async (content: string, options?: SendMessageOptions) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const request: ChatRequest = {
      conversationPublicId,
      content,
      ...createBaseRequest(),
      ...options,
    };

    const shouldUseNonStream = learningMode === 'vocabulary';
    if (shouldUseNonStream) {
      console.log('📚 词汇学习模式：使用非流式接口');
      await sendMessageNonStream(request);
    } else {
      await sendMessageStream(request);
    }
  }, [isAuthenticated, conversationPublicId, loading, learningMode, createBaseRequest, sendMessageNonStream, sendMessageStream]);

  const sendAudioMessage = useCallback(async (audioData: string, audioFormat: string, duration: number) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const request: ChatRequest = {
      conversationPublicId,
      content: '',
      ...createBaseRequest(),
      messageType: 'audio',
      audioData,
      audioFormat,
      audioDuration: duration,
    };

    await sendMessageStream(request);
  }, [isAuthenticated, conversationPublicId, loading, createBaseRequest, sendMessageStream]);

  const sendImageMessage = useCallback(async (content: string, imageData: string, imageFormat: string) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const request: ChatRequest = {
      conversationPublicId,
      content: content || '',
      ...createBaseRequest(),
      messageType: 'image',
      imageData,
      imageFormat,
    };

    await sendMessageStream(request);
  }, [isAuthenticated, conversationPublicId, loading, createBaseRequest, sendMessageStream]);

  const sendMessageWithIntent = useCallback(async (content: string, intent: string, currentWord: string) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const messageContent = content.trim()
      ? `[intent:${intent}][current_word:${currentWord}][user_input:${content.trim()}]`
      : `[intent:${intent}][current_word:${currentWord}]`;

    const request: ChatRequest = {
      conversationPublicId,
      content: messageContent,
      ...createBaseRequest(),
      intent: intent as any,
      currentWord,
    };

    console.log('📚 词汇学习意图消息：使用非流式接口，intent:', intent);
    await sendMessageNonStream(request);
  }, [isAuthenticated, conversationPublicId, loading, createBaseRequest, sendMessageNonStream]);

  const retryMessage = useCallback(async (assistantMessageId: number) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const assistantMessageIndex = messages.findIndex((m) => m.id === assistantMessageId);
    
    if (assistantMessageIndex === -1) {
      alert('找不到要重试的消息');
      return;
    }

    const assistantMessage = messages[assistantMessageIndex];
    
    if (assistantMessage.role !== 'assistant') {
      alert('只能重试AI助手的消息');
      return;
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, assistantMessageIndex);
    setMessages(updatedMessages);

    const request: RetryMessageRequest = {
      conversationPublicId,
      assistantMessageId,
      model,
      mode,
      learningMode,
      vocabularyCategory,
      vocabularyDifficulty,
    };

    try {
      await chatService.retryMessageStream(
        request,
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          recordTokensFromMessage(finalMessage, conversationPublicId);
          loadMessages();
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
        },
        (error) => {
          console.error('重试消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('重试消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('重试消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('重试消息失败');
    }
  }, [isAuthenticated, conversationPublicId, loading, messages, model, mode, learningMode, vocabularyCategory, vocabularyDifficulty, loadMessages, recordTokensFromMessage]);

  const retryMessageWithModel = useCallback(async (assistantMessageId: number, targetModel: string) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const assistantMessageIndex = messages.findIndex((m) => m.id === assistantMessageId);
    
    if (assistantMessageIndex === -1) {
      alert('找不到要重试的消息');
      return;
    }

    const assistantMessage = messages[assistantMessageIndex];
    
    if (assistantMessage.role !== 'assistant') {
      alert('只能重试AI助手的消息');
      return;
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, assistantMessageIndex);
    setMessages(updatedMessages);

    const request: RetryMessageRequest = {
      conversationPublicId,
      assistantMessageId,
      model: targetModel,
      mode,
      learningMode,
      vocabularyCategory,
      vocabularyDifficulty,
    };

    try {
      await chatService.retryMessageStream(
        request,
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          recordTokensFromMessage(finalMessage, conversationPublicId);
          loadMessages();
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
        },
        (error) => {
          console.error('重试消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('重试消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('重试消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('重试消息失败');
    }
  }, [isAuthenticated, conversationPublicId, loading, messages, mode, learningMode, vocabularyCategory, vocabularyDifficulty, loadMessages, recordTokensFromMessage]);

  const editMessage = useCallback(async (userMessageId: number, newContent: string) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const userMessageIndex = messages.findIndex((m) => m.id === userMessageId);
    
    if (userMessageIndex === -1) {
      alert('找不到要编辑的消息');
      return;
    }

    const userMessage = messages[userMessageIndex];
    
    if (userMessage.role !== 'user') {
      alert('只能编辑用户消息');
      return;
    }

    if (newContent.trim() === userMessage.content.trim()) {
      return;
    }

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, userMessageIndex + 1).map(m => 
      m.id === userMessageId ? { ...m, content: newContent } : m
    );
    setMessages(updatedMessages);

    const request: EditMessageRequest = {
      conversationPublicId,
      userMessageId,
      newContent,
    };

    try {
      await chatService.editMessageStream(
        request,
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          recordTokensFromMessage(finalMessage, conversationPublicId);
          loadMessages();
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
        },
        (error) => {
          console.error('编辑消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('编辑消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('编辑消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('编辑消息失败');
    }
  }, [isAuthenticated, conversationPublicId, loading, messages, loadMessages, recordTokensFromMessage]);

  const editAudioMessage = useCallback(async (
    userMessageId: number, 
    newContent: string, 
    audioData?: string, 
    audioFormat?: string, 
    audioDuration?: number
  ) => {
    if (!isAuthenticated || !conversationPublicId || loading) return;

    const userMessageIndex = messages.findIndex((m) => m.id === userMessageId);
    
    if (userMessageIndex === -1) {
      alert('找不到要编辑的消息');
      return;
    }

    const userMessage = messages[userMessageIndex];
    
    if (userMessage.role !== 'user') {
      alert('只能编辑用户消息');
      return;
    }

    const finalAudioData = audioData || userMessage.audioData;
    const finalAudioFormat = audioFormat || userMessage.audioFormat;
    const finalAudioDuration = audioDuration || userMessage.audioDuration;

    setLoading(true);
    setStreamingContent('');
    setAgentStatus({ thinking: '', toolCalls: [] });

    const updatedMessages = messages.slice(0, userMessageIndex + 1).map(m => 
      m.id === userMessageId 
        ? { 
            ...m, 
            content: newContent,
            audioData: finalAudioData,
            audioFormat: finalAudioFormat,
            audioDuration: finalAudioDuration,
          } 
        : m
    );
    setMessages(updatedMessages);

    const request: ChatRequest = {
      conversationPublicId,
      content: newContent,
      ...createBaseRequest(),
      messageType: finalAudioData ? 'audio' : 'text',
      audioData: finalAudioData,
      audioFormat: finalAudioFormat,
      audioDuration: finalAudioDuration,
    };

    try {
      await chatService.sendMessageStream(
        request,
        (chunk) => {
          setStreamingContent((prev) => prev + chunk);
        },
        (finalMessage) => {
          recordTokensFromMessage(finalMessage, request.conversationPublicId || null);
          loadMessages();
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
        },
        (error) => {
          console.error('编辑消息错误:', error);
          setMessages(messages);
          setStreamingContent('');
          setLoading(false);
          setAgentStatus({ thinking: '', toolCalls: [] });
          alert('编辑消息失败: ' + error);
        },
        (thinking) => {
          setAgentStatus((prev) => ({ ...prev, thinking }));
        },
        (toolName, toolId) => {
          setAgentStatus((prev) => ({
            ...prev,
            thinking: '',
            toolCalls: [
              ...prev.toolCalls,
              {
                toolName,
                toolId,
                status: 'calling',
              },
            ],
          }));
        },
        (_toolName, toolId, success, result, error) => {
          setAgentStatus((prev) => ({
            ...prev,
            toolCalls: prev.toolCalls.map((tc) =>
              tc.toolId === toolId
                ? {
                    ...tc,
                    status: success ? 'success' : 'error',
                    result,
                    error,
                  }
                : tc
            ),
          }));
        }
      );
    } catch (error) {
      console.error('编辑消息失败:', error);
      setMessages(messages);
      setStreamingContent('');
      setLoading(false);
      alert('编辑消息失败');
    }
  }, [isAuthenticated, conversationPublicId, loading, messages, createBaseRequest, loadMessages, recordTokensFromMessage]);

  return {
    messages,
    loading,
    streamingContent,
    agentStatus,
    sendMessage,
    sendAudioMessage,
    sendImageMessage,
    sendMessageWithIntent,
    retryMessage,
    retryMessageWithModel,
    editMessage,
    editAudioMessage,
    loadMessages,
  };
}
