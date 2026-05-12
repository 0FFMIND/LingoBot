import { create } from 'zustand'
import { ConversationDTO, LearningMode } from '../types'
import { conversationApi } from '../api'

const PAGE_SIZE = 20

interface ConversationStore {
  conversations: ConversationDTO[]
  currentConversation: ConversationDTO | null
  currentPage: number
  hasMoreConversations: boolean
  loadingMoreConversations: boolean
  newConversationId: number | null
  showModeSelectorForNewChat: boolean
  conversationLearningModes: Record<number, LearningMode>
  globalLearningMode: LearningMode
  mainView: 'chat' | 'settings' | 'vocabulary-manager'

  loadConversations: () => Promise<void>
  loadMoreConversations: () => Promise<void>
  selectConversation: (conversation: ConversationDTO) => void
  createConversation: (title: string) => Promise<void>
  createConversationWithMode: (selectedMode: LearningMode) => Promise<ConversationDTO | null>
  deleteConversation: (id: number) => Promise<void>
  handleLearningModeSelect: (selectedMode: LearningMode) => Promise<void>
  getCurrentLearningMode: () => LearningMode
  isWaitingForMode: () => boolean
  setMainView: (v: 'chat' | 'settings' | 'vocabulary-manager') => void
  reset: () => void
}

export const useConversationStore = create<ConversationStore>((set, get) => ({
  conversations: [],
  currentConversation: null,
  currentPage: 0,
  hasMoreConversations: true,
  loadingMoreConversations: false,
  newConversationId: null,
  showModeSelectorForNewChat: false,
  conversationLearningModes: {},
  globalLearningMode: 'chat',
  mainView: 'chat',

  loadConversations: async () => {
    try {
      const pageResponse = await conversationApi.getByPage(0, PAGE_SIZE)
      const data = pageResponse.content

      const { currentConversation } = get()
      set({
        conversations: data,
        currentPage: 0,
        hasMoreConversations: pageResponse.hasNext,
      })

      if (data.length > 0 && !currentConversation) {
        const currentConvFromBackend = await conversationApi.getCurrent()

        if (currentConvFromBackend) {
          const matchingConv = data.find((c: ConversationDTO) => c.id === currentConvFromBackend.id)
          if (matchingConv) {
            set({ currentConversation: matchingConv })
            if (matchingConv.learningMode) {
              set({ globalLearningMode: matchingConv.learningMode as LearningMode })
            }
            return
          }
        }

        set({ currentConversation: data[0] })
        try {
          await conversationApi.setCurrent(data[0].id)
        } catch (e) {
          console.warn('保存当前对话到后端失败:', e)
        }
        if (data[0].learningMode) {
          set({ globalLearningMode: data[0].learningMode as LearningMode })
        }
      }
    } catch (error) {
      console.error('加载对话列表失败:', error)
    }
  },

  loadMoreConversations: async () => {
    const { loadingMoreConversations, hasMoreConversations, currentPage, conversations } = get()
    if (loadingMoreConversations || !hasMoreConversations) return

    set({ loadingMoreConversations: true })
    try {
      const nextPage = currentPage + 1
      const pageResponse = await conversationApi.getByPage(nextPage, PAGE_SIZE)

      const existingIds = new Set(conversations.map((c: ConversationDTO) => c.id))
      const newConversations = pageResponse.content.filter((c: ConversationDTO) => !existingIds.has(c.id))

      if (newConversations.length > 0) {
        set(s => ({ conversations: [...s.conversations, ...newConversations] }))
      }

      set({ currentPage: nextPage, hasMoreConversations: pageResponse.hasNext })
    } catch (error) {
      console.error('加载更多对话失败:', error)
    } finally {
      set({ loadingMoreConversations: false })
    }
  },

  selectConversation: (conversation: ConversationDTO) => {
    set({
      currentConversation: conversation,
      showModeSelectorForNewChat: false,
      newConversationId: null,
      mainView: 'chat',
    })

    let selectedMode: LearningMode = 'chat'
    if (conversation.learningMode) {
      selectedMode = conversation.learningMode as LearningMode
    } else {
      const modes = get().conversationLearningModes
      if (modes[conversation.id]) {
        selectedMode = modes[conversation.id]
      }
    }
    set({ globalLearningMode: selectedMode })

    try {
      conversationApi.setCurrent(conversation.id)
    } catch (e) {
      console.warn('保存当前对话到后端失败:', e)
    }
  },

  createConversation: async (title: string) => {
    try {
      const newConversation = await conversationApi.create({ title })
      set(s => ({
        conversations: [newConversation, ...s.conversations],
        currentConversation: newConversation,
        newConversationId: newConversation.id,
        showModeSelectorForNewChat: true,
        mainView: 'chat',
      }))
    } catch (error) {
      console.error('创建对话失败:', error)
      alert('创建对话失败')
    }
  },

  createConversationWithMode: async (selectedMode: LearningMode) => {
    try {
      const modeConfig = (await import('../types')).LEARNING_MODES[selectedMode]
      const newConversation = await conversationApi.create({ title: modeConfig.label })

      set(s => ({
        conversations: [newConversation, ...s.conversations],
        currentConversation: newConversation,
        mainView: 'chat',
        conversationLearningModes: { ...s.conversationLearningModes, [newConversation.id]: selectedMode },
        globalLearningMode: selectedMode,
      }))

      // Update conversation in list with learning mode
      set(s => ({
        conversations: s.conversations.map(c =>
          c.id === newConversation.id ? { ...c, learningMode: selectedMode } : c
        ),
      }))

      try {
        await conversationApi.updateLearningMode(newConversation.id, selectedMode)
      } catch (e) {
        console.error('保存学习模式到后端失败:', e)
      }

      return newConversation
    } catch (error) {
      console.error('创建对话失败:', error)
      alert('创建对话失败')
      return null
    }
  },

  deleteConversation: async (id: number) => {
    try {
      await conversationApi.delete(id)
      const { conversations, currentConversation, newConversationId } = get()
      set(s => ({
        conversations: s.conversations.filter(c => c.id !== id),
        conversationLearningModes: (() => {
          const updated = { ...s.conversationLearningModes }
          delete updated[id]
          return updated
        })(),
      }))

      if (currentConversation?.id === id) {
        const remaining = conversations.filter(c => c.id !== id)
        const newCurrent = remaining.length > 0 ? remaining[0] : null
        set({ currentConversation: newCurrent })

        try {
          await conversationApi.setCurrent(newCurrent ? newCurrent.id : null)
        } catch (e) {
          console.warn('更新当前对话到后端失败:', e)
        }

        if (newCurrent?.learningMode) {
          set({ globalLearningMode: newCurrent.learningMode as LearningMode })
        }

        if (newConversationId === id) {
          set({ showModeSelectorForNewChat: false, newConversationId: null })
        }
      }
    } catch (error) {
      console.error('删除对话失败:', error)
      alert('删除对话失败')
    }
  },

  handleLearningModeSelect: async (selectedMode: LearningMode) => {
    const { newConversationId, showModeSelectorForNewChat, currentConversation } = get()
    if (newConversationId !== null && showModeSelectorForNewChat) {
      const conversationId = newConversationId

      set(s => ({
        conversationLearningModes: { ...s.conversationLearningModes, [conversationId]: selectedMode },
        globalLearningMode: selectedMode,
        showModeSelectorForNewChat: false,
        newConversationId: null,
      }))

      set(s => ({
        conversations: s.conversations.map(c =>
          c.id === conversationId ? { ...c, learningMode: selectedMode } : c
        ),
      }))

      if (currentConversation && currentConversation.id === conversationId) {
        set({ currentConversation: { ...currentConversation, learningMode: selectedMode } })
      }

      try {
        await conversationApi.updateLearningMode(conversationId, selectedMode)
      } catch (e) {
        console.error('保存学习模式到后端失败:', e)
      }
    }
  },

  getCurrentLearningMode: () => {
    const { currentConversation, conversationLearningModes, globalLearningMode } = get()
    if (currentConversation && conversationLearningModes[currentConversation.id]) {
      return conversationLearningModes[currentConversation.id]
    }
    if (currentConversation?.learningMode) {
      return currentConversation.learningMode as LearningMode
    }
    return globalLearningMode
  },

  isWaitingForMode: () => {
    const { showModeSelectorForNewChat, currentConversation, newConversationId } = get()
    return showModeSelectorForNewChat &&
      currentConversation?.id === newConversationId
  },

  setMainView: (v) => set({ mainView: v }),

  reset: () => set({
    conversations: [],
    currentConversation: null,
    currentPage: 0,
    hasMoreConversations: true,
    loadingMoreConversations: false,
    newConversationId: null,
    showModeSelectorForNewChat: false,
    conversationLearningModes: {},
    globalLearningMode: 'chat',
    mainView: 'chat',
  }),
}))
