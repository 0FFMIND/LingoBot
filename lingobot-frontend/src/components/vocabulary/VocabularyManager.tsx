import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { 
  VocabularyTab, 
  VocabularySortBy, 
  VocabularyStatus, 
  UserVocabularyDTO,
  PageResponseDTO,
  VocabularyStatsDTO,
  VOCABULARY_CATEGORIES,
  VOCABULARY_DIFFICULTIES,
} from '../../types';
import { vocabularyService } from '../../services';
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

const partOfSpeechOptions = [
  { value: '', label: '未设置' },
  { value: 'n.', label: 'n. 名词' },
  { value: 'v.', label: 'v. 动词' },
  { value: 'adj.', label: 'adj. 形容词' },
  { value: 'adv.', label: 'adv. 副词' },
  { value: 'prep.', label: 'prep. 介词' },
  { value: 'conj.', label: 'conj. 连词' },
  { value: 'pron.', label: 'pron. 代词' },
  { value: 'interj.', label: 'interj. 感叹词' },
  { value: 'det.', label: 'det. 限定词' },
];

const PAGE_SIZE = 7;

const VocabularyManager: React.FC<VocabularyManagerProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<VocabularyTab>('all');
  const [sortBy, setSortBy] = useState<VocabularySortBy>('last_seen');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<UserVocabularyDTO[]>([]);
  const [stats, setStats] = useState<VocabularyStatsDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [editingItem, setEditingItem] = useState<UserVocabularyDTO | null>(null);
  const [editForm, setEditForm] = useState({
    word: '',
    phonetic: '',
    partOfSpeech: '',
    meaning: '',
    category: '',
    difficulty: '',
  });
  const [aiModifying, setAiModifying] = useState(false);

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

  const loadItems = useCallback(async (pageNum: number) => {
    setLoading(true);
    const tabConfig = tabs.find(t => t.key === activeTab)!;

    try {
      const response: PageResponseDTO<UserVocabularyDTO> = await vocabularyService.getUserVocabularies({
        status: tabConfig.status,
        filterType: tabConfig.filterType,
        sortBy,
        search: searchKeyword || undefined,
        page: pageNum,
        size: PAGE_SIZE,
      });

      setItems(response.content);
      setTotalElements(response.totalElements);
      setTotalPages(response.totalPages);
    } catch (error) {
      console.error('Failed to load vocabulary items:', error);
    } finally {
      setLoading(false);
    }
  }, [activeTab, sortBy, searchKeyword]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    setPage(0);
    loadItems(0);
  }, [activeTab, sortBy, searchKeyword, loadItems]);

  const handlePageChange = (newPage: number) => {
    if (newPage === page || newPage < 0 || (totalPages > 0 && newPage >= totalPages)) {
      return;
    }
    setPage(newPage);
    loadItems(newPage);
  };

  const paginationButtons = useMemo(() => {
    const buttons: (number | string)[] = [];
    const currentPage = page + 1;
    const total = totalPages;
    
    if (total <= 7) {
      for (let i = 1; i <= total; i++) {
        buttons.push(i);
      }
      return buttons;
    }
    
    if (currentPage <= 4) {
      buttons.push(1, 2, 3, 4, 5, '...', total);
    } else if (currentPage >= total - 3) {
      buttons.push(1, '...', total - 4, total - 3, total - 2, total - 1, total);
    } else {
      buttons.push(1, '...', currentPage - 1, currentPage, currentPage + 1, '...', total);
    }
    
    return buttons;
  }, [page, totalPages]);

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

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchKeyword(e.target.value);
  };

  const handleEditClick = (item: UserVocabularyDTO) => {
    setEditingItem(item);
    setEditForm({
      word: item.word || '',
      phonetic: item.phonetic || '',
      partOfSpeech: item.partOfSpeech || '',
      meaning: item.meaning || '',
      category: item.category || '',
      difficulty: item.difficulty || '',
    });
  };

  const getCategoryOptions = () => {
    return [
      { value: '', label: '未设置' },
      ...VOCABULARY_CATEGORIES.map(cat => ({
        value: cat.category,
        label: cat.label
      }))
    ];
  };

  const getDifficultyOptions = (category: string) => {
    if (!category) {
      return [
        { value: '', label: '未设置' }
      ];
    }
    const filtered = VOCABULARY_DIFFICULTIES.filter(d => d.category === category);
    return [
      { value: '', label: '未设置' },
      ...filtered.map(diff => ({
        value: diff.difficulty,
        label: diff.scoreRange ? `${diff.scoreRange} (${diff.label})` : diff.label
      }))
    ];
  };

  const handleCategoryChange = (newCategory: string) => {
    setEditForm(prev => {
      const currentDifficulty = prev.difficulty;
      if (!newCategory) {
        return { ...prev, category: newCategory, difficulty: '' };
      }
      const validDifficulty = VOCABULARY_DIFFICULTIES.filter(d => d.category === newCategory);
      if (validDifficulty.find(d => d.difficulty === currentDifficulty)) {
        return { ...prev, category: newCategory };
      }
      return {
        ...prev,
        category: newCategory,
        difficulty: validDifficulty[0]?.difficulty || ''
      };
    });
  };

  const handleAIModify = async () => {
    if (!editingItem) return;
    setAiModifying(true);
    try {
      const request = {
        id: editingItem.id,
        ...editForm,
      };
      const updated = await vocabularyService.aiModifyVocabulary(request);
      setEditForm({
        word: updated.word || '',
        phonetic: updated.phonetic || '',
        partOfSpeech: updated.partOfSpeech || '',
        meaning: updated.meaning || '',
        category: updated.category || '',
        difficulty: updated.difficulty || '',
      });
      setItems(prev => prev.map(item => item.id === updated.id ? updated : item));
      loadStats();
    } catch (error) {
      console.error('AI修改失败:', error);
      alert('AI修改失败，请稍后再试');
    } finally {
      setAiModifying(false);
    }
  };

  const handleSaveEdit = async () => {
    if (!editingItem) return;
    try {
      const { word, ...updateData } = editForm;
      const updated = await vocabularyService.updateUserVocabulary(editingItem.id, updateData);
      setItems(prev => prev.map(item => item.id === updated.id ? updated : item));
      setEditingItem(null);
      loadStats();
    } catch (error) {
      console.error('Failed to update vocabulary:', error);
      alert('保存失败，请稍后再试');
    }
  };

  const handleDelete = async (item: UserVocabularyDTO) => {
    const word = item.word || '这个单词';
    if (!window.confirm(`确定删除「${word}」吗？删除后它不会出现在单词卡管理列表中。`)) {
      return;
    }
    try {
      await vocabularyService.deleteUserVocabulary(item.id);
      setItems(prev => prev.filter(current => current.id !== item.id));
      setTotalElements(prev => Math.max(0, prev - 1));
      loadStats();
    } catch (error) {
      console.error('Failed to delete vocabulary:', error);
      alert('删除失败，请稍后再试');
    }
  };

  return (
    <div className="vocabulary-manager">
      <div className="vocabulary-manager-header">
        <div className="vocabulary-manager-title">
          <h2>📚 单词卡管理</h2>
          {stats && (
            <span className="vocabulary-total-count">
              共 {stats.totalCount} 个单词
            </span>
          )}
        </div>
      </div>

      <div className="vocabulary-top-bar">
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

        <div className="vocabulary-search-box">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            className="search-input"
            placeholder="搜索单词..."
            value={searchKeyword}
            onChange={handleSearchChange}
          />
        </div>

        <div className="vocabulary-toolbar">
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
                  onEdit={handleEditClick}
                  onDelete={handleDelete}
                />
              ))}
            </div>
            
            {loading && (
              <div className="vocabulary-loading">
                <div className="loading-spinner"></div>
                <span>加载中...</span>
              </div>
            )}
            
            {!loading && totalPages > 1 && items.length > 0 && (
              <div className="pagination-section">
                <button
                  className="pagination-btn"
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page === 0}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="15 18 9 12 15 6" />
                  </svg>
                </button>

                {paginationButtons.map((btn, index) => (
                  typeof btn === 'number' ? (
                    <button
                      key={index}
                      className={`pagination-number ${page + 1 === btn ? 'active' : ''}`}
                      onClick={() => handlePageChange(btn - 1)}
                    >
                      {btn}
                    </button>
                  ) : (
                    <span key={index} className="pagination-ellipsis">...</span>
                  )
                ))}

                <button
                  className="pagination-btn"
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page >= totalPages - 1}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="9 18 15 12 9 6" />
                  </svg>
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {editingItem && (
        <div className="vocabulary-edit-overlay" onClick={() => setEditingItem(null)}>
          <div className="vocabulary-edit-modal" onClick={(event) => event.stopPropagation()}>
            <div className="vocabulary-edit-header">
              <h3>编辑单词卡</h3>
              <button className="vocabulary-edit-close" onClick={() => setEditingItem(null)} type="button">×</button>
            </div>
            <div className="vocabulary-edit-grid">
              <label>
                <span>单词</span>
                <input value={editForm.word} readOnly disabled className="vocabulary-edit-disabled" />
              </label>
              <label>
                <span>音标</span>
                <input value={editForm.phonetic} onChange={(e) => setEditForm(prev => ({ ...prev, phonetic: e.target.value }))} />
              </label>
              <label>
                <span>词性</span>
                <select value={editForm.partOfSpeech} onChange={(e) => setEditForm(prev => ({ ...prev, partOfSpeech: e.target.value }))}>
                  {partOfSpeechOptions.map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>分类</span>
                <select value={editForm.category} onChange={(e) => handleCategoryChange(e.target.value)}>
                  {getCategoryOptions().map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>难度</span>
                <select value={editForm.difficulty} onChange={(e) => setEditForm(prev => ({ ...prev, difficulty: e.target.value }))}>
                  {getDifficultyOptions(editForm.category).map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label className="wide">
                <span>释义</span>
                <textarea value={editForm.meaning} onChange={(e) => setEditForm(prev => ({ ...prev, meaning: e.target.value }))} />
              </label>
            </div>
            <div className="vocabulary-edit-actions">
              <button className="vocabulary-edit-cancel" onClick={() => setEditingItem(null)} type="button">取消</button>
              <button 
                className="vocabulary-edit-ai" 
                onClick={handleAIModify} 
                type="button"
                disabled={aiModifying}
              >
                {aiModifying ? 'AI修改中...' : '🤖 AI修改'}
              </button>
              <button className="vocabulary-edit-save" onClick={handleSaveEdit} type="button">保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default VocabularyManager;
