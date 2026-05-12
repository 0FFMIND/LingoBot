import React, { useCallback } from 'react'

export interface VocabularyData {
  action: string
  word?: string
  phonetic?: string
  partOfSpeech?: string
  meaning?: string
  example?: string
  exampleTranslation?: string
  chineseSentenceForTranslation?: string
  userEnglishSentence?: string
  sentenceAnalysis?: string
  synonyms?: string[]
  antonyms?: string[]
  vocabularyCategory?: string
  vocabularyDifficulty?: string
  level?: string
  message?: string
  correct?: boolean
  user_answer?: string
  correct_answer?: string
  display_mode?: string
  current_word?: string
  feedback?: string
  hasFeedback?: boolean
  is_correct?: boolean
  check_feedback?: string
  user_meaning?: string
  correct_meaning?: string
}

interface VocabularyFlashcardProps {
  data: VocabularyData
  onSendWithIntent?: (content: string, intent: string, currentWord: string) => void
  onNextWord?: () => void
  onPrevWord?: () => void
  onRegenerate?: () => void
  onSaveMeaning?: (meaning: string) => void
  onSaveEnglishSentence?: (sentence: string) => void
  onAnalyzeSentence?: () => void
  cardId?: number
  isStandalone?: boolean
  hasPrev?: boolean
  hasNext?: boolean
  isCompleted?: boolean
  totalCount?: number
  currentIndex?: number
  userMeaningGuess?: string
  userEnglishSentence?: string
  isLoading?: boolean
  meaningCheckCompleted?: boolean
  meaningIsCorrect?: boolean
  meaningCheckResult?: string
  chineseSentenceForTranslation?: string
  sentenceAnalysisCompleted?: boolean
  sentenceHasNewWord?: boolean
  sentenceMeaningMatches?: boolean
  sentenceAnalysis?: string
}

const cleanPhonetic = (phonetic: string): string => {
  if (!phonetic) return ''
  return phonetic.replace(/^\/+|\/+$/g, '')
}

export const isVocabularyJson = (content: string): VocabularyData | null => {
  console.log('🔍 [isVocabularyJson - VocabularyFlashcard] 尝试解析内容:', {
    contentLength: content?.length,
    contentPreview: content?.substring(0, 100),
  })
  try {
    const parsed = JSON.parse(content)
    console.log('🔍 [isVocabularyJson - VocabularyFlashcard] 解析成功:', {
      action: parsed.action,
      word: parsed.word,
      phonetic: parsed.phonetic,
      is_correct: parsed.is_correct,
    })
    if (parsed.action === 'check_meaning_accuracy' && parsed.word) {
      console.log('🔍 [isVocabularyJson - VocabularyFlashcard] 匹配 check_meaning_accuracy 模式')
      return parsed as VocabularyData
    }
    if (parsed.action && parsed.word && parsed.phonetic) {
      console.log('🔍 [isVocabularyJson - VocabularyFlashcard] 匹配标准词汇卡模式')
      return parsed as VocabularyData
    }
    console.log('🔍 [isVocabularyJson - VocabularyFlashcard] 不匹配任何模式，返回 null')
    return null
  } catch (e) {
    console.warn('🔍 [isVocabularyJson - VocabularyFlashcard] 解析失败:', e)
    return null
  }
}

const VocabularyFlashcard: React.FC<VocabularyFlashcardProps> = ({
  data,
  onSendWithIntent,
  onNextWord,
  onPrevWord,
  onRegenerate,
  onSaveMeaning,
  onSaveEnglishSentence,
  onAnalyzeSentence,
  cardId: _cardId,
  isStandalone = false,
  hasPrev = false,
  hasNext = false,
  isCompleted = false,
  totalCount,
  currentIndex,
  userMeaningGuess,
  userEnglishSentence: savedUserEnglishSentence,
  isLoading = false,
  meaningCheckCompleted,
  meaningIsCorrect,
  meaningCheckResult,
  chineseSentenceForTranslation: propChineseSentence,
  sentenceAnalysisCompleted,
  sentenceHasNewWord,
  sentenceMeaningMatches,
  sentenceAnalysis,
}) => {
  const isLastWord = !hasNext && totalCount !== undefined && currentIndex !== undefined
    ? currentIndex >= totalCount - 1
    : !hasNext
  const hasMeaningCheckResultFromData =
    data.action === 'check_meaning_accuracy' &&
    data.is_correct !== undefined

  const effectiveMeaningCheckCompleted =
    meaningCheckCompleted !== undefined ? meaningCheckCompleted : hasMeaningCheckResultFromData

  const effectiveMeaningIsCorrect =
    meaningIsCorrect !== undefined ? meaningIsCorrect : (data.is_correct ?? false)

  const effectiveMeaningCheckResult =
    meaningCheckResult !== undefined ? meaningCheckResult : (data.check_feedback ?? '')

  console.log('🔍 [VocabularyFlashcard] 词汇检查状态:', {
    dataAction: data.action,
    dataIsCorrect: data.is_correct,
    propMeaningCheckCompleted: meaningCheckCompleted,
    hasMeaningCheckResultFromData,
    effectiveMeaningCheckCompleted,
    propMeaningIsCorrect: meaningIsCorrect,
    effectiveMeaningIsCorrect,
  })

  const effectiveUserMeaning = userMeaningGuess || data.user_meaning || ''

  const effectiveMeaning = data.meaning || data.correct_meaning || ''

  const effectiveChineseSentence = propChineseSentence || data.chineseSentenceForTranslation || data.exampleTranslation || ''

  const effectiveSentenceAnalysisCompleted = sentenceAnalysisCompleted ?? false
  const effectiveSentenceAnalysis = sentenceAnalysis || ''
  const effectiveHasNewWord = sentenceHasNewWord
  const effectiveMeaningMatches = sentenceMeaningMatches

  const getInitialStep = (): 1 | 2 | 3 => {
    if (isCompleted) return 3
    if (savedUserEnglishSentence) return 3
    if (effectiveUserMeaning || hasMeaningCheckResultFromData) return 2
    return 1
  }

  const [step, setStep] = React.useState<1 | 2 | 3>(getInitialStep)
  const [meaningInput, setMeaningInput] = React.useState(effectiveUserMeaning)
  const [englishSentenceInput, setEnglishSentenceInput] = React.useState(savedUserEnglishSentence || '')
  const [isPlayingPronunciation, setIsPlayingPronunciation] = React.useState(false)
  const [audioAvailable, setAudioAvailable] = React.useState<boolean | null>(null)
  const meaningInputRef = React.useRef<HTMLInputElement>(null)
  const sentenceInputRef = React.useRef<HTMLTextAreaElement>(null)
  const pronunciationAudioRef = React.useRef<HTMLAudioElement | null>(null)

  const canNavigate = step === 3 && (effectiveSentenceAnalysisCompleted || isCompleted)

  const getLevelDisplay = () => {
    const { vocabularyCategory, vocabularyDifficulty } = data
    if (!vocabularyCategory || !vocabularyDifficulty) return null

    const categoryLabels: Record<string, string> = {
      'cefr': 'CEFR',
      'toefl': 'TOEFL',
    }

    const difficultyLabels: Record<string, string> = {
      'a1': 'A1',
      'a2': 'A2',
      'b1': 'B1',
      'b2': 'B2',
      'c1': 'C1',
      'c2': 'C2',
      '4.0-5.0': '4.0-5.0',
      '5.5-6.5': '5.5-6.5',
      '7.0-8.0': '7.0-8.0',
      '8.5-9.0': '8.5-9.0',
      '60-80': '60-80',
      '81-100': '81-100',
      '101-110': '101-110',
      '111-120': '111-120',
    }

    const category = categoryLabels[vocabularyCategory.toLowerCase()] || vocabularyCategory
    const difficulty = difficultyLabels[vocabularyDifficulty.toLowerCase()] || vocabularyDifficulty

    return {
      label: `${category} ${difficulty}`,
      color: getLevelColor(vocabularyDifficulty),
    }
  }

  const getLevelColor = (difficulty: string): string => {
    const d = difficulty.toLowerCase()
    if (['a1', 'a2', '4.0-5.0', '60-80'].includes(d)) return '#22c55e'
    if (['b1', 'b2', '5.5-6.5', '81-100'].includes(d)) return '#3b82f6'
    if (['c1', '7.0-8.0', '101-110'].includes(d)) return '#f59e0b'
    if (['c2', '8.5-9.0', '111-120'].includes(d)) return '#ef4444'
    return '#64748b'
  }

  React.useEffect(() => {
    const newStep = getInitialStep()
    setStep(newStep)
    setMeaningInput(effectiveUserMeaning)
    setEnglishSentenceInput(savedUserEnglishSentence || '')
  }, [data.word, effectiveUserMeaning, savedUserEnglishSentence, isCompleted, data.is_correct, data.action])

  React.useEffect(() => {
    const word = data.word || data.current_word
    if (!word) {
      setAudioAvailable(null)
      return
    }

    setAudioAvailable(null)
    const audioUrl = `/api/tts/word?word=${encodeURIComponent(word)}&voiceType=us`
    const audio = new Audio()

    const handleCanPlayThrough = () => {
      setAudioAvailable(true)
    }

    const handleError = () => {
      setAudioAvailable(false)
    }

    audio.addEventListener('canplaythrough', handleCanPlayThrough)
    audio.addEventListener('error', handleError)

    audio.src = audioUrl
    audio.load()

    return () => {
      audio.removeEventListener('canplaythrough', handleCanPlayThrough)
      audio.removeEventListener('error', handleError)
    }
  }, [data.word, data.current_word])

  React.useEffect(() => {
    if (step === 1 && meaningInputRef.current) {
      meaningInputRef.current.focus()
    }
    if (step === 2 && sentenceInputRef.current) {
      sentenceInputRef.current.focus()
    }
  }, [step])

  const handleRevealMeaning = () => {
    if (meaningInput.trim() && onSaveMeaning) {
      onSaveMeaning(meaningInput.trim())
    }
    setStep(2)
  }

  const handleSubmitEnglishSentence = () => {
    if (!englishSentenceInput.trim()) return
    if (onSaveEnglishSentence) {
      onSaveEnglishSentence(englishSentenceInput.trim())
    }
    setStep(3)
    if (onAnalyzeSentence) {
      onAnalyzeSentence()
    }
  }

  const playPronunciation = useCallback(async () => {
    const word = data.word || data.current_word
    if (!word) return

    if (isPlayingPronunciation && pronunciationAudioRef.current) {
      pronunciationAudioRef.current.pause()
      pronunciationAudioRef.current = null
      setIsPlayingPronunciation(false)
      return
    }

    if (audioAvailable === false) {
      console.warn('音频不可用，无法播放:', word)
      return
    }

    try {
      setIsPlayingPronunciation(true)
      const audioUrl = `/api/tts/word?word=${encodeURIComponent(word)}&voiceType=us`
      const audio = new Audio()
      pronunciationAudioRef.current = audio

      audio.onended = () => {
        setIsPlayingPronunciation(false)
        pronunciationAudioRef.current = null
      }

      audio.onerror = () => {
        setIsPlayingPronunciation(false)
        setAudioAvailable(false)
        pronunciationAudioRef.current = null
      }

      audio.src = audioUrl
      await audio.play()
    } catch (error) {
      console.error('播放发音失败:', error)
      setIsPlayingPronunciation(false)
      setAudioAvailable(false)
    }
  }, [data.word, data.current_word, isPlayingPronunciation, audioAvailable])

  const cardClassName = isStandalone
    ? 'vocabulary-flashcard vocabulary-flashcard-standalone'
    : 'vocabulary-flashcard'

  return (
    <div className={cardClassName}>
      <div className="flashcard-header">
        <div className="flashcard-icon">📖</div>
        {totalCount !== undefined && currentIndex !== undefined && (
          <span className="card-counter">
            {currentIndex + 1} / {totalCount}
          </span>
        )}
        {(() => {
          const levelDisplay = getLevelDisplay()
          if (!levelDisplay) return null
          return (
            <span
              className="level-badge"
              style={{ backgroundColor: `${levelDisplay.color}20`, color: levelDisplay.color }}
            >
              {levelDisplay.label}
            </span>
          )
        })()}
      </div>

      <div className="flashcard-word-section">
        <div className="flashcard-word">{data.word}</div>
        <div className="flashcard-phonetic-wrapper">
          {data.partOfSpeech && <span className="flashcard-pos">{data.partOfSpeech}</span>}
          {data.phonetic && <span className="flashcard-phonetic">/{cleanPhonetic(data.phonetic)}/</span>}
          {audioAvailable === true && (
            <button
              className="pronunciation-btn"
              onClick={playPronunciation}
              disabled={isPlayingPronunciation}
              title={isPlayingPronunciation ? '播放中...' : '播放发音'}
            >
              {isPlayingPronunciation ? '🔊' : '🔈'}
            </button>
          )}
        </div>
      </div>

      {step === 1 && (
        <div className="meaning-guess-section">
          <div className="meaning-guess-label">请输入这个单词的中文意思：</div>
          <div className="meaning-guess-row">
            <input
              ref={meaningInputRef}
              type="text"
              className="meaning-guess-input"
              placeholder="例如：冗长的，啰嗦的"
              value={meaningInput}
              onChange={(e) => setMeaningInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleRevealMeaning() }}
            />
            <button className="reveal-meaning-btn" onClick={handleRevealMeaning}>
              提交答案
            </button>
          </div>
        </div>
      )}

      {step === 2 && (
        <>
          {effectiveMeaning && (
            <div className="flashcard-content meaning-content expanded">
              <div className="content-title">
                <span className="title-icon">📖</span>
                <span>释义</span>
              </div>
              <div className="meaning-text">{effectiveMeaning}</div>
            </div>
          )}
          {data.synonyms && data.synonyms.length > 0 && (
            <div className="flashcard-content synonyms-content expanded">
              <div className="content-title">
                <span className="title-icon">🔗</span>
                <span>Synonyms</span>
              </div>
              <div className="synonyms-list">
                {data.synonyms.map((synonym, index) => (
                  <span key={index} className="synonym-tag">{synonym}</span>
                ))}
              </div>
            </div>
          )}
          {meaningInput.trim() && (
            effectiveMeaningCheckCompleted ? (
              <div className={`meaning-check-result ${effectiveMeaningIsCorrect ? 'correct' : 'incorrect'}`}>
                <span className="meaning-check-icon">{effectiveMeaningIsCorrect ? 'OK' : '!'}</span>
                <span className="meaning-check-label">{effectiveMeaningIsCorrect ? 'Meaning understood' : 'Needs review'}</span>
                {effectiveMeaningCheckResult && <p className="meaning-check-feedback">{effectiveMeaningCheckResult}</p>}
              </div>
            ) : (
              <div className="meaning-check-pending">
                <span className="typing-english">⏳ 正在检查你的理解...</span>
              </div>
            )
          )}

          {effectiveChineseSentence && (
            <div className="flashcard-content sentence-content expanded">
              <div className="content-title">
                <span className="title-icon">📝</span>
                <span>中文例句</span>
              </div>
              <div className="sentence-text">"{effectiveChineseSentence}"</div>
            </div>
          )}

          <div className="sentence-input-section">
            <div className="content-title" style={{ marginBottom: '8px' }}>
              <span className="title-icon">✍️</span>
              <span>你的英文句子</span>
            </div>
            <div className="sentence-input-hint">
              请根据上面的中文例句，写出对应的英文句子（必须包含新单词）
            </div>
            <div className="sentence-input-row">
              <textarea
                ref={sentenceInputRef}
                className="sentence-input"
                placeholder="请输入英文句子...（Shift+Enter 换行，Enter 提交）"
                value={englishSentenceInput}
                rows={3}
                onChange={(e) => setEnglishSentenceInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSubmitEnglishSentence() } }}
              />
              <button
                className="submit-sentence-btn"
                onClick={handleSubmitEnglishSentence}
                disabled={!englishSentenceInput.trim()}
              >
                提交句子
              </button>
            </div>
          </div>
        </>
      )}

      {step === 3 && (
        <>
          {effectiveMeaning && (
            <div className="flashcard-content meaning-content expanded">
              <div className="content-title">
                <span className="title-icon">📖</span>
                <span>释义</span>
              </div>
              <div className="meaning-text">{effectiveMeaning}</div>
            </div>
          )}
          {data.synonyms && data.synonyms.length > 0 && (
            <div className="flashcard-content synonyms-content expanded">
              <div className="content-title">
                <span className="title-icon">🔗</span>
                <span>Synonyms</span>
              </div>
              <div className="synonyms-list">
                {data.synonyms.map((synonym, index) => (
                  <span key={index} className="synonym-tag">{synonym}</span>
                ))}
              </div>
            </div>
          )}

          {effectiveUserMeaning && (
            effectiveMeaningCheckCompleted ? (
              <div className={`meaning-check-result ${effectiveMeaningIsCorrect ? 'correct' : 'incorrect'}`}>
                <span className="meaning-check-icon">{effectiveMeaningIsCorrect ? 'OK' : '!'}</span>
                <span className="meaning-check-label">{effectiveMeaningIsCorrect ? 'Meaning understood' : 'Needs review'}</span>
                {effectiveMeaningCheckResult && <p className="meaning-check-feedback">{effectiveMeaningCheckResult}</p>}
              </div>
            ) : (
              <div className="meaning-check-pending">
                <span className="typing-english">⏳ 正在检查你的理解...</span>
              </div>
            )
          )}

          {savedUserEnglishSentence && (
            <div className="flashcard-content sentence-content expanded">
              <div className="content-title">
                <span className="title-icon">✍️</span>
                <span>你的英文句子</span>
              </div>
              <div className="sentence-text">"{savedUserEnglishSentence}"</div>
            </div>
          )}

          {data.example && (
            <div className="flashcard-content example-content expanded">
              <div className="content-title">
                <span className="title-icon">📖</span>
                <span>参考例句</span>
              </div>
              <div className="example-text">"{data.example}"</div>
            </div>
          )}

          {!effectiveSentenceAnalysisCompleted && !isCompleted ? (
            <div className="sentence-waiting">
              <span className="typing-english">⏳ 正在分析你的英文句子...</span>
            </div>
          ) : effectiveSentenceAnalysisCompleted && (
            <>
              <div className="sentence-check-results" style={{ marginBottom: '12px' }}>
                <div className={`check-result-item ${effectiveHasNewWord ? 'correct' : 'incorrect'}`}>
                  <span className="check-icon">{effectiveHasNewWord ? 'OK' : '!'}</span>
                  <span>{effectiveHasNewWord ? 'Uses new word' : 'Missing new word'}</span>
                </div>
                <div className={`check-result-item ${effectiveMeaningMatches ? 'correct' : 'incorrect'}`}>
                  <span className="check-icon">{effectiveMeaningMatches ? 'OK' : '!'}</span>
                  <span>{effectiveMeaningMatches ? 'Meaning matches' : 'Meaning mismatch'}</span>
                </div>
              </div>

              {effectiveSentenceAnalysis && (
                <div className="flashcard-content feedback-content expanded">
                  <div className="content-title">
                    <span className="title-icon">💡</span>
                    <span>AI 分析</span>
                  </div>
                  <div className="feedback-text">{effectiveSentenceAnalysis}</div>
                </div>
              )}
            </>
          )}
        </>
      )}

      {isStandalone && (
        <div className="flashcard-actions-standalone">
          <div className="navigation-buttons">
            <button className="prev-word-btn" onClick={onPrevWord} disabled={!hasPrev || isLoading}>
              ← 上一个
            </button>
            {onRegenerate && (
              <button className="generate-next-btn" onClick={onRegenerate} disabled={isLoading}>
                🔄 重新生成
              </button>
            )}
            <button className="next-word-btn" onClick={onNextWord} disabled={isLoading || !canNavigate}>
              下一个 →
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default VocabularyFlashcard
