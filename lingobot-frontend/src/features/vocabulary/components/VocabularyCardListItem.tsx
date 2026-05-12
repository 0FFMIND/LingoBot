import React from 'react';
import { UserVocabularyDTO } from '../../../types';

interface VocabularyCardListItemProps {
  item: UserVocabularyDTO;
  onPlayAudio?: (word: string) => void;
}

const VocabularyCardListItem: React.FC<VocabularyCardListItemProps> = ({ item, onPlayAudio }) => {
  const getStatusInfo = (status: string) => {
    switch (status) {
      case 'NEW':
        return { label: '新词', color: '#6366f1', bgColor: '#eef2ff' };
      case 'LEARNING':
        return { label: '学习中', color: '#3b82f6', bgColor: '#dbeafe' };
      case 'REVIEWING':
        return { label: '复习中', color: '#f59e0b', bgColor: '#fef3c7' };
      case 'MASTERED':
        return { label: '已掌握', color: '#22c55e', bgColor: '#dcfce7' };
      case 'IGNORED':
        return { label: '已忽略', color: '#6b7280', bgColor: '#f3f4f6' };
      default:
        return { label: status, color: '#6b7280', bgColor: '#f3f4f6' };
    }
  };

  const getMasteryBarColor = (score: number, status: string) => {
    if (status === 'MASTERED') return '#22c55e';
    if (status === 'REVIEWING') return '#f59e0b';
    if (status === 'LEARNING') return '#3b82f6';
    if (score <= 0.4) return '#ef4444';
    return '#6366f1';
  };

  const formatNextReview = (dateStr?: string) => {
    if (!dateStr) return '未设置';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = date.getTime() - now.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    
    if (diffDays < 0) return '已到期';
    if (diffDays === 0) return `今天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
    if (diffDays === 1) return `明天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
    if (diffDays < 7) return `${diffDays}天后`;
    return `${Math.floor(diffDays / 7)}周后`;
  };

  const formatPhonetic = (phonetic: string) => {
    let result = phonetic.trim();
    if (result.startsWith('/')) {
      result = result.substring(1);
    }
    if (result.endsWith('/')) {
      result = result.substring(0, result.length - 1);
    }
    result = result.trim();
    return result ? `/${result}/` : '';
  };

  const statusInfo = getStatusInfo(item.status);
  const masteryPercent = Math.round(item.masteryScore * 100);
  const barColor = getMasteryBarColor(item.masteryScore, item.status);

  return (
    <div className="vocabulary-list-item">
      <div className="vocabulary-item-left">
        <div className="vocabulary-word-row">
          <span className="vocabulary-word">{item.word || '未知单词'}</span>
          <button 
            className="vocabulary-play-btn"
            onClick={() => onPlayAudio?.(item.word || '')}
            title="播放发音"
          >
            🔊
          </button>
          <span className="vocabulary-phonetic">
            {item.phonetic ? formatPhonetic(item.phonetic) : ''}
          </span>
          <span 
            className="vocabulary-status-tag"
            style={{ color: statusInfo.color, backgroundColor: statusInfo.bgColor }}
          >
            {statusInfo.label}
          </span>
        </div>
        <div className="vocabulary-meaning">
          {item.meaning || '暂无释义'}
        </div>
      </div>

      <div className="vocabulary-item-right">
        <div className="vocabulary-mastery-bar">
          <div className="mastery-bar-bg">
            <div 
              className="mastery-bar-fill" 
              style={{ width: `${masteryPercent}%`, backgroundColor: barColor }}
            />
          </div>
          <span className="mastery-percent" style={{ color: barColor }}>
            {masteryPercent}%
          </span>
        </div>

        <div className="vocabulary-stats-row">
          <div className="vocabulary-stat-item" title="已学习次数">
            <span className="stat-icon">📚</span>
            <span>已学习 {item.seenCount} 次</span>
          </div>
          <div className="vocabulary-stat-item" title="答错次数">
            <span className="stat-icon">❌</span>
            <span>答错 {item.wrongCount} 次</span>
          </div>
          <div className="vocabulary-stat-item" title="下次复习">
            <span className="stat-icon">⏰</span>
            <span>下次复习 {formatNextReview(item.nextReviewAt)}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VocabularyCardListItem;
