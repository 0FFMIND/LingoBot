import { create } from 'zustand'
import { MessageDTO, ChatRequest, RetryMessageRequest, EditMessageRequest } from '../types'
import { chatApi, conversationApi } from '../api'

const COMPACT_COOLDOWN_MS = 10000

interface AgentStatus {
  thinking: string
  toolCalls: Array<{
    toolName: string
    toolId: string
    status: 'calling' | 'success' | 'error'
    result?: string
    error?: string
  }>
}

interface ChatStore {
  messages: MessageDTO[]
  loading: boolean
  streamingContent: string
  agentStatus: AgentStatus
  mode: 'chat' | 'agent'
  isCompacting: boolean
  compactingConversationPublicId: string | null
  lastCompactTime: Record<string, number>

  loadMessages: (conversationPublicId: string) => Promise<void>
  sendMessage: (request: ChatRequest) => Promise<void>
  sendAudioMessage: (request: ChatRequest) => Promise<void>
  sendImageMessage: (request: ChatRequest) => Promise<void>
  sendVocabularyMessage: (request: ChatRequest) => Promise<any>
  retryMessage: (request: RetryMessageRequest) => Promise<void>
  retryMessageWithModel: (request: RetryMessageRequest) => Promise<void>
  editMessage: (request: EditMessageRequest) => Promise<void>
  editAudioMessage: (request: ChatRequest, userMessageId: number) => Promise<void>
  setMode: (mode: 'chat' | 'agent') => void
  manualCompact: (conversationPublicId: string) => Promise<void>
  reset: () => void
}

const makeStreamCallbacks = (get: () => ChatStore, set: (partial: Partial<ChatStore> | ((s: ChatStore) => Partial<ChatStore>)) => void, conversationPublicId: string, originalMessages?: MessageDTO[]) => ({
  onChunk: (chunk: string) => set(s => ({ streamingContent: s.streamingContent + chunk })),
  onFinal: (_final: any) => {
    get().loadMessages(conversationPublicId)
    set({ streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
  },
  onError: (error: string) => {
    console.error('流式消息错误:', error)
    if (originalMessages !== undefined) {
      set({ messages: originalMessages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
    } else {
      set(s => ({ messages: s.messages.slice(0, -1), streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } }))
    }
    alert('发送消息失败: ' + error)
  },
  onThinking: (thinking: string) => set(s => ({ agentStatus: { ...s.agentStatus, thinking } })),
  onToolCall: (toolName: string, toolId: string) => set(s => ({
    agentStatus: {
      ...s.agentStatus,
      thinking: '',
      toolCalls: [...s.agentStatus.toolCalls, { toolName, toolId, status: 'calling' as const }],
    },
  })),
  onToolResult: (_toolName: string, toolId: string, success: boolean, result?: string, error?: string) => set(s => ({
    agentStatus: {
      ...s.agentStatus,
      toolCalls: s.agentStatus.toolCalls.map(tc =>
        tc.toolId === toolId ? { ...tc, status: (success ? 'success' : 'error') as 'success' | 'error', result, error } : tc
      ),
    },
  })),
})

export const useChatStore = create<ChatStore>((set, get) => ({
  messages: [],
  loading: false,
  streamingContent: '',
  agentStatus: { thinking: '', toolCalls: [] },
  mode: 'chat',
  isCompacting: false,
  compactingConversationPublicId: null,
  lastCompactTime: {},

  loadMessages: async (conversationPublicId: string) => {
    try {
      const data = await chatApi.getMessages(conversationPublicId)
      set({ messages: data })
    } catch (error) {
      console.error('加载消息失败:', error)
    }
  },

  sendMessage: async (request: ChatRequest) => {
    const { messages } = get()
    set({ loading: true, streamingContent: '', agentStatus: { thinking: '', toolCalls: [] } })

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: 0,
      content: request.content,
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: request.messageType,
    }
    set({ messages: [...messages, tempUserMessage] })

    const { onChunk, onFinal, onError, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    try {
      await chatApi.sendMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('发送消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('发送消息失败')
    }
  },

  sendAudioMessage: async (request: ChatRequest) => {
    const { messages } = get()
    set({ loading: true, streamingContent: '', agentStatus: { thinking: '', toolCalls: [] } })

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: 0,
      content: request.content || '',
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: 'audio',
      audioData: request.audioData,
      audioFormat: request.audioFormat,
      audioDuration: request.audioDuration,
    }
    set({ messages: [...messages, tempUserMessage] })

    const { onChunk, onFinal, onError, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    try {
      await chatApi.sendMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('发送语音消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('发送消息失败')
    }
  },

  sendImageMessage: async (request: ChatRequest) => {
    const { messages } = get()
    set({ loading: true, streamingContent: '', agentStatus: { thinking: '', toolCalls: [] } })

    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: 0,
      content: request.content || '',
      role: 'user',
      timestamp: new Date().toISOString(),
      messageType: 'image',
      imageData: request.imageData,
      imageFormat: request.imageFormat,
    }
    set({ messages: [...messages, tempUserMessage] })

    const { onChunk, onFinal, onError, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    try {
      await chatApi.sendMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('发送图片消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('发送消息失败')
    }
  },

  sendVocabularyMessage: async (request: ChatRequest) => {
    const { messages } = get()
    set({ loading: true })

    const messageContent = request.content
    const tempUserMessage: MessageDTO = {
      id: Date.now(),
      conversationId: 0,
      content: messageContent,
      role: 'user',
      timestamp: new Date().toISOString(),
    }
    set({ messages: [...messages, tempUserMessage] })

    try {
      const response = await chatApi.sendVocabularySentenceMessage(request)
      get().loadMessages(request.conversationPublicId!)
      set({ loading: false })
      return response
    } catch (error) {
      console.error('发送消息失败:', error)
      set({ messages, loading: false })
      alert('发送消息失败')
      return null
    }
  },

  retryMessage: async (request: RetryMessageRequest) => {
    const { messages } = get()
    const assistantMessageIndex = messages.findIndex(m => m.id === request.assistantMessageId)

    if (assistantMessageIndex === -1) {
      alert('找不到要重试的消息')
      return
    }

    if (messages[assistantMessageIndex].role !== 'assistant') {
      alert('只能重试AI助手的消息')
      return
    }

    set({
      loading: true,
      streamingContent: '',
      agentStatus: { thinking: '', toolCalls: [] },
      messages: messages.slice(0, assistantMessageIndex),
    })

    const { onChunk, onFinal, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    const onError = (error: string) => {
      console.error('重试消息错误:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('重试消息失败: ' + error)
    }

    try {
      await chatApi.retryMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('重试消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('重试消息失败')
    }
  },

  retryMessageWithModel: async (request: RetryMessageRequest) => {
    const { messages } = get()
    const assistantMessageIndex = messages.findIndex(m => m.id === request.assistantMessageId)

    if (assistantMessageIndex === -1) {
      alert('找不到要重试的消息')
      return
    }

    if (messages[assistantMessageIndex].role !== 'assistant') {
      alert('只能重试AI助手的消息')
      return
    }

    set({
      loading: true,
      streamingContent: '',
      agentStatus: { thinking: '', toolCalls: [] },
      messages: messages.slice(0, assistantMessageIndex),
    })

    const { onChunk, onFinal, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    const onError = (error: string) => {
      console.error('重试消息错误:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('重试消息失败: ' + error)
    }

    try {
      await chatApi.retryMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('重试消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('重试消息失败')
    }
  },

  editMessage: async (request: EditMessageRequest) => {
    const { messages } = get()
    const userMessageIndex = messages.findIndex(m => m.id === request.userMessageId)

    if (userMessageIndex === -1) {
      alert('找不到要编辑的消息')
      return
    }

    if (messages[userMessageIndex].role !== 'user') {
      alert('只能编辑用户消息')
      return
    }

    const updatedMessages = messages.slice(0, userMessageIndex + 1).map(m =>
      m.id === request.userMessageId ? { ...m, content: request.newContent } : m
    )
    set({ loading: true, streamingContent: '', agentStatus: { thinking: '', toolCalls: [] }, messages: updatedMessages })

    const { onChunk, onFinal, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    const onError = (error: string) => {
      console.error('编辑消息错误:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('编辑消息失败: ' + error)
    }

    try {
      await chatApi.editMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('编辑消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('编辑消息失败')
    }
  },

  editAudioMessage: async (request: ChatRequest, userMessageId: number) => {
    const { messages } = get()
    const userMessageIndex = messages.findIndex(m => m.id === userMessageId)

    if (userMessageIndex === -1) {
      alert('找不到要编辑的消息')
      return
    }

    if (messages[userMessageIndex].role !== 'user') {
      alert('只能编辑用户消息')
      return
    }

    const userMessage = messages[userMessageIndex]
    const updatedMessages = messages.slice(0, userMessageIndex + 1).map(m =>
      m.id === userMessageId
        ? {
            ...m,
            content: request.content,
            audioData: request.audioData || m.audioData,
            audioFormat: request.audioFormat || m.audioFormat,
            audioDuration: request.audioDuration || m.audioDuration,
          }
        : m
    )
    set({ loading: true, streamingContent: '', agentStatus: { thinking: '', toolCalls: [] }, messages: updatedMessages })

    const finalRequest: ChatRequest = {
      ...request,
      messageType: request.audioData ? 'audio' : (userMessage.audioData ? 'audio' : 'text'),
      audioData: request.audioData || userMessage.audioData,
      audioFormat: request.audioFormat || userMessage.audioFormat,
      audioDuration: request.audioDuration || userMessage.audioDuration,
    }

    const { onChunk, onFinal, onThinking, onToolCall, onToolResult } =
      makeStreamCallbacks(get, set, request.conversationPublicId!, messages)

    const onError = (error: string) => {
      console.error('编辑消息错误:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('编辑消息失败: ' + error)
    }

    try {
      await chatApi.sendMessageStream(finalRequest, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('编辑消息失败:', error)
      set({ messages, streamingContent: '', loading: false })
      alert('编辑消息失败')
    }
  },

  setMode: (mode) => set({ mode }),

  manualCompact: async (conversationPublicId: string) => {
    const { isCompacting, lastCompactTime } = get()

    if (isCompacting) {
      console.log('正在执行其他Compact操作，请稍候')
      return
    }

    const now = Date.now()
    const lastTime = lastCompactTime[conversationPublicId] || 0
    if (now - lastTime < COMPACT_COOLDOWN_MS) {
      const remainingSeconds = Math.ceil((COMPACT_COOLDOWN_MS - (now - lastTime)) / 1000)
      alert(`操作过于频繁，请 ${remainingSeconds} 秒后再试`)
      return
    }

    set({ isCompacting: true, compactingConversationPublicId: conversationPublicId })

    try {
      console.log('手动执行Compact，conversationPublicId:', conversationPublicId)
      const result = await conversationApi.executeCompact(conversationPublicId)

      set(s => ({ lastCompactTime: { ...s.lastCompactTime, [conversationPublicId]: Date.now() } }))

      if (result.executed) {
        console.log('Compact执行成功，节省了', result.savedTokens, 'tokens')
        alert(`压缩成功！节省了 ${result.savedTokens || 0} tokens`)
      } else {
        console.log('Compact未执行，原因:', result.reason)
        if (result.reason) {
          alert(`压缩未执行: ${result.reason}`)
        }
      }
    } catch (error) {
      console.error('手动Compact失败:', error)
      alert('压缩失败: ' + (error instanceof Error ? error.message : '未知错误'))
    } finally {
      set({ isCompacting: false, compactingConversationPublicId: null })
    }
  },

  reset: () => set({
    messages: [],
    loading: false,
    streamingContent: '',
    agentStatus: { thinking: '', toolCalls: [] },
    isCompacting: false,
    compactingConversationPublicId: null,
  }),
}))
