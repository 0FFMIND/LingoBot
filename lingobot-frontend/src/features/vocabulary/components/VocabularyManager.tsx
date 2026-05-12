import React, { useState, useEffect, useCallback } from 'react';
import { 
  VocabularyTab, 
  VocabularySortBy, 
  VocabularyStatus, 
  UserVocabularyDTO,
  PageResponseDTO,
  VocabularyStatsDTO 
} from '../../../types';
import { vocabularyService } from '../../../services';
import VocabularyCardListItem from './VocabularyCardListItem';

interface VocabularyManagerProps {
  onBack?: () => void;
}

interface TabConfig {
  key: VocabularyTab;
  label: string;
  icon: string;
  status?: VocabularyStatus;
  filterType?: string;
}

const tabs: TabConfig[] = [
  { key: 'all', label: '全部', icon: '📚', status: undefined, filterType: undefined },
  { key: 'to_review', label: '待复习', icon: '📖', status: undefined, filterType: 'to_review' },
  { key: 'learning', label: '学习中', icon: '✏️', status: 'LEARNING', filterType: undefined },
  { key: 'mastered', label: '已掌握', icon: '✅', status: 'MASTERED', filterType: undefined },
  { key: 'difficult', label: '易错词', icon: '❌', status: undefined, filterType: 'difficult' },
];

const sortOptions: { value: VocabularySortBy; label: string }[] = [
  { value: 'last_seen', label: '最近学习' },
  { value: 'first_seen', label: '最早学习' },
  { value: 'mastery_desc', label: '掌握度高到低' },
  { value: 'mastery_asc', label: '掌握度低到高' },
  { value: 'seen_count', label: '学习次数' },
  { value: 'wrong_count', label: '错误次数' },
  { value: 'next_review', label: '复习时间' },
];

const PAGE_SIZE = 20;

const VocabularyManager: React.FC<VocabularyManagerProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<VocabularyTab>('all');
  const [sortBy, setSortBy] = useState<VocabularySortBy>('last_seen');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<UserVocabularyDTO[]>([]);
  const [stats, setStats] = useState<VocabularyStatsDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  const getTabCount = (tab: VocabularyTab): number => {
    if (!stats) return 0;
    switch (tab) {
      case 'all': return stats.totalCount;
      case 'to_review': return stats.toReviewCount;
      case 'learning': return stats.learningCount;
      case 'mastered': return stats.masteredCount;
      case 'difficult': return stats.learningCount;
      default: return 0;
    }
  };

  const loadStats = useCallback(async () => {
    try {
      const data = await vocabularyService.getVocabularyStats();
      setStats(data);
    } catch (error) {
      console.error('Failed to load vocabulary stats:', error);
    }
  }, []);

  const loadItems = useCallback(async (resetPage = false) => {
    setLoading(true);
    const currentPage = resetPage ? 0 : page;
    const tabConfig = tabs.find(t => t.key === activeTab)!;

    try {
      const response: PageResponseDTO<UserVocabularyDTO> = await vocabularyService.getUserVocabularies({
        status: tabConfig.status,
        filterType: tabConfig.filterType,
        sortBy,
        page: currentPage,
        size: PAGE_SIZE,
      });

      if (resetPage) {
        setItems(response.content);
      } else {
        setItems(prev => [...prev, ...response.content]);
      }
      setHasMore(response.hasNext);
      setTotalElements(response.totalElements);
      if (resetPage) {
        setPage(1);
      }
    } catch (error) {
      console.error('Failed to load vocabulary items:', error);
    } finally {
      setLoading(false);
    }
  }, [activeTab, sortBy, page]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    setPage(0);
    loadItems(true);
  }, [activeTab, sortBy]);

  const handleLoadMore = () => {
    if (!loading && hasMore) {
      loadItems(false);
      setPage(prev => prev + 1);
    }
  };

  const handleTabChange = (tab: VocabularyTab) => {
    if (tab !== activeTab) {
      setActiveTab(tab);
      setItems([]);
    }
  };

  const handlePlayAudio = (word: string) => {
    if ('speechSynthesis' in window && word) {
      const utterance = new SpeechSynthesisUtterance(word);
      utterance.lang = 'en-US';
      speechSynthesis.speak(utterance);
    }
  };

  return (
    <div className="vocabulary-manager">
      <div className="vocabulary-manager-header">
        <div className="vocabulary-manager-title">
          {onBack && (
            <button className="vocabulary-back-btn" onClick={onBack}>
              ←
            </button>
          )}
          <h2>📚 单词卡管理</h2>
          {stats && (
            <span className="vocabulary-total-count">
              共 {stats.totalCount} 个单词
            </span>
          )}
        </div>
      </div>

      <div className="vocabulary-tabs">
        {tabs.map(tab => (
          <button
            key={tab.key}
            className={`vocabulary-tab-btn ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => handleTabChange(tab.key)}
          >
            <span className="tab-icon">{tab.icon}</span>
            <span className="tab-label">{tab.label}</span>
            <span className="tab-count">{getTabCount(tab.key)}</span>
          </button>
        ))}
      </div>

      <div className="vocabulary-toolbar">
        <div className="vocabulary-toolbar-left">
          <button className="toolbar-btn" title="筛选">
            🔍 筛选
          </button>
        </div>
        <div className="vocabulary-toolbar-right">
          <span className="sort-label">排序：</span>
          <select
            className="sort-select"
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as VocabularySortBy)}
          >
            {sortOptions.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="vocabulary-list-container">
        {items.length === 0 && !loading ? (
          <div className="vocabulary-empty-state">
            <div className="empty-icon">📖</div>
            <h3>暂无单词</h3>
            <p>去学习一些单词吧！</p>
          </div>
        ) : (
          <>
            <div className="vocabulary-list">
              {items.map(item => (
                <VocabularyCardListItem
                  key={item.id}
                  item={item}
                  onPlayAudio={handlePlayAudio}
                />
              ))}
            </div>
            
            {loading && (
              <div className="vocabulary-loading">
                <div className="loading-spinner"></div>
                <span>加载中...</span>
              </div>
            )}
            
            {!loading && hasMore && (
              <button 
                className="load-more-btn"
                onClick={handleLoadMore}
              >
                加载更多
              </button>
            )}
            
            {!loading && !hasMore && items.length > 0 && (
              <div className="vocabulary-list-footer">
                已显示 {items.length} / {totalElements} 个单词
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default VocabularyManager;
