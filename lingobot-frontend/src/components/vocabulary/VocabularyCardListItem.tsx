import React from 'react';
import { UserVocabularyDTO } from '../../types';

interface VocabularyCardListItemProps {
  item: UserVocabularyDTO;
  onPlayAudio?: (word: string) => void;
  onEdit?: (item: UserVocabularyDTO) => void;
  onDelete?: (item: UserVocabularyDTO) => void;
}

const statusLabels: Record<string, string> = {
  NEW: '新词',
  LEARNING: '学习中',
  REVIEWING: '复习中',
  MASTERED: '已掌握',
};

const statusClassNames: Record<string, string> = {
  NEW: 'new',
  LEARNING: 'learning',
  REVIEWING: 'reviewing',
  MASTERED: 'mastered',
};

const VocabularyCardListItem: React.FC<VocabularyCardListItemProps> = ({
  item,
  onPlayAudio,
  onEdit,
  onDelete,
}) => {
  const masteryPercent = Math.max(0, Math.min(100, Math.round((item.masteryScore || 0) * 100)));

  const formatNextReview = (dateStr?: string, neverReview?: boolean) => {
    if (neverReview) return '永不';
    if (!dateStr) return '未设置';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = date.getTime() - now.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    const time = `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;

    if (diffMs <= 0) return '现在';
    if (diffDays <= 1) return `明天 ${time}`;
    if (diffDays < 7) return `${diffDays}天后 ${time}`;
    return `${Math.floor(diffDays / 7)}周后`;
  };

  const formatPhonetic = (phonetic?: string) => {
    const trimmed = phonetic?.trim();
    if (!trimmed) return '';
    const inner = trimmed.replace(/^\/|\/$/g, '').trim();
    return inner ? `/${inner}/` : '';
  };

  const statusClass = statusClassNames[item.status] || 'new';

  return (
    <div className="vocabulary-list-item">
      <div className="vocabulary-item-main">
        <div className="vocabulary-word-block">
          <div className="vocabulary-word-row">
            <span className="vocabulary-word" title={item.word || '未知单词'}>
              {item.word || '未知单词'}
            </span>
            <button
              className="vocabulary-play-btn"
              onClick={() => onPlayAudio?.(item.word || '')}
              title="播放发音"
              type="button"
            >
              🔊
            </button>
          </div>
          <div className="vocabulary-phonetic">{formatPhonetic(item.phonetic)}</div>
        </div>

        <div className="vocabulary-meaning" title={`${item.partOfSpeech ? `${item.partOfSpeech} ` : ''}${item.meaning || '暂无释义'}`}>
          {item.partOfSpeech && <span className="vocabulary-pos">{item.partOfSpeech}</span>}
          <span className="vocabulary-meaning-text">{item.meaning || '暂无释义'}</span>
        </div>

        <span className={`vocabulary-status-tag ${statusClass}`}>
          {statusLabels[item.status] || item.status}
        </span>
      </div>

      <div className="vocabulary-item-progress">
        <div className="vocabulary-mastery-bar">
          <div className="mastery-bar-bg">
            <div className="mastery-bar-fill" style={{ width: `${masteryPercent}%` }} />
          </div>
          <span className="mastery-percent">{masteryPercent}%</span>
        </div>
      </div>

      <div className="vocabulary-stats-row">
        <div className="vocabulary-stat-item">
          <span className="stat-icon">▣</span>
          <span>已学习 {item.seenCount || 0} 次</span>
        </div>
        <div className="vocabulary-stat-item">
          <span className="stat-icon error">×</span>
          <span>答错 {item.wrongCount || 0} 次</span>
        </div>
        <div className="vocabulary-stat-item">
          <span className="stat-icon">◷</span>
          <span title={item.neverReview ? '已设置为不再复习' : item.nextReviewAt ? `下次复习时间：${new Date(item.nextReviewAt).toLocaleString()}` : '未设置下次复习时间'}>
            下次复习 {formatNextReview(item.nextReviewAt, item.neverReview)}
          </span>
        </div>
      </div>

      <div className="vocabulary-item-actions">
        <button className="vocabulary-action-btn" onClick={() => onEdit?.(item)} title="编辑" type="button">
          ✎
        </button>
        <button className="vocabulary-action-btn danger" onClick={() => onDelete?.(item)} title="删除" type="button">
          ×
        </button>
      </div>
    </div>
  );
};

export default VocabularyCardListItem;
