import { AudioAttachment } from '../types';

export const normalizeAudioUrl = (url: string): string => {
  if (!url) return url;
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  if (url.startsWith('/')) {
    return url;
  }
  return `/${url}`;
};

export interface VocabularyData {
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
}

export interface ParsedMessage {
  text: string;
  audioAttachments: AudioAttachment[];
}

export const parseMessageContent = (content: string): ParsedMessage => {
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

export const isVocabularyJson = (content: string): VocabularyData | null => {
  try {
    const parsed = JSON.parse(content);
    if (parsed.action && parsed.word && parsed.phonetic) {
      return parsed as VocabularyData;
    }
    return null;
  } catch {
    return null;
  }
};

export const cleanPhonetic = (phonetic: string): string => {
  if (!phonetic) return '';
  return phonetic.replace(/^\/+|\/+$/g, '');
};
