import React, { useState, useEffect, useCallback } from 'react';
import { balanceService } from '../../services';
import { BalanceTransactionDTO, TransactionType } from '../../types';

interface BalanceTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentBalance: number;
}

const BalanceTransactionModal: React.FC<BalanceTransactionModalProps> = ({
  isOpen,
  onClose,
  currentBalance,
}) => {
  const [transactions, setTransactions] = useState<BalanceTransactionDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [totalPages, setTotalPages] = useState(0);

  const loadTransactions = useCallback(async (pageNum: number) => {
    setLoading(true);
    setError(null);
    try {
      const response = await balanceService.getTransactions(pageNum, 10);
      if (pageNum === 0) {
        setTransactions(response.content);
      } else {
        setTransactions((prev) => [...prev, ...response.content]);
      }
      setHasMore(response.hasNext);
      setTotalPages(response.totalPages);
    } catch (err) {
      setError('加载交易记录失败，请稍后重试');
      console.error('加载交易记录失败:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      setPage(0);
      setTransactions([]);
      loadTransactions(0);
    }
  }, [isOpen, loadTransactions]);

  const handleLoadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    loadTransactions(nextPage);
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const getTransactionTypeLabel = (type: TransactionType) => {
    switch (type) {
      case 'CHARGE':
        return '扣费';
      case 'RECHARGE':
        return '充值';
      case 'ADMIN_ADJUSTMENT':
        return '管理员调账';
      default:
        return type;
    }
  };

  const getTransactionTypeColor = (type: TransactionType) => {
    switch (type) {
      case 'CHARGE':
        return '#e74c3c';
      case 'RECHARGE':
        return '#27ae60';
      case 'ADMIN_ADJUSTMENT':
        return '#f39c12';
      default:
        return '#666';
    }
  };

  const getTransactionIcon = (type: TransactionType) => {
    switch (type) {
      case 'CHARGE':
        return '➖';
      case 'RECHARGE':
        return '➕';
      case 'ADMIN_ADJUSTMENT':
        return '⚙️';
      default:
        return '📋';
    }
  };

  const getTransactionBgColor = (transaction: BalanceTransactionDTO) => {
    if (transaction.type === 'CHARGE') {
      return '#fef2f2';
    }
    if (transaction.type === 'RECHARGE') {
      return '#f0fdf4';
    }
    // ADMIN_ADJUSTMENT：根据余额变化判断背景色
    const diff = transaction.balanceAfter - transaction.balanceBefore;
    return diff >= 0 ? '#fff8e1' : '#fff5f5';
  };

  const getAmountSign = (transaction: BalanceTransactionDTO) => {
    if (transaction.type === 'CHARGE') {
      return '-';
    }
    if (transaction.type === 'RECHARGE') {
      return '+';
    }
    // ADMIN_ADJUSTMENT：根据余额变化判断正负号
    const diff = transaction.balanceAfter - transaction.balanceBefore;
    return diff >= 0 ? '+' : '-';
  };

  if (!isOpen) return null;

  return (
    <div className="auth-modal-overlay" onClick={onClose}>
      <div
        className="auth-modal"
        onClick={(e) => e.stopPropagation()}
        style={{ maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}
      >
        <div className="auth-modal-header">
          <h2>💰 余额明细</h2>
          <button className="auth-close-btn" onClick={onClose}>
            ×
          </button>
        </div>

        <div
          style={{
            padding: '16px 20px',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: '#fff',
          }}
        >
          <div style={{ fontSize: '14px', opacity: 0.9, marginBottom: '4px' }}>
            当前余额
          </div>
          <div style={{ fontSize: '28px', fontWeight: 'bold' }}>
            {currentBalance} <span style={{ fontSize: '16px' }}>点</span>
          </div>
        </div>

        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '0',
            maxHeight: '400px',
          }}
        >
          {loading && transactions.length === 0 ? (
            <div style={{ padding: '40px', textAlign: 'center', color: '#666' }}>
              加载中...
            </div>
          ) : error ? (
            <div style={{ padding: '40px', textAlign: 'center', color: '#e74c3c' }}>
              {error}
            </div>
          ) : transactions.length === 0 ? (
            <div style={{ padding: '40px', textAlign: 'center', color: '#666' }}>
              <div style={{ fontSize: '48px', marginBottom: '16px' }}>📋</div>
              <p>暂无交易记录</p>
            </div>
          ) : (
            <>
              {transactions.map((transaction) => (
                <div
                  key={transaction.publicId}
                  style={{
                    padding: '12px 20px',
                    borderBottom: '1px solid #f0f0f0',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                  }}
                >
                  <div
                    style={{
                      width: '40px',
                      height: '40px',
                      borderRadius: '50%',
                      background: getTransactionBgColor(transaction),
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '18px',
                    }}
                  >
                    {getTransactionIcon(transaction.type)}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span
                        style={{
                          fontWeight: '500',
                          color: getTransactionTypeColor(transaction.type),
                        }}
                      >
                        {getTransactionTypeLabel(transaction.type)}
                      </span>
                      <span
                        style={{
                          fontWeight: 'bold',
                          color: getTransactionTypeColor(transaction.type),
                          fontSize: '16px',
                        }}
                      >
                        {getAmountSign(transaction)}
                        {transaction.amount}
                      </span>
                    </div>
                    {transaction.description && (
                      <div style={{ fontSize: '12px', color: '#666', marginTop: '2px' }}>
                        {transaction.description}
                        {transaction.apiCategory && ` · ${transaction.apiCategory}`}
                      </div>
                    )}
                    <div style={{ fontSize: '11px', color: '#999', marginTop: '2px' }}>
                      {formatDate(transaction.createdAt)}
                    </div>
                  </div>
                </div>
              ))}

              {hasMore && (
                <div style={{ padding: '12px 20px', textAlign: 'center' }}>
                  <button
                    onClick={handleLoadMore}
                    disabled={loading}
                    style={{
                      padding: '8px 20px',
                      background: 'transparent',
                      border: '1px solid #e0e0e0',
                      borderRadius: '4px',
                      color: '#666',
                      cursor: loading ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {loading ? '加载中...' : `加载更多 (${page + 1}/${totalPages})`}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default BalanceTransactionModal;
