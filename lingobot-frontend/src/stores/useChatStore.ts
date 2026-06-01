import { create } from 'zustand'
import { MessageDTO, ChatRequest, RetryMessageRequest, EditMessageRequest, MessagePageResponse } from '../types'
import { chatApi, conversationApi } from '../api'

const COMPACT_COOLDOWN_MS = 5000
const MESSAGE_PAGE_SIZE = 20

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

interface CompactResultState {
  success: boolean
  message: string
  savedTokens?: number
  beforeTokens?: number
  afterTokens?: number
  compactedCardsCount?: number
  recentCardsCount?: number
  totalCompactedCards?: number
}

interface ChatStore {
  messages: MessageDTO[]
  loading: boolean
  loadingMore: boolean
  hasMoreMessages: boolean
  nextPageToLoad: number
  streamingContent: string
  agentStatus: AgentStatus
  mode: 'chat' | 'agent'
  isCompacting: boolean
  compactingConversationPublicId: string | null
  lastCompactTime: Record<string, number>
  compactResult: Record<string, CompactResultState | null>
  compactCooldownRemaining: Record<string, number>
  compactCooldownTimers: Record<string, number | null>

  loadMessages: (conversationPublicId: string) => Promise<void>
  loadMoreMessages: (conversationPublicId: string) => Promise<void>
  sendMessage: (request: ChatRequest) => void
  sendAudioMessage: (request: ChatRequest) => void
  sendImageMessage: (request: ChatRequest) => void
  sendVocabularyMessage: (request: ChatRequest) => Promise<any>
  retryMessage: (request: RetryMessageRequest) => void
  retryMessageWithModel: (request: RetryMessageRequest) => void
  editMessage: (request: EditMessageRequest) => void
  setMode: (mode: 'chat' | 'agent') => void
  manualCompact: (conversationPublicId: string) => Promise<void>
  clearCompactResult: (conversationPublicId: string) => void
  clearCompactCooldown: (conversationPublicId: string) => void
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
  loadingMore: false,
  hasMoreMessages: true,
  nextPageToLoad: 0,
  streamingContent: '',
  agentStatus: { thinking: '', toolCalls: [] },
  mode: 'chat',
  isCompacting: false,
  compactingConversationPublicId: null,
  lastCompactTime: {},
  compactResult: {},
  compactCooldownRemaining: {},
  compactCooldownTimers: {},

  loadMessages: async (conversationPublicId: string) => {
    try {
      const pageResult = await chatApi.getMessages(conversationPublicId, 0, MESSAGE_PAGE_SIZE)
      set({ 
        messages: pageResult.content, 
        hasMoreMessages: pageResult.hasNext,
        nextPageToLoad: 1 
      })
    } catch (error) {
      console.error('加载消息失败:', error)
    }
  },

  loadMoreMessages: async (conversationPublicId: string) => {
    const { loadingMore, hasMoreMessages, nextPageToLoad } = get()
    if (loadingMore || !hasMoreMessages) return

    set({ loadingMore: true })
    try {
      const pageResult = await chatApi.getMessages(conversationPublicId, nextPageToLoad, MESSAGE_PAGE_SIZE)
      set((s) => ({
        messages: [...pageResult.content, ...s.messages],
        hasMoreMessages: pageResult.hasNext,
        nextPageToLoad: s.nextPageToLoad + 1,
      }))
    } catch (error) {
      console.error('加载更多消息失败:', error)
    } finally {
      set({ loadingMore: false })
    }
  },

  sendMessage: (request: ChatRequest) => {
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
      chatApi.sendMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('发送消息失败:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('发送消息失败')
    }
  },

  sendAudioMessage: (request: ChatRequest) => {
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
      chatApi.sendMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('发送语音消息失败:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('发送消息失败')
    }
  },

  sendImageMessage: (request: ChatRequest) => {
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
      chatApi.sendMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('发送图片消息失败:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
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

  retryMessage: (request: RetryMessageRequest) => {
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
      chatApi.retryMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('重试消息失败:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('重试消息失败')
    }
  },

  retryMessageWithModel: (request: RetryMessageRequest) => {
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
      chatApi.retryMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('重试消息失败:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('重试消息失败')
    }
  },

  editMessage: (request: EditMessageRequest) => {
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
      m.id === request.userMessageId
        ? {
            ...m,
            content: request.newContent,
            audioData: request.audioData ?? m.audioData,
            audioFormat: request.audioFormat ?? m.audioFormat,
            audioDuration: request.audioDuration ?? m.audioDuration,
            imageData: request.imageData ?? m.imageData,
            imageFormat: request.imageFormat ?? m.imageFormat,
            messageType: request.audioData ? 'audio' : (request.imageData ? 'image' : m.messageType),
          }
        : m
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
      chatApi.editMessageStream(request, onChunk, onFinal, onError, onThinking, onToolCall, onToolResult)
    } catch (error) {
      console.error('编辑消息失败:', error)
      set({ messages, streamingContent: '', loading: false, agentStatus: { thinking: '', toolCalls: [] } })
      alert('编辑消息失败')
    }
  },

  setMode: (mode) => set({ mode }),

  manualCompact: async (conversationPublicId: string) => {
    const { isCompacting, lastCompactTime, compactCooldownTimers } = get()

    if (isCompacting) {
      console.log('正在执行其他Compact操作，请稍候')
      return
    }

    const now = Date.now()
    const lastTime = lastCompactTime[conversationPublicId] || 0
    if (now - lastTime < COMPACT_COOLDOWN_MS) {
      const remainingSeconds = Math.ceil((COMPACT_COOLDOWN_MS - (now - lastTime)) / 1000)
      
      if (compactCooldownTimers[conversationPublicId]) {
        window.clearInterval(compactCooldownTimers[conversationPublicId]!)
      }
      
      set(s => ({
        compactCooldownRemaining: { ...s.compactCooldownRemaining, [conversationPublicId]: remainingSeconds },
        compactResult: {
          ...s.compactResult,
          [conversationPublicId]: {
            success: false,
            message: `操作过于频繁，请 ${remainingSeconds} 秒后再试`,
          },
        },
      }))
      
      const timer = window.setInterval(() => {
        const currentRemaining = get().compactCooldownRemaining[conversationPublicId] || 0
        if (currentRemaining <= 1) {
          window.clearInterval(timer)
          set(s => ({
            compactCooldownRemaining: { ...s.compactCooldownRemaining, [conversationPublicId]: 0 },
            compactCooldownTimers: { ...s.compactCooldownTimers, [conversationPublicId]: null },
            compactResult: {
              ...s.compactResult,
              [conversationPublicId]: null,
            },
          }))
        } else {
          const newRemaining = currentRemaining - 1
          set(s => ({
            compactCooldownRemaining: { ...s.compactCooldownRemaining, [conversationPublicId]: newRemaining },
            compactResult: {
              ...s.compactResult,
              [conversationPublicId]: {
                success: false,
                message: `操作过于频繁，请 ${newRemaining} 秒后再试`,
              },
            },
          }))
        }
      }, 1000)
      
      set(s => ({
        compactCooldownTimers: { ...s.compactCooldownTimers, [conversationPublicId]: timer },
      }))
      
      return
    }

    set({
      isCompacting: true,
      compactingConversationPublicId: conversationPublicId,
      compactResult: {
        ...get().compactResult,
        [conversationPublicId]: null,
      },
    })

    try {
      console.log('手动执行Compact，conversationPublicId:', conversationPublicId)
      const result = await conversationApi.executeCompact(conversationPublicId)

      set(s => ({ lastCompactTime: { ...s.lastCompactTime, [conversationPublicId]: Date.now() } }))

      if (result.executed) {
        console.log('Compact执行成功，节省了', result.savedTokens, 'tokens')
        set(s => ({
          compactResult: {
            ...s.compactResult,
            [conversationPublicId]: {
              success: true,
              message: `压缩成功！节省了 ${result.savedTokens || 0} tokens`,
              savedTokens: result.savedTokens || 0,
              beforeTokens: result.beforeTokens,
              afterTokens: result.afterTokens,
              compactedCardsCount: result.compactedCardsCount ?? result.compactedCardCount,
              recentCardsCount: result.recentCardsCount,
              totalCompactedCards: result.totalCompactedCards,
            },
          },
        }))
      } else {
        console.log('Compact未执行，原因:', result.reason)
        set(s => ({
          compactResult: {
            ...s.compactResult,
            [conversationPublicId]: {
              success: false,
              message: result.reason ? `压缩未执行: ${result.reason}` : '压缩未执行',
            },
          },
        }))
      }
    } catch (error) {
      console.error('手动Compact失败:', error)
      set(s => ({
        compactResult: {
          ...s.compactResult,
          [conversationPublicId]: {
            success: false,
            message: '压缩失败: ' + (error instanceof Error ? error.message : '未知错误'),
          },
        },
      }))
    } finally {
      set({ isCompacting: false, compactingConversationPublicId: null })
    }
  },

  clearCompactResult: (conversationPublicId: string) => {
    set(s => ({
      compactResult: {
        ...s.compactResult,
        [conversationPublicId]: null,
      },
    }))
  },

  clearCompactCooldown: (conversationPublicId: string) => {
    const timers = get().compactCooldownTimers
    if (timers[conversationPublicId]) {
      window.clearInterval(timers[conversationPublicId]!)
    }
    set(s => ({
      compactCooldownTimers: { ...s.compactCooldownTimers, [conversationPublicId]: null },
      compactCooldownRemaining: { ...s.compactCooldownRemaining, [conversationPublicId]: 0 },
    }))
  },

  reset: () => {
    const timers = get().compactCooldownTimers
    Object.values(timers).forEach(timer => {
      if (timer) window.clearInterval(timer)
    })
    set({
      messages: [],
      loading: false,
      loadingMore: false,
      hasMoreMessages: true,
      nextPageToLoad: 0,
      streamingContent: '',
      agentStatus: { thinking: '', toolCalls: [] },
      isCompacting: false,
      compactingConversationPublicId: null,
      compactResult: {},
      compactCooldownRemaining: {},
      compactCooldownTimers: {},
    })
  },
}))
