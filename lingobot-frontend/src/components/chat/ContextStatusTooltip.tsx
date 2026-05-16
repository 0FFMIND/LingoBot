import React, { useState, useRef, useEffect, useCallback } from 'react';
import ReactDOM from 'react-dom';
import { ContextStatusDTO } from '../../types';
import { useTokenUsageStore, ConversationTokenUsage } from '../../stores';
import { conversationService } from '../../services/conversationService';

interface ContextStatusTooltipProps {
  status: ContextStatusDTO;
  onManualCompact?: (publicId: string) => void;
  publicId: string;
  isCompacting?: boolean;
  compactingConversationPublicId?: string | null;
  children: React.ReactNode;
}

const ContextStatusTooltip: React.FC<ContextStatusTooltipProps> = ({
  status,
  onManualCompact,
  publicId,
  isCompacting = false,
  compactingConversationPublicId = null,
  children,
}) => {
  const [showTooltip, setShowTooltip] = useState(false);
  const [isTooltipFadingOut, setIsTooltipFadingOut] = useState(false);
  const [freshStatus, setFreshStatus] = useState<ContextStatusDTO | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState({ left: 0, top: 0 });
  const containerRef = useRef<HTMLDivElement>(null);
  const hideTimerRef = useRef<number | null>(null);
  const isCurrentlyCompacting = isCompacting && compactingConversationPublicId === publicId;

  const localUsage = useTokenUsageStore((state) => state.usageByConversationPublicId[publicId]);
  const maxTokens = useTokenUsageStore((state) => state.maxTokensPerConversation);
  const activeStatus = freshStatus || status;

  const getCombinedUsage = (): {
    currentTokens: number;
    maxTokens: number;
    tokenRatio: number;
    wordCardsTotal: number;
    hasLocalData: boolean;
  } => {
    const backendTokens = activeStatus.currentTokens ?? 0;
    const backendMax = activeStatus.maxTokens ?? maxTokens;
    const localTokens = localUsage?.totalTokens ?? 0;
    const localWordCards = localUsage?.wordCardsCount ?? 0;

    const currentTokens = Math.max(backendTokens, localTokens);
    const effectiveMax = backendMax > 0 ? backendMax : maxTokens;
    const tokenRatio = Math.min(currentTokens / effectiveMax, 1);
    const wordCardsTotal = Math.max(activeStatus.wordCardsTotal ?? 0, localWordCards);

    return {
      currentTokens,
      maxTokens: effectiveMax,
      tokenRatio,
      wordCardsTotal,
      hasLocalData: localTokens > 0 || localWordCards > 0,
    };
  };

  const combined = getCombinedUsage();

  const getStatusText = () => {
    if (isCurrentlyCompacting) return '正在压缩上下文...';
    if (activeStatus.shouldCompact) return '建议压缩上下文';
    if (combined.tokenRatio >= 0.7) return '上下文用量偏高';
    return '上下文状态正常';
  };

  const getStatusColor = () => {
    if (isCurrentlyCompacting) return '#3498db';
    if (activeStatus.shouldCompact) return '#e74c3c';
    if (combined.tokenRatio >= 0.7) return '#f39c12';
    return '#27ae60';
  };

  const handleCompact = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onManualCompact && !isCurrentlyCompacting) {
      onManualCompact(publicId);
      setShowTooltip(false);
      setIsTooltipFadingOut(false);
    }
  };

  const updatePosition = useCallback(() => {
    if (containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const tooltipWidth = 260;
      const viewportWidth = window.innerWidth;
      let left = rect.right + 10;
      if (left + tooltipWidth > viewportWidth - 8) {
        left = rect.left - tooltipWidth - 10;
      }
      setTooltipPosition({
        left,
        top: rect.top - 50,
      });
    }
  }, []);

  const cancelHide = () => {
    if (hideTimerRef.current !== null) {
      window.clearTimeout(hideTimerRef.current);
      hideTimerRef.current = null;
    }
    setIsTooltipFadingOut(false);
  };

  const handleMouseEnter = () => {
    cancelHide();
    updatePosition();
    setShowTooltip(true);
    conversationService.getContextStatus(publicId)
      .then(setFreshStatus)
      .catch(() => {
        setFreshStatus(null);
      });
  };

  const handleMouseLeave = () => {
    setIsTooltipFadingOut(true);
    if (hideTimerRef.current !== null) {
      window.clearTimeout(hideTimerRef.current);
    }
    hideTimerRef.current = window.setTimeout(() => {
      setShowTooltip(false);
      setIsTooltipFadingOut(false);
      hideTimerRef.current = null;
    }, 260);
  };

  useEffect(() => {
    if (!showTooltip) return;

    window.addEventListener('scroll', updatePosition, true);
    window.addEventListener('resize', updatePosition);

    return () => {
      window.removeEventListener('scroll', updatePosition, true);
      window.removeEventListener('resize', updatePosition);
    };
  }, [showTooltip, updatePosition]);

  useEffect(() => {
    return () => {
      if (hideTimerRef.current !== null) {
        window.clearTimeout(hideTimerRef.current);
      }
    };
  }, []);

  const tooltipPanel = showTooltip
    ? ReactDOM.createPortal(
        <div
          onMouseEnter={cancelHide}
          onMouseLeave={handleMouseLeave}
          style={{
            position: 'fixed',
            left: tooltipPosition.left,
            top: tooltipPosition.top,
            zIndex: 2147483647,
            opacity: isTooltipFadingOut ? 0 : 1,
            transform: isTooltipFadingOut ? 'translateY(3px)' : 'translateY(0)',
            transition: 'opacity 0.18s ease, transform 0.18s ease',
            padding: '12px',
            background: '#fff',
            border: '1px solid #ddd',
            borderRadius: '8px',
            boxShadow: '0 4px 20px rgba(0,0,0,0.18)',
            minWidth: '220px',
            maxWidth: '280px',
            fontSize: '12px',
          }}
        >
          <div
            style={{
              fontWeight: 'bold',
              marginBottom: '8px',
              color: getStatusColor(),
              fontSize: '13px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <span>{activeStatus.shouldCompact ? '⚠️' : '✅'}</span>
            {getStatusText()}
          </div>

          <div style={{ marginBottom: '10px', color: '#555' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', padding: '2px 0' }}>
              <span>Token 用量:</span>
              <span style={{ fontWeight: '600', color: getStatusColor() }}>
                {combined.currentTokens.toLocaleString()} / {combined.maxTokens.toLocaleString()}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', padding: '2px 0' }}>
              <span>使用率:</span>
              <span style={{ fontWeight: '600' }}>
                {Math.round(combined.tokenRatio * 100)}%
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', padding: '2px 0' }}>
              <span>单词卡片:</span>
              <span style={{ fontWeight: '500' }}>
                {combined.wordCardsTotal} 张
              </span>
            </div>
            {localUsage && localUsage.totalTokens > 0 && (
              <>
                <div style={{ borderTop: '1px dashed #e0e0e0', margin: '6px 0' }} />
                <div style={{ fontSize: '11px', color: '#777', marginBottom: '4px' }}>
                  📊 本地记录统计
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '3px', padding: '1px 0' }}>
                  <span style={{ color: '#888' }}>累计输入:</span>
                  <span style={{ fontWeight: '500', color: '#555' }}>
                    {localUsage.promptTokens.toLocaleString()}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '3px', padding: '1px 0' }}>
                  <span style={{ color: '#888' }}>累计输出:</span>
                  <span style={{ fontWeight: '500', color: '#555' }}>
                    {localUsage.completionTokens.toLocaleString()}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '3px', padding: '1px 0' }}>
                  <span style={{ color: '#888' }}>AI 对话数:</span>
                  <span style={{ fontWeight: '500', color: '#555' }}>
                    {localUsage.messagesCount} 次
                  </span>
                </div>
              </>
            )}
          </div>

          {activeStatus.compactReason && (
            <div
              style={{
                padding: '8px 10px',
                background: '#fff3cd',
                borderRadius: '6px',
                marginBottom: '10px',
                color: '#856404',
                fontSize: '11px',
                lineHeight: '1.4',
              }}
            >
              💡 {activeStatus.compactReason}
            </div>
          )}

          {onManualCompact && (
            <button
              onClick={handleCompact}
              disabled={isCurrentlyCompacting}
              style={{
                width: '100%',
                padding: '8px 12px',
                background: isCurrentlyCompacting ? '#cbd5e0' : (activeStatus.shouldCompact ? '#e74c3c' : '#3498db'),
                color: '#fff',
                border: 'none',
                borderRadius: '6px',
                cursor: isCurrentlyCompacting ? 'not-allowed' : 'pointer',
                fontSize: '12px',
                fontWeight: '600',
                transition: 'all 0.2s',
              }}
            >
              {isCurrentlyCompacting ? '⏳ 压缩中...' : (activeStatus.shouldCompact ? '🔧 立即压缩' : '🔧 手动压缩上下文')}
            </button>
          )}

          <div
            style={{
              position: 'absolute',
              top: '50px',
              left: '-8px',
              width: '0',
              height: '0',
              borderTop: '8px solid transparent',
              borderBottom: '8px solid transparent',
              borderRight: '8px solid #ddd',
            }}
          />
          <div
            style={{
              position: 'absolute',
              top: '51px',
              left: '-6px',
              width: '0',
              height: '0',
              borderTop: '7px solid transparent',
              borderBottom: '7px solid transparent',
              borderRight: '7px solid #fff',
            }}
          />
        </div>,
        document.body
      )
    : null;

  return (
    <div
      ref={containerRef}
      className="context-status-tooltip"
      style={{ position: 'relative', display: 'inline-block' }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {children}
      {tooltipPanel}
    </div>
  );
};

export default ContextStatusTooltip;
