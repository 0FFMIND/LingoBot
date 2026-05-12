export type VocabularyCategory = 'cefr' | 'ielts' | 'toefl';

export type VocabularyDifficulty = 
  | 'a1' | 'a2' | 'b1' | 'b2' | 'c1' | 'c2'
  | '4.0-5.0' | '5.5-6.5' | '7.0-8.0' | '8.5-9.0'
  | '60-80' | '81-100' | '101-110' | '111-120';

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
  { difficulty: '4.0-5.0', label: '4.0-5.0', category: 'ielts' },
  { difficulty: '5.5-6.5', label: '5.5-6.5', category: 'ielts' },
  { difficulty: '7.0-8.0', label: '7.0-8.0', category: 'ielts' },
  { difficulty: '8.5-9.0', label: '8.5-9.0', category: 'ielts' },
  { difficulty: '60-80', label: '60-80', category: 'toefl' },
  { difficulty: '81-100', label: '81-100', category: 'toefl' },
  { difficulty: '101-110', label: '101-110', category: 'toefl' },
  { difficulty: '111-120', label: '111-120', category: 'toefl' },
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
