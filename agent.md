# LingoBot 后端架构说明

## Windows 本地启动指南，如果是Linux请查看STARTUP.md

### 当前环境

- **PostgreSQL 15**（Windows 服务：`postgresql-x64-15`）
- **Redis**（Windows 服务：`Redis`）
- **PostgreSQL 安装路径**：`C:\Program Files\PostgreSQL\15\bin\`
- **Redis 安装路径**：`C:\Program Files\Redis\`
- **.env 文件位置**：项目根目录

### 环境依赖

- Java 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 15+（Windows 本地安装，非 Docker）
- Redis（Windows 本地安装，非 Docker）

***

### 1. PostgreSQL

验证连接

```powershell
# 使用完整路径连接（默认 postgres 用户无密码）
& "C:\Program Files\PostgreSQL\15\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres -c "SELECT version();"
```

#### 初始化数据库（首次）

```powershell
# 连接到 PostgreSQL（使用默认 postgres 用户）
& "C:\Program Files\PostgreSQL\15\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres
```

进入 `postgres=#` 后执行：

```sql
-- 从 .env 读取 DB_USERNAME 和 DB_PASSWORD
CREATE USER <DB_USERNAME> WITH PASSWORD '<DB_PASSWORD>';
CREATE DATABASE lingobotdb OWNER <DB_USERNAME>;
GRANT ALL PRIVILEGES ON DATABASE lingobotdb TO <DB_USERNAME>;

-- 如果数据库已存在：
ALTER DATABASE lingobotdb OWNER TO <DB_USERNAME>;
GRANT ALL PRIVILEGES ON DATABASE lingobotdb TO <DB_USERNAME>;

\q
```

验证新用户连接：

```powershell
& { $env:PGPASSWORD='<DB_PASSWORD>'; & "C:\Program Files\PostgreSQL\15\bin\psql.exe" -h localhost -p 5432 -U <DB_USERNAME> -d lingobotdb -c "SELECT 1;" }
```

***

### 2. Redis

#### 验证连接

```powershell
redis-cli ping
# 返回 PONG 即正常
```

***

### 3. 后端（Spring Boot）

需要先检查当前是否存在其他的java进程占用端口8080

```powershell
netstat -an | findstr 8080
```

如果存在，请先kill，再启动后端

```powershell
cd lingobot-backend
mvn clean package -DskipTests
java -jar target/lingobot-backend-1.0.0.jar
```

后端启动成功后访问：<http://localhost:8080>

***

### 4. 前端（React + Vite）

```powershell
cd lingobot-frontend
npm install
npm run dev
```

前端启动成功后访问：<http://localhost:3000>

***

### 启动顺序

```
PostgreSQL → Redis → 后端 → 前端
```

实际操作步骤：

1. 确保 PostgreSQL 服务运行（`postgresql-x64-15`）
2. 确保 Redis 服务运行（`Redis`）
3. 编译并运行后端
4. 运行前端

***

### 常见问题排查

1. **PostgreSQL 连接失败**：
   - 检查服务是否启动：`Get-Service postgresql-x64-15`
   - 确认端口 5432 未被占用
   - 验证用户和数据库是否已创建
2. **Redis 连接失败**：
   - 检查服务是否启动：`Get-Service Redis`
   - 端口 6379 是否可用

***

## 异常管理

### 总体原则

后端所有异常统一由 `GlobalExceptionHandler`（`@RestControllerAdvice`）捕获，转为标准 `ApiResponse<Void>` 格式返回给前端。Controller 内部**不应**自己 try-catch 并返回错误响应，除非需要区分同一异常类型下的不同业务分支。

### 异常分类与 HTTP 状态码

| 异常类                            | 触发场景                          | HTTP 状态码 |
| ------------------------------ | ----------------------------- | -------- |
| `ChatException`                | 聊天相关业务异常（对话不存在、消息为空等）         | 400      |
| `AuthException`                | 认证与用户相关异常（用户名已存在、密码错误等）      | 400      |
| `BalanceException`             | 用户余额相关异常（余额不足以支付等）            | 402      |
| `BusinessException`            | 通用业务异常（无明确分类的业务错误）            | 400      |
| `IllegalArgumentException`     | 请求参数校验失败                      | 400      |
| `IllegalStateException`        | 限流触发（如登录频率过高）                 | 429      |
| `AuthenticationException`      | JWT 无效或未登录，Spring Security 抛出 | 401      |
| `AccessDeniedException`        | 已登录但权限不足（如普通用户访问管理员接口）        | 403      |
| `AsyncRequestTimeoutException` | SSE 长连接正常断开，无需响应体             | —        |
| `RuntimeException`（兜底）         | 未被以上规则覆盖的运行时异常                | 500      |
| `Exception`（兜底）                | 所有其他未知异常                      | 500      |

### 自定义异常类

所有自定义异常均继承 `BaseException`（持有 `ErrorCode` 字段、提供 `getErrorCode()`），并采用**静态工厂方法模式**提供语义化的创建方式。

#### `ChatException`

聊天相关业务异常，用于对话、消息、AI 响应等场景：

```java
// 使用语义化工厂方法
throw ChatException.conversationNotFound();
throw ChatException.messageContentEmpty();
throw ChatException.aiResponseEmpty();
throw ChatException.toolCallExceeded();
throw ChatException.audioDataInvalid();

// 自定义消息
throw ChatException.conversationNotFound("对话不存在: " + conversationId);

// 通用方法
throw ChatException.badRequest("参数错误");
throw ChatException.badRequest("自定义错误消息");

// 兜底方式：直接指定任意 ErrorCode
throw ChatException.of(ErrorCode.SOME_ERROR_CODE);
throw ChatException.of(ErrorCode.SOME_ERROR_CODE, "自定义消息");
```

#### `AuthException`

认证与用户相关异常，用于注册、登录、账户管理等场景：

```java
// 使用语义化工厂方法
throw AuthException.usernameExists();
throw AuthException.usernameOrPasswordError();
throw AuthException.emailAlreadyRegistered();
throw AuthException.invalidEmail();
throw AuthException.invalidVerificationCode();
throw AuthException.accountLocked();
throw AuthException.userNotFound();
throw AuthException.passwordIncorrect();
throw AuthException.currentPasswordIncorrect();
throw AuthException.newPasswordSameAsCurrent();
throw AuthException.passwordsNotMatch();

// 自定义消息
throw AuthException.usernameExists("该昵称已被使用");
throw AuthException.userNotFound("用户不存在: " + userId);

// 通用方法
throw AuthException.badRequest("用户名不能为空");
throw AuthException.tooManyRequests("登录尝试次数过多，请稍后再试");

// 兜底方式：直接指定任意 ErrorCode
throw AuthException.of(ErrorCode.USERNAME_EXISTS);
throw AuthException.of(ErrorCode.USERNAME_EXISTS, "自定义消息");
```

#### `BalanceException`

余额相关异常（如余额不足等）：

```java
// 使用默认消息（来自 ErrorCode.PAYMENT_REQUIRED）
throw BalanceException.paymentRequired();

// 自定义消息
throw BalanceException.paymentRequired("余额不足，请充值");

// 语义化工厂方法：余额不足
throw BalanceException.insufficientBalance();

// 语义化工厂方法，自定义消息（可包含具体余额信息，由调用方传入）
throw BalanceException.insufficientBalance("余额不足。当前余额: " + currentBalance + "，需要: " + requiredCost);

// 兜底方式：直接指定任意 ErrorCode
throw BalanceException.of(ErrorCode.PAYMENT_REQUIRED);
throw BalanceException.of(ErrorCode.PAYMENT_REQUIRED, "自定义消息");
```

`GlobalExceptionHandler` 捕获后返回 HTTP 402，响应体 `code` 为 `ErrorCode.PAYMENT_REQUIRED (402)`。

#### `BusinessException`

通用业务异常，用于没有明确分类的业务错误：

```java
// 通用方法
throw BusinessException.badRequest("参数错误");
throw BusinessException.notFound("资源不存在");
throw BusinessException.tooManyRequests("请求过于频繁");

// 兜底方式：直接指定任意 ErrorCode
throw BusinessException.of(ErrorCode.SOME_ERROR_CODE);
throw BusinessException.of(ErrorCode.SOME_ERROR_CODE, "自定义消息");
```

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

1. **在 `ErrorCode` 枚举中添加对应的业务错误码**
   - 业务错误码使用 `1xxx` 段，按业务领域分段：
     - 通用业务：`1000-1099`
     - 用户与认证：`1100-1199`
     - 聊天相关：`1200-1299`
   - HTTP 级别错误码直接使用标准 HTTP 状态码（如 `BAD_REQUEST(400, "...")`）
   - 位置：`src/main/java/com/lingobot/infrastructure/common/response/ErrorCode.java`

2. **在 `infrastructure/common/exception/` 下新建异常类**（如果需要新的业务领域）
   - 继承 `BaseException`
   - 采用静态工厂方法模式（与现有异常类风格一致）
   - 提供 `of()` 兜底方法和语义化工厂方法

3. **在 `GlobalExceptionHandler` 中添加对应的异常处理器**
   - 使用 `@ExceptionHandler` 注解指定异常类型
   - 设置对应的 HTTP 状态码和 `ErrorCode`
   - 位置：`src/main/java/com/lingobot/infrastructure/common/exception/GlobalExceptionHandler.java`

4. **更新本文档**：在异常分类表格中添加新异常，在"自定义异常类"部分补充使用示例

```java
// 1. 在 ErrorCode 添加
SOME_NEW_CODE(1009, "...描述..."),

// 2. 新建异常类（继承 BaseException）
public class SomeNewException extends BaseException {
    private SomeNewException(ErrorCode errorCode) { super(errorCode); }
    private SomeNewException(ErrorCode errorCode, String customMessage) { super(errorCode, customMessage); }

    public static SomeNewException of(ErrorCode errorCode) { return new SomeNewException(errorCode); }
    public static SomeNewException of(ErrorCode errorCode, String msg) { return new SomeNewException(errorCode, msg); }

    public static SomeNewException someReason() { return new SomeNewException(ErrorCode.SOME_NEW_CODE); }
    public static SomeNewException someReason(String msg) { return new SomeNewException(ErrorCode.SOME_NEW_CODE, msg); }
}

// 3. 在 GlobalExceptionHandler 添加
@ExceptionHandler(SomeNewException.class)
public ResponseEntity<ApiResponse<Void>> handleSomeNewException(SomeNewException ex) {
    log.warn("...: {}", ex.getMessage());
    return ResponseEntity.status(extractHttpStatus(ex.getErrorCode()))
            .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
}
```

