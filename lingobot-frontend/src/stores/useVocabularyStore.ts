import { create } from 'zustand'
import { VocabularyCardDTO, VocabularyCategory, VocabularyDifficulty } from '../types'
import { vocabularyApi } from '../api'

interface VocabularyStore {
  currentVocabularyCard: VocabularyCardDTO | null
  vocabularyCardLoading: boolean

  loadCard: (conversationId: number, vocabularyCategory?: VocabularyCategory, vocabularyDifficulty?: VocabularyDifficulty) => Promise<void>
  handlePrevWord: (conversationId: number, currentPosition: number, vocabularyDifficulty: VocabularyDifficulty) => Promise<void>
  handleNextWord: (conversationId: number, currentPosition: number, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => Promise<void>
  handleRegenerateWord: (conversationId: number, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => Promise<void>
  saveUserMeaning: (cardId: number, meaning: string) => Promise<void>
  saveUserEnglishSentence: (cardId: number, sentence: string) => Promise<void>
  analyzeUserSentence: (cardId: number) => Promise<void>
  pollSentenceAnalysisResult: (cardId: number) => Promise<void>
  markCardComplete: (cardId: number) => Promise<void>
  pollMeaningCheckResult: (cardId: number) => Promise<void>
  setCard: (card: VocabularyCardDTO | null) => void
  reset: () => void
}

export const useVocabularyStore = create<VocabularyStore>((set, get) => ({
  currentVocabularyCard: null,
  vocabularyCardLoading: false,

  loadCard: async (conversationId: number, vocabularyCategory?: VocabularyCategory, vocabularyDifficulty?: VocabularyDifficulty) => {
    set({ vocabularyCardLoading: true })
    try {
      const existingCard = await vocabularyApi.getCurrentCard(conversationId)
      if (existingCard) {
        set({ currentVocabularyCard: existingCard })
        console.log('已恢复词汇卡状态:', existingCard.word)
      } else {
        console.log('该对话没有词汇卡，准备生成新词汇卡')
        if (vocabularyDifficulty) {
          const newCard = await vocabularyApi.generateNextCard(conversationId, vocabularyCategory, vocabularyDifficulty)
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

  handleNextWord: async (conversationId: number, currentPosition: number, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => {
    set({ vocabularyCardLoading: true })
    try {
      const card = await vocabularyApi.getNextCard(conversationId, currentPosition, vocabularyCategory, vocabularyDifficulty)
      if (card) {
        set({ currentVocabularyCard: card })
      } else {
        const newCard = await vocabularyApi.generateNextCard(conversationId, vocabularyCategory, vocabularyDifficulty)
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

  handleRegenerateWord: async (conversationId: number, vocabularyCategory: VocabularyCategory, vocabularyDifficulty: VocabularyDifficulty) => {
    set({ vocabularyCardLoading: true })
    try {
      const card = await vocabularyApi.regenerateCard(conversationId, vocabularyCategory, vocabularyDifficulty)
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
    console.log('🔍 [useVocabularyStore] saveUserMeaning 被调用:', { cardId, meaning })
    try {
      const updated = await vocabularyApi.updateUserMeaning(cardId, meaning)
      console.log('🔍 [useVocabularyStore] 保存用户意思响应:', updated)
      if (updated) {
        set({ currentVocabularyCard: updated })
      }
    } catch (error) {
      console.error('保存用户意思失败:', error)
    }
    console.log('🔍 [useVocabularyStore] 开始轮询释义检查结果，cardId:', cardId)
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
    console.log('🔍 [useVocabularyStore] 开始轮询释义检查结果:', { cardId, maxAttempts, intervalMs })
    for (let i = 0; i < maxAttempts; i++) {
      console.log('🔍 [useVocabularyStore] 轮询第', i + 1, '次，等待', intervalMs, 'ms...')
      await new Promise(resolve => setTimeout(resolve, intervalMs))
      try {
        const result = await vocabularyApi.getMeaningCheckStatus(cardId)
        console.log('🔍 [useVocabularyStore] 轮询结果:', { attempt: i + 1, result })
        if (result.meaningCheckCompleted) {
          console.log('🔍 [useVocabularyStore] 释义检查完成，更新状态:', result)
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
    console.log('🔍 [useVocabularyStore] 轮询超时，未收到结果')
  },

  setCard: (card) => set({ currentVocabularyCard: card }),

  reset: () => set({ currentVocabularyCard: null, vocabularyCardLoading: false }),
}))
