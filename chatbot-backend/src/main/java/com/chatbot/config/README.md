# Config 模块

## 模块概述

Config 模块负责系统的配置管理，包含各种配置属性类和 Spring 配置类。该模块集中管理系统的配置项，包括 AI 模型配置、安全配置、Web 配置、Redis 配置等，确保配置的集中管理和类型安全。

## 主要功能

- **模型配置**：OpenAI、Anthropic、Qwen 等 AI 模型的 API 配置
- **安全配置**：Spring Security 配置、JWT 认证过滤器
- **Web 配置**：跨域配置、静态资源配置
- **代理配置**：HTTP 代理服务器配置
- **Redis 配置**：Redis 连接和操作配置
- **日志配置**：SSE 日志推送相关配置
- **MCP 工具配置**：MCP 工具注册配置（已迁移到 mcp 模块）

## 配置类说明

### 模型配置类

#### `OpenAiProperties` OpenAI 配置属性
- 配置前缀：`openai`
- 配置项：
  - `baseUrl`：API 基础 URL（默认：`https://openrouter.ai/api`）
  - `apiKey`：API 密钥
  - `model`：默认模型名称
  - `reasoningEffort`：推理努力程度
  - `temperature`：温度参数（默认：0.7）
  - `maxTokens`：最大输出 token 数（默认：4096）
  - `timeout`：请求超时时间（默认：60000ms）
- 方法：
  - `getCompletionsUrl()`：获取完整的对话补全 URL

#### `AnthropicProperties` Anthropic 配置属性
- 配置前缀：`anthropic`
- 配置项：
  - `baseUrl`：API 基础 URL（默认：`https://api.anthropic.com`）
  - `apiKey`：API 密钥
  - `apiVersion`：API 版本（默认：`2023-06-01`）
  - `model`：默认模型名称
  - `temperature`：温度参数
  - `maxTokens`：最大输出 token 数
  - `timeout`：请求超时时间
- 方法：
  - `getMessagesUrl()`：获取完整的消息 URL

#### `QwenProperties` Qwen 配置属性
- 配置前缀：`qwen`
- 配置项：
  - `baseUrl`：API 基础 URL
  - `apiKey`：API 密钥
  - `model`：默认模型名称
  - `audioModel`：音频模型名称
  - `temperature`：温度参数
  - `maxTokens`：最大输出 token 数
  - `timeout`：请求超时时间
  - `audioEnabled`：音频功能是否启用
- 嵌套类：
  - `AudioModelConfig`：音频模型配置
    - 支持多种音频模型（Qwen Omni、MiMo Omni、GPT-4o Audio）
    - 包含模型名称、显示名称、提供商、支持的模态、价格信息
    - 支持根据模型名称自动匹配配置

### 代理配置类

#### `ProxyProperties` 代理配置属性
- 配置前缀：`proxy`
- 配置项：
  - `enabled`：是否启用代理
  - `host`：代理服务器主机
  - `port`：代理服务器端口
- 方法：
  - `isValid()`：检查代理配置是否有效

### 安全配置类

#### `SecurityConfig` Spring Security 配置
- 配置内容：
  - 禁用 CSRF（适用于 API 服务）
  - 配置会话管理（无状态）
  - 配置 URL 访问权限
    - 公开端点：`/api/auth/**`、`/api/logs/stream`、`/audio/**`
    - 其他端点需要认证
  - 配置 JWT 认证过滤器
  - 配置 CORS（跨域）

#### `JwtAuthenticationFilter` JWT 认证过滤器
- 继承 `OncePerRequestFilter`
- 功能：
  - 从请求头提取 JWT 令牌
  - 验证令牌有效性
  - 解析用户信息
  - 设置 Spring Security 上下文

### Web 配置类

#### `WebConfig` Web 配置
- 实现 `WebMvcConfigurer`
- 配置内容：
  - 跨域配置（CORS）
    - 允许的来源：`http://localhost:3000`、`http://127.0.0.1:3000`
    - 允许的方法：GET、POST、PUT、DELETE、OPTIONS
    - 允许的头：所有
    - 允许凭证：是
  - 资源处理器配置
    - 静态资源映射
    - 音频文件路径映射

### Redis 配置类

#### `RedisConfig` Redis 配置
- 配置内容：
  - Redis 连接工厂
  - RedisTemplate 配置
  - 键值序列化器（String 序列化）

### 日志配置类

#### `SseLogAppender` SSE 日志追加器
- 继承 Logback 的 `UnsynchronizedAppenderBase`
- 功能：
  - 将日志事件转发到 `LogPushService`
  - 支持不同日志级别
  - 处理异常堆栈跟踪
  - 异步推送，不阻塞主线程

#### `SseLogAppenderConfig` SSE 日志追加器配置
- 功能：
  - 初始化 Logback 日志系统
  - 注册 `SseLogAppender`
  - 配置日志级别阈值

### WebClient 配置类

#### `WebClientConfig` WebClient 配置
- 功能：
  - 配置 `WebClient.Builder`
  - 用于 Anthropic 服务的异步 HTTP 请求
  - 配置基础 URL 和默认头

### 配置类（已迁移）

#### `McpToolConfig` MCP 工具配置
- **已迁移到 mcp 模块**
- 功能：
  - 注册内置 MCP 工具
  - 服务启动时初始化

## 配置优先级

Spring Boot 配置优先级（从高到低）：
1. 命令行参数
2. 系统环境变量
3. `application.properties` / `application.yml`
4. `@ConfigurationProperties` 绑定的默认值

## 配置示例

### application.properties 示例
```properties
# OpenAI 配置
openai.base-url=https://openrouter.ai/api
openai.api-key=${OPENAI_API_KEY}
openai.model=openai/gpt-3.5-turbo-0125
openai.temperature=0.7
openai.max-tokens=4096
openai.timeout=60000

# Anthropic 配置
anthropic.base-url=https://api.anthropic.com
anthropic.api-key=${ANTHROPIC_API_KEY}
anthropic.model=claude-sonnet-4-6
anthropic.api-version=2023-06-01

# Qwen 配置
qwen.base-url=https://openrouter.ai/api
qwen.api-key=${QWEN_API_KEY}
qwen.model=qwen/qwen3.5-flash-02-23
qwen.audio-model=xiaomi/mimo-v2-omni
qwen.audio-enabled=true

# JWT 配置
jwt.secret=${JWT_SECRET:your-secret-key}
jwt.expiration=86400000
jwt.issuer=chatbot

# 代理配置
proxy.enabled=false
proxy.host=localhost
proxy.port=7897

# 登录限流配置
rate-limit.max-attempts=5
rate-limit.lock-duration-minutes=15
```

## 安全最佳实践

### 敏感配置
- **API 密钥**：从环境变量读取，不要硬编码
- **JWT 密钥**：使用足够长度的随机字符串
- **生产环境**：使用密钥管理服务（KMS）

### 配置验证
- 使用 `@Validated` 进行配置验证
- 启动时检查必需配置项
- 提供合理的默认值

## 模块依赖

Config 模块被以下模块依赖：
- **LLM 模块**：使用模型配置和代理配置
- **Auth 模块**：使用 JWT 配置和安全配置
- **Log 模块**：使用日志配置
- **Common 模块**：被 JwtProperties 依赖
