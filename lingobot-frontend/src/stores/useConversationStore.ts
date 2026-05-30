import { create } from 'zustand'
import { ConversationDTO, LearningMode, VocabularyIntent } from '../types'
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
  showVocabularySubMode: boolean
  selectedVocabularyIntent: VocabularyIntent | null

  loadConversations: () => Promise<void>
  loadMoreConversations: () => Promise<void>
  selectConversation: (conversation: ConversationDTO) => void
  createConversation: (title: string) => Promise<void>
  createConversationWithMode: (selectedMode: LearningMode, vocabularyIntent?: VocabularyIntent) => Promise<ConversationDTO | null>
  deleteConversation: (publicId: string) => Promise<void>
  handleLearningModeSelect: (selectedMode: LearningMode) => Promise<void>
  handleVocabularyIntentSelect: (intent: VocabularyIntent) => Promise<void>
  getCurrentLearningMode: () => LearningMode
  getCurrentVocabularyIntent: () => VocabularyIntent | null
  isWaitingForMode: () => boolean
  startNewChatWithModeSelection: () => void
  setMainView: (v: 'chat' | 'settings' | 'vocabulary-manager') => void
  setShowVocabularySubMode: (show: boolean) => void
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
  showVocabularySubMode: false,
  selectedVocabularyIntent: null,

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

  createConversationWithMode: async (selectedMode: LearningMode, vocabularyIntent?: VocabularyIntent) => {
    try {
      const modeConfig = (await import('../types')).LEARNING_MODES[selectedMode]
      const request: any = { title: modeConfig.label }
      if (selectedMode === 'vocabulary' && vocabularyIntent) {
        request.vocabularyIntent = vocabularyIntent
      }
      const newConversation = await conversationApi.create(request)

      set(s => ({
        conversations: [newConversation, ...s.conversations],
        currentConversation: newConversation,
        mainView: 'chat',
        conversationLearningModes: { ...s.conversationLearningModes, [newConversation.publicId]: selectedMode },
        globalLearningMode: selectedMode,
        waitingForModeSelection: false,
        showVocabularySubMode: false,
        selectedVocabularyIntent: vocabularyIntent || null,
      }))

      set(s => ({
        conversations: s.conversations.map(c =>
          c.publicId === newConversation.publicId ? { ...c, learningMode: selectedMode, vocabularyIntent } : c
        ),
      }))

      try {
        const updatedFromLearningMode = await conversationApi.updateLearningMode(newConversation.publicId, selectedMode)
        let finalUpdated = updatedFromLearningMode
        if (selectedMode === 'vocabulary' && vocabularyIntent) {
          const updatedFromVocabularyIntent = await conversationApi.updateVocabularyIntent(newConversation.publicId, vocabularyIntent)
          finalUpdated = updatedFromVocabularyIntent || updatedFromLearningMode
        }
        if (finalUpdated) {
          set(s => ({
            conversations: s.conversations.map(c =>
              c.publicId === newConversation.publicId ? { ...c, ...finalUpdated, learningMode: selectedMode, vocabularyIntent } : c
            ),
            currentConversation: s.currentConversation?.publicId === newConversation.publicId
              ? { ...s.currentConversation, ...finalUpdated, learningMode: selectedMode, vocabularyIntent }
              : s.currentConversation,
          }))
        }
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
    
    if (selectedMode === 'vocabulary') {
      set({ showVocabularySubMode: true })
      return
    }
    
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
        const updatedConversation = await conversationApi.updateLearningMode(publicId, selectedMode)
        if (updatedConversation) {
          set(s => ({
            conversations: s.conversations.map(c =>
              c.publicId === publicId ? { ...c, ...updatedConversation, learningMode: selectedMode } : c
            ),
            currentConversation: s.currentConversation?.publicId === publicId
              ? { ...s.currentConversation, ...updatedConversation, learningMode: selectedMode }
              : s.currentConversation,
          }))
        }
      } catch (e) {
        console.error('保存学习模式到后端失败:', e)
      }
    }
  },

  handleVocabularyIntentSelect: async (intent: VocabularyIntent) => {
    const { newConversationPublicId, showModeSelectorForNewChat, currentConversation } = get()
    
    if (newConversationPublicId !== null && showModeSelectorForNewChat) {
      const publicId = newConversationPublicId
      const selectedMode: LearningMode = 'vocabulary'

      set(s => ({
        conversationLearningModes: { ...s.conversationLearningModes, [publicId]: selectedMode },
        globalLearningMode: selectedMode,
        showModeSelectorForNewChat: false,
        newConversationPublicId: null,
        showVocabularySubMode: false,
        selectedVocabularyIntent: intent,
      }))

      set(s => ({
        conversations: s.conversations.map(c =>
          c.publicId === publicId ? { ...c, learningMode: selectedMode, vocabularyIntent: intent } : c
        ),
      }))

      if (currentConversation && currentConversation.publicId === publicId) {
        set({ currentConversation: { ...currentConversation, learningMode: selectedMode, vocabularyIntent: intent } })
      }

      try {
        const updatedFromLearningMode = await conversationApi.updateLearningMode(publicId, selectedMode)
        const updatedFromVocabularyIntent = await conversationApi.updateVocabularyIntent(publicId, intent)
        const finalUpdated = updatedFromVocabularyIntent || updatedFromLearningMode
        if (finalUpdated) {
          set(s => ({
            conversations: s.conversations.map(c =>
              c.publicId === publicId ? { ...c, ...finalUpdated, learningMode: selectedMode, vocabularyIntent: intent } : c
            ),
            currentConversation: s.currentConversation?.publicId === publicId
              ? { ...s.currentConversation, ...finalUpdated, learningMode: selectedMode, vocabularyIntent: intent }
              : s.currentConversation,
          }))
        }
      } catch (e) {
        console.error('保存学习模式到后端失败:', e)
      }
    } else if (currentConversation) {
      const publicId = currentConversation.publicId

      set(s => ({
        selectedVocabularyIntent: intent,
        showVocabularySubMode: false,
        conversations: s.conversations.map(c =>
          c.publicId === publicId ? { ...c, vocabularyIntent: intent } : c
        ),
        currentConversation: { ...currentConversation, vocabularyIntent: intent },
      }))

      try {
        const updated = await conversationApi.updateVocabularyIntent(publicId, intent)
        if (updated) {
          set(s => ({
            conversations: s.conversations.map(c =>
              c.publicId === publicId ? { ...c, ...updated, vocabularyIntent: intent } : c
            ),
            currentConversation: s.currentConversation?.publicId === publicId
              ? { ...s.currentConversation, ...updated, vocabularyIntent: intent }
              : s.currentConversation,
          }))
        }
      } catch (e) {
        console.error('更新词汇学习意图到后端失败:', e)
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

  getCurrentVocabularyIntent: () => {
    const { currentConversation, selectedVocabularyIntent } = get()
    if (currentConversation?.vocabularyIntent) {
      return currentConversation.vocabularyIntent as VocabularyIntent
    }
    return selectedVocabularyIntent
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

  setShowVocabularySubMode: (show: boolean) => set({ showVocabularySubMode: show }),

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
    showVocabularySubMode: false,
    selectedVocabularyIntent: null,
  }),
}))
