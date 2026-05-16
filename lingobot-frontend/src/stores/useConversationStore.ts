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
  newConversationPublicId: string | null
  showModeSelectorForNewChat: boolean
  waitingForModeSelection: boolean
  conversationLearningModes: Record<string, LearningMode>
  globalLearningMode: LearningMode
  mainView: 'chat' | 'settings' | 'vocabulary-manager'

  loadConversations: () => Promise<void>
  loadMoreConversations: () => Promise<void>
  selectConversation: (conversation: ConversationDTO) => void
  createConversation: (title: string) => Promise<void>
  createConversationWithMode: (selectedMode: LearningMode) => Promise<ConversationDTO | null>
  deleteConversation: (publicId: string) => Promise<void>
  handleLearningModeSelect: (selectedMode: LearningMode) => Promise<void>
  getCurrentLearningMode: () => LearningMode
  isWaitingForMode: () => boolean
  startNewChatWithModeSelection: () => void
  setMainView: (v: 'chat' | 'settings' | 'vocabulary-manager') => void
  reset: () => void
}

export const useConversationStore = create<ConversationStore>((set, get) => ({
  conversations: [],
  currentConversation: null,
  currentPage: 0,
  hasMoreConversations: true,
  loadingMoreConversations: false,
  newConversationPublicId: null,
  showModeSelectorForNewChat: false,
  waitingForModeSelection: false,
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
          const matchingConv = data.find((c: ConversationDTO) => c.publicId === currentConvFromBackend.publicId)
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
          await conversationApi.setCurrent(data[0].publicId)
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

      const existingIds = new Set(conversations.map((c: ConversationDTO) => c.publicId))
      const newConversations = pageResponse.content.filter((c: ConversationDTO) => !existingIds.has(c.publicId))

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
      newConversationPublicId: null,
      waitingForModeSelection: false,
      mainView: 'chat',
    })

    let selectedMode: LearningMode = 'chat'
    if (conversation.learningMode) {
      selectedMode = conversation.learningMode as LearningMode
    } else {
      const modes = get().conversationLearningModes
      if (modes[conversation.publicId]) {
        selectedMode = modes[conversation.publicId]
      }
    }
    set({ globalLearningMode: selectedMode })

    try {
      conversationApi.setCurrent(conversation.publicId)
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
        newConversationPublicId: newConversation.publicId,
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
        conversationLearningModes: { ...s.conversationLearningModes, [newConversation.publicId]: selectedMode },
        globalLearningMode: selectedMode,
        waitingForModeSelection: false,
      }))

      set(s => ({
        conversations: s.conversations.map(c =>
          c.publicId === newConversation.publicId ? { ...c, learningMode: selectedMode } : c
        ),
      }))

      try {
        await conversationApi.updateLearningMode(newConversation.publicId, selectedMode)
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

  deleteConversation: async (publicId: string) => {
    try {
      await conversationApi.delete(publicId)
      const { conversations, currentConversation, newConversationPublicId } = get()
      set(s => ({
        conversations: s.conversations.filter(c => c.publicId !== publicId),
        conversationLearningModes: (() => {
          const updated = { ...s.conversationLearningModes }
          delete updated[publicId]
          return updated
        })(),
      }))

      if (currentConversation?.publicId === publicId) {
        const remaining = conversations.filter(c => c.publicId !== publicId)
        const newCurrent = remaining.length > 0 ? remaining[0] : null
        set({ currentConversation: newCurrent })

        try {
          await conversationApi.setCurrent(newCurrent ? newCurrent.publicId : null)
        } catch (e) {
          console.warn('更新当前对话到后端失败:', e)
        }

        if (newCurrent?.learningMode) {
          set({ globalLearningMode: newCurrent.learningMode as LearningMode })
        }

        if (newConversationPublicId === publicId) {
          set({ showModeSelectorForNewChat: false, newConversationPublicId: null })
        }
      }
    } catch (error) {
      console.error('删除对话失败:', error)
      alert('删除对话失败')
    }
  },

  handleLearningModeSelect: async (selectedMode: LearningMode) => {
    const { newConversationPublicId, showModeSelectorForNewChat, currentConversation } = get()
    if (newConversationPublicId !== null && showModeSelectorForNewChat) {
      const publicId = newConversationPublicId

      set(s => ({
        conversationLearningModes: { ...s.conversationLearningModes, [publicId]: selectedMode },
        globalLearningMode: selectedMode,
        showModeSelectorForNewChat: false,
        newConversationPublicId: null,
      }))

      set(s => ({
        conversations: s.conversations.map(c =>
          c.publicId === publicId ? { ...c, learningMode: selectedMode } : c
        ),
      }))

      if (currentConversation && currentConversation.publicId === publicId) {
        set({ currentConversation: { ...currentConversation, learningMode: selectedMode } })
      }

      try {
        await conversationApi.updateLearningMode(publicId, selectedMode)
      } catch (e) {
        console.error('保存学习模式到后端失败:', e)
      }
    }
  },

  getCurrentLearningMode: () => {
    const { currentConversation, conversationLearningModes, globalLearningMode } = get()
    if (currentConversation && conversationLearningModes[currentConversation.publicId]) {
      return conversationLearningModes[currentConversation.publicId]
    }
    if (currentConversation?.learningMode) {
      return currentConversation.learningMode as LearningMode
    }
    return globalLearningMode
  },

  isWaitingForMode: () => {
    const { showModeSelectorForNewChat, currentConversation, newConversationPublicId, waitingForModeSelection } = get()
    return waitingForModeSelection || (showModeSelectorForNewChat &&
      currentConversation?.publicId === newConversationPublicId)
  },

  startNewChatWithModeSelection: () => {
    set({
      waitingForModeSelection: true,
      currentConversation: null,
      mainView: 'chat',
    })
  },

  setMainView: (v) => set({ mainView: v }),

  reset: () => set({
    conversations: [],
    currentConversation: null,
    currentPage: 0,
    hasMoreConversations: true,
    loadingMoreConversations: false,
    newConversationPublicId: null,
    showModeSelectorForNewChat: false,
    waitingForModeSelection: false,
    conversationLearningModes: {},
    globalLearningMode: 'chat',
    mainView: 'chat',
  }),
}))
