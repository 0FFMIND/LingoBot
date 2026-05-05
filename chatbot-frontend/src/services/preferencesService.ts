import { httpClient } from './httpClient';
import { 
  UserPreference, 
  UpdateUserPreferenceRequest,
  ModelType,
  VocabularyCategory,
  VocabularyDifficulty,
} from '../types';

export const preferencesService = {
  getPreferences: async (): Promise<UserPreference | null> => {
    try {
      const response = await httpClient.getRaw('/preferences');
      const result = await response.json();
      console.log('📥 获取用户偏好设置:', result);
      return result.data || null;
    } catch (error) {
      console.warn('获取用户偏好设置失败，将使用默认值:', error);
      return null;
    }
  },

  updatePreferences: async (request: UpdateUserPreferenceRequest): Promise<UserPreference> => {
    console.log('📤 更新用户偏好设置:', request);
    const response = await httpClient.putRaw('/preferences', request);
    const result = await response.json();
    console.log('📥 用户偏好设置更新结果:', result);
    return result.data;
  },

  updateVocabularyCategory: async (category: VocabularyCategory): Promise<UserPreference> => {
    const response = await httpClient.putRaw('/preferences/vocabulary/category', {
      vocabularyCategory: category,
    });
    const result = await response.json();
    return result.data;
  },

  updateVocabularyDifficulty: async (difficulty: VocabularyDifficulty): Promise<UserPreference> => {
    const response = await httpClient.putRaw('/preferences/vocabulary/difficulty', {
      vocabularyDifficulty: difficulty,
    });
    const result = await response.json();
    return result.data;
  },

  updateVocabularyModel: async (model: ModelType): Promise<UserPreference> => {
    const response = await httpClient.putRaw('/preferences/vocabulary/model', {
      vocabularyModel: model,
    });
    const result = await response.json();
    return result.data;
  },

  updateChatModel: async (model: ModelType): Promise<UserPreference> => {
    const response = await httpClient.putRaw('/preferences/chat/model', {
      chatModel: model,
    });
    const result = await response.json();
    return result.data;
  },
};
