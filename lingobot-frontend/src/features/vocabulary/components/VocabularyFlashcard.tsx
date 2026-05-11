import React, { useCallback } from 'react'

export interface VocabularyData {
  action: string
  word?: string
  phonetic?: string
  partOfSpeech?: string
  meaning?: string
  example?: string
  exampleTranslation?: string
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
  sentence?: string
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
  onSaveSentence?: (sentence: string) => void
  cardId?: number
  isStandalone?: boolean
  hasPrev?: boolean
  hasNext?: boolean
  isCompleted?: boolean
  totalCount?: number
  currentIndex?: number
  userMeaningGuess?: string
  userSentence?: string
  isLoading?: boolean
  meaningCheckCompleted?: boolean
  meaningIsCorrect?: boolean
  meaningCheckResult?: string
}

const cleanPhonetic = (phonetic: string): string => {
  if (!phonetic) return ''
  return phonetic.replace(/^\/+|\/+$/g, '')
}

export const isVocabularyJson = (content: string): VocabularyData | null => {
  try {
    const parsed = JSON.parse(content)
    if (parsed.action === 'display_sentence_feedback' && parsed.sentence) {
      return parsed as VocabularyData
    }
    if (parsed.action === 'check_meaning_accuracy' && parsed.word) {
      return parsed as VocabularyData
    }
    if (parsed.action && parsed.word && parsed.phonetic) {
      return parsed as VocabularyData
    }
    return null
  } catch {
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
  onSaveSentence,
  cardId: _cardId,
  isStandalone = false,
  hasPrev = false,
  hasNext = false,
  isCompleted = false,
  totalCount,
  currentIndex,
  userMeaningGuess,
  userSentence: savedUserSentence,
  isLoading = false,
  meaningCheckCompleted,
  meaningIsCorrect,
  meaningCheckResult,
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

  const effectiveUserMeaning = userMeaningGuess || data.user_meaning || ''

  const effectiveMeaning = data.meaning || data.correct_meaning || ''

  const getInitialStep = (): 1 | 2 | 3 => {
    if (isCompleted || data.hasFeedback) return 3
    if (savedUserSentence) return 3
    if (effectiveUserMeaning || hasMeaningCheckResultFromData) return 2
    return 1
  }

  const [step, setStep] = React.useState<1 | 2 | 3>(getInitialStep)
  const [meaningInput, setMeaningInput] = React.useState(effectiveUserMeaning)
  const [userSentence, setUserSentence] = React.useState(savedUserSentence || '')
  const [isPlayingPronunciation, setIsPlayingPronunciation] = React.useState(false)
  const [audioAvailable, setAudioAvailable] = React.useState<boolean | null>(null)
  const meaningInputRef = React.useRef<HTMLInputElement>(null)
  const sentenceInputRef = React.useRef<HTMLInputElement>(null)
  const pronunciationAudioRef = React.useRef<HTMLAudioElement | null>(null)

  const isPureFeedbackCard =
    (data.display_mode === 'sentence_feedback' ||
    data.action === 'display_sentence_feedback') && !data.hasFeedback

  const hasMergedFeedback = data.hasFeedback && data.sentence

  const canNavigate = step === 3 && (data.hasFeedback || isCompleted)

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
      'beginner': '初级',
      'intermediate': '中级',
      'advanced': '高级',
      'expert': '专家级',
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
    if (['a1', 'a2', 'beginner'].includes(d)) return '#22c55e'
    if (['b1', 'b2', 'intermediate'].includes(d)) return '#3b82f6'
    if (['c1', 'advanced'].includes(d)) return '#f59e0b'
    if (['c2', 'expert'].includes(d)) return '#ef4444'
    return '#64748b'
  }

  // 新单词出现时重置状态
  React.useEffect(() => {
    const newStep = getInitialStep()
    setStep(newStep)
    setMeaningInput(effectiveUserMeaning)
    setUserSentence(savedUserSentence || '')
  }, [data.word, effectiveUserMeaning, savedUserSentence, isCompleted, data.hasFeedback, data.is_correct, data.action])

  // 检查音频是否可用
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

  // 自动聚焦意思输入框
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

  const handleSubmitSentence = () => {
    if (!userSentence.trim()) return
    if (onSaveSentence) {
      onSaveSentence(userSentence.trim())
    }
    setStep(3)
    if (onSendWithIntent && data.word) {
      onSendWithIntent(userSentence, 'make_sentence', data.word)
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

  // 合并后的反馈卡片：在同一个卡片中展示单词信息 + 造句反馈
  if (hasMergedFeedback) {
    const levelDisplay = getLevelDisplay()
    return (
      <div className={cardClassName}>
        <div className="flashcard-header">
          <div className="flashcard-icon">📚</div>
          {levelDisplay && (
            <span
              className="level-badge"
              style={{ backgroundColor: `${levelDisplay.color}20`, color: levelDisplay.color }}
            >
              {levelDisplay.label}
            </span>
          )}
        </div>
        <div className="flashcard-word-section">
          <div className="flashcard-word">{data.word || data.current_word}</div>
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
              <span className="title-icon">🔄</span>
              <span>同义词</span>
            </div>
            <div className="synonyms-list">
              {data.synonyms.map((synonym, index) => (
                <span key={index} className="synonym-tag">{synonym}</span>
              ))}
            </div>
          </div>
        )}

        <div className="flashcard-content sentence-content expanded">
          <div className="content-title">
            <span className="title-icon">📝</span>
            <span>你的造句</span>
          </div>
          <div className="sentence-text">"{data.sentence}"</div>
        </div>

        {data.feedback && (
          <div className="flashcard-content feedback-content expanded">
            <div className="content-title">
              <span className="title-icon">💡</span>
              <span>AI 反馈</span>
            </div>
            <div className="feedback-text">{data.feedback}</div>
          </div>
        )}

        {data.example && (
          <div className="flashcard-content example-content expanded">
            <div className="content-title">
              <span className="title-icon">✍️</span>
              <span>参考例句</span>
            </div>
            <div className="example-text">"{data.example}"</div>
            {data.exampleTranslation && (
              <div className="example-translation">
                <span className="translation-label">翻译：</span>
                <span>{data.exampleTranslation}</span>
              </div>
            )}
          </div>
        )}

        {isStandalone && (
          <div className="flashcard-actions-standalone">
            {totalCount !== undefined && currentIndex !== undefined && (
              <div className="card-progress">
                <span className="progress-text">第 {currentIndex + 1} / {totalCount} 个单词</span>
              </div>
            )}
            <div className="navigation-buttons">
              <button className="prev-word-btn" onClick={onPrevWord} disabled={!hasPrev || isLoading}>
                ← 上一个
              </button>
              {onRegenerate && (
                <button className="generate-next-btn" onClick={onRegenerate} disabled={isLoading || !isLastWord}>
                  {isLoading ? '⏳ 生成中...' : '🔄 重新生成'}
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

  if (isPureFeedbackCard) {
    const levelDisplay = getLevelDisplay()
    return (
      <div className={cardClassName}>
        <div className="flashcard-header">
          <div className="flashcard-icon">📚</div>
          {levelDisplay && (
            <span
              className="level-badge"
              style={{ backgroundColor: `${levelDisplay.color}20`, color: levelDisplay.color }}
            >
              {levelDisplay.label}
            </span>
          )}
        </div>
        <div className="flashcard-word-section">
          <div className="flashcard-word">{data.word || data.current_word}</div>
          <div className="flashcard-phonetic-wrapper">
            {data.partOfSpeech && <span className="flashcard-pos">{data.partOfSpeech}</span>}
            {data.phonetic && <span className="flashcard-phonetic">/{cleanPhonetic(data.phonetic)}/</span>}
          </div>
        </div>
        <div className="flashcard-content sentence-content expanded">
          <div className="content-title">
            <span className="title-icon">📝</span>
            <span>你的造句</span>
          </div>
          <div className="sentence-text">"{data.sentence}"</div>
        </div>
        {data.feedback && (
          <div className="flashcard-content feedback-content expanded">
            <div className="content-title">
              <span className="title-icon">💡</span>
              <span>AI 反馈</span>
            </div>
            <div className="feedback-text">{data.feedback}</div>
          </div>
        )}
        {data.example && (
          <div className="flashcard-content example-content expanded">
            <div className="content-title">
              <span className="title-icon">✍️</span>
              <span>参考例句</span>
            </div>
            <div className="example-text">"{data.example}"</div>
            {data.exampleTranslation && (
              <div className="example-translation">
                <span className="translation-label">翻译：</span>
                <span>{data.exampleTranslation}</span>
              </div>
            )}
          </div>
        )}
        {isStandalone && (
          <div className="flashcard-actions-standalone">
            {totalCount !== undefined && currentIndex !== undefined && (
              <div className="card-progress">
                <span className="progress-text">第 {currentIndex + 1} / {totalCount} 个单词</span>
              </div>
            )}
            <div className="navigation-buttons">
              <button className="prev-word-btn" onClick={onPrevWord} disabled={!hasPrev || isLoading}>
                ← 上一个
              </button>
              {onRegenerate && (
                <button className="generate-next-btn" onClick={onRegenerate} disabled={isLoading || !isLastWord}>
                  {isLoading ? '⏳ 生成中...' : '🔄 重新生成'}
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

  return (
    <div className={cardClassName}>
      <div className="flashcard-header">
        <div className="flashcard-icon">📚</div>
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

      {/* 单词、词性和音标始终显示 */}
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

      {/* 第一步：猜意思 */}
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

      {/* 第二步：显示释义+同义词，造句 */}
      {step === 2 && (
        <>
          {data.meaning && (
            <div className="flashcard-content meaning-content expanded">
              <div className="content-title">
                <span className="title-icon">📖</span>
                <span>释义</span>
              </div>
              <div className="meaning-text">{data.meaning}</div>
            </div>
          )}
          {data.synonyms && data.synonyms.length > 0 && (
            <div className="flashcard-content synonyms-content expanded">
              <div className="content-title">
                <span className="title-icon">🔄</span>
                <span>同义词</span>
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
                <span className="meaning-check-icon">{effectiveMeaningIsCorrect ? '✓' : '✗'}</span>
                <span className="meaning-check-label">{effectiveMeaningIsCorrect ? '理解正确！' : '理解有偏差'}</span>
                {effectiveMeaningCheckResult && <p className="meaning-check-feedback">{effectiveMeaningCheckResult}</p>}
              </div>
            ) : (
              <div className="meaning-check-pending">
                <span className="typing-english">⏳ 正在检查你的理解...</span>
              </div>
            )
          )}
          <div className="sentence-input-section">
            <div className="sentence-input-label">📝 现在用这个单词造一个英文句子吧！</div>
            <div className="sentence-input-row">
              <input
                ref={sentenceInputRef}
                type="text"
                className="sentence-input"
                placeholder="请输入一个包含该单词的完整句子..."
                value={userSentence}
                onChange={(e) => setUserSentence(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') handleSubmitSentence() }}
              />
              <button
                className="submit-sentence-btn"
                onClick={handleSubmitSentence}
                disabled={!userSentence.trim()}
              >
                提交造句
              </button>
            </div>
          </div>
        </>
      )}

      {step === 3 && (
        <>
          {data.meaning && (
            <div className="flashcard-content meaning-content expanded">
              <div className="content-title">
                <span className="title-icon">📖</span>
                <span>释义</span>
              </div>
              <div className="meaning-text">{data.meaning}</div>
            </div>
          )}
          {data.synonyms && data.synonyms.length > 0 && (
            <div className="flashcard-content synonyms-content expanded">
              <div className="content-title">
                <span className="title-icon">🔄</span>
                <span>同义词</span>
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
                <span className="meaning-check-icon">{effectiveMeaningIsCorrect ? '✓' : '✗'}</span>
                <span className="meaning-check-label">{effectiveMeaningIsCorrect ? '理解正确！' : '理解有偏差'}</span>
                {effectiveMeaningCheckResult && <p className="meaning-check-feedback">{effectiveMeaningCheckResult}</p>}
              </div>
            ) : (
              <div className="meaning-check-pending">
                <span className="typing-english">⏳ 正在检查你的理解...</span>
              </div>
            )
          )}

          {!data.hasFeedback && !isCompleted ? (
            <div className="sentence-waiting">
              <span className="typing-english">⏳ 正在分析你的句子...</span>
            </div>
          ) : (
            <>
              {userSentence && (
                <div className="flashcard-content sentence-content expanded">
                  <div className="content-title">
                    <span className="title-icon">📝</span>
                    <span>你的造句</span>
                  </div>
                  <div className="sentence-text">"{userSentence}"</div>
                </div>
              )}
              {data.feedback && (
                <div className="flashcard-content feedback-content expanded">
                  <div className="content-title">
                    <span className="title-icon">💡</span>
                    <span>AI 反馈</span>
                  </div>
                  <div className="feedback-text">{data.feedback}</div>
                </div>
              )}
              {data.example && (
                <div className="flashcard-content example-content expanded">
                  <div className="content-title">
                    <span className="title-icon">✍️</span>
                    <span>参考例句</span>
                  </div>
                  <div className="example-text">"{data.example}"</div>
                  {data.exampleTranslation && (
                    <div className="example-translation">
                      <span className="translation-label">翻译：</span>
                      <span>{data.exampleTranslation}</span>
                    </div>
                  )}
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
