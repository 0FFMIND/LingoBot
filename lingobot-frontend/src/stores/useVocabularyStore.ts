import { create } from 'zustand'
import { VocabularyCardDTO, VocabularyCategory, VocabularyDifficulty } from '../types'
import { vocabularyApi } from '../api'

interface VocabularyStore {
  currentVocabularyCard: VocabularyCardDTO | null
  vocabularyCardLoading: boolean
  vocabularyCardError: string | null
  cardCache: Record<string, VocabularyCardDTO>

  loadCard: (conversationPublicId: string, vocabularyCategory?: VocabularyCategory, vocabularyDifficulty?: VocabularyDifficulty) => Promise<void>
  handlePrevWord: (conversationPublicId: string, currentPosition: number, vocabularyDifficulty: VocabularyDifficulty) => Promise<void>
  handleNextWord: (conversationPublicId: string, currentPosition: number, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => Promise<void>
  handleRegenerateWord: (conversationPublicId: string, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => Promise<void>
  saveUserMeaning: (cardId: number, meaning: string) => Promise<void>
  saveUserEnglishSentence: (cardId: number, sentence: string) => Promise<void>
  analyzeUserSentence: (cardId: number) => Promise<void>
  pollSentenceAnalysisResult: (cardId: number) => Promise<void>
  markCardComplete: (cardId: number) => Promise<void>
  pollMeaningCheckResult: (cardId: number) => Promise<void>
  preloadCards: (conversationPublicId: string, position: number) => Promise<void>
  getCachedCard: (conversationPublicId: string, position: number) => VocabularyCardDTO | undefined
  updateLastViewedPosition: (conversationPublicId: string, position: number) => Promise<void>
  setCard: (card: VocabularyCardDTO | null) => void
  clearError: () => void
  reset: () => void
}

let activeLoadCardRequestKey: string | null = null

export const useVocabularyStore = create<VocabularyStore>((set, get) => ({
  currentVocabularyCard: null,
  vocabularyCardLoading: false,
  vocabularyCardError: null,
  cardCache: {},

  loadCard: async (conversationPublicId: string, vocabularyCategory?: VocabularyCategory, vocabularyDifficulty?: VocabularyDifficulty) => {
    const requestKey = `${conversationPublicId}:${vocabularyCategory ?? ''}:${vocabularyDifficulty ?? ''}`
    if (activeLoadCardRequestKey === requestKey) {
      return
    }

    activeLoadCardRequestKey = requestKey
    set({ vocabularyCardLoading: true, vocabularyCardError: null })
    try {
      const existingCard = await vocabularyApi.getCurrentCard(conversationPublicId)
      if (existingCard) {
        set({ currentVocabularyCard: existingCard })
        get().preloadCards(conversationPublicId, existingCard.position)
      } else {
        if (vocabularyDifficulty) {
          const newCard = await vocabularyApi.generateNextCard(conversationPublicId, vocabularyCategory, vocabularyDifficulty)
          if (newCard) {
            set({ currentVocabularyCard: newCard })
            get().preloadCards(conversationPublicId, newCard.position)
          }
        } else {
          set({ currentVocabularyCard: null })
        }
      }
    } catch (error) {
      set({ 
        currentVocabularyCard: null,
        vocabularyCardError: error instanceof Error ? error.message : '加载词汇卡失败'
      })
    } finally {
      if (activeLoadCardRequestKey === requestKey) {
        activeLoadCardRequestKey = null
      }
      set({ vocabularyCardLoading: false })
    }
  },

  handlePrevWord: async (conversationPublicId: string, currentPosition: number, _vocabularyDifficulty: string) => {
    set({ vocabularyCardLoading: true, vocabularyCardError: null })
    try {
      const prevPosition = currentPosition - 1
      const cachedCard = get().getCachedCard(conversationPublicId, prevPosition)
      
      let card: VocabularyCardDTO | null = null
      if (cachedCard) {
        card = cachedCard
        get().updateLastViewedPosition(conversationPublicId, card.position)
      } else {
        card = await vocabularyApi.getPrevCard(conversationPublicId, currentPosition)
      }
      
      if (card) {
        set({ currentVocabularyCard: card })
        get().preloadCards(conversationPublicId, card.position)
      }
    } catch (error) {
      console.error('获取上一个单词失败:', error)
      set({ vocabularyCardError: error instanceof Error ? error.message : '获取上一个单词失败' })
    } finally {
      set({ vocabularyCardLoading: false })
    }
  },

  handleNextWord: async (conversationPublicId: string, currentPosition: number, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => {
    set({ vocabularyCardLoading: true, vocabularyCardError: null })
    try {
      const nextPosition = currentPosition + 1
      const cachedCard = get().getCachedCard(conversationPublicId, nextPosition)
      
      let card: VocabularyCardDTO | null = null
      if (cachedCard) {
        card = cachedCard
        get().updateLastViewedPosition(conversationPublicId, card.position)
      } else {
        card = await vocabularyApi.getNextCard(conversationPublicId, currentPosition, vocabularyCategory, vocabularyDifficulty)
      }
      
      if (card) {
        set({ currentVocabularyCard: card })
        get().preloadCards(conversationPublicId, card.position)
      } else {
        const newCard = await vocabularyApi.generateNextCard(conversationPublicId, vocabularyCategory, vocabularyDifficulty)
        if (newCard) {
          set({ currentVocabularyCard: newCard })
          get().preloadCards(conversationPublicId, newCard.position)
        }
      }
    } catch (error) {
      console.error('获取下一个单词失败:', error)
      set({ vocabularyCardError: error instanceof Error ? error.message : '获取下一个单词失败' })
    } finally {
      set({ vocabularyCardLoading: false })
    }
  },

  handleRegenerateWord: async (conversationPublicId: string, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => {
    const currentPosition = get().currentVocabularyCard?.position
    if (currentPosition === undefined) {
      return
    }

    set({ vocabularyCardLoading: true, vocabularyCardError: null })
    try {
      const card = await vocabularyApi.regenerateCard(conversationPublicId, currentPosition, vocabularyCategory, vocabularyDifficulty)
      if (card) {
        set({ currentVocabularyCard: card })
        const cacheKey = `${conversationPublicId}:${currentPosition}`
        set(state => {
          const newCache = { ...state.cardCache }
          delete newCache[cacheKey]
          return { cardCache: newCache }
        })
        get().preloadCards(conversationPublicId, card.position)
      }
    } catch (error) {
      console.error('重新生成单词失败:', error)
      set({ vocabularyCardError: error instanceof Error ? error.message : '重新生成单词失败' })
    } finally {
      set({ vocabularyCardLoading: false })
    }
  },

  saveUserMeaning: async (cardId: number, meaning: string) => {
    try {
      const updated = await vocabularyApi.updateUserMeaning(cardId, meaning)
      if (updated) {
        set({ currentVocabularyCard: updated })
      }
    } catch (error) {
      console.error('保存用户意思失败:', error)
    }
    get().pollMeaningCheckResult(cardId)
  },

  saveUserEnglishSentence: async (cardId: number, sentence: string) => {
    try {
      const updated = await vocabularyApi.updateUserEnglishSentence(cardId, sentence)
      if (updated) {
        set({ currentVocabularyCard: updated })
      }
    } catch (error) {
      console.error('保存用户英文句子失败:', error)
    }
  },

  analyzeUserSentence: async (cardId: number) => {
    try {
      await vocabularyApi.analyzeUserSentence(cardId)
      get().pollSentenceAnalysisResult(cardId)
    } catch (error) {
      console.error('触发句子分析失败:', error)
    }
  },

  pollSentenceAnalysisResult: async (cardId: number) => {
    const maxAttempts = 20
    const intervalMs = 1500
    for (let i = 0; i < maxAttempts; i++) {
      await new Promise(resolve => setTimeout(resolve, intervalMs))
      try {
        const result = await vocabularyApi.getSentenceAnalysisStatus(cardId)
        if (result.sentenceAnalysisCompleted) {
          set(s => ({
            currentVocabularyCard: s.currentVocabularyCard && s.currentVocabularyCard.id === cardId
              ? {
                  ...s.currentVocabularyCard,
                  sentenceAnalysisCompleted: true,
                  sentenceHasNewWord: result.sentenceHasNewWord,
                  sentenceMeaningMatches: result.sentenceMeaningMatches,
                  sentenceAnalysis: result.sentenceAnalysis,
                  userEnglishSentence: result.userEnglishSentence,
                }
              : s.currentVocabularyCard,
          }))
          return
        }
      } catch (e) {
        console.warn('轮询句子分析结果失败:', e)
      }
    }
  },

  markCardComplete: async (cardId: number) => {
    try {
      const updated = await vocabularyApi.markAsCompleted(cardId)
      if (updated) {
        set({ currentVocabularyCard: updated })
      }
    } catch (error) {
      console.error('标记词汇卡完成失败:', error)
    }
  },

  pollMeaningCheckResult: async (cardId: number) => {
    const maxAttempts = 20
    const intervalMs = 1500
    for (let i = 0; i < maxAttempts; i++) {
      await new Promise(resolve => setTimeout(resolve, intervalMs))
      try {
        const result = await vocabularyApi.getMeaningCheckStatus(cardId)
        if (result.meaningCheckCompleted) {
          set(s => ({
            currentVocabularyCard: s.currentVocabularyCard && s.currentVocabularyCard.id === cardId
              ? {
                  ...s.currentVocabularyCard,
                  meaningCheckCompleted: true,
                  meaningIsCorrect: result.meaningIsCorrect,
                  meaningCheckResult: result.meaningCheckResult,
                  chineseSentenceForTranslation: result.chineseSentenceForTranslation || s.currentVocabularyCard.chineseSentenceForTranslation,
                }
              : s.currentVocabularyCard,
          }))
          return
        }
      } catch (e) {
        console.warn('轮询释义检查结果失败:', e)
      }
    }
  },

  preloadCards: async (conversationPublicId: string, position: number) => {
    try {
      const cards = await vocabularyApi.getCardsAroundPosition(conversationPublicId, position)
      if (cards && cards.length > 0) {
        set(state => {
          const newCache = { ...state.cardCache }
          cards.forEach(card => {
            const cacheKey = `${conversationPublicId}:${card.position}`
            newCache[cacheKey] = card
          })
          return { cardCache: newCache }
        })
      }
    } catch (error) {
      console.warn('预加载词汇卡失败:', error)
    }
  },

  getCachedCard: (conversationPublicId: string, position: number) => {
    const cacheKey = `${conversationPublicId}:${position}`
    return get().cardCache[cacheKey]
  },

  updateLastViewedPosition: async (conversationPublicId: string, position: number) => {
    try {
      await vocabularyApi.updateLastViewedPosition(conversationPublicId, position)
    } catch (error) {
      console.warn('更新最后查看位置失败:', error)
    }
  },

  setCard: (card) => set({ currentVocabularyCard: card }),

  clearError: () => set({ vocabularyCardError: null }),

  reset: () => set({ currentVocabularyCard: null, vocabularyCardLoading: false, vocabularyCardError: null, cardCache: {} }),
}))
