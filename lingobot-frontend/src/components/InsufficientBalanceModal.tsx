import React from 'react';

interface InsufficientBalanceModalProps {
  isOpen: boolean;
  onClose: () => void;
  message?: string;
  currentBalance?: number;
  requiredCost?: number;
}

const InsufficientBalanceModal: React.FC<InsufficientBalanceModalProps> = ({
  isOpen,
  onClose,
  message = '你的余额不足',
  currentBalance,
  requiredCost
}) => {
  if (!isOpen) return null;

  return (
    <div className="auth-modal-overlay" onClick={onClose}>
      <div className="auth-modal" onClick={(e) => e.stopPropagation()}>
        <div className="auth-modal-header">
          <h2>⚠️ 余额不足</h2>
          <button className="auth-close-btn" onClick={onClose}>
            ×
          </button>
        </div>

        <div style={{ padding: '20px', textAlign: 'center' }}>
          <div style={{ fontSize: '48px', marginBottom: '16px' }}>💰</div>
          <p style={{ fontSize: '16px', marginBottom: '16px', color: '#666' }}>
            {message}
          </p>
          
          {currentBalance !== undefined && (
            <div style={{ 
              background: '#fff3f3', 
              padding: '16px', 
              borderRadius: '8px', 
              marginBottom: '16px'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span style={{ color: '#666' }}>当前余额:</span>
                <span style={{ fontWeight: 'bold', color: '#e74c3c' }}>
                  {currentBalance} 点数
                </span>
              </div>
              {requiredCost !== undefined && (
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: '#666' }}>需要点数:</span>
                  <span style={{ fontWeight: 'bold', color: '#333' }}>
                    {requiredCost} 点数
                  </span>
                </div>
              )}
            </div>
          )}

          <p style={{ fontSize: '14px', color: '#888', marginBottom: '20px' }}>
            余额不足，请前往「账户设置 &gt; 账户余额」充值后继续使用
          </p>

          <button
            type="button"
            className="auth-submit-btn"
            onClick={onClose}
            style={{ width: '100%' }}
          >
            知道了
          </button>
        </div>
      </div>
    </div>
  );
};

export default InsufficientBalanceModal;
