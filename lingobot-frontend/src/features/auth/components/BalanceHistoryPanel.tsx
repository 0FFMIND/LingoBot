import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { balanceService } from '../../../services';
import { BalanceTransactionDTO, TransactionSummaryDTO, TransactionType } from '../../../types';

const PAGE_SIZE = 5;

type FilterTab = 'all' | 'income' | 'expense';
type QuickFilterType = 'none' | 'week' | 'month' | 'custom';

const BalanceHistoryPanel: React.FC = () => {
  const [transactions, setTransactions] = useState<BalanceTransactionDTO[]>([]);
  const [summary, setSummary] = useState<TransactionSummaryDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [totalPages, setTotalPages] = useState(0);
  const [filterTab, setFilterTab] = useState<FilterTab>('all');
  const [quickFilter, setQuickFilter] = useState<QuickFilterType>('none');
  
  const today = new Date();
  const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  const isInitialLoad = useRef(true);
  
  const formatDateForInput = (date: Date) => {
    return date.toISOString().split('T')[0];
  };
  
  const [startDate, setStartDate] = useState<string>(formatDateForInput(firstDayOfMonth));
  const [endDate, setEndDate] = useState<string>(formatDateForInput(today));
  const [useDateFilter, setUseDateFilter] = useState(false);

  const loadTransactions = useCallback(async (
    pageNum: number,
    start?: string,
    end?: string
  ) => {
    setLoading(true);
    setError(null);
    try {
      const response = await balanceService.getTransactions(
        pageNum,
        PAGE_SIZE,
        filterTab,
        useDateFilter ? start : undefined,
        useDateFilter ? end : undefined
      );
      
      setTransactions(response.content);
      setHasMore(response.hasNext);
      setTotalPages(response.totalPages);
    } catch (err) {
      setError('加载交易记录失败，请稍后重试');
      console.error('加载交易记录失败:', err);
    } finally {
      setLoading(false);
    }
  }, [useDateFilter, filterTab]);

  const loadSummary = useCallback(async (start?: string, end?: string) => {
    try {
      const data = await balanceService.getTransactionSummary(
        useDateFilter ? start : undefined,
        useDateFilter ? end : undefined
      );
      setSummary(data);
    } catch (err) {
      console.error('加载统计信息失败:', err);
    }
  }, [useDateFilter]);

  useEffect(() => {
    if (isInitialLoad.current) {
      isInitialLoad.current = false;
      loadTransactions(0, startDate, endDate);
      loadSummary(startDate, endDate);
      return;
    }
    
    setPage(0);
    loadTransactions(0, startDate, endDate);
    loadSummary(startDate, endDate);
  }, [useDateFilter, startDate, endDate, loadTransactions, loadSummary]);

  const handlePageChange = (newPage: number) => {
    if (newPage === page || newPage < 0 || (totalPages > 0 && newPage >= totalPages)) {
      return;
    }
    setPage(newPage);
    loadTransactions(newPage, startDate, endDate);
  };

  const applyQuickFilter = (type: 'week' | 'month') => {
    const now = new Date();
    let start: Date;
    
    if (type === 'week') {
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    } else {
      start = new Date(now.getFullYear(), now.getMonth() - 1, now.getDate());
    }
    
    setQuickFilter(type);
    setUseDateFilter(true);
    setStartDate(formatDateForInput(start));
    setEndDate(formatDateForInput(now));
  };

  const formatDateTime = (dateString: string) => {
    const date = new Date(dateString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`;
  };

  const getTransactionTypeLabel = (type: TransactionType) => {
    switch (type) {
      case 'CHARGE':
        return '支出';
      case 'RECHARGE':
        return '收入';
      case 'ADMIN_ADJUSTMENT':
        return '调账';
      default:
        return type;
    }
  };

  const getTransactionTypeColor = (type: TransactionType, transaction: BalanceTransactionDTO) => {
    if (type === 'CHARGE') return '#ef4444';
    if (type === 'RECHARGE') return '#22c55e';
    const diff = transaction.balanceAfter - transaction.balanceBefore;
    return diff >= 0 ? '#22c55e' : '#ef4444';
  };

  const getAmountDisplay = (transaction: BalanceTransactionDTO) => {
    if (transaction.type === 'CHARGE') return `-${transaction.amount}`;
    if (transaction.type === 'RECHARGE') return `+${transaction.amount}`;
    const diff = transaction.balanceAfter - transaction.balanceBefore;
    return diff >= 0 ? `+${transaction.amount}` : `-${transaction.amount}`;
  };

  const filteredTransactions = transactions;

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

  return (
    <div className="balance-history-panel">
      <div className="balance-history-header">
        <h3 className="balance-history-title">💰 余额明细</h3>
      </div>

      <div className="balance-history-toolbar">
        <div className="filter-tabs">
          <button
            className={`filter-tab ${filterTab === 'all' ? 'active' : ''}`}
            onClick={() => setFilterTab('all')}
          >
            全部
          </button>
          <button
            className={`filter-tab ${filterTab === 'income' ? 'active' : ''}`}
            onClick={() => setFilterTab('income')}
          >
            收入
          </button>
          <button
            className={`filter-tab ${filterTab === 'expense' ? 'active' : ''}`}
            onClick={() => setFilterTab('expense')}
          >
            支出
          </button>
        </div>

        <div className="date-filter-section">
          <input
            type="date"
            className="date-input"
            value={startDate}
            onChange={(e) => {
              setStartDate(e.target.value);
              setQuickFilter('custom');
              setUseDateFilter(true);
            }}
          />
          <span className="date-separator">~</span>
          <input
            type="date"
            className="date-input"
            value={endDate}
            onChange={(e) => {
              setEndDate(e.target.value);
              setQuickFilter('custom');
              setUseDateFilter(true);
            }}
          />
          <button
            className={`quick-filter-btn ${quickFilter === 'none' ? 'active' : ''}`}
            onClick={() => {
              setQuickFilter('none');
              setUseDateFilter(false);
              setStartDate(formatDateForInput(firstDayOfMonth));
              setEndDate(formatDateForInput(today));
            }}
          >
            按月份
          </button>
          <button
            className={`quick-filter-btn ${quickFilter === 'week' ? 'active' : ''}`}
            onClick={() => applyQuickFilter('week')}
          >
            最近7天
          </button>
          <button
            className={`quick-filter-btn ${quickFilter === 'month' ? 'active' : ''}`}
            onClick={() => applyQuickFilter('month')}
          >
            最近30天
          </button>
        </div>
      </div>

      {summary && (
        <div className="summary-section">
          <div className="summary-item income">
            <div className="summary-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 5v14M5 12h14" />
              </svg>
            </div>
            <div className="summary-content">
              <div className="summary-label">所选时间范围收入</div>
              <div className="summary-value positive">+{summary.totalIncome} 点</div>
            </div>
          </div>
          <div className="summary-item expense">
            <div className="summary-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M5 12h14" />
              </svg>
            </div>
            <div className="summary-content">
              <div className="summary-label">所选时间范围支出</div>
              <div className="summary-value negative">-{summary.totalExpense} 点</div>
            </div>
          </div>
          <div className="summary-item net">
            <div className="summary-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
              </svg>
            </div>
            <div className="summary-content">
              <div className="summary-label">净变化</div>
              <div className={`summary-value ${summary.netChange >= 0 ? 'positive' : 'negative'}`}>
                {summary.netChange >= 0 ? '+' : ''}{summary.netChange} 点
              </div>
            </div>
          </div>
          <div className="summary-item count">
            <div className="summary-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
              </svg>
            </div>
            <div className="summary-content">
              <div className="summary-label">记录数</div>
              <div className="summary-value count">{summary.totalRecords} 条</div>
            </div>
          </div>
        </div>
      )}

      <div className="transactions-table-container">
        {loading && transactions.length === 0 ? (
          <div className="loading-state">
            <div className="loading-spinner"></div>
            <span>加载中...</span>
          </div>
        ) : error ? (
          <div className="error-state">
            {error}
          </div>
        ) : filteredTransactions.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📋</div>
            <p>暂无交易记录</p>
          </div>
        ) : (
          <>
            <table className="transactions-table">
              <thead>
                <tr>
                  <th className="th-time">时间</th>
                  <th className="th-type">类型</th>
                  <th className="th-description">描述</th>
                  <th className="th-change">变化</th>
                  <th className="th-balance">余额</th>
                </tr>
              </thead>
              <tbody>
                {filteredTransactions.map((transaction) => (
                  <tr key={transaction.id} className="transaction-row">
                    <td className="td-time">
                      {formatDateTime(transaction.createdAt)}
                    </td>
                    <td className="td-type">
                      <span
                        className={`type-badge ${transaction.type === 'CHARGE' ? 'expense' : 'income'}`}
                      >
                        {getTransactionTypeLabel(transaction.type)}
                      </span>
                    </td>
                    <td className="td-description">
                      <div className="description-main">
                        {transaction.description || '-'}
                      </div>
                      {transaction.conversationId && (
                        <div className="description-sub">
                          对话 ID: dlg_{transaction.conversationId.toString().padStart(10, '0')}
                        </div>
                      )}
                    </td>
                    <td className="td-change">
                      <span
                        style={{ color: getTransactionTypeColor(transaction.type, transaction) }}
                      >
                        {getAmountDisplay(transaction)}
                      </span>
                    </td>
                    <td className="td-balance">
                      {transaction.balanceAfter}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {totalPages > 1 && (
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
    </div>
  );
};

export default BalanceHistoryPanel;
