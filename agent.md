# LingoBot 后端架构说明

## 异常管理

### 总体原则

后端所有异常统一由 `GlobalExceptionHandler`（`@RestControllerAdvice`）捕获，转为标准 `ApiResponse<Void>` 格式返回给前端。Controller 内部**不应**自己 try-catch 并返回错误响应，除非需要区分同一异常类型下的不同业务分支。

### 异常分类与 HTTP 状态码

| 异常类 | 触发场景 | HTTP 状态码 |
|--------|---------|-------------|
| `ChatException` | 业务逻辑主动抛出（对话不存在、消息为空等） | 400 |
| `IllegalArgumentException` | 请求参数校验失败 | 400 |
| `IllegalStateException` | 限流触发（如登录频率过高） | 429 |
| `AuthenticationException` | JWT 无效或未登录，Spring Security 抛出 | 401 |
| `AccessDeniedException` | 已登录但权限不足（如普通用户访问管理员接口） | 403 |
| `InsufficientBalanceException` | 用户余额不足以支付本次 API 调用 | 402 |
| `AsyncRequestTimeoutException` | SSE 长连接正常断开，无需响应体 | — |
| `RuntimeException`（兜底） | 未被以上规则覆盖的运行时异常 | 500 |
| `Exception`（兜底） | 所有其他未知异常 | 500 |

### 自定义异常类

#### `ChatException`

通用业务异常，用于对话、消息相关的业务规则违反场景。

```java
// 对话不存在
throw new ChatException("对话不存在或无权访问");

// 包含原始原因
throw new ChatException("AI 响应解析失败", cause);
```

#### `InsufficientBalanceException`

余额不足时抛出，支持三种构造方式：

```java
// 仅传 message
throw new InsufficientBalanceException("余额不足");

// 传入当前余额和所需费用（message 自动生成）
throw new InsufficientBalanceException(currentBalance, requiredCost);

// 自定义 message + 数值（用于日志或调试）
throw new InsufficientBalanceException("套餐余额不足", currentBalance, requiredCost);
```

`GlobalExceptionHandler` 捕获后返回 HTTP 402，响应体 `code` 为 `ErrorCode.PAYMENT_REQUIRED (402)`。

### 响应体格式

所有异常均包装为 `ApiResponse<Void>`：

```json
{
  "code": 400,
  "message": "对话不存在或无权访问",
  "data": null,
  "timestamp": "2026-05-08T12:00:00"
}
```

`code` 字段来自 `ErrorCode` 枚举，禁止在代码中硬编码数字。

### 何时抛异常 vs 何时返回错误响应

- **优先抛异常**：让 `GlobalExceptionHandler` 统一处理，减少 Controller 的 try-catch 噪声。
- **允许在 Controller 手动返回错误**：需要携带特定 HTTP 状态码且异常类型不好区分时（如 404 vs 400 均可能来自同一业务方法）。
- **禁止**：在 Controller 或 Service 中直接 `return ApiResponse.error(400, "...")` 硬编码数字错误码，必须走 `ErrorCode` 枚举。

### 扩展新异常

1. 在 `infrastructure/common/exception/` 下新建异常类，继承 `RuntimeException`。
2. 在 `GlobalExceptionHandler` 中添加对应的 `@ExceptionHandler` 方法，指定 HTTP 状态码和 `ErrorCode`。
3. 在 `ErrorCode` 枚举中添加对应的业务错误码（业务级别用 1xxx 段）。

```java
// 1. 新建异常类
public class SomeNewException extends RuntimeException {
    public SomeNewException(String message) { super(message); }
}

// 2. 在 GlobalExceptionHandler 添加
@ExceptionHandler(SomeNewException.class)
public ResponseEntity<ApiResponse<Void>> handleSomeNewException(SomeNewException ex) {
    log.warn("...: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ErrorCode.SOME_NEW_CODE, ex.getMessage()));
}

// 3. 在 ErrorCode 添加
SOME_NEW_CODE(1009, "...描述..."),
```
