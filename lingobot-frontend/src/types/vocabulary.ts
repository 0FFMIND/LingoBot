export type LearningMode = 'chat' | 'vocabulary' | 'writing' | 'grammar' | 'listening' | 'speaking';

export type VocabularyStatus = 'NEW' | 'LEARNING' | 'REVIEWING' | 'MASTERED' | 'IGNORED';
export type VocabularyEventType = 'NEW_LEARNING' | 'REVIEW' | 'HYBRID';
export type VocabularyTab = 'all' | 'to_review' | 'learning' | 'mastered' | 'difficult';
export type VocabularySortBy = 'last_seen' | 'first_seen' | 'mastery_desc' | 'mastery_asc' | 'seen_count' | 'wrong_count' | 'next_review';

export interface VocabularyStatsDTO {
  totalCount: number;
  newCount: number;
  learningCount: number;
  reviewingCount: number;
  masteredCount: number;
  ignoredCount: number;
  toReviewCount: number;
}

export interface UserVocabularyDTO {
  id: number;
  userId: number;
  vocabularyWordId: number;
  word?: string;
  phonetic?: string;
  meaning?: string;
  level?: string;
  status: VocabularyStatus;
  masteryScore: number;
  seenCount: number;
  correctCount: number;
  wrongCount: number;
  firstSeenAt?: string;
  lastSeenAt?: string;
  nextReviewAt?: string;
  lastEventType?: VocabularyEventType;
  createdAt?: string;
  updatedAt?: string;
}

export interface VocabularyCardDTO {
  id: number;
  conversationId: number;
  word: string;
  phonetic: string;
  meaning?: string;
  example?: string;
  exampleTranslation?: string;
  synonyms?: string[];
  antonyms?: string[];
  level?: string;
  position: number;
  userMeaningGuess?: string;
  meaningCheckResult?: string;
  meaningIsCorrect?: boolean;
  meaningCheckCompleted?: boolean;
  userSentence?: string;
  aiFeedback?: string;
  isCompleted: boolean;
  createdAt?: string;
  updatedAt?: string;
  hasPrev?: boolean;
  hasNext?: boolean;
  totalCount?: number;
  currentIndex?: number;
  isRegenerated?: boolean;
  regenerationIndex?: number;
  regeneratedWords?: string[];
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
    label: '普通聊天',
    icon: '💬',
    description: '自由对话，练习英语表达',
    welcomeMessage: '欢迎使用英语聊天！有什么想聊的吗？',
    placeholder: '用英语输入消息，或粘贴图片...',
    systemPrompt: undefined,
  },
  vocabulary: {
    mode: 'vocabulary',
    label: '词汇学习',
    icon: '📚',
    description: '学习新单词，提升词汇量',
    welcomeMessage: '欢迎使用词汇学习！我会为你展示单词卡片，让我们一起学习英语词汇吧。',
    placeholder: '回答单词意思，或说"查看释义"...',
    systemPrompt: '你是一位专业的英语词汇老师。你的任务是帮助用户学习英语词汇。你可以：1. 生成单词卡片供用户学习 2. 解释单词含义和用法 3. 提供例句帮助理解 4. 展示同义词和反义词 5. 帮助用户记忆单词。请用简洁清晰的中文回复，必要时使用英文示例。',
  },
  writing: {
    mode: 'writing',
    label: '写作批改',
    icon: '✍️',
    description: '批改作文，提升写作能力',
    welcomeMessage: '欢迎使用写作批改功能！请粘贴你的英语作文，我会帮你：1. 检查语法错误 2. 改进用词和句式 3. 提供写作建议 4. 给出评分和评语。',
    placeholder: '粘贴你的英语作文...',
    systemPrompt: '你是一位专业的英语写作老师。你的任务是批改用户的英语作文。请按照以下格式给出反馈：1. 整体评价和评分（满分100）2. 语法错误修正（列出具体错误和正确用法）3. 用词改进建议 4. 句式优化建议 5. 写作技巧总结。请用中文详细解释，让用户明白如何改进。',
  },
  grammar: {
    mode: 'grammar',
    label: '语法练习',
    icon: '📝',
    description: '学习语法规则，做练习题',
    welcomeMessage: '欢迎使用语法练习！我可以帮你：1. 解释语法规则 2. 提供练习题 3. 分析你的错误 4. 针对性练习。你想从哪个语法点开始？',
    placeholder: '询问语法问题，或要求练习题...',
    systemPrompt: '你是一位专业的英语语法老师。你的任务是帮助用户学习和练习英语语法。你可以：1. 用简单易懂的语言解释语法规则 2. 提供适合用户水平的练习题 3. 详细分析用户的错误 4. 提供记忆技巧。请用中文解释，给出清晰的英文示例。',
  },
  listening: {
    mode: 'listening',
    label: '听力练习',
    icon: '🎧',
    description: '练习听力，提升理解能力',
    welcomeMessage: '欢迎使用听力练习！我可以帮你：1. 提供听力材料和练习 2. 解释听力技巧 3. 分析听不懂的原因 4. 听写练习。你想怎么开始？',
    placeholder: '询问听力技巧，或要求听写练习...',
    systemPrompt: '你是一位专业的英语听力老师。你的任务是帮助用户提升英语听力能力。你可以：1. 提供听力练习建议 2. 解释听力技巧和策略 3. 分析听力材料中的难点 4. 设计听写练习 5. 推荐适合的听力资源。请用中文详细解释。',
  },
  speaking: {
    mode: 'speaking',
    label: '口语练习',
    icon: '🗣️',
    description: '练习口语，提升表达能力',
    welcomeMessage: '欢迎使用口语练习！我可以帮你：1. 角色扮演对话 2. 提供话题和讨论 3. 纠正发音和语调 4. 提供口语表达建议 5. 模拟场景练习。准备好了就开始吧！',
    placeholder: '选择话题，或开始角色扮演...',
    systemPrompt: '你是一位专业的英语口语老师和对话伙伴。你的任务是帮助用户练习英语口语。你可以：1. 进行角色扮演对话 2. 提供讨论话题 3. 温和地纠正用户的语法和用词错误 4. 提供更自然的表达方式 5. 鼓励用户多说。请用英文和用户对话，但在纠正和解释时使用中文。',
  },
};
