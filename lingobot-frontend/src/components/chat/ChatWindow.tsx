import React, { useState, useEffect, useRef, useCallback } from 'react'
import {
  ModelType,
  LEARNING_MODES,
  VocabularyCategory,
  VocabularyDifficulty,
  VOCABULARY_CATEGORIES,
  VOCABULARY_DIFFICULTIES,
  MODELS,
} from '../../types'
import { useAuthStore, useConversationStore, useChatStore, useVocabularyStore } from '../../stores'
import { usePreferences } from '../../hooks'
import VoiceRecorder from './VoiceRecorder'
import MessageContent from './MessageContent'
import WelcomeMessage from './WelcomeMessage'
import { ModeSelectorGrid, learningFeatures } from './LearningModeSelector'
import UserAudioMessage from './media/UserAudioMessage'
import VocabularyFlashcard, { isVocabularyJson } from '../vocabulary/VocabularyFlashcard'

const modelConfig = {
  qwen: {
    label: 'Qwen 3.5',
    icon: '🌊',
    description: '阿里通义千问，支持图片输入',
    supportsImage: true,
    supportsAudio: false,
    disabledReason: '不支持语音输入',
  },
  xiaomi: {
    label: 'XiaoMi Omni',
    icon: '🔮',
    description: '小米AI模型，支持多模态',
    supportsImage: true,
    supportsAudio: true,
    disabledReason: '',
  },
}

const ChatWindow: React.FC = () => {
  // Store hooks
  const { isAuthenticated, setShowAuthModal } = useAuthStore()
  const {
    currentConversation,
    getCurrentLearningMode,
    isWaitingForMode,
    handleLearningModeSelect,
    handleVocabularyIntentSelect,
    createConversationWithMode,
    showVocabularySubMode,
    selectedVocabularyIntent,
    setShowVocabularySubMode,
    getCurrentVocabularyIntent,
  } = useConversationStore()
  const {
    messages,
    loading,
    streamingContent,
    agentStatus,
    mode,
    sendMessage,
    sendAudioMessage,
    sendImageMessage,
    sendVocabularyMessage,
    retryMessage,
    retryMessageWithModel,
    editMessage,
    editAudioMessage,
  } = useChatStore()
  const {
    currentVocabularyCard,
    vocabularyCardLoading,
    handlePrevWord,
    handleNextWord,
    handleRegenerateWord,
    saveUserMeaning,
    saveUserEnglishSentence,
    analyzeUserSentence,
  } = useVocabularyStore()
  const preferences = usePreferences(isAuthenticated)

  const DEFAULT_VOCABULARY_BATCH_SIZE = 10

  const learningMode = getCurrentLearningMode()
  const showModeSelector = isWaitingForMode()
  const disabled = !isAuthenticated
  const model = preferences.chatModel
  const vocabularyCategory = preferences.vocabularyCategory
  const vocabularyDifficulty = preferences.vocabularyDifficulty
  const vocabularyModel = preferences.vocabularyModel

  // Local UI state
  const [inputValue, setInputValue] = useState('')
  const [editingMessageId, setEditingMessageId] = useState<number | null>(null)
  const [editContent, setEditContent] = useState('')
  const [showModeMenu, setShowModeMenu] = useState(false)
  const [isVoiceRecording, setIsVoiceRecording] = useState(false)
  const [selectedImage, setSelectedImage] = useState<{ data: string; format: string; preview: string } | null>(null)
  const [expandedRetryMessageId, setExpandedRetryMessageId] = useState<number | null>(null)
  const [editingAudioMessageId, setEditingAudioMessageId] = useState<number | null>(null)
  const [editAudioData, setEditAudioData] = useState<string | null>(null)
  const [editAudioFormat, setEditAudioFormat] = useState<string | null>(null)
  const [editAudioDuration, setEditAudioDuration] = useState<number | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const modeMenuRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const previousConversationPublicIdRef = useRef<string | null>(null)

  const learningConfig = LEARNING_MODES[learningMode]

  const filteredDifficulties = VOCABULARY_DIFFICULTIES.filter(
    d => d.category === vocabularyCategory
  )

  const renderVocabularyCategorySelector = () => (
    <div className="vocabulary-selector-wrapper">
      <div className="vocabulary-selector-label">划分标准：</div>
      <select
        className="vocabulary-selector-dropdown"
        value={vocabularyCategory}
        onChange={(e) => {
          const newCategory = e.target.value as VocabularyCategory
          const firstDiff = VOCABULARY_DIFFICULTIES.find(d => d.category === newCategory)
          preferences.updatePreferences({
            vocabularyCategory: newCategory,
            vocabularyDifficulty: firstDiff?.difficulty
          })
        }}
        disabled={disabled || loading}
      >
        {VOCABULARY_CATEGORIES.map((cat) => (
          <option key={cat.category} value={cat.category}>
            {cat.label}
          </option>
        ))}
      </select>
    </div>
  )

  const renderVocabularyDifficultySelector = () => (
    <div className="vocabulary-selector-wrapper">
      <div className="vocabulary-selector-label">难度：</div>
      <select
        className="vocabulary-selector-dropdown"
        value={vocabularyDifficulty}
        onChange={(e) => {
          preferences.setVocabularyDifficulty(e.target.value as VocabularyDifficulty)
        }}
        disabled={disabled || loading}
      >
        {filteredDifficulties.map((diff) => (
          <option key={`${diff.category}-${diff.difficulty}`} value={diff.difficulty}>
            {diff.scoreRange ? `${diff.scoreRange} (${diff.label})` : diff.label}
          </option>
        ))}
      </select>
    </div>
  )

  const renderVocabularyModelSelector = () => (
    <div className="vocabulary-selector-wrapper">
      <div className="vocabulary-selector-label">模型：</div>
      <select
        className="vocabulary-selector-dropdown"
        value={vocabularyModel}
        onChange={(e) => {
          preferences.setVocabularyModel(e.target.value as ModelType)
        }}
        disabled={disabled || loading}
      >
        {MODELS.map((m) => (
          <option key={m.model} value={m.model}>
            {m.label}
          </option>
        ))}
      </select>
    </div>
  )

  const handleSendWithIntent = async (content: string, intent: string, currentWord: string) => {
    if (!isAuthenticated) {
      setShowAuthModal(true)
      return
    }
    if (!currentConversation || loading || showModeSelector) return

    const messageContent = content.trim()
      ? `[intent:${intent}][current_word:${currentWord}][user_input:${content.trim()}]`
      : `[intent:${intent}][current_word:${currentWord}]`

    const response = await sendVocabularyMessage({
      conversationPublicId: currentConversation.publicId,
      content: messageContent,
      mode,
      model: vocabularyModel,
      learningMode,
      intent: intent as any,
      currentWord,
      vocabularyCategory,
      vocabularyDifficulty,
    })

    if (learningMode === 'vocabulary') {
      try {
        const { vocabularyApi } = await import('../../api')
        const updatedCard = await vocabularyApi.getCurrentCard(currentConversation.publicId)
        if (updatedCard) useVocabularyStore.setState({ currentVocabularyCard: updatedCard })
      } catch (e) {
        console.error('重新加载词汇卡失败:', e)
      }
    }
  }

  const getLatestVocabularyData = () => {
    let latestData: any = null
    let flashcardData: any = null

    for (let i = messages.length - 1; i >= 0; i--) {
      const message = messages[i]
      if (message.role !== 'assistant') continue

      const vocabData = isVocabularyJson(message.content)
      if (!vocabData) continue

      if (!latestData) {
        latestData = vocabData
      }

      if (vocabData.action === 'display_flashcard' || (vocabData.synonyms && vocabData.synonyms.length > 0)) {
        flashcardData = vocabData
        break
      }
    }

    if (!latestData) return null

    if (flashcardData && latestData !== flashcardData) {
      return {
        ...flashcardData,
        ...latestData,
        synonyms: latestData.synonyms?.length ? latestData.synonyms : flashcardData.synonyms,
      }
    }

    return latestData
  }

  const renderVocabularyView = () => {
    if (currentVocabularyCard) {
      const vocabData = {
        action: 'new_word',
        word: currentVocabularyCard.word,
        phonetic: currentVocabularyCard.phonetic,
        partOfSpeech: currentVocabularyCard.partOfSpeech,
        meaning: currentVocabularyCard.meaning,
        example: currentVocabularyCard.example,
        exampleTranslation: currentVocabularyCard.exampleTranslation,
        chineseSentenceForTranslation: currentVocabularyCard.chineseSentenceForTranslation || currentVocabularyCard.exampleTranslation,
        synonyms: currentVocabularyCard.synonyms || [],
        antonyms: currentVocabularyCard.antonyms || [],
        vocabularyCategory: currentVocabularyCard.category,
        vocabularyDifficulty: currentVocabularyCard.difficulty,
      }

      return (
        <div className="vocabulary-view">
          <VocabularyFlashcard
            data={vocabData}
            onSendWithIntent={handleSendWithIntent}
            onPrevWord={() => handlePrevWord(currentConversation!.publicId, currentVocabularyCard.position, vocabularyDifficulty)}
            onNextWord={() => handleNextWord(currentConversation!.publicId, currentVocabularyCard.position, vocabularyCategory, vocabularyDifficulty)}
            onRegenerate={() => handleRegenerateWord(currentConversation!.publicId, vocabularyCategory, vocabularyDifficulty)}
            onSaveMeaning={(meaning: string) => saveUserMeaning(currentVocabularyCard.id, meaning)}
            onSaveEnglishSentence={(sentence: string) => saveUserEnglishSentence(currentVocabularyCard.id, sentence)}
            onAnalyzeSentence={() => analyzeUserSentence(currentVocabularyCard.id)}
            cardId={currentVocabularyCard.id}
            isStandalone={true}
            isLoading={vocabularyCardLoading || loading}
            hasPrev={currentVocabularyCard.hasPrev || false}
            hasNext={currentVocabularyCard.hasNext || false}
            isCompleted={currentVocabularyCard.isCompleted || false}
            totalCount={currentVocabularyCard.totalCount ?? DEFAULT_VOCABULARY_BATCH_SIZE}
            currentIndex={currentVocabularyCard.currentIndex ?? 0}
            userMeaningGuess={currentVocabularyCard.userMeaningGuess}
            userEnglishSentence={currentVocabularyCard.userEnglishSentence}
            meaningCheckCompleted={currentVocabularyCard.meaningCheckCompleted}
            meaningIsCorrect={currentVocabularyCard.meaningIsCorrect}
            meaningCheckResult={currentVocabularyCard.meaningCheckResult}
            chineseSentenceForTranslation={currentVocabularyCard.chineseSentenceForTranslation || currentVocabularyCard.exampleTranslation}
            sentenceAnalysisCompleted={currentVocabularyCard.sentenceAnalysisCompleted}
            sentenceHasNewWord={currentVocabularyCard.sentenceHasNewWord}
            sentenceMeaningMatches={currentVocabularyCard.sentenceMeaningMatches}
            sentenceAnalysis={currentVocabularyCard.sentenceAnalysis}
          />
        </div>
      )
    }

    if (vocabularyCardLoading) {
      return (
        <div className="vocabulary-view-empty">
          <div className="empty-icon">📚</div>
          <h3>准备开始词汇学习</h3>
          <p className="typing-english">单词卡片即将加载...</p>
        </div>
      )
    }

    const vocabData = getLatestVocabularyData()

    if (!vocabData) {
      return (
        <div className="vocabulary-view-empty">
          <div className="empty-icon">📚</div>
          <h3>准备开始词汇学习</h3>
          <p>单词卡片即将加载...</p>
        </div>
      )
    }

    return (
      <div className="vocabulary-view">
        <VocabularyFlashcard
          data={vocabData}
          onSendWithIntent={handleSendWithIntent}
          onPrevWord={currentConversation ? () => handlePrevWord(currentConversation.publicId, 0, vocabularyDifficulty) : undefined}
          onNextWord={currentConversation ? () => handleNextWord(currentConversation.publicId, 0, vocabularyCategory, vocabularyDifficulty) : undefined}
          onRegenerate={currentConversation ? () => handleRegenerateWord(currentConversation.publicId, vocabularyCategory, vocabularyDifficulty) : undefined}
          isStandalone={true}
          isLoading={vocabularyCardLoading || loading}
        />
      </div>
    )
  }

  const getVocabularyPriorityText = () => {
    const intent = getCurrentVocabularyIntent()
    switch (intent) {
      case 'review':
        return '◎ 复习优先'
      case 'new_word':
        return '◎ 新单词优先'
      case 'hybrid':
        return '◎ 混合优先'
      default:
        return '◎ 复习优先'
    }
  }

  const renderVocabularyProgressBar = () => {
    const total = currentVocabularyCard?.totalCount ?? DEFAULT_VOCABULARY_BATCH_SIZE
    const index = currentVocabularyCard?.currentIndex ?? 0
    const current = total > 0 ? index + 1 : 0
    const progress = total > 0 ? Math.min(100, Math.max(0, (current / total) * 100)) : 0

    return (
      <div className="vocabulary-progress-strip">
        <div className="vocabulary-progress-left">
          <span className="vocabulary-progress-count">
            第 <strong>{current || 0}</strong> / {total || 0} 张
          </span>
          <div className="vocabulary-progress-track" aria-hidden="true">
            <div className="vocabulary-progress-fill" style={{ width: `${progress}%` }} />
          </div>
        </div>
        <span className="vocabulary-review-priority">{getVocabularyPriorityText()}</span>
      </div>
    )
  }

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (modeMenuRef.current && !modeMenuRef.current.contains(event.target as Node)) {
        setShowModeMenu(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streamingContent, agentStatus])

  // 从聊天消息中提取 check_meaning_accuracy 结果并更新词汇卡状态
  useEffect(() => {
    if (!currentVocabularyCard) return
    for (let i = messages.length - 1; i >= 0; i--) {
      const msg = messages[i]
      if (msg.role !== 'assistant') continue
      const vocabData = isVocabularyJson(msg.content)
      if (vocabData?.action === 'check_meaning_accuracy' && vocabData.is_correct !== undefined) {
        const needsUpdate = !currentVocabularyCard.meaningCheckCompleted ||
          (!currentVocabularyCard.chineseSentenceForTranslation && vocabData.chineseSentenceForTranslation)
        if (needsUpdate) {
          useVocabularyStore.getState().setCard({
            ...currentVocabularyCard,
            meaningCheckCompleted: true,
            meaningIsCorrect: vocabData.is_correct as boolean,
            meaningCheckResult: (vocabData.check_feedback as string) || '',
            chineseSentenceForTranslation: (vocabData.chineseSentenceForTranslation as string) || currentVocabularyCard.chineseSentenceForTranslation,
          })
        }
        break
      }
    }
  }, [messages, currentVocabularyCard?.id])

  useEffect(() => {
    const conversationPublicId = currentConversation?.publicId ?? null
    if (previousConversationPublicIdRef.current !== conversationPublicId) {
      previousConversationPublicIdRef.current = conversationPublicId
      useVocabularyStore.getState().reset()
    }
  }, [currentConversation?.publicId])

  useEffect(() => {
    if (
      learningMode === 'vocabulary' &&
      isAuthenticated &&
      currentConversation &&
      !currentVocabularyCard &&
      !vocabularyCardLoading &&
      !loading
    ) {
      console.log('📚 词汇学习模式：自动初始化词汇卡，难度:', vocabularyDifficulty)
      useVocabularyStore.getState().loadCard(currentConversation.publicId, vocabularyCategory, vocabularyDifficulty)
    }
  }, [learningMode, isAuthenticated, currentConversation, currentVocabularyCard, vocabularyCardLoading, loading, vocabularyDifficulty])

  const handleSend = useCallback(() => {
    if (!isAuthenticated) {
      setShowAuthModal(true)
      return
    }
    if (!currentConversation || loading || showModeSelector) return

    if (selectedImage) {
      sendImageMessage({
        conversationPublicId: currentConversation.publicId,
        content: inputValue.trim(),
        mode,
        model,
        learningMode,
        messageType: 'image',
        imageData: selectedImage.data,
        imageFormat: selectedImage.format,
        vocabularyCategory,
        vocabularyDifficulty,
      })
      setSelectedImage(null)
      setInputValue('')
    } else if (inputValue.trim() && !loading && !disabled && !isVoiceRecording) {
      if (learningMode === 'vocabulary') {
        // vocabulary mode uses sendVocabularyMessage via handleSendWithIntent pattern
        sendMessage({
          conversationPublicId: currentConversation.publicId,
          content: inputValue.trim(),
          mode,
          model,
          learningMode,
          vocabularyCategory,
          vocabularyDifficulty,
        })
      } else {
        sendMessage({
          conversationPublicId: currentConversation.publicId,
          content: inputValue.trim(),
          mode,
          model,
          learningMode,
          vocabularyCategory,
          vocabularyDifficulty,
        })
      }
      setInputValue('')
    }
  }, [
    inputValue, selectedImage, isAuthenticated, currentConversation, loading, showModeSelector,
    disabled, isVoiceRecording, mode, model, learningMode, vocabularyCategory, vocabularyDifficulty,
    sendMessage, sendImageMessage, setShowAuthModal,
  ])

  const handleAudioRecordingComplete = useCallback((audioData: string, audioFormat: string, duration: number) => {
    if (!loading && !disabled && currentConversation) {
      sendAudioMessage({
        conversationPublicId: currentConversation.publicId,
        content: '',
        mode,
        model,
        learningMode,
        messageType: 'audio',
        audioData,
        audioFormat,
        audioDuration: duration,
        vocabularyCategory,
        vocabularyDifficulty,
      })
    }
    setIsVoiceRecording(false)
  }, [loading, disabled, currentConversation, mode, model, learningMode, vocabularyCategory, vocabularyDifficulty, sendAudioMessage])

  const handleAudioCancel = useCallback(() => {
    setIsVoiceRecording(false)
  }, [])

  const toggleVoiceRecorder = useCallback(() => {
    if (loading || disabled) return
    if (!isVoiceRecording) {
      setIsVoiceRecording(true)
    }
  }, [loading, disabled, isVoiceRecording])

  const handleImageSelect = useCallback((file: File) => {
    if (!file.type.startsWith('image/')) {
      alert('请选择图片文件')
      return
    }

    const maxSize = 10 * 1024 * 1024
    if (file.size > maxSize) {
      alert('图片大小不能超过10MB')
      return
    }

    const reader = new FileReader()
    reader.onload = (e) => {
      const result = e.target?.result as string
      const base64Data = result.split(',')[1]
      const format = file.type.split('/')[1] || 'png'

      setSelectedImage({
        data: base64Data,
        format,
        preview: result,
      })
    }
    reader.readAsDataURL(file)
  }, [])

  const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      handleImageSelect(file)
    }
    e.target.value = ''
  }, [handleImageSelect])

  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    const items = e.clipboardData.items
    for (let i = 0; i < items.length; i++) {
      if (items[i].type.startsWith('image/')) {
        const file = items[i].getAsFile()
        if (file) {
          handleImageSelect(file)
        }
        break
      }
    }
  }, [handleImageSelect])

  const removeSelectedImage = useCallback(() => {
    setSelectedImage(null)
  }, [])

  const getPreviousUserMessage = (assistantMessageId: number) => {
    const index = messages.findIndex(m => m.id === assistantMessageId)
    if (index > 0) {
      const prevMessage = messages[index - 1]
      if (prevMessage.role === 'user') {
        return prevMessage
      }
    }
    return null
  }

  const isModelDisabledForMessage = (modelType: ModelType, message: any): boolean => {
    const config = modelConfig[modelType]
    if (message.messageType === 'audio' && !config.supportsAudio) {
      return true
    }
    return false
  }

  const startEdit = (message: any) => {
    if (loading || disabled) return
    setEditingMessageId(message.id)
    setEditContent(message.content)
  }

  const startAudioEdit = (message: any) => {
    if (loading || disabled) return
    setEditingAudioMessageId(message.id)
    setEditContent(message.content)
    setEditAudioData(null)
    setEditAudioFormat(null)
    setEditAudioDuration(null)
  }

  const cancelEdit = () => {
    setEditingMessageId(null)
    setEditContent('')
  }

  const cancelAudioEdit = () => {
    setEditingAudioMessageId(null)
    setEditContent('')
    setEditAudioData(null)
    setEditAudioFormat(null)
    setEditAudioDuration(null)
  }

  const saveEdit = (message: any) => {
    if (!editContent.trim()) {
      alert('消息内容不能为空')
      return
    }
    if (editContent.trim() === message.content.trim()) {
      alert('消息内容没有变化')
      setEditingMessageId(null)
      setEditContent('')
      return
    }
    if (!disabled && currentConversation) {
      editMessage({
        conversationPublicId: currentConversation.publicId,
        userMessageId: message.id,
        newContent: editContent.trim(),
      })
    }
    setEditingMessageId(null)
    setEditContent('')
  }

  const handleEditAudioRecordingComplete = useCallback((audioData: string, audioFormat: string, duration: number) => {
    setEditAudioData(audioData)
    setEditAudioFormat(audioFormat)
    setEditAudioDuration(duration)
  }, [])

  const handleEditAudioCancel = useCallback(() => {
    setEditAudioData(null)
    setEditAudioFormat(null)
    setEditAudioDuration(null)
  }, [])

  const saveAudioEdit = (message: any) => {
    if (!disabled && currentConversation) {
      const hasNewAudio = editAudioData !== null
      const hasContentChange = editContent.trim() !== message.content.trim()

      if (!hasNewAudio && !hasContentChange) {
        alert('消息内容没有变化')
        cancelAudioEdit()
        return
      }

      editAudioMessage(
        {
          conversationPublicId: currentConversation.publicId,
          content: editContent.trim(),
          mode,
          model,
          learningMode,
          audioData: editAudioData || undefined,
          audioFormat: editAudioFormat || undefined,
          audioDuration: editAudioDuration || undefined,
          vocabularyCategory,
          vocabularyDifficulty,
        },
        message.id
      )
    }
    cancelAudioEdit()
  }

  const toggleRetryMenu = (messageId: number) => {
    if (expandedRetryMessageId === messageId) {
      setExpandedRetryMessageId(null)
    } else {
      setExpandedRetryMessageId(messageId)
    }
  }

  const handleRetryWithModel = (assistantMessageId: number, modelType: ModelType) => {
    if (!disabled && currentConversation) {
      retryMessageWithModel({
        conversationPublicId: currentConversation.publicId,
        assistantMessageId,
        model: modelType,
        mode,
        learningMode,
        vocabularyCategory,
        vocabularyDifficulty,
      })
    }
    setExpandedRetryMessageId(null)
  }

  const handleRetry = (assistantMessageId: number) => {
    if (!disabled && currentConversation) {
      retryMessage({
        conversationPublicId: currentConversation.publicId,
        assistantMessageId,
        model,
        mode,
        learningMode,
        vocabularyCategory,
        vocabularyDifficulty,
      })
    }
  }

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp)
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const renderStreamingMessage = () => {
    if (!loading) return null

    const displayContent = streamingContent || ''
    const hasContent = !!displayContent
    const isThinking = agentStatus?.thinking && !hasContent
    const hasToolCalls = agentStatus?.toolCalls && agentStatus.toolCalls.length > 0

    const getToolStatusIcon = (status: string) => {
      switch (status) {
        case 'calling': return '⏳'
        case 'success': return '✅'
        case 'error': return '❌'
        default: return '⏳'
      }
    }

    return (
      <div className="message-english assistant streaming">
        <div className="message-avatar-english">
          <div className="avatar-bubble-english assistant">
            <span className="avatar-emoji">🤖</span>
          </div>
        </div>
        <div className="message-content-english">
          <div className="message-header-english">
            <span className="message-role-english">English Coach</span>
          </div>

          {isThinking && (
            <div className="agent-status-english thinking-status">
              <span className="typing-english">🤔 {agentStatus.thinking}</span>
            </div>
          )}

          {hasToolCalls && (
            <div className="agent-status-english tool-calls-status">
              {agentStatus!.toolCalls.map((toolCall, index) => (
                <div key={toolCall.toolId || index} className="tool-call-item-english">
                  <span className="tool-status-icon-english">{getToolStatusIcon(toolCall.status)}</span>
                  <span className="tool-name-english">{toolCall.toolName}</span>
                  {toolCall.status === 'calling' && (
                    <span className="typing-english">执行中...</span>
                  )}
                </div>
              ))}
            </div>
          )}

          {hasContent && (
            <div className="message-bubble-english assistant">
              <span>{displayContent}</span>
              <span className="cursor-english">▌</span>
            </div>
          )}

          {!isThinking && !hasToolCalls && !hasContent && (
            <div className="message-bubble-english assistant">
              <span className="typing-english">正在思考...</span>
            </div>
          )}
        </div>
      </div>
    )
  }

  const renderLearningFeatures = () => (
    <div className="learning-features-section">
      <div className="features-header">
        <div className="features-title">
          <span className="title-icon">{learningConfig.icon}</span>
          <span className="title-text">{learningConfig.label}</span>
        </div>
      </div>
      <div className="features-grid">
        {learningFeatures.map((feature) => (
          <button
            key={feature.mode}
            className={`feature-card ${learningMode === feature.mode ? 'active' : ''}`}
            style={
              learningMode === feature.mode
                ? { borderColor: feature.color, backgroundColor: feature.bgColor }
                : {}
            }
            onClick={() => {
              if (!disabled) {
                handleLearningModeSelect(feature.mode)
              }
            }}
            disabled={disabled}
          >
            <span
              className="feature-icon"
              style={{ backgroundColor: feature.bgColor, color: feature.color }}
            >
              {feature.icon}
            </span>
            <span className="feature-label">{feature.label}</span>
            <span className="feature-label-en">{feature.labelEn}</span>
          </button>
        ))}
      </div>
    </div>
  )

  if (!currentConversation) {
    if (disabled) {
      const defaultLearningConfig = LEARNING_MODES['chat']
      return (
        <div className="chat-window-english empty disabled">
          <div className="chat-header-english">
            <div className="header-left-english">
              <h2 className="header-title-english">
                <span className="title-prefix">English Learning Coach</span>
                <span className="title-ai">AI</span>
                <span className="header-mode-badge">
                  <span className="header-mode-icon">{defaultLearningConfig.icon}</span>
                  <span className="header-mode-label">{defaultLearningConfig.label}</span>
                </span>
              </h2>
            </div>
          </div>
          <div className="welcome-section-empty">
            <WelcomeMessage username="" learningConfig={defaultLearningConfig} />
          </div>
        </div>
      )
    }

    return (
      <div className={`chat-window-english mode-selector-view ${disabled ? 'disabled' : ''}`}>
        <div className="chat-header-english">
          <div className="header-left-english">
            <h2 className="header-title-english">
              <span className="title-prefix">English Learning Coach</span>
              <span className="title-ai">AI</span>
            </h2>
          </div>
        </div>
        <ModeSelectorGrid
          learningMode={learningMode}
          onLearningModeSelect={handleLearningModeSelect}
          onVocabularyIntentSelect={handleVocabularyIntentSelect}
          onCreateConversationWithMode={createConversationWithMode}
          disabled={disabled}
          isEmptyState={true}
          showVocabularySubMode={showVocabularySubMode}
          selectedVocabularyIntent={selectedVocabularyIntent}
          onBackToModeSelector={() => setShowVocabularySubMode(false)}
        />
      </div>
    )
  }

  if (showModeSelector) {
    return (
      <div className={`chat-window-english mode-selector-view ${disabled ? 'disabled' : ''}`}>
        <div className="chat-header-english">
          <div className="header-left-english">
            <h2 className="header-title-english">
              <span className="title-prefix">English Learning Coach</span>
              <span className="title-ai">AI</span>
            </h2>
          </div>
        </div>
        <ModeSelectorGrid
          learningMode={learningMode}
          onLearningModeSelect={handleLearningModeSelect}
          onVocabularyIntentSelect={handleVocabularyIntentSelect}
          disabled={disabled}
          isEmptyState={false}
          showVocabularySubMode={showVocabularySubMode}
          selectedVocabularyIntent={selectedVocabularyIntent}
          onBackToModeSelector={() => setShowVocabularySubMode(false)}
        />
      </div>
    )
  }

  if (learningMode === 'vocabulary' && currentConversation) {
    return (
      <div className={`chat-window-english vocabulary-view-mode ${disabled ? 'disabled' : ''}`}>
        <div className="chat-header-english">
          <div className="header-left-english">
            <h2 className="header-title-english">
              <span className="title-prefix">English Learning Coach</span>
              <span className="title-ai">AI</span>
              <span className="header-mode-badge">
                <span className="header-mode-icon">{learningConfig.icon}</span>
                <span className="header-mode-label">{learningConfig.label}</span>
              </span>
            </h2>
          </div>
          <div className="header-right-english vocabulary-selectors">
            {renderVocabularyCategorySelector()}
            {renderVocabularyDifficultySelector()}
            {renderVocabularyModelSelector()}
          </div>
        </div>

        {renderVocabularyProgressBar()}

        <div className="vocabulary-main-area">
          {renderVocabularyView()}
        </div>
      </div>
    )
  }

  return (
    <div className={`chat-window-english ${disabled ? 'disabled' : ''}`}>
      <div className="chat-header-english">
        <div className="header-left-english">
          <h2 className="header-title-english">
            <span className="title-prefix">English Learning Coach</span>
            <span className="title-ai">AI</span>
            <span className="header-mode-badge">
              <span className="header-mode-icon">{learningConfig.icon}</span>
              <span className="header-mode-label">{learningConfig.label}</span>
            </span>
          </h2>
        </div>
      </div>

      <div className="chat-messages-english">
        {messages.length === 0 ? (
          <WelcomeMessage username="" learningConfig={learningConfig} />
        ) : (
          messages.map((message) => {
            const isUserMessage = message.role === 'user'
            const isAssistantMessage = message.role === 'assistant'
            const isAudioMessage = message.messageType === 'audio'
            const prevUserMessage = isAssistantMessage ? getPreviousUserMessage(message.id) : null
            const isEditing = editingMessageId === message.id
            const isEditingAudio = editingAudioMessageId === message.id

            return (
              <div
                key={message.id}
                className={`message-english ${message.role} ${isAssistantMessage ? 'assistant-message-group' : ''}`}
              >
                <div className="message-avatar-english">
                  <div className={`avatar-bubble-english ${message.role}`}>
                    <span className="avatar-emoji">
                      {isUserMessage ? '👤' : '🤖'}
                    </span>
                  </div>
                </div>
                <div className="message-content-english">
                  <div className="message-header-english">
                    <span className="message-role-english">
                      {isUserMessage ? 'You' : 'English Coach'}
                    </span>
                    {isAssistantMessage && (
                      <span className="message-time-english">
                        {formatTime(message.timestamp)}
                      </span>
                    )}
                  </div>

                  {isEditing ? (
                    <div className="edit-mode-english">
                      <textarea
                        className="edit-textarea-english"
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        autoFocus
                      />
                      <div className="edit-actions-english">
                        <button
                          className="save-edit-button-english"
                          onClick={() => saveEdit(message)}
                          disabled={disabled}
                        >
                          保存
                        </button>
                        <button
                          className="cancel-edit-button-english"
                          onClick={cancelEdit}
                        >
                          取消
                        </button>
                      </div>
                    </div>
                  ) : isEditingAudio ? (
                    <div className="edit-mode-english audio-edit-mode">
                      <div className="audio-edit-controls-english">
                        {editAudioData ? (
                          <div className="audio-preview-container-english">
                            <UserAudioMessage
                              audioData={editAudioData}
                              audioFormat={editAudioFormat || 'webm'}
                              audioDuration={editAudioDuration || 0}
                            />
                            <span className="audio-preview-label-english">新录音</span>
                          </div>
                        ) : (
                          <div className="audio-original-container-english">
                            {message.audioData && (
                              <div>
                                <UserAudioMessage
                                  audioData={message.audioData}
                                  audioFormat={message.audioFormat || 'webm'}
                                  audioDuration={message.audioDuration}
                                />
                                <span className="audio-preview-label-english">原录音</span>
                              </div>
                            )}
                          </div>
                        )}

                        {!editAudioData && (
                          <div className="audio-re-record-section-english">
                            <button
                              className="re-record-button-english"
                              onClick={() => setIsVoiceRecording(true)}
                              disabled={loading || disabled}
                              title="重新录音"
                            >
                              🎙️ 重新录音
                            </button>
                          </div>
                        )}

                        {isVoiceRecording && (
                          <VoiceRecorder
                            onRecordingComplete={handleEditAudioRecordingComplete}
                            onCancel={handleEditAudioCancel}
                            autoStart={true}
                            disabled={loading || disabled}
                          />
                        )}
                      </div>

                      <textarea
                        className="edit-textarea-english"
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        placeholder="编辑文字内容（可选）..."
                      />

                      <div className="edit-actions-english">
                        <button
                          className="save-edit-button-english"
                          onClick={() => saveAudioEdit(message)}
                          disabled={disabled}
                        >
                          保存
                        </button>
                        <button
                          className="cancel-edit-button-english"
                          onClick={cancelAudioEdit}
                        >
                          取消
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      {isVocabularyJson(message.content) ? (
                        <MessageContent
                          content={message.content}
                          messageType={message.messageType}
                          audioData={message.audioData}
                          audioFormat={message.audioFormat}
                          audioDuration={message.audioDuration}
                          imageData={message.imageData}
                          imageFormat={message.imageFormat}
                          onSendWithIntent={handleSendWithIntent}
                        />
                      ) : (
                        <div className={`message-bubble-english ${message.role}`}>
                          <MessageContent
                            content={message.content}
                            messageType={message.messageType}
                            audioData={message.audioData}
                            audioFormat={message.audioFormat}
                            audioDuration={message.audioDuration}
                            imageData={message.imageData}
                            imageFormat={message.imageFormat}
                          />
                        </div>
                      )}

                      {isUserMessage && !loading && !disabled && (
                        <div className="message-actions-bar-english user-actions">
                          {isAudioMessage ? (
                            <button
                              className="edit-button-english audio-edit-button"
                              onClick={() => startAudioEdit(message)}
                              title="编辑语音和文字"
                            >
                              ✏️ 编辑
                            </button>
                          ) : (
                            <button
                              className="edit-button-english"
                              onClick={() => startEdit(message)}
                              title="编辑"
                            >
                              ✏️ 编辑
                            </button>
                          )}
                        </div>
                      )}

                      {isAssistantMessage && !loading && !disabled && (
                        <div className="message-actions-bar-english">
                          <div className="retry-controls-english">
                            <button
                              className="retry-button-english"
                              onClick={() => toggleRetryMenu(message.id)}
                              title="重试"
                            >
                              🔄 重新生成
                            </button>

                            {expandedRetryMessageId === message.id && (
                              <div className="model-selector-dropdown-english">
                                <div className="model-selector-title-english">选择模型重试</div>
                                <div className="model-selector-options-english">
                                  {(Object.keys(modelConfig) as ModelType[]).map((m) => {
                                    const isDisabled = prevUserMessage ? isModelDisabledForMessage(m, prevUserMessage) : false
                                    const cfg = modelConfig[m]

                                    return (
                                      <button
                                        key={m}
                                        className={`model-selector-option-english ${isDisabled ? 'disabled' : ''} ${model === m ? 'active' : ''}`}
                                        onClick={() => {
                                          if (!isDisabled) {
                                            handleRetryWithModel(message.id, m)
                                          }
                                        }}
                                        disabled={isDisabled}
                                        title={isDisabled ? cfg.disabledReason : ''}
                                      >
                                        <span className="model-icon-english">{cfg.icon}</span>
                                        <div className="model-info-english">
                                          <span className="model-name-english">{cfg.label}</span>
                                          <span className="model-desc-english">{cfg.description}</span>
                                        </div>
                                        {isDisabled && (
                                          <span className="disabled-reason-english">{cfg.disabledReason}</span>
                                        )}
                                        {model === m && !isDisabled && <span className="check-mark-english">✓</span>}
                                      </button>
                                    )
                                  })}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              </div>
            )
          })
        )}
        {renderStreamingMessage()}
        <div ref={messagesEndRef} />
      </div>

      <div className={`chat-input-english ${disabled ? 'disabled' : ''}`}>
        <div className="model-mode-dropdown-english" ref={modeMenuRef}>
          <button
            className="model-mode-toggle-btn-english"
            onClick={() => setShowModeMenu(!showModeMenu)}
            disabled={loading || disabled}
            title="选择模型"
          >
            <span className="model-icon-english">{modelConfig[model].icon}</span>
            <span className="model-label-english">{modelConfig[model].label}</span>
          </button>
          {showModeMenu && (
            <div className="model-mode-menu-english">
              <div className="menu-section-english">
                <div className="section-title-english">选择模型</div>
                <div className="model-options-english">
                  {(Object.keys(modelConfig) as ModelType[]).map((m) => (
                    <button
                      key={m}
                      className={`model-option-english ${model === m ? 'active' : ''}`}
                      onClick={() => {
                        preferences.setChatModel(m)
                        setShowModeMenu(false)
                      }}
                    >
                      <span className="model-icon-english">{modelConfig[m].icon}</span>
                      <div className="model-info-english">
                        <span className="model-name-english">{modelConfig[m].label}</span>
                        <span className="model-desc-english">{modelConfig[m].description}</span>
                      </div>
                      {model === m && <span className="check-mark-english">✓</span>}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="input-right-wrapper-english">
          <input
            type="file"
            ref={fileInputRef}
            style={{ display: 'none' }}
            accept="image/*"
            onChange={handleFileChange}
          />

          {isVoiceRecording ? (
            <VoiceRecorder
              onRecordingComplete={handleAudioRecordingComplete}
              onCancel={handleAudioCancel}
              autoStart={true}
              disabled={loading || disabled}
            />
          ) : (
            <div className="text-input-area-english">
              {selectedImage && (
                <div className="selected-image-preview-container-english">
                  <div className="selected-image-preview-english">
                    <img
                      src={selectedImage.preview}
                      alt="选中的图片"
                      className="preview-thumbnail-english"
                      onClick={() => window.open(selectedImage.preview, '_blank')}
                    />
                    <button
                      className="remove-image-btn-english"
                      onClick={removeSelectedImage}
                      title="移除图片"
                    >
                      ×
                    </button>
                  </div>
                </div>
              )}
              <div className="input-row-container-english">
                <div className="input-box-container-english">
                  <input
                    ref={inputRef}
                    type="text"
                    placeholder={disabled ? '请先登录后使用' : '输入你想练习的内容，例如：用英语描述我的家乡...'}
                    value={inputValue}
                    onChange={(e) => setInputValue(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                    onPaste={handlePaste}
                    disabled={loading || disabled}
                  />
                  <button
                    className="voice-mic-btn-inline-english"
                    onClick={toggleVoiceRecorder}
                    disabled={loading || disabled}
                    title="语音输入"
                  >
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path>
                      <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
                      <line x1="12" y1="19" x2="12" y2="23"></line>
                      <line x1="8" y1="23" x2="16" y2="23"></line>
                    </svg>
                  </button>
                </div>
                <button
                  className="send-btn-english"
                  onClick={handleSend}
                  disabled={loading || (!inputValue.trim() && !selectedImage) || disabled}
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"></path>
                  </svg>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default ChatWindow
