import React, { useState, useEffect, useRef, useCallback } from 'react';
import { 
  ConversationDTO, 
  MessageDTO, 
  AudioAttachment, 
  ModelType, 
  MessageType, 
  LearningMode, 
  LEARNING_MODES,
  VocabularyCategory,
  VocabularyDifficulty,
  VOCABULARY_CATEGORIES,
  VOCABULARY_DIFFICULTIES,
  MODELS,
  VocabularyCardDTO,
} from '../types';
import VoiceRecorder from './VoiceRecorder';

interface ChatWindowProps {
  conversation: ConversationDTO | null;
  messages: MessageDTO[];
  onSendMessage: (content: string) => void;
  onSendAudioMessage?: (audioData: string, audioFormat: string, duration: number) => void;
  onSendImageMessage?: (content: string, imageData: string, imageFormat: string) => void;
  onRetryMessage?: (assistantMessageId: number) => void;
  onRetryWithModel?: (assistantMessageId: number, model: ModelType) => void;
  onEditMessage?: (userMessageId: number, newContent: string) => void;
  onEditAudioMessage?: (userMessageId: number, newContent: string, audioData?: string, audioFormat?: string, audioDuration?: number) => void;
  onSendWithIntent?: (content: string, intent: string, currentWord: string) => void;
  loading: boolean;
  streamingContent?: string;
  disabled?: boolean;
  mode: 'chat' | 'agent';
  onModeChange: (mode: 'chat' | 'agent') => void;
  model: ModelType;
  onModelChange: (model: ModelType) => void;
  agentStatus?: {
    thinking: string;
    toolCalls: Array<{
      toolName: string;
      toolId: string;
      status: 'calling' | 'success' | 'error';
      result?: string;
      error?: string;
    }>;
  };
  learningMode?: LearningMode;
  onLearningModeSelect?: (mode: LearningMode) => void;
  showModeSelector?: boolean;
  vocabularyCategory?: VocabularyCategory;
  onVocabularyCategoryChange?: (category: VocabularyCategory) => void;
  vocabularyDifficulty?: VocabularyDifficulty;
  onVocabularyDifficultyChange?: (difficulty: VocabularyDifficulty) => void;
  vocabularyModel?: ModelType;
  onVocabularyModelChange?: (model: ModelType) => void;
  currentVocabularyCard?: VocabularyCardDTO | null;
  vocabularyCardLoading?: boolean;
  onPrevWord?: () => void;
  onNextWord?: () => void;
  onRegenerateWord?: () => void;
  onSaveVocabularyMeaning?: (cardId: number, meaning: string) => void;
  onSaveVocabularySentence?: (cardId: number, sentence: string) => void;
  onCreateConversationWithMode?: (mode: LearningMode) => void;
}

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
};

const learningFeatures = [
  {
    mode: 'chat' as LearningMode,
    label: '日常对话',
    labelEn: 'Daily Chat',
    icon: '💬',
    color: '#6366f1',
    bgColor: '#eef2ff',
  },
  {
    mode: 'grammar' as LearningMode,
    label: '语法纠错',
    labelEn: 'Grammar Fix',
    icon: '📝',
    color: '#8b5cf6',
    bgColor: '#f5f3ff',
  },
  {
    mode: 'vocabulary' as LearningMode,
    label: '词汇拓展',
    labelEn: 'Vocabulary',
    icon: '📚',
    color: '#10b981',
    bgColor: '#ecfdf5',
  },
  {
    mode: 'writing' as LearningMode,
    label: '发音训练',
    labelEn: 'Pronunciation',
    icon: '🔊',
    color: '#f59e0b',
    bgColor: '#fffbeb',
  },
];

interface ParsedMessage {
  text: string;
  audioAttachments: AudioAttachment[];
}

const normalizeAudioUrl = (url: string): string => {
  if (!url) return url;
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  if (url.startsWith('/')) {
    return url;
  }
  return `/${url}`;
};

const parseMessageContent = (content: string): ParsedMessage => {
  const result: ParsedMessage = {
    text: content,
    audioAttachments: [],
  };

  const urlPattern = /(?:https?:\/\/[^\s"']+|\/[^\s"']*)\.(mp3|wav|ogg|m4a)(\?[^\s"']*)?/gi;
  const matches = content.match(urlPattern);

  if (matches) {
    const uniqueUrls = [...new Set(matches)];
    uniqueUrls.forEach((url, index) => {
      const normalizedUrl = normalizeAudioUrl(url);
      const existingIndex = result.audioAttachments.findIndex(a => a.url === normalizedUrl);
      if (existingIndex === -1) {
        result.audioAttachments.push({
          id: `audio-${index}`,
          url: normalizedUrl,
          title: `音频 ${index + 1}`,
          type: 'audio/mp3',
        });
      }
    });
  }

  try {
    const jsonPattern = /\{(?:[^{}]|\{[^{}]*\})*"audio_url"(?:[^{}]|\{[^{}]*\})*\}/g;
    const jsonMatches = content.match(jsonPattern);

    if (jsonMatches) {
      jsonMatches.forEach((jsonStr, jsonIndex) => {
        try {
          const parsed = JSON.parse(jsonStr) as {
            audio_url?: string;
            bird_name?: string;
            description?: string;
            message?: string;
            duration_seconds?: number;
          };

          if (parsed.audio_url) {
            const normalizedUrl = normalizeAudioUrl(parsed.audio_url);
            const existingIndex = result.audioAttachments.findIndex(a => a.url === normalizedUrl);
            if (existingIndex === -1) {
              result.audioAttachments.push({
                id: `audio-json-${jsonIndex}`,
                url: normalizedUrl,
                title: parsed.bird_name || `音频 ${result.audioAttachments.length + 1}`,
                type: 'audio/mp3',
                description: parsed.description || parsed.message,
                durationSeconds: parsed.duration_seconds,
              });
            }
          }
        } catch (e) {
          console.warn('解析音频JSON失败:', e);
        }
      });
    }
  } catch (e) {
    console.warn('解析消息内容时出错:', e);
  }

  return result;
};

const AudioPlayer: React.FC<{ attachment: AudioAttachment }> = ({ attachment }) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleTimeUpdate = () => {
      if (audio.duration) {
        setProgress((audio.currentTime / audio.duration) * 100);
      }
    };

    const handleLoadedMetadata = () => {
      setDuration(audio.duration);
      setIsLoading(false);
      setError(null);
    };

    const handleEnded = () => {
      setIsPlaying(false);
      setProgress(0);
    };

    const handleError = () => {
      setError('音频加载失败');
      setIsLoading(false);
    };

    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('error', handleError);

    return () => {
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('error', handleError);
    };
  }, [attachment.url]);

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio) return;

    if (isPlaying) {
      audio.pause();
    } else {
      audio.play().catch(e => {
        setError(`播放失败: ${e.message}`);
      });
    }
    setIsPlaying(!isPlaying);
  };

  const handleProgressClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const audio = audioRef.current;
    if (!audio || !audio.duration) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const percent = (e.clientX - rect.left) / rect.width;
    audio.currentTime = percent * audio.duration;
    setProgress(percent * 100);
  };

  const formatTime = (seconds: number): string => {
    if (!seconds || !isFinite(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="audio-player-english">
      <audio ref={audioRef} src={attachment.url} preload="metadata" />
      <div className="audio-player-header-english">
        <div className="audio-icon-english">🔊</div>
        <div className="audio-info-english">
          <div className="audio-title-english">{attachment.title}</div>
          {attachment.description && (
            <div className="audio-desc-english">{attachment.description}</div>
          )}
        </div>
      </div>
      <div className="audio-controls-english">
        <button
          className="play-button-english"
          onClick={togglePlay}
          disabled={isLoading || !!error}
        >
          {isLoading ? '⏳' : (isPlaying ? '⏸️' : '▶️')}
        </button>
        <div className="progress-container-english" onClick={handleProgressClick}>
          <div className="progress-bar-english">
            <div
              className="progress-fill-english"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
        <div className="time-display-english">
          {formatTime((progress / 100) * (duration || 0))} / {formatTime(duration)}
        </div>
      </div>
    </div>
  );
};

const UserAudioMessage: React.FC<{
  audioData: string;
  audioFormat: string;
  audioDuration?: number;
}> = ({ audioData, audioFormat, audioDuration }) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(audioDuration || 0);
  const audioRef = useRef<HTMLAudioElement>(null);

  const audioUrl = `data:audio/${audioFormat};base64,${audioData}`;

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleTimeUpdate = () => {
      if (audio.duration) {
        setProgress((audio.currentTime / audio.duration) * 100);
      }
    };

    const handleLoadedMetadata = () => {
      setDuration(audio.duration);
    };

    const handleEnded = () => {
      setIsPlaying(false);
      setProgress(0);
    };

    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('ended', handleEnded);

    return () => {
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('ended', handleEnded);
    };
  }, [audioUrl]);

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio) return;

    if (isPlaying) {
      audio.pause();
    } else {
      audio.play().catch(e => console.error('播放失败:', e));
    }
    setIsPlaying(!isPlaying);
  };

  const handleProgressClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const audio = audioRef.current;
    if (!audio || !audio.duration) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const percent = (e.clientX - rect.left) / rect.width;
    audio.currentTime = percent * audio.duration;
    setProgress(percent * 100);
  };

  const formatTime = (seconds: number): string => {
    if (!seconds || !isFinite(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="user-audio-message-english">
      <audio ref={audioRef} src={audioUrl} preload="metadata" />
      <div className="audio-bubble-english">
        <button
          className="audio-play-btn-english"
          onClick={togglePlay}
          title={isPlaying ? '暂停' : '播放'}
        >
          {isPlaying ? '⏸️' : '▶️'}
        </button>
        <div
          className="audio-progress-container-english"
          onClick={handleProgressClick}
        >
          <div className="audio-progress-bar-english">
            <div
              className="audio-progress-fill-english"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
        <span className="audio-duration-english">{formatTime(duration)}</span>
      </div>
    </div>
  );
};

const UserImageMessage: React.FC<{
  imageData: string;
  imageFormat?: string;
}> = ({ imageData, imageFormat }) => {
  const imageUrl = `data:image/${imageFormat || 'png'};base64,${imageData}`;

  return (
    <div className="user-image-message-english">
      <img
        src={imageUrl}
        alt="用户上传的图片"
        className="message-image-english"
        style={{ maxWidth: '300px', maxHeight: '300px', borderRadius: '12px', cursor: 'pointer' }}
        onClick={() => window.open(imageUrl, '_blank')}
      />
    </div>
  );
};

interface VocabularyData {
  action: string;
  word?: string;
  phonetic?: string;
  partOfSpeech?: string;
  meaning?: string;
  example?: string;
  exampleTranslation?: string;
  synonyms?: string[];
  antonyms?: string[];
  vocabularyCategory?: string;
  vocabularyDifficulty?: string;
  level?: string;
  message?: string;
  correct?: boolean;
  user_answer?: string;
  correct_answer?: string;
  display_mode?: string;
  sentence?: string;
  current_word?: string;
  feedback?: string;
  hasFeedback?: boolean;
  is_correct?: boolean;
  check_feedback?: string;
  user_meaning?: string;
  correct_meaning?: string;
}

interface VocabularyFlashcardProps {
  data: VocabularyData;
  onSendWithIntent?: (content: string, intent: string, currentWord: string) => void;
  onNextWord?: () => void;
  onPrevWord?: () => void;
  onRegenerate?: () => void;
  onSaveMeaning?: (meaning: string) => void;
  onSaveSentence?: (sentence: string) => void;
  cardId?: number;
  isStandalone?: boolean;
  hasPrev?: boolean;
  isCompleted?: boolean;
  totalCount?: number;
  currentIndex?: number;
  userMeaningGuess?: string;
  userSentence?: string;
  isLoading?: boolean;
  meaningCheckCompleted?: boolean;
  meaningIsCorrect?: boolean;
  meaningCheckResult?: string;
}

const cleanPhonetic = (phonetic: string): string => {
  if (!phonetic) return '';
  return phonetic.replace(/^\/+|\/+$/g, '');
};

const VocabularyFlashcard: React.FC<VocabularyFlashcardProps> = ({
  data,
  onSendWithIntent,
  onNextWord,
  onPrevWord,
  onRegenerate,
  onSaveMeaning,
  onSaveSentence,
  cardId,
  isStandalone = false,
  hasPrev = false,
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
  const hasMeaningCheckResultFromData = 
    data.action === 'check_meaning_accuracy' && 
    data.is_correct !== undefined;

  const effectiveMeaningCheckCompleted = 
    meaningCheckCompleted !== undefined ? meaningCheckCompleted : hasMeaningCheckResultFromData;
  
  const effectiveMeaningIsCorrect = 
    meaningIsCorrect !== undefined ? meaningIsCorrect : (data.is_correct ?? false);
  
  const effectiveMeaningCheckResult = 
    meaningCheckResult !== undefined ? meaningCheckResult : (data.check_feedback ?? '');

  const effectiveUserMeaning = userMeaningGuess || data.user_meaning || '';

  const effectiveMeaning = data.meaning || data.correct_meaning || '';

  const getInitialStep = (): 1 | 2 | 3 => {
    if (isCompleted || data.hasFeedback) return 3;
    if (savedUserSentence) return 3;
    if (effectiveUserMeaning || hasMeaningCheckResultFromData) return 2;
    return 1;
  };

  const [step, setStep] = React.useState<1 | 2 | 3>(getInitialStep);
  const [meaningInput, setMeaningInput] = React.useState(effectiveUserMeaning);
  const [userSentence, setUserSentence] = React.useState(savedUserSentence || '');
  const [isPlayingPronunciation, setIsPlayingPronunciation] = React.useState(false);
  const [audioAvailable, setAudioAvailable] = React.useState<boolean | null>(null);
  const meaningInputRef = React.useRef<HTMLInputElement>(null);
  const sentenceInputRef = React.useRef<HTMLInputElement>(null);
  const pronunciationAudioRef = React.useRef<HTMLAudioElement | null>(null);

  const isPureFeedbackCard =
    (data.display_mode === 'sentence_feedback' ||
    data.action === 'display_sentence_feedback') && !data.hasFeedback;

  const hasMergedFeedback = data.hasFeedback && data.sentence;

  const canNavigate = step === 3 && (data.hasFeedback || isCompleted);

  const getLevelDisplay = () => {
    const { vocabularyCategory, vocabularyDifficulty } = data;
    if (!vocabularyCategory || !vocabularyDifficulty) return null;

    const categoryLabels: Record<string, string> = {
      'cefr': 'CEFR',
      'toefl': 'TOEFL',
    };

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
    };

    const category = categoryLabels[vocabularyCategory.toLowerCase()] || vocabularyCategory;
    const difficulty = difficultyLabels[vocabularyDifficulty.toLowerCase()] || vocabularyDifficulty;

    return {
      label: `${category} ${difficulty}`,
      color: getLevelColor(vocabularyDifficulty),
    };
  };

  const getLevelColor = (difficulty: string): string => {
    const d = difficulty.toLowerCase();
    if (['a1', 'a2', 'beginner'].includes(d)) return '#22c55e';
    if (['b1', 'b2', 'intermediate'].includes(d)) return '#3b82f6';
    if (['c1', 'advanced'].includes(d)) return '#f59e0b';
    if (['c2', 'expert'].includes(d)) return '#ef4444';
    return '#64748b';
  };

  // 新单词出现时重置状态
  React.useEffect(() => {
    const newStep = getInitialStep();
    setStep(newStep);
    setMeaningInput(effectiveUserMeaning);
    setUserSentence(savedUserSentence || '');
  }, [data.word, effectiveUserMeaning, savedUserSentence, isCompleted, data.hasFeedback, data.is_correct, data.action]);

  // 检查音频是否可用
  React.useEffect(() => {
    const word = data.word || data.current_word;
    if (!word) {
      setAudioAvailable(null);
      return;
    }

    setAudioAvailable(null);
    const audioUrl = `/api/tts/word?word=${encodeURIComponent(word)}&voiceType=us`;
    const audio = new Audio();
    
    const handleCanPlayThrough = () => {
      console.log('✅ 音频可用:', word);
      setAudioAvailable(true);
    };
    
    const handleError = (e: Event) => {
      console.warn('❌ 音频不可用:', word, e);
      setAudioAvailable(false);
    };

    audio.addEventListener('canplaythrough', handleCanPlayThrough);
    audio.addEventListener('error', handleError);
    
    audio.src = audioUrl;
    audio.load();

    return () => {
      audio.removeEventListener('canplaythrough', handleCanPlayThrough);
      audio.removeEventListener('error', handleError);
    };
  }, [data.word, data.current_word]);

  // 自动聚焦意思输入框
  React.useEffect(() => {
    if (step === 1 && meaningInputRef.current) {
      meaningInputRef.current.focus();
    }
    if (step === 2 && sentenceInputRef.current) {
      sentenceInputRef.current.focus();
    }
  }, [step]);

  const handleRevealMeaning = () => {
    if (meaningInput.trim() && onSaveMeaning) {
      onSaveMeaning(meaningInput.trim());
    }
    setStep(2);
  };

  const handleSubmitSentence = () => {
    if (!userSentence.trim()) return;
    if (onSaveSentence) {
      onSaveSentence(userSentence.trim());
    }
    setStep(3);
    if (onSendWithIntent && data.word) {
      onSendWithIntent(userSentence, 'make_sentence', data.word);
    }
  };

  const playPronunciation = useCallback(async () => {
    const word = data.word || data.current_word;
    if (!word) return;

    if (isPlayingPronunciation && pronunciationAudioRef.current) {
      pronunciationAudioRef.current.pause();
      pronunciationAudioRef.current = null;
      setIsPlayingPronunciation(false);
      return;
    }

    if (audioAvailable === false) {
      console.warn('音频不可用，无法播放:', word);
      return;
    }

    try {
      setIsPlayingPronunciation(true);
      const audioUrl = `/api/tts/word?word=${encodeURIComponent(word)}&voiceType=us`;
      const audio = new Audio();
      pronunciationAudioRef.current = audio;
      
      audio.onended = () => {
        setIsPlayingPronunciation(false);
        pronunciationAudioRef.current = null;
      };
      
      audio.onerror = (e) => {
        console.error('播放发音失败:', word, e);
        setIsPlayingPronunciation(false);
        setAudioAvailable(false);
        pronunciationAudioRef.current = null;
      };
      
      audio.src = audioUrl;
      await audio.play();
    } catch (error) {
      console.error('播放发音失败:', error);
      setIsPlayingPronunciation(false);
      setAudioAvailable(false);
    }
  }, [data.word, data.current_word, isPlayingPronunciation, audioAvailable]);

  const cardClassName = isStandalone
    ? 'vocabulary-flashcard vocabulary-flashcard-standalone'
    : 'vocabulary-flashcard';

  // 合并后的反馈卡片：在同一个卡片中展示单词信息 + 造句反馈
  if (hasMergedFeedback) {
    const levelDisplay = getLevelDisplay();
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
                <button className="generate-next-btn" onClick={onRegenerate} disabled={isLoading}>
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
    );
  }

  if (isPureFeedbackCard) {
    const levelDisplay = getLevelDisplay();
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
                <button className="generate-next-btn" onClick={onRegenerate} disabled={isLoading}>
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
    );
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
          const levelDisplay = getLevelDisplay();
          if (!levelDisplay) return null;
          return (
            <span
              className="level-badge"
              style={{ backgroundColor: `${levelDisplay.color}20`, color: levelDisplay.color }}
            >
              {levelDisplay.label}
            </span>
          );
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
              onKeyDown={(e) => { if (e.key === 'Enter') handleRevealMeaning(); }}
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
                onKeyDown={(e) => { if (e.key === 'Enter') handleSubmitSentence(); }}
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
              <button className="generate-next-btn" onClick={onRegenerate} disabled={isLoading || !canNavigate}>
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
  );
};

const isVocabularyJson = (content: string): VocabularyData | null => {
  try {
    const parsed = JSON.parse(content);
    if (parsed.action === 'display_sentence_feedback' && parsed.sentence) {
      return parsed as VocabularyData;
    }
    if (parsed.action === 'check_meaning_accuracy' && parsed.word) {
      return parsed as VocabularyData;
    }
    if (parsed.action && parsed.word && parsed.phonetic) {
      return parsed as VocabularyData;
    }
    return null;
  } catch {
    return null;
  }
};

interface MessageContentProps {
  content: string;
  messageType?: MessageType;
  audioData?: string;
  audioFormat?: string;
  audioDuration?: number;
  imageData?: string;
  imageFormat?: string;
  onSendWithIntent?: (content: string, intent: string, currentWord: string) => void;
}

const MessageContent: React.FC<MessageContentProps> = ({ 
  content, 
  messageType, 
  audioData, 
  audioFormat, 
  audioDuration, 
  imageData, 
  imageFormat,
  onSendWithIntent 
}) => {
  if (messageType === 'audio' && audioData) {
    return (
      <div className="message-content-wrapper-english">
        <UserAudioMessage
          audioData={audioData}
          audioFormat={audioFormat || 'webm'}
          audioDuration={audioDuration}
        />
        {content && <div className="message-text-english whitespace-pre-wrap">{content}</div>}
      </div>
    );
  }

  if (messageType === 'image' && imageData) {
    return (
      <div className="message-content-wrapper-english">
        <UserImageMessage
          imageData={imageData}
          imageFormat={imageFormat}
        />
        {content && <div className="message-text-english whitespace-pre-wrap">{content}</div>}
      </div>
    );
  }

  const vocabData = isVocabularyJson(content);
  if (vocabData) {
    return <VocabularyFlashcard data={vocabData} onSendWithIntent={onSendWithIntent} />;
  }

  const parsed = parseMessageContent(content);

  if (parsed.audioAttachments.length === 0) {
    return <div className="message-text-english whitespace-pre-wrap">{content}</div>;
  }

  let textWithoutAudio = content;
  parsed.audioAttachments.forEach(attachment => {
    textWithoutAudio = textWithoutAudio.replace(attachment.url, '');
  });

  const jsonPattern = /\{[\s\S]*?"audio_url"[\s\S]*?\}/g;
  textWithoutAudio = textWithoutAudio.replace(jsonPattern, '').trim();

  return (
    <div className="message-content-wrapper-english">
      {textWithoutAudio && (
        <div className="message-text-english whitespace-pre-wrap">{textWithoutAudio}</div>
      )}
      <div className="audio-attachments-english">
        {parsed.audioAttachments.map(attachment => (
          <AudioPlayer key={attachment.id} attachment={attachment} />
        ))}
      </div>
    </div>
  );
};

const WelcomeMessage: React.FC<{
  username?: string;
  learningConfig: {
    welcomeMessage: string;
    icon: string;
    label: string;
  };
}> = ({ username = '你', learningConfig }) => {
  return (
    <div className="welcome-section-english">
      <div className="welcome-card-english">
        <div className="welcome-avatar-english">
          <div className="avatar-robot-english">
            <span className="robot-emoji">🤖</span>
          </div>
        </div>
        <div className="welcome-content-english">
          <h2 className="welcome-title-english">
            {username ? `Hi, ${username}!` : 'Hi'}
          </h2>
          <p className="welcome-text-english">
            {learningConfig.welcomeMessage}
          </p>
          <p className="welcome-hint-english">
            告诉我你的学习目标或想练习的内容吧，我会为你量身定制学习体验。
          </p>
        </div>
      </div>
    </div>
  );
};

const ChatWindow: React.FC<ChatWindowProps> = ({
  conversation,
  messages,
  onSendMessage,
  onSendAudioMessage,
  onSendImageMessage,
  onRetryMessage,
  onRetryWithModel,
  onEditMessage,
  onEditAudioMessage,
  onSendWithIntent,
  loading,
  streamingContent = '',
  disabled = false,
  model,
  onModelChange,
  agentStatus,
  learningMode = 'chat',
  onLearningModeSelect,
  showModeSelector = false,
  vocabularyCategory = 'cefr',
  onVocabularyCategoryChange,
  vocabularyDifficulty = 'b2',
  onVocabularyDifficultyChange,
  vocabularyModel = 'qwen',
  onVocabularyModelChange,
  currentVocabularyCard,
  vocabularyCardLoading = false,
  onPrevWord,
  onNextWord,
  onRegenerateWord,
  onSaveVocabularyMeaning,
  onSaveVocabularySentence,
  onCreateConversationWithMode,
}) => {
  const [inputValue, setInputValue] = useState('');
  const [editingMessageId, setEditingMessageId] = useState<number | null>(null);
  const [editContent, setEditContent] = useState('');
  const [showModeMenu, setShowModeMenu] = useState(false);
  const [isVoiceRecording, setIsVoiceRecording] = useState(false);
  const [selectedImage, setSelectedImage] = useState<{ data: string; format: string; preview: string } | null>(null);
  const [expandedRetryMessageId, setExpandedRetryMessageId] = useState<number | null>(null);
  const [editingAudioMessageId, setEditingAudioMessageId] = useState<number | null>(null);
  const [editAudioData, setEditAudioData] = useState<string | null>(null);
  const [editAudioFormat, setEditAudioFormat] = useState<string | null>(null);
  const [editAudioDuration, setEditAudioDuration] = useState<number | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const modeMenuRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const learningConfig = LEARNING_MODES[learningMode];

  const renderModeSelector = (isEmptyState: boolean = false) => (
    <div className="mode-selector-section">
      <div className="mode-selector-header">
        <h2 className="mode-selector-title">选择学习模式</h2>
        <p className="mode-selector-subtitle">请选择您想要的学习模式，开始您的英语学习之旅</p>
      </div>
      <div className="mode-selector-grid">
        {learningFeatures.map((feature) => (
          <button
            key={feature.mode}
            className={`mode-selector-card ${learningMode === feature.mode ? 'selected' : ''}`}
            style={
              learningMode === feature.mode
                ? { borderColor: feature.color, backgroundColor: feature.bgColor }
                : {}
            }
            onClick={() => {
              if (isEmptyState && onCreateConversationWithMode && !disabled) {
                onCreateConversationWithMode(feature.mode);
              } else if (onLearningModeSelect && !disabled) {
                onLearningModeSelect(feature.mode);
              }
            }}
            disabled={disabled}
          >
            <span
              className="mode-selector-icon"
              style={{ backgroundColor: feature.bgColor, color: feature.color }}
            >
              {feature.icon}
            </span>
            <span className="mode-selector-label">{feature.label}</span>
            <span className="mode-selector-label-en">{feature.labelEn}</span>
            {learningMode === feature.mode && !isEmptyState && (
              <span className="mode-selector-check">✓</span>
            )}
          </button>
        ))}
      </div>
    </div>
  );

  const filteredDifficulties = VOCABULARY_DIFFICULTIES.filter(
    d => d.category === vocabularyCategory
  );

  const getCategoryLabel = (cat: VocabularyCategory) => {
    const config = VOCABULARY_CATEGORIES.find(c => c.category === cat);
    return config?.label || cat;
  };

  const getDifficultyLabel = (diff: VocabularyDifficulty) => {
    const config = VOCABULARY_DIFFICULTIES.find(d => d.difficulty === diff && d.category === vocabularyCategory);
    return config?.label || diff;
  };

  const renderVocabularyCategorySelector = () => (
    <div className="vocabulary-selector-wrapper">
      <div className="vocabulary-selector-label">划分标准：</div>
      <select
        className="vocabulary-selector-dropdown"
        value={vocabularyCategory}
        onChange={(e) => {
          if (onVocabularyCategoryChange) {
            const newCategory = e.target.value as VocabularyCategory;
            onVocabularyCategoryChange(newCategory);
            const firstDiff = VOCABULARY_DIFFICULTIES.find(d => d.category === newCategory);
            if (firstDiff && onVocabularyDifficultyChange) {
              onVocabularyDifficultyChange(firstDiff.difficulty);
            }
          }
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
  );

  const renderVocabularyDifficultySelector = () => (
    <div className="vocabulary-selector-wrapper">
      <div className="vocabulary-selector-label">难度：</div>
      <select
        className="vocabulary-selector-dropdown"
        value={vocabularyDifficulty}
        onChange={(e) => {
          if (onVocabularyDifficultyChange) {
            onVocabularyDifficultyChange(e.target.value as VocabularyDifficulty);
          }
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
  );

  const renderVocabularyModelSelector = () => (
    <div className="vocabulary-selector-wrapper">
      <div className="vocabulary-selector-label">模型：</div>
      <select
        className="vocabulary-selector-dropdown"
        value={vocabularyModel}
        onChange={(e) => {
          if (onVocabularyModelChange) {
            onVocabularyModelChange(e.target.value as ModelType);
          }
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
  );

  const getLatestVocabularyData = (): VocabularyData | null => {
    let flashcardData: VocabularyData | null = null;
    let feedbackData: VocabularyData | null = null;

    for (let i = messages.length - 1; i >= 0; i--) {
      const message = messages[i];
      if (message.role === 'assistant') {
        const vocabData = isVocabularyJson(message.content);
        if (vocabData) {
          const isFeedback = vocabData.display_mode === 'sentence_feedback' || 
                            vocabData.action === 'display_sentence_feedback';
          
          if (isFeedback && !feedbackData) {
            feedbackData = vocabData;
          } else if (!isFeedback && !flashcardData) {
            flashcardData = vocabData;
          }

          if (flashcardData && feedbackData) {
            const flashcardWord = flashcardData.word?.toLowerCase();
            const feedbackWord = (feedbackData.word || feedbackData.current_word)?.toLowerCase();
            
            if (flashcardWord && feedbackWord && flashcardWord === feedbackWord) {
              return {
                ...flashcardData,
                ...feedbackData,
                word: flashcardData.word || feedbackData.word || feedbackData.current_word,
                phonetic: flashcardData.phonetic || feedbackData.phonetic,
                meaning: flashcardData.meaning || feedbackData.meaning,
                synonyms: flashcardData.synonyms || feedbackData.synonyms || [],
                antonyms: flashcardData.antonyms || feedbackData.antonyms || [],
                level: flashcardData.level || feedbackData.level,
                hasFeedback: true,
              };
            }
          }
        }
      }
    }

    if (feedbackData && !flashcardData) {
      return {
        ...feedbackData,
        word: feedbackData.word || feedbackData.current_word,
        hasFeedback: true,
      };
    }

    return flashcardData;
  };

  const handleNextWord = () => {
    if (onSendWithIntent) {
      onSendWithIntent('', 'next_word', '');
    }
  };

  const handlePrevWord = () => {
    if (onSendWithIntent) {
      onSendWithIntent('', 'prev_word', '');
    }
  };

  const handleRegenerate = () => {
    if (onSendWithIntent) {
      onSendWithIntent('', 'regenerate', '');
    }
  };

  const renderVocabularyView = () => {
    if (currentVocabularyCard) {
      const vocabData: VocabularyData = {
        action: 'new_word',
        word: currentVocabularyCard.word,
        phonetic: currentVocabularyCard.phonetic,
        meaning: currentVocabularyCard.meaning,
        example: currentVocabularyCard.example,
        exampleTranslation: currentVocabularyCard.exampleTranslation,
        synonyms: currentVocabularyCard.synonyms || [],
        antonyms: currentVocabularyCard.antonyms || [],
        vocabularyDifficulty: currentVocabularyCard.level,
        feedback: currentVocabularyCard.aiFeedback || undefined,
        hasFeedback: !!currentVocabularyCard.aiFeedback,
      };

      return (
        <div className="vocabulary-view">
          <VocabularyFlashcard
            data={vocabData}
            onSendWithIntent={onSendWithIntent}
            onPrevWord={onPrevWord}
            onNextWord={onNextWord}
            onRegenerate={onRegenerateWord}
            onSaveMeaning={onSaveVocabularyMeaning ? (meaning) => onSaveVocabularyMeaning(currentVocabularyCard!.id, meaning) : undefined}
            onSaveSentence={onSaveVocabularySentence ? (sentence) => onSaveVocabularySentence(currentVocabularyCard!.id, sentence) : undefined}
            cardId={currentVocabularyCard.id}
            isStandalone={true}
            isLoading={vocabularyCardLoading || loading}
            hasPrev={currentVocabularyCard.hasPrev || false}
            isCompleted={currentVocabularyCard.isCompleted || false}
            totalCount={currentVocabularyCard.totalCount ?? 1}
            currentIndex={currentVocabularyCard.currentIndex ?? 0}
            userMeaningGuess={currentVocabularyCard.userMeaningGuess}
            userSentence={currentVocabularyCard.userSentence}
            meaningCheckCompleted={currentVocabularyCard.meaningCheckCompleted}
            meaningIsCorrect={currentVocabularyCard.meaningIsCorrect}
            meaningCheckResult={currentVocabularyCard.meaningCheckResult}
          />
        </div>
      );
    }

    if (vocabularyCardLoading) {
      return (
        <div className="vocabulary-view-empty">
          <div className="empty-icon">📚</div>
          <h3>准备开始词汇学习</h3>
          <p className="typing-english">单词卡片即将加载...</p>
        </div>
      );
    }

    const vocabData = getLatestVocabularyData();

    if (!vocabData) {
      return (
        <div className="vocabulary-view-empty">
          <div className="empty-icon">📚</div>
          <h3>准备开始词汇学习</h3>
          <p>单词卡片即将加载...</p>
        </div>
      );
    }

    return (
      <div className="vocabulary-view">
        <VocabularyFlashcard
          data={vocabData}
          onSendWithIntent={onSendWithIntent}
          onPrevWord={onPrevWord}
          onNextWord={onNextWord}
          onRegenerate={onRegenerateWord}
          isStandalone={true}
          isLoading={vocabularyCardLoading || loading}
        />
      </div>
    );
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (modeMenuRef.current && !modeMenuRef.current.contains(event.target as Node)) {
        setShowModeMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingContent, agentStatus]);

  const handleSend = useCallback(() => {
    if (selectedImage && onSendImageMessage) {
      onSendImageMessage(inputValue.trim(), selectedImage.data, selectedImage.format);
      setSelectedImage(null);
      setInputValue('');
    } else if (inputValue.trim() && !loading && !disabled && !isVoiceRecording) {
      onSendMessage(inputValue.trim());
      setInputValue('');
    }
  }, [inputValue, selectedImage, onSendImageMessage, onSendMessage, loading, disabled, isVoiceRecording]);

  const handleAudioRecordingComplete = useCallback((audioData: string, audioFormat: string, duration: number) => {
    if (onSendAudioMessage && !loading && !disabled) {
      onSendAudioMessage(audioData, audioFormat, duration);
    }
    setIsVoiceRecording(false);
  }, [onSendAudioMessage, loading, disabled]);

  const handleAudioCancel = useCallback(() => {
    setIsVoiceRecording(false);
  }, []);

  const toggleVoiceRecorder = useCallback(() => {
    if (loading || disabled) return;
    if (!isVoiceRecording) {
      setIsVoiceRecording(true);
    }
  }, [loading, disabled, isVoiceRecording]);

  const handleImageSelect = useCallback((file: File) => {
    if (!file.type.startsWith('image/')) {
      alert('请选择图片文件');
      return;
    }

    const maxSize = 10 * 1024 * 1024;
    if (file.size > maxSize) {
      alert('图片大小不能超过10MB');
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      const result = e.target?.result as string;
      const base64Data = result.split(',')[1];
      const format = file.type.split('/')[1] || 'png';

      setSelectedImage({
        data: base64Data,
        format,
        preview: result,
      });
    };
    reader.readAsDataURL(file);
  }, []);

  const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      handleImageSelect(file);
    }
    e.target.value = '';
  }, [handleImageSelect]);

  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    const items = e.clipboardData.items;
    for (let i = 0; i < items.length; i++) {
      if (items[i].type.startsWith('image/')) {
        const file = items[i].getAsFile();
        if (file) {
          handleImageSelect(file);
        }
        break;
      }
    }
  }, [handleImageSelect]);

  const removeSelectedImage = useCallback(() => {
    setSelectedImage(null);
  }, []);

  const getPreviousUserMessage = (assistantMessageId: number): MessageDTO | null => {
    const index = messages.findIndex(m => m.id === assistantMessageId);
    if (index > 0) {
      const prevMessage = messages[index - 1];
      if (prevMessage.role === 'user') {
        return prevMessage;
      }
    }
    return null;
  };

  const isModelDisabledForMessage = (modelType: ModelType, message: MessageDTO): boolean => {
    const config = modelConfig[modelType];
    if (message.messageType === 'audio' && !config.supportsAudio) {
      return true;
    }
    return false;
  };

  const startEdit = (message: MessageDTO) => {
    if (loading || disabled) return;
    setEditingMessageId(message.id);
    setEditContent(message.content);
  };

  const startAudioEdit = (message: MessageDTO) => {
    if (loading || disabled) return;
    setEditingAudioMessageId(message.id);
    setEditContent(message.content);
    setEditAudioData(null);
    setEditAudioFormat(null);
    setEditAudioDuration(null);
  };

  const cancelEdit = () => {
    setEditingMessageId(null);
    setEditContent('');
  };

  const cancelAudioEdit = () => {
    setEditingAudioMessageId(null);
    setEditContent('');
    setEditAudioData(null);
    setEditAudioFormat(null);
    setEditAudioDuration(null);
  };

  const saveEdit = (message: MessageDTO) => {
    if (!editContent.trim()) {
      alert('消息内容不能为空');
      return;
    }
    if (editContent.trim() === message.content.trim()) {
      alert('消息内容没有变化');
      setEditingMessageId(null);
      setEditContent('');
      return;
    }
    if (onEditMessage && !disabled) {
      onEditMessage(message.id, editContent.trim());
    }
    setEditingMessageId(null);
    setEditContent('');
  };

  const handleEditAudioRecordingComplete = useCallback((audioData: string, audioFormat: string, duration: number) => {
    setEditAudioData(audioData);
    setEditAudioFormat(audioFormat);
    setEditAudioDuration(duration);
  }, []);

  const handleEditAudioCancel = useCallback(() => {
    setEditAudioData(null);
    setEditAudioFormat(null);
    setEditAudioDuration(null);
  }, []);

  const saveAudioEdit = (message: MessageDTO) => {
    if (onEditAudioMessage && !disabled) {
      const hasNewAudio = editAudioData !== null;
      const hasContentChange = editContent.trim() !== message.content.trim();

      if (!hasNewAudio && !hasContentChange) {
        alert('消息内容没有变化');
        cancelAudioEdit();
        return;
      }

      onEditAudioMessage(
        message.id,
        editContent.trim(),
        editAudioData || undefined,
        editAudioFormat || undefined,
        editAudioDuration || undefined
      );
    }
    cancelAudioEdit();
  };

  const toggleRetryMenu = (messageId: number) => {
    if (expandedRetryMessageId === messageId) {
      setExpandedRetryMessageId(null);
    } else {
      setExpandedRetryMessageId(messageId);
    }
  };

  const handleRetryWithModel = (assistantMessageId: number, modelType: ModelType) => {
    if (onRetryWithModel && !disabled) {
      onRetryWithModel(assistantMessageId, modelType);
    }
    setExpandedRetryMessageId(null);
  };

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const renderStreamingMessage = () => {
    if (!loading) return null;

    const displayContent = streamingContent || '';
    const hasContent = !!displayContent;
    const isThinking = agentStatus?.thinking && !hasContent;
    const hasToolCalls = agentStatus?.toolCalls && agentStatus.toolCalls.length > 0;

    const getToolStatusIcon = (status: string) => {
      switch (status) {
        case 'calling': return '⏳';
        case 'success': return '✅';
        case 'error': return '❌';
        default: return '⏳';
      }
    };

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
    );
  };

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
              if (onLearningModeSelect && !disabled) {
                onLearningModeSelect(feature.mode);
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
  );

  if (!conversation) {
    if (disabled) {
      const defaultLearningConfig = LEARNING_MODES['chat'];
      return (
        <div className={`chat-window-english empty disabled`}>
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
      );
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
        {renderModeSelector(true)}
      </div>
    );
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
        {renderModeSelector()}
      </div>
    );
  }

  if (learningMode === 'vocabulary' && conversation) {
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

        <div className="vocabulary-main-area">
          {renderVocabularyView()}
        </div>
      </div>
    );
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
            const isUserMessage = message.role === 'user';
            const isAssistantMessage = message.role === 'assistant';
            const isAudioMessage = message.messageType === 'audio';
            const prevUserMessage = isAssistantMessage ? getPreviousUserMessage(message.id) : null;
            const isEditing = editingMessageId === message.id;
            const isEditingAudio = editingAudioMessageId === message.id;

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
                          onSendWithIntent={onSendWithIntent}
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

                      {isUserMessage && (onEditMessage || onEditAudioMessage) && !loading && !disabled && (
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

                      {isAssistantMessage && (onRetryMessage || onRetryWithModel) && !loading && !disabled && (
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
                                    const isDisabled = prevUserMessage ? isModelDisabledForMessage(m, prevUserMessage) : false;
                                    const config = modelConfig[m];

                                    return (
                                      <button
                                        key={m}
                                        className={`model-selector-option-english ${isDisabled ? 'disabled' : ''} ${model === m ? 'active' : ''}`}
                                        onClick={() => {
                                          if (!isDisabled) {
                                            handleRetryWithModel(message.id, m);
                                          }
                                        }}
                                        disabled={isDisabled}
                                        title={isDisabled ? config.disabledReason : ''}
                                      >
                                        <span className="model-icon-english">{config.icon}</span>
                                        <div className="model-info-english">
                                          <span className="model-name-english">{config.label}</span>
                                          <span className="model-desc-english">{config.description}</span>
                                        </div>
                                        {isDisabled && (
                                          <span className="disabled-reason-english">{config.disabledReason}</span>
                                        )}
                                        {model === m && !isDisabled && <span className="check-mark-english">✓</span>}
                                      </button>
                                    );
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
            );
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
                        onModelChange(m);
                        setShowModeMenu(false);
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
  );
};

export default ChatWindow;
