import { useState, useEffect, useCallback, useMemo } from 'react';
import { preferencesService } from '../services';
import { 
  UserPreference, 
  UpdateUserPreferenceRequest,
  ModelType,
  VocabularyCategory,
  VocabularyDifficulty,
  DEFAULT_PREFERENCES,
  MODELS,
} from '../types';

export interface UsePreferencesResult {
  preferences: UserPreference | null;
  loading: boolean;
  error: string | null;
  initialized: boolean;
  
  refreshPreferences: () => Promise<void>;
  updatePreferences: (request: UpdateUserPreferenceRequest) => Promise<UserPreference | null>;
  
  vocabularyCategory: VocabularyCategory;
  vocabularyDifficulty: VocabularyDifficulty;
  vocabularyProvider: string;
  vocabularyModel: string;
  chatProvider: string;
  chatModel: string;
  
  setVocabularyCategory: (category: VocabularyCategory) => Promise<void>;
  setVocabularyDifficulty: (difficulty: VocabularyDifficulty) => Promise<void>;
  setVocabularyModel: (modelType: ModelType) => Promise<void>;
  setChatModel: (modelType: ModelType) => Promise<void>;
}

export function usePreferences(
  isAuthenticated: boolean
): UsePreferencesResult {
  const [preferences, setPreferences] = useState<UserPreference | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [initialized, setInitialized] = useState(false);

  const vocabularyCategory = (preferences?.vocabularyCategory || DEFAULT_PREFERENCES.vocabularyCategory) as VocabularyCategory;
  const vocabularyDifficulty = (preferences?.vocabularyDifficulty || DEFAULT_PREFERENCES.vocabularyDifficulty) as VocabularyDifficulty;
  const vocabularyProvider = preferences?.vocabularyProvider || DEFAULT_PREFERENCES.vocabularyProvider;
  const vocabularyModel = preferences?.vocabularyModel || DEFAULT_PREFERENCES.vocabularyModel;
  const chatProvider = preferences?.chatProvider || DEFAULT_PREFERENCES.chatProvider;
  const chatModel = preferences?.chatModel || DEFAULT_PREFERENCES.chatModel;

  const refreshPreferences = useCallback(async () => {
    if (!isAuthenticated) {
      setInitialized(true);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await preferencesService.getPreferences();
      if (result) {
        setPreferences(result);
        console.log('✅ 用户偏好设置已加载:', result);
      }
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : '加载偏好设置失败';
      setError(errorMessage);
      console.warn('加载用户偏好设置失败:', e);
    } finally {
      setLoading(false);
      setInitialized(true);
    }
  }, [isAuthenticated]);

  const updatePreferences = useCallback(async (request: UpdateUserPreferenceRequest): Promise<UserPreference | null> => {
    if (!isAuthenticated) {
      console.warn('用户未登录，无法更新偏好设置');
      return null;
    }

    try {
      const result = await preferencesService.updatePreferences(request);
      setPreferences(result);
      console.log('✅ 用户偏好设置已更新:', result);
      return result;
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : '更新偏好设置失败';
      setError(errorMessage);
      console.error('更新用户偏好设置失败:', e);
      return null;
    }
  }, [isAuthenticated]);

  const setVocabularyCategory = useCallback(async (category: VocabularyCategory) => {
    if (!isAuthenticated) return;
    const prev = preferences;
    setPreferences(p => p ? { ...p, vocabularyCategory: category } : p);
    try {
      const result = await preferencesService.updateVocabularyCategory(category);
      setPreferences(result);
    } catch (e) {
      setPreferences(prev);
      console.error('更新词汇分类失败:', e);
    }
  }, [isAuthenticated, preferences]);

  const setVocabularyDifficulty = useCallback(async (difficulty: VocabularyDifficulty) => {
    if (!isAuthenticated) return;
    const prev = preferences;
    setPreferences(p => p ? { ...p, vocabularyDifficulty: difficulty } : p);
    try {
      const result = await preferencesService.updateVocabularyDifficulty(difficulty);
      setPreferences(result);
    } catch (e) {
      setPreferences(prev);
      console.error('更新难度级别失败:', e);
    }
  }, [isAuthenticated, preferences]);

  const setVocabularyModel = useCallback(async (modelType: ModelType) => {
    if (!isAuthenticated) return;
    const selected = MODELS.find(m => m.model === modelType);
    if (!selected) return;
    
    const prev = preferences;
    setPreferences(p => p ? { 
      ...p, 
      vocabularyProvider: selected.providerId, 
      vocabularyModel: selected.modelId 
    } : p);
    try {
      const result = await preferencesService.updateVocabularyModel(modelType);
      setPreferences(result);
    } catch (e) {
      setPreferences(prev);
      console.error('更新词汇模型失败:', e);
    }
  }, [isAuthenticated, preferences]);

  const setChatModel = useCallback(async (modelType: ModelType) => {
    if (!isAuthenticated) return;
    const selected = MODELS.find(m => m.model === modelType);
    if (!selected) return;
    
    const prev = preferences;
    setPreferences(p => p ? { 
      ...p, 
      chatProvider: selected.providerId, 
      chatModel: selected.modelId 
    } : p);
    try {
      const result = await preferencesService.updateChatModel(modelType);
      setPreferences(result);
    } catch (e) {
      setPreferences(prev);
      console.error('更新聊天模型失败:', e);
    }
  }, [isAuthenticated, preferences]);

  useEffect(() => {
    if (isAuthenticated && !initialized) {
      refreshPreferences();
    } else if (!isAuthenticated) {
      setPreferences(null);
      setInitialized(true);
    }
  }, [isAuthenticated, initialized, refreshPreferences]);

  return {
    preferences,
    loading,
    error,
    initialized,
    refreshPreferences,
    updatePreferences,
    vocabularyCategory,
    vocabularyDifficulty,
    vocabularyProvider,
    vocabularyModel,
    chatProvider,
    chatModel,
    setVocabularyCategory,
    setVocabularyDifficulty,
    setVocabularyModel,
    setChatModel,
  };
}
