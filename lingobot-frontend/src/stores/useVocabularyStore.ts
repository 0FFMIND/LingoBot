import { create } from 'zustand'
import { VocabularyCardDTO } from '../types'
import { vocabularyApi } from '../api'

interface VocabularyStore {
  currentVocabularyCard: VocabularyCardDTO | null
  vocabularyCardLoading: boolean

  loadCard: (conversationId: number, vocabularyDifficulty?: string) => Promise<void>
  handlePrevWord: (conversationId: number, currentPosition: number, vocabularyDifficulty: string) => Promise<void>
  handleNextWord: (conversationId: number, currentPosition: number, vocabularyDifficulty: string) => Promise<void>
  handleRegenerateWord: (conversationId: number, vocabularyDifficulty: string) => Promise<void>
  saveUserMeaning: (cardId: number, meaning: string) => Promise<void>
  saveUserSentence: (cardId: number, sentence: string) => Promise<void>
  markCardComplete: (cardId: number) => Promise<void>
  pollMeaningCheckResult: (cardId: number) => Promise<void>
  setCard: (card: VocabularyCardDTO | null) => void
  reset: () => void
}

export const useVocabularyStore = create<VocabularyStore>((set, get) => ({
  currentVocabularyCard: null,
  vocabularyCardLoading: false,

  loadCard: async (conversationId: number, vocabularyDifficulty?: string) => {
    set({ vocabularyCardLoading: true })
    try {
      const existingCard = await vocabularyApi.getCurrentCard(conversationId)
      if (existingCard) {
        set({ currentVocabularyCard: existingCard })
        console.log('已恢复词汇卡状态:', existingCard.word)
      } else {
        console.log('该对话没有词汇卡，准备生成新词汇卡')
        if (vocabularyDifficulty) {
          const newCard = await vocabularyApi.generateNextCard(conversationId, vocabularyDifficulty.toUpperCase())
          if (newCard) {
            set({ currentVocabularyCard: newCard })
            console.log('已生成新词汇卡:', newCard.word)
          }
        } else {
          console.warn('没有提供难度级别，无法生成新词汇卡')
          set({ currentVocabularyCard: null })
        }
      }
    } catch (e) {
      console.log('获取/生成词汇卡失败:', e)
      set({ currentVocabularyCard: null })
    } finally {
      set({ vocabularyCardLoading: false })
    }
  },

  handlePrevWord: async (conversationId: number, currentPosition: number, _vocabularyDifficulty: string) => {
    set({ vocabularyCardLoading: true })
    try {
      const card = await vocabularyApi.getPrevCard(conversationId, currentPosition)
      if (card) {
        set({ currentVocabularyCard: card })
      }
    } catch (error) {
      console.error('获取上一个单词失败:', error)
    } finally {
      set({ vocabularyCardLoading: false })
    }
  },

  handleNextWord: async (conversationId: number, currentPosition: number, vocabularyDifficulty: string) => {
    set({ vocabularyCardLoading: true })
    try {
      const card = await vocabularyApi.getNextCard(conversationId, currentPosition, vocabularyDifficulty.toUpperCase())
      if (card) {
        set({ currentVocabularyCard: card })
      } else {
        const newCard = await vocabularyApi.generateNextCard(conversationId, vocabularyDifficulty.toUpperCase())
        if (newCard) {
          set({ currentVocabularyCard: newCard })
        }
      }
    } catch (error) {
      console.error('获取下一个单词失败:', error)
    } finally {
      set({ vocabularyCardLoading: false })
    }
  },

  handleRegenerateWord: async (conversationId: number, vocabularyDifficulty: string) => {
    set({ vocabularyCardLoading: true })
    try {
      const card = await vocabularyApi.regenerateCard(conversationId, vocabularyDifficulty.toUpperCase())
      if (card) {
        set({ currentVocabularyCard: card })
      }
    } catch (error) {
      console.error('重新生成单词失败:', error)
      alert('重新生成单词失败: ' + (error instanceof Error ? error.message : '未知错误'))
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

  saveUserSentence: async (cardId: number, sentence: string) => {
    try {
      const updated = await vocabularyApi.updateUserSentence(cardId, sentence)
      if (updated) {
        set({ currentVocabularyCard: updated })
      }
    } catch (error) {
      console.error('保存用户造句失败:', error)
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

  setCard: (card) => set({ currentVocabularyCard: card }),

  reset: () => set({ currentVocabularyCard: null, vocabularyCardLoading: false }),
}))
