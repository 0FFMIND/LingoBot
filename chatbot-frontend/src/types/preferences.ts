export type VocabularyCategory = 'cefr' | 'ielts' | 'toefl';

export type VocabularyDifficulty = 
  | 'a1' | 'a2' | 'b1' | 'b2' | 'c1' | 'c2'
  | 'beginner' | 'intermediate' | 'advanced' | 'expert';

export type ModelType = 'qwen' | 'xiaomi';

export interface VocabularyCategoryConfig {
  category: VocabularyCategory;
  label: string;
  description: string;
}

export interface VocabularyDifficultyConfig {
  difficulty: VocabularyDifficulty;
  label: string;
  category: VocabularyCategory;
  scoreRange?: string;
}

export interface UserPreference {
  id: number;
  userId: number;
  vocabularyCategory: VocabularyCategory;
  vocabularyDifficulty: VocabularyDifficulty;
  vocabularyModel: ModelType;
  chatModel: ModelType;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateUserPreferenceRequest {
  vocabularyCategory?: VocabularyCategory;
  vocabularyDifficulty?: VocabularyDifficulty;
  vocabularyModel?: ModelType;
  chatModel?: ModelType;
}

export interface ModelConfig {
  model: ModelType;
  label: string;
  icon: string;
  description: string;
  provider: string;
  supportsAudio: boolean;
  supportsImage: boolean;
}

export const VOCABULARY_CATEGORIES: VocabularyCategoryConfig[] = [
  { category: 'cefr', label: 'CEFR 等级', description: '欧洲语言共同参考框架 (A1-C2)' },
  { category: 'ielts', label: 'IELTS', description: '雅思词汇等级' },
  { category: 'toefl', label: 'TOEFL', description: '托福词汇等级' },
];

export const VOCABULARY_DIFFICULTIES: VocabularyDifficultyConfig[] = [
  { difficulty: 'a1', label: 'A1', category: 'cefr' },
  { difficulty: 'a2', label: 'A2', category: 'cefr' },
  { difficulty: 'b1', label: 'B1', category: 'cefr' },
  { difficulty: 'b2', label: 'B2', category: 'cefr' },
  { difficulty: 'c1', label: 'C1', category: 'cefr' },
  { difficulty: 'c2', label: 'C2', category: 'cefr' },
  { difficulty: 'beginner', label: '4.0-5.0 分', category: 'ielts', scoreRange: '初级' },
  { difficulty: 'intermediate', label: '5.5-6.5 分', category: 'ielts', scoreRange: '中级' },
  { difficulty: 'advanced', label: '7.0-8.0 分', category: 'ielts', scoreRange: '高级' },
  { difficulty: 'expert', label: '8.5-9.0 分', category: 'ielts', scoreRange: '专家级' },
  { difficulty: 'beginner', label: '60-80 分', category: 'toefl', scoreRange: '初级' },
  { difficulty: 'intermediate', label: '81-100 分', category: 'toefl', scoreRange: '中级' },
  { difficulty: 'advanced', label: '101-110 分', category: 'toefl', scoreRange: '高级' },
  { difficulty: 'expert', label: '111-120 分', category: 'toefl', scoreRange: '专家级' },
];

export const MODELS: ModelConfig[] = [
  {
    model: 'qwen',
    label: 'Qwen 3.5',
    icon: '🌊',
    description: '阿里通义千问，支持多模态输入',
    provider: 'Alibaba Cloud',
    supportsAudio: true,
    supportsImage: true,
  },
  {
    model: 'xiaomi',
    label: 'MiMo V2 Omni',
    icon: '🔮',
    description: '小米AI模型，支持多模态',
    provider: 'Xiaomi',
    supportsAudio: true,
    supportsImage: true,
  },
];

export const DEFAULT_PREFERENCES: Omit<UserPreference, 'id' | 'userId' | 'createdAt' | 'updatedAt'> = {
  vocabularyCategory: 'cefr',
  vocabularyDifficulty: 'b2',
  vocabularyModel: 'qwen',
  chatModel: 'qwen',
};
