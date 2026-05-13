import { ModelType, LearningMode } from '../types';

export interface ModelConfig {
  label: string;
  icon: string;
  description: string;
  supportsImage: boolean;
  supportsAudio: boolean;
  disabledReason: string;
}

export const modelConfig: Record<ModelType, ModelConfig> = {
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

export interface LearningFeature {
  mode: LearningMode;
  label: string;
  labelEn: string;
  icon: string;
  color: string;
  bgColor: string;
}

export const learningFeatures: LearningFeature[] = [
  {
    mode: 'chat',
    label: '日常对话',
    labelEn: 'Daily Chat',
    icon: '💬',
    color: '#6366f1',
    bgColor: '#eef2ff',
  },
  {
    mode: 'vocabulary',
    label: '词汇拓展',
    labelEn: 'Vocabulary',
    icon: '📚',
    color: '#10b981',
    bgColor: '#ecfdf5',
  },
];
