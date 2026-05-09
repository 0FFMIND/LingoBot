# Common Infrastructure

## 响应体流转

所有 API 响应统一通过 `ApiResponse<T>` 包装，`ErrorCode` 枚举管理所有错误码。

```
请求
  │
  ├─ 正常路径 ─► Controller
  │                  │
  │                  ├─ 成功 ─► ResponseEntity + ApiResponse.success() / created()
  │                  │          HTTP 2xx，body 中 code 与 HTTP 状态码一致
  │                  │
  │                  └─ 业务失败 ─► ResponseEntity.badRequest() + ApiResponse.error(ErrorCode, msg)
  │                                 HTTP 4xx，body 中 code 来自 ErrorCode 枚举
  │
  └─ 异常路径 ─► GlobalExceptionHandler（@RestControllerAdvice 自动拦截）
                     │
                     ├─ ChatException / AuthException / BalanceException / BusinessException
                     │    └─ 均继承 BaseException，携带 ErrorCode
                     │         extractHttpStatus：400-599 直接用，1xxx 业务码→400，其余→500
                     │
                     ├─ Spring Security 异常
                     │    ├─ AuthenticationException → HTTP 401
                     │    └─ AccessDeniedException   → HTTP 403
                     │
                     ├─ IllegalStateException（限流）→ HTTP 429
                     ├─ IllegalArgumentException（参数校验）→ HTTP 400
                     ├─ AsyncRequestTimeoutException（SSE 正常断开）→ 无响应体
                     └─ RuntimeException / Exception（兜底）→ HTTP 500
```

## 各类职责

| 类 | 职责 |
|----|------|
| `ApiResponse<T>` | 统一响应体，包含 code / message / data / timestamp |
| `ErrorCode` | 所有错误码的唯一来源，禁止在代码中硬编码数字 |
| `GlobalExceptionHandler` | 捕获未处理异常，转为统一 ApiResponse 格式返回 |

## 约定

- **成功**：`ApiResponse.success()` 或 `ApiResponse.created()`
- **业务失败**：`ApiResponse.error(ErrorCode, message)`，code 来自枚举
- **DELETE**：返回 `ResponseEntity.noContent().build()`，HTTP 204，无响应体
- **上游服务（如 OpenRouter）动态错误**：`ApiResponse.errorUpstream(code, message)`，仅允许 5xx
- 前端按响应体 `code` 判断业务结果，同时获得 `message` 字符串内容
