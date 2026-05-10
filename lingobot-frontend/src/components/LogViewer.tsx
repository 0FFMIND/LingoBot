import React, { useState, useEffect, useRef, useCallback } from 'react';
import { authUtils } from '../api';

interface LogEntry {
  id: number;
  timestamp: string;
  level: string;
  logger: string;
  message: string;
  formattedMessage: string;
  hasJson: boolean;
  logType: 'request' | 'response' | 'other';
  uniqueKey: string;
  identifier?: string;
}

type LogFilter = 'all' | 'request' | 'response' | 'other';

let logIdCounter = 0;

const generateLogKey = (timestamp: string, level: string, logger: string, message: string): string => {
  return `${timestamp}|${level}|${logger}|${message.substring(0, 200)}`;
};

interface LogViewerProps {
  fullPage?: boolean;
}

const LogViewer: React.FC<LogViewerProps> = ({ fullPage = false }) => {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [showFormatted, setShowFormatted] = useState(true);
  const [filters, setFilters] = useState<Set<LogFilter>>(new Set(['request', 'response', 'other']));
  const [isDevEnv, setIsDevEnv] = useState<boolean | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const pendingResponseHeaderRef = useRef<LogEntry | null>(null);
  const isConnectingRef = useRef(false);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const seenLogKeysRef = useRef<Set<string>>(new Set());

  const generateUniqueId = (): number => {
    return ++logIdCounter;
  };

  const isDuplicateLog = useCallback((key: string): boolean => {
    if (seenLogKeysRef.current.has(key)) {
      return true;
    }
    seenLogKeysRef.current.add(key);
    if (seenLogKeysRef.current.size > 500) {
      const keysArray = Array.from(seenLogKeysRef.current);
      seenLogKeysRef.current = new Set(keysArray.slice(-250));
    }
    return false;
  }, []);

  const tryParseAndFormatJson = useCallback((text: string): { formatted: string; hasJson: boolean } => {
    let result = text;
    let hasJson = false;

    const jsonPatterns = [
      /\{[^{}]*\}/g,
      /\[[^\[\]]*\]/g,
    ];

    for (const pattern of jsonPatterns) {
      result = result.replace(pattern, (match) => {
        try {
          const parsed = JSON.parse(match);
          hasJson = true;
          return '\n' + JSON.stringify(parsed, null, 2) + '\n';
        } catch {
          return match;
        }
      });
    }

    const jsonPattern = /\{[\s\S]*\}/;
    const jsonMatch = text.match(jsonPattern);
    if (jsonMatch && !hasJson) {
      try {
        const parsed = JSON.parse(jsonMatch[0]);
        hasJson = true;
        result = text.replace(jsonMatch[0], '\n' + JSON.stringify(parsed, null, 2) + '\n');
      } catch {
        // 不是有效的 JSON
      }
    }

    result = result.replace(/\\n/g, '\n');
    result = result.replace(/\\"/g, '"');
    result = result.replace(/\\\\/g, '\\');

    return { formatted: result, hasJson };
  }, []);

  const shouldFilterLog = useCallback((logger: string, message: string): boolean => {
    if (logger.includes('ChatServiceImpl')) {
      if (message.includes('发送流式 chunk')) {
        return true;
      }
      if (message.includes('流式 chunk')) {
        return true;
      }
      if (message.includes('流式处理完成')) {
        return true;
      }
      if (message.includes('SSE 连接完成')) {
        return true;
      }
    }
    if (logger.includes('LogPushService')) {
      if (message.includes('SSE emitter')) {
        return true;
      }
    }
    if (logger.includes('JwtAuthenticationFilter')) {
      if (message.includes('用户已认证')) {
        return true;
      }
    }
    if (logger.includes('ConversationServiceImpl')) {
      if (message.includes('获取用户对话列表')) {
        return true;
      }
    }
    return false;
  }, []);

  const detectLogType = useCallback((message: string): { type: 'request' | 'response' | 'other'; isResponseHeader: boolean } => {
    if (message.includes('请求 JSON:')) {
      return { type: 'request', isResponseHeader: false };
    }
    if (message.includes('响应完整 JSON:')) {
      return { type: 'response', isResponseHeader: true };
    }
    return { type: 'other', isResponseHeader: false };
  }, []);

  const mergeResponseLogs = useCallback((headerLog: LogEntry, contentLog: LogEntry): LogEntry => {
    const mergedMessage = headerLog.message.replace('响应完整 JSON:', '响应完整 JSON:') + '\n' + contentLog.message;
    const { formatted, hasJson } = tryParseAndFormatJson(mergedMessage);
    
    return {
      id: generateUniqueId(),
      timestamp: headerLog.timestamp,
      level: headerLog.level,
      logger: headerLog.logger,
      message: mergedMessage,
      formattedMessage: formatted,
      hasJson: hasJson,
      logType: 'response',
      uniqueKey: headerLog.uniqueKey + '|' + contentLog.uniqueKey,
      identifier: headerLog.identifier,
    };
  }, [tryParseAndFormatJson]);

  const toggleFilter = useCallback((filter: LogFilter) => {
    setFilters(prev => {
      const allSelected = prev.has('request') && prev.has('response') && prev.has('other');
      
      if (filter === 'all') {
        if (allSelected) {
          return new Set<LogFilter>();
        } else {
          return new Set<LogFilter>(['request', 'response', 'other']);
        }
      }
      
      const newFilters = new Set(prev);
      if (newFilters.has(filter)) {
        newFilters.delete(filter);
      } else {
        newFilters.add(filter);
      }
      return newFilters;
    });
  }, []);

  const shouldShowLog = useCallback((log: LogEntry): boolean => {
    return filters.has(log.logType);
  }, [filters]);

  const handleLogData = useCallback((data: string) => {
    const logEntry = parseLogEntry(data);
    
    if (logEntry && !shouldFilterLog(logEntry.logger, logEntry.message)) {
      if (logEntry.logType === 'response' && logEntry.message.includes('响应完整 JSON:') && logEntry.message.trim() === '响应完整 JSON:') {
        pendingResponseHeaderRef.current = logEntry;
        return;
      }
      
      if (pendingResponseHeaderRef.current) {
        const mergedLog = mergeResponseLogs(pendingResponseHeaderRef.current, logEntry);
        pendingResponseHeaderRef.current = null;
        setLogs(prev => {
          const newLogs = [...prev, mergedLog];
          if (newLogs.length > 100) {
            return newLogs.slice(newLogs.length - 100);
          }
          return newLogs;
        });
        return;
      }
      
      setLogs(prev => {
        const newLogs = [...prev, logEntry];
        if (newLogs.length > 100) {
          return newLogs.slice(newLogs.length - 100);
        }
        return newLogs;
      });
    }
  }, [mergeResponseLogs, shouldFilterLog]);

  const connectToLogStream = useCallback((isDev: boolean) => {
    if (isConnectingRef.current) {
      console.log('⚠️ [LogViewer] 已有连接正在建立中，跳过');
      return;
    }
    
    const token = authUtils.getToken();
    if (!isDev && !token) {
      console.log('⚠️ [LogViewer] 未登录，跳过连接');
      return;
    }
    
    console.log('🔌 [LogViewer] 尝试连接日志流 SSE: /api/logs/stream', isDev ? '(开发环境)' : '(生产环境)');
    
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    
    if (eventSourceRef.current) {
      console.log('🔌 [LogViewer] 关闭现有 SSE 连接');
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    
    isConnectingRef.current = true;
    
    const url = isDev ? '/api/logs/stream' : `/api/logs/stream?token=${encodeURIComponent(token || '')}`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;

    eventSource.onopen = () => {
      console.log('✅ [LogViewer] SSE 连接已建立');
      isConnectingRef.current = false;
      setIsConnected(true);
      addLog('INFO', 'LogViewer', '已连接到日志流');
    };

    eventSource.addEventListener('log', (event) => {
      handleLogData((event as MessageEvent).data);
    });

    eventSource.onerror = () => {
      if (eventSourceRef.current !== eventSource) {
        return;
      }

      eventSource.close();
      eventSourceRef.current = null;
      isConnectingRef.current = false;
      setIsConnected(false);
      addLog('ERROR', 'LogViewer', '日志流连接错误，3秒后重试...');
      
      reconnectTimeoutRef.current = window.setTimeout(() => {
        connectToLogStream(isDev);
      }, 3000);
    };
  }, [handleLogData, isDevEnv]);

  useEffect(() => {
    let cancelled = false;
    
    const init = async () => {
      try {
        const response = await fetch('/api/logs/dev-check');
        const isDev = await response.json();
        if (cancelled) return;
        setIsDevEnv(isDev);
        if (isDev) {
          addLog('INFO', 'LogViewer', '开发环境模式，无需登录即可查看日志');
        }
        connectToLogStream(isDev);
      } catch (error) {
        if (cancelled) return;
        console.error('⚠️ [LogViewer] 检查开发环境失败:', error);
        setIsDevEnv(false);
      }
    };
    
    init();

    return () => {
      cancelled = true;
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
      
      if (eventSourceRef.current) {
        console.log('🔌 [LogViewer] 关闭现有 SSE 连接');
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      
      isConnectingRef.current = false;
    };
  }, []);

  const parseLogEntry = (data: string): LogEntry | null => {
    const match = data.match(/^\[([^\]]+)\]\s+\[([^\]]+)\]\s+\[([^\]]+)\]\s+([^\s]+)\s+-\s+([\s\S]+)/);

    if (match) {
      const timestamp = match[1];
      const level = match[2];
      const identifier = match[3];
      const logger = match[4];
      const message = match[5];
      const { formatted, hasJson } = tryParseAndFormatJson(message);
      const { type } = detectLogType(message);
      const uniqueKey = generateLogKey(timestamp, level, logger, message);

      if (isDuplicateLog(uniqueKey)) {
        return null;
      }

      return {
        id: generateUniqueId(),
        timestamp,
        level,
        logger,
        message,
        formattedMessage: formatted,
        hasJson,
        logType: type,
        uniqueKey,
        identifier,
      };
    }

    return null;
  };

  const addLog = (level: string, logger: string, message: string) => {
    const timestamp = new Date().toLocaleTimeString();
    const uniqueKey = generateLogKey(timestamp, level, logger, message);
    
    if (isDuplicateLog(uniqueKey)) {
      return;
    }
    
    const { formatted, hasJson } = tryParseAndFormatJson(message);
    const { type } = detectLogType(message);
    const newLog: LogEntry = {
      id: generateUniqueId(),
      timestamp,
      level,
      logger,
      message,
      formattedMessage: formatted,
      hasJson,
      logType: type,
      uniqueKey,
    };
    setLogs(prev => [...prev, newLog]);
  };

  const clearLogs = () => {
    setLogs([]);
  };

  const getLevelColor = (level: string) => {
    switch (level.toUpperCase()) {
      case 'ERROR':
        return 'text-red-600';
      case 'WARN':
        return 'text-yellow-700';
      case 'INFO':
        return 'text-green-600';
      case 'DEBUG':
        return 'text-gray-600';
      default:
        return 'text-gray-700';
    }
  };

  const getLevelBg = (level: string) => {
    switch (level.toUpperCase()) {
      case 'ERROR':
        return 'bg-red-100 text-red-700';
      case 'WARN':
        return 'bg-yellow-100 text-yellow-700';
      case 'INFO':
        return 'bg-green-100 text-green-700';
      case 'DEBUG':
        return 'bg-gray-100 text-gray-600';
      default:
        return 'bg-gray-100 text-gray-600';
    }
  };

  const filteredLogs = logs.filter(shouldShowLog);

  const getLogTypeLabel = (logType: string): string => {
    switch (logType) {
      case 'request':
        return '请求';
      case 'response':
        return '响应';
      default:
        return '';
    }
  };

  const getLogTypeColor = (logType: string): string => {
    switch (logType) {
      case 'request':
        return 'bg-blue-100 text-blue-700';
      case 'response':
        return 'bg-purple-100 text-purple-700';
      default:
        return '';
    }
  };

  const getLogLevelClass = (level: string): string => {
    switch (level.toUpperCase()) {
      case 'ERROR':
        return 'log-level-error';
      case 'WARN':
        return 'log-level-warn';
      case 'INFO':
        return 'log-level-info';
      case 'DEBUG':
        return 'log-level-debug';
      default:
        return 'log-level-info';
    }
  };

  const getLogLevelTextClass = (level: string): string => {
    switch (level.toUpperCase()) {
      case 'ERROR':
        return 'log-message-error';
      case 'WARN':
        return 'log-message-warn';
      case 'INFO':
        return 'log-message-info';
      case 'DEBUG':
        return 'log-message-debug';
      default:
        return 'log-message-info';
    }
  };

  const getLogTypeClass = (logType: string): string => {
    switch (logType) {
      case 'request':
        return 'log-type-request';
      case 'response':
        return 'log-type-response';
      default:
        return '';
    }
  };

  const getLogIdentity = (log: LogEntry): string => log.identifier || 'SYSTEM';

  if (fullPage) {
    return (
      <div className="log-viewer-full-page">
        <div className="log-viewer-toolbar">
          <div className="flex items-center gap-3">
            <span className={`inline-block w-2 h-2 rounded-full flex-shrink-0 ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
            <span className="font-medium text-sm text-gray-700">
              后端日志 ({filteredLogs.length}/{logs.length}) - {isConnected ? '已连接' : '未连接'}
            </span>
          </div>
          <div className="log-toolbar-controls">
            <label className="log-filter-label log-filter-all">
              <input
                type="checkbox"
                checked={filters.has('request') && filters.has('response') && filters.has('other')}
                onChange={() => toggleFilter('all')}
                className="log-filter-checkbox"
              />
              <span>全部</span>
            </label>
            <label className="log-filter-label log-filter-request">
              <input
                type="checkbox"
                checked={filters.has('request')}
                onChange={() => toggleFilter('request')}
                className="log-filter-checkbox"
              />
              <span>请求JSON</span>
            </label>
            <label className="log-filter-label log-filter-response">
              <input
                type="checkbox"
                checked={filters.has('response')}
                onChange={() => toggleFilter('response')}
                className="log-filter-checkbox"
              />
              <span>响应JSON</span>
            </label>
            <label className="log-filter-label log-filter-other">
              <input
                type="checkbox"
                checked={filters.has('other')}
                onChange={() => toggleFilter('other')}
                className="log-filter-checkbox"
              />
              <span>其他</span>
            </label>
            <span className="log-toolbar-divider"></span>
            <button
              className="log-action-btn"
              onClick={(e) => {
                e.stopPropagation();
                setShowFormatted(!showFormatted);
              }}
              title={showFormatted ? '显示原始格式' : '显示格式化 JSON'}
            >
              {showFormatted ? '格式化' : '原始'}
            </button>
            <button
              className="log-action-btn log-action-clear"
              onClick={(e) => {
                e.stopPropagation();
                clearLogs();
              }}
            >
              清空
            </button>
          </div>
        </div>

        <div className="log-viewer-content">
          {filteredLogs.length === 0 ? (
            <div className="flex items-center justify-center h-24 text-gray-400 text-sm">
              暂无匹配的日志
            </div>
          ) : (
            <div className="divide-y divide-gray-200">
              {filteredLogs.map((log) => (
                <div
                  key={log.id}
                  className="px-4 py-2 hover:bg-gray-100 transition-colors"
                  style={{ width: '100%', boxSizing: 'border-box' }}
                >
                  <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <span className="text-gray-400 text-xs whitespace-nowrap">[{log.timestamp}]</span>
                    <span className={`px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap ${getLevelBg(log.level)}`}>
                      [{log.level}]
                    </span>
                    <span className={`px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap ${getLogIdentity(log) === 'SYSTEM' ? 'bg-gray-100 text-gray-700' : 'bg-orange-100 text-orange-700'}`} title={getLogIdentity(log)}>
                      [{getLogIdentity(log)}]
                    </span>
                    {log.logType !== 'other' && (
                      <span className={`px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap ${getLogTypeColor(log.logType)}`}>
                        {getLogTypeLabel(log.logType)}
                      </span>
                    )}
                    <span className="text-indigo-600 text-xs font-medium" style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={log.logger}>
                      {log.logger.includes('.') ? log.logger.split('.').pop() : log.logger} -
                    </span>
                  </div>
                  <pre
                    className={`text-xs ml-1 ${getLevelColor(log.level)}`}
                    style={{
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      overflowWrap: 'anywhere',
                      maxWidth: '100%',
                      margin: 0,
                    }}
                  >
                    {showFormatted && log.hasJson ? log.formattedMessage : log.message}
                  </pre>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      className="fixed bottom-0 z-50 bg-white border-t border-gray-200 shadow-lg overflow-hidden"
      style={{ left: '320px', width: 'calc((100vw - 320px) / 2)', display: 'none' }}
    >
      <div
        className="flex items-center justify-between px-4 py-2 bg-gray-100 transition-colors"
      >
        <div className="flex items-center gap-3">
          <span className={`inline-block w-2 h-2 rounded-full flex-shrink-0 ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
          <span className="font-medium text-sm text-gray-700">
            后端日志 ({filteredLogs.length}/{logs.length}) - {isConnected ? '已连接' : '未连接'}
          </span>
        </div>
        <div className="log-toolbar-controls">
          <label className="log-filter-label log-filter-all">
            <input
              type="checkbox"
              checked={filters.has('request') && filters.has('response') && filters.has('other')}
              onChange={() => toggleFilter('all')}
              className="log-filter-checkbox"
            />
            <span>全部</span>
          </label>
          <label className="log-filter-label log-filter-request">
            <input
              type="checkbox"
              checked={filters.has('request')}
              onChange={() => toggleFilter('request')}
              className="log-filter-checkbox"
            />
            <span>请求JSON</span>
          </label>
          <label className="log-filter-label log-filter-response">
            <input
              type="checkbox"
              checked={filters.has('response')}
              onChange={() => toggleFilter('response')}
              className="log-filter-checkbox"
            />
            <span>响应JSON</span>
          </label>
          <label className="log-filter-label log-filter-other">
            <input
              type="checkbox"
              checked={filters.has('other')}
              onChange={() => toggleFilter('other')}
              className="log-filter-checkbox"
            />
            <span>其他</span>
          </label>
          <span className="log-toolbar-divider"></span>
          <button
            className="log-action-btn"
            onClick={(e) => {
              e.stopPropagation();
              setShowFormatted(!showFormatted);
            }}
            title={showFormatted ? '显示原始格式' : '显示格式化 JSON'}
          >
            {showFormatted ? '格式化' : '原始'}
          </button>
          <button
            className="log-action-btn log-action-clear"
            onClick={(e) => {
              e.stopPropagation();
              clearLogs();
            }}
          >
            清空
          </button>

        </div>
      </div>

      <div style={{ maxHeight: 'calc(100vh - 50px)', overflowY: 'auto', overflowX: 'hidden', width: '100%' }} className="bg-gray-50">
          {filteredLogs.length === 0 ? (
            <div className="flex items-center justify-center h-24 text-gray-400 text-sm">
              暂无匹配的日志
            </div>
          ) : (
            <div className="divide-y divide-gray-200">
              {filteredLogs.map((log) => (
                <div
                  key={log.id}
                  className="px-4 py-2 hover:bg-gray-100 transition-colors"
                  style={{ width: '100%', boxSizing: 'border-box' }}
                >
                  <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <span className="text-gray-400 text-xs whitespace-nowrap">[{log.timestamp}]</span>
                    <span className={`px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap ${getLevelBg(log.level)}`}>
                      [{log.level}]
                    </span>
                    <span className={`px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap ${getLogIdentity(log) === 'SYSTEM' ? 'bg-gray-100 text-gray-700' : 'bg-orange-100 text-orange-700'}`} title={getLogIdentity(log)}>
                      [{getLogIdentity(log)}]
                    </span>
                    {log.logType !== 'other' && (
                      <span className={`px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap ${getLogTypeColor(log.logType)}`}>
                        {getLogTypeLabel(log.logType)}
                      </span>
                    )}
                    <span className="text-indigo-600 text-xs font-medium" style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={log.logger}>
                      {log.logger.includes('.') ? log.logger.split('.').pop() : log.logger} -
                    </span>
                  </div>
                  <pre
                    className={`text-xs ml-1 ${getLevelColor(log.level)}`}
                    style={{
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      overflowWrap: 'anywhere',
                      maxWidth: '100%',
                      margin: 0,
                    }}
                  >
                    {showFormatted && log.hasJson ? log.formattedMessage : log.message}
                  </pre>
                </div>
              ))}
            </div>
          )}
        </div>
    </div>
  );
};

export default LogViewer;
