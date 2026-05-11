import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface ConversationTokenUsage {
  conversationId: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  messagesCount: number
  wordCardsCount: number
  lastUpdated: string
}

export interface TokenUsageStore {
  usageByConversation: Record<number, ConversationTokenUsage>
  globalTotalTokens: number
  globalPromptTokens: number
  globalCompletionTokens: number
  maxTokensPerConversation: number

  recordTokenUsage: (conversationId: number, promptTokens: number, completionTokens: number, totalTokens: number) => void
  recordWordCard: (conversationId: number) => void
  getUsageForConversation: (conversationId: number | null) => ConversationTokenUsage | null
  resetConversationUsage: (conversationId: number) => void
  resetAll: () => void
  setMaxTokens: (max: number) => void
  getTokenRatio: (conversationId: number | null) => number
}

const DEFAULT_MAX_TOKENS = 4096 * 2

const createEmptyUsage = (conversationId: number): ConversationTokenUsage => ({
  conversationId,
  totalTokens: 0,
  promptTokens: 0,
  completionTokens: 0,
  messagesCount: 0,
  wordCardsCount: 0,
  lastUpdated: new Date().toISOString(),
})

export const useTokenUsageStore = create<TokenUsageStore>()(
  persist(
    (set, get) => ({
      usageByConversation: {},
      globalTotalTokens: 0,
      globalPromptTokens: 0,
      globalCompletionTokens: 0,
      maxTokensPerConversation: DEFAULT_MAX_TOKENS,

      recordTokenUsage: (conversationId, promptTokens, completionTokens, totalTokens) => {
        const { usageByConversation } = get()
        const existing = usageByConversation[conversationId] || createEmptyUsage(conversationId)

        const updated: ConversationTokenUsage = {
          ...existing,
          totalTokens: existing.totalTokens + (totalTokens || (promptTokens + completionTokens)),
          promptTokens: existing.promptTokens + promptTokens,
          completionTokens: existing.completionTokens + completionTokens,
          messagesCount: existing.messagesCount + 1,
          lastUpdated: new Date().toISOString(),
        }

        set(state => ({
          usageByConversation: {
            ...state.usageByConversation,
            [conversationId]: updated,
          },
          globalTotalTokens: state.globalTotalTokens + (totalTokens || (promptTokens + completionTokens)),
          globalPromptTokens: state.globalPromptTokens + promptTokens,
          globalCompletionTokens: state.globalCompletionTokens + completionTokens,
        }))
      },

      recordWordCard: (conversationId) => {
        const { usageByConversation } = get()
        const existing = usageByConversation[conversationId] || createEmptyUsage(conversationId)

        const updated: ConversationTokenUsage = {
          ...existing,
          wordCardsCount: existing.wordCardsCount + 1,
          lastUpdated: new Date().toISOString(),
        }

        set(state => ({
          usageByConversation: {
            ...state.usageByConversation,
            [conversationId]: updated,
          },
        }))
      },

      getUsageForConversation: (conversationId) => {
        if (!conversationId) return null
        return get().usageByConversation[conversationId] || null
      },

      resetConversationUsage: (conversationId) => {
        const { usageByConversation } = get()
        const existing = usageByConversation[conversationId]

        if (!existing) return

        set(state => {
          const newUsage = { ...state.usageByConversation }
          delete newUsage[conversationId]

          return {
            usageByConversation: newUsage,
            globalTotalTokens: Math.max(0, state.globalTotalTokens - existing.totalTokens),
            globalPromptTokens: Math.max(0, state.globalPromptTokens - existing.promptTokens),
            globalCompletionTokens: Math.max(0, state.globalCompletionTokens - existing.completionTokens),
          }
        })
      },

      resetAll: () => {
        set({
          usageByConversation: {},
          globalTotalTokens: 0,
          globalPromptTokens: 0,
          globalCompletionTokens: 0,
        })
      },

      setMaxTokens: (max) => {
        set({ maxTokensPerConversation: max })
      },

      getTokenRatio: (conversationId) => {
        const usage = conversationId ? get().usageByConversation[conversationId] : null
        if (!usage) return 0
        return Math.min(usage.totalTokens / get().maxTokensPerConversation, 1)
      },
    }),
    {
      name: 'lingobot-token-usage',
      partialize: (state) => ({
        usageByConversation: state.usageByConversation,
        globalTotalTokens: state.globalTotalTokens,
        globalPromptTokens: state.globalPromptTokens,
        globalCompletionTokens: state.globalCompletionTokens,
        maxTokensPerConversation: state.maxTokensPerConversation,
      }),
    }
  )
)
