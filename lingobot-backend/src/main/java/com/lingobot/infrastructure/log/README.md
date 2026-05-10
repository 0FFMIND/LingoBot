# Log Infrastructure

## 日志流转

`com.lingobot` 包内的应用日志通过自定义 Logback Appender 推送到前端日志面板。

```
应用代码 log.info/debug/error
  │
  ▼
Logback 日志事件
  │
  ▼
SseLogAppender
  │
  ├─ 过滤非 com.lingobot 日志
  │
  ├─ 跳过 LogPushService 自身日志，避免递归推送
  │
  ├─ 从 SecurityContext 获取当前用户
  │    ├─ 已认证用户 → [username]
  │    └─ 无用户上下文 → [SYSTEM]
  │
  └─ 调用 LogPushService.pushLog()
          │
          ├─ 拼装前端展示格式
          ├─ 写入最近 200 条历史日志
          └─ 推送到所有活跃 SSE 连接
                    │
                    ▼
              前端 /api/logs/stream
```

## 日志格式

前端日志流统一使用以下格式：

```text
[timestamp] [level] [username] logger - message
[timestamp] [level] [SYSTEM] logger - message
```

## 各类职责

| 类 | 职责 |
|----|------|
| `SseLogAppender` | 接收 Logback 事件，提取级别、logger、消息、异常和用户标识 |
| `LogPushService` | 格式化日志，维护历史日志，向 SSE 连接推送日志 |
| `LogController` | 提供 `/api/logs/stream` SSE 连接入口 |
| `SseLogAppenderConfig` | 在 Spring 启动后把 ApplicationContext 注入到 Appender |

## 约定

- **SSE 认证**：前端使用 `EventSource`，通过 `/api/logs/stream?token=...` 传递 JWT。
- **用户标识**：HTTP 请求线程中能取到认证用户时显示用户名，否则显示 `[SYSTEM]`。
- **历史日志**：新连接会收到最近 200 条日志。
- **异常日志**：如果日志事件包含异常，后端会把异常消息和堆栈拼入日志内容。
- **过滤范围**：只推送 `com.lingobot` 包日志，避免把第三方框架日志刷到前端。
