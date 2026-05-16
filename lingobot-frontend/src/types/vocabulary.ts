export type LearningMode = 'chat' | 'vocabulary';

export type VocabularyIntent = 'new_word' | 'review' | 'hybrid';

export type VocabularyStatus = 'NEW' | 'LEARNING' | 'REVIEWING' | 'MASTERED';
export type VocabularyEventType = 'NEW_LEARNING' | 'REVIEW' | 'HYBRID';
export type VocabularyTab = 'all' | 'to_review' | 'learning' | 'mastered' | 'difficult';
export type VocabularySortBy = 'last_seen' | 'first_seen' | 'mastery_desc' | 'mastery_asc' | 'seen_count' | 'wrong_count' | 'next_review';

export interface VocabularyStatsDTO {
  totalCount: number;
  newCount: number;
  learningCount: number;
  reviewingCount: number;
  masteredCount: number;
  toReviewCount: number;
  difficultCount: number;
}

export interface UserVocabularyDTO {
  id: number;
  userId: number;
  vocabularyWordId: number;
  latestCardId?: number;
  word?: string;
  phonetic?: string;
  partOfSpeech?: string;
  meaning?: string;
  example?: string;
  exampleTranslation?: string;
  synonyms?: string[];
  category?: string;
  difficulty?: string;
  level?: string;
  status: VocabularyStatus;
  masteryScore: number;
  seenCount: number;
  correctCount: number;
  wrongCount: number;
  firstSeenAt?: string;
  lastSeenAt?: string;
  nextReviewAt?: string;
  neverReview?: boolean;
  lastEventType?: VocabularyEventType;
  createdAt?: string;
  updatedAt?: string;
}

export interface VocabularyCardDTO {
  id: number;
  conversationId: number;
  word: string;
  phonetic: string;
  partOfSpeech?: string;
  meaning?: string;
  example?: string;
  exampleTranslation?: string;
  chineseSentenceForTranslation?: string;
  userEnglishSentence?: string;
  sentenceAnalysis?: string;
  aiFeedback?: string;
  sentenceAnalysisCompleted?: boolean;
  sentenceHasNewWord?: boolean;
  sentenceMeaningMatches?: boolean;
  synonyms?: string[];
  antonyms?: string[];
  category?: string;
  difficulty?: string;
  level?: string;
  position: number;
  userMeaningGuess?: string;
  meaningCheckResult?: string;
  meaningIsCorrect?: boolean;
  meaningCheckCompleted?: boolean;
  isCompleted: boolean;
  createdAt?: string;
  updatedAt?: string;
  hasPrev?: boolean;
  hasNext?: boolean;
  totalCount?: number;
  currentIndex?: number;
  isRegenerated?: boolean;
  isRevealed?: boolean;
  regenerationIndex?: number;
  regeneratedWords?: string[];
}

export interface UpdateLearningStateRequest {
  status?: VocabularyStatus;
  masteryScore?: number;
  nextReviewAt?: string | null;
  neverReview?: boolean;
}

export interface CreateVocabularyCardRequest {
  word: string;
  phonetic?: string;
  meaning?: string;
  example?: string;
  exampleTranslation?: string;
  synonyms?: string[];
  antonyms?: string[];
  level?: string;
}

export interface LearningModeConfig {
  mode: LearningMode;
  label: string;
  icon: string;
  description: string;
  welcomeMessage: string;
  placeholder: string;
  systemPrompt?: string;
}

export const LEARNING_MODES: Record<LearningMode, LearningModeConfig> = {
  chat: {
    mode: 'chat',
    label: '日常对话',
    icon: '💬',
    description: '自由对话，练习英语表达',
    welcomeMessage: '欢迎使用英语聊天！有什么想聊的吗？',
    placeholder: '用英语输入消息，或粘贴图片...',
    systemPrompt: undefined,
  },
  vocabulary: {
    mode: 'vocabulary',
    label: '词汇拓展',
    icon: '📚',
    description: '学习新单词，提升词汇量',
    welcomeMessage: '欢迎使用词汇学习！我会为你展示单词卡片，让我们一起学习英语词汇吧。',
    placeholder: '回答单词意思，或说"查看释义"...',
    systemPrompt: '你是一位专业的英语词汇老师。你的任务是帮助用户学习英语词汇。你可以：1. 生成单词卡片供用户学习 2. 解释单词含义和用法 3. 提供例句帮助理解 4. 展示同义词和反义词 5. 帮助用户记忆单词。请用简洁清晰的中文回复，必要时使用英文示例。',
  },
};
