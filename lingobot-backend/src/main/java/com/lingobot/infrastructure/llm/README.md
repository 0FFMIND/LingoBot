# LLM Module

## 模块概述

大语言模型（LLM）交互模块负责封装与 AI 模型 API 的所有通信，提供统一的调用接口。

主要职责：

- 统一请求封装：所有 LLM 调用统一使用 OpenAI 兼容格式
- 多模态支持：文本、音频、图片的单模态和混合模态输入
- 响应模式：非流式（一次性返回）和流式（SSE 逐字返回）
- 工具调用：支持原生 OpenAI tool_calls 格式和兼容 TOOL_CALL: 标记格式
- 模型路由：根据请求类型自动选择支持的模型，不支持时自动回退
- 错误处理：API 调用失败自动重试（瞬时错误），统一异常封装
- 格式转换：音频/图片格式自动规范化，多模态消息自动构建

## API / 调用流转

LLM 服务不直接对外暴露 HTTP 接口，而是通过 `ModelRouterService` 作为业务层的统一入口。

```text
业务层（Chat / Vocabulary 等）
  └─ ModelRouterService.xxx() 【统一入口，日志记录】
       └─ LlmService.xxx() 【实际执行调用】
            ├─ 参数准备（模型名转换、消息格式处理）
            ├─ 格式检查与转换（音频/图片规范化）
            ├─ 模型能力检测（不支持时自动回退）
            ├─ 调用 LLM API（Java HttpClient）
            │    ├─ 非流式：POST /v1/chat/completions，同步等待响应
            │    └─ 流式：POST /v1/chat/completions，SSE 逐行解析
            ├─ 工具调用解析（如果启用 tools）
            └─ 返回结果
```

## 非流式聊天 Workflow

普通非流式文本请求，等待完整响应后返回。

```text
业务层调用 ModelRouterService.chat(model, messages)
  └─ ModelRouterService.chat()
       ├─ 记录路由日志
       └─ LlmService.chat()
            └─ LlmService.chatWithTools(model, messages, null) 【复用带工具方法】
                 ├─ llmProperties.getFullModelName(model) → 转换为完整模型名
                 ├─ translateForModel(messages, null) → 消息格式转换（当前直接返回）
                 ├─ 构建 OpenAiChatRequest
                 │    ├─ model、messages、temperature、maxTokens
                 │    ├─ stream=false
                 │    └─ tools=null, toolChoice=null
                 ├─ logRequest() → 脱敏记录请求日志
                 └─ callApi(request) 【带重试的 API 调用】
                      └─ doCallApi()
                           ├─ 序列化请求为 JSON
                           ├─ 构造 HttpRequest（POST /v1/chat/completions）
                           ├─ 发送请求（同步）
                           ├─ 检查 HTTP 状态码（>=400 抛出异常）
                           └─ 反序列化为 OpenAiChatResponse
                 ├─ 解析响应：choices[0].message.content
                 └─ 返回纯文本内容
```

## 流式聊天 Workflow

通过 SSE（Server-Sent Events）逐字返回响应，适合实时聊天场景。

```text
业务层调用 ModelRouterService.chatStream(model, messages)
  └─ ModelRouterService.chatStream()
       ├─ 记录路由日志
       └─ LlmService.chatStream()
            ├─ llmProperties.getFullModelName(model) → 转换为完整模型名
            ├─ translateForModel(messages, null) → 消息格式转换
            └─ chatStreamInternalWithModel(messages, fullModelName) 【内部流式实现】
                 ├─ Flux.create(emitter -> { ... }) 【创建响应式流】
                 │    ├─ 构建 OpenAiChatRequest（stream=true）
                 │    ├─ 构造 HttpRequest（Accept: text/event-stream）
                 │    ├─ 发送请求（同步获取 InputStream）
                 │    ├─ 检查 HTTP 状态码
                 │    ├─ 逐行读取响应
                 │    │    ├─ 跳过空行和 [DONE] 标记
                 │    │    ├─ 解析 data: { ... } JSON
                 │    │    ├─ 提取 choices[0].delta.content
                 │    │    └─ emitter.next(text) → 发射文本片段
                 │    ├─ 检查是否有内容返回（无内容抛出异常）
                 │    └─ emitter.complete() → 流结束
                 └─ subscribeOn(boundedElastic) → 异步调度，避免阻塞
```

### 多模态（音频）流式 Workflow

带音频输入的流式聊天，自动处理格式转换和模型回退。

```text
业务层调用 ModelRouterService.chatStreamWithAudio(model, messages, audioData, audioFormat)
  └─ ModelRouterService.chatStreamWithAudio()
       ├─ 记录路由日志
       ├─ 检查 llmProperties.isAudioEnabled() → 未启用回退到文本
       └─ LlmService.chatStreamWithAudio(messages, audioData, audioFormat, model)
            ├─ audioConversionService.convertIfNeeded() → 音频格式转换（如需）
            ├─ normalizeAudioFormat(audioFormat) → 规范化格式名
            ├─ llmProperties.getFullModelName(model) → 转换完整模型名
            ├─ llmProperties.getAudioModelConfigForModel(model) → 获取模型能力配置
            │    └─ 不支持音频 → 切换到 audioModel，更新配置
            ├─ prepareAudioMessages(messages, finalAudioData, finalFormat)
            │    └─ 遍历消息，将纯文本 user 消息转换为多模态消息（文本+音频）
            └─ chatStreamInternalWithModel(sendMessages, fullModelName) → 复用流式实现
```

### 多模态（图片）流式 Workflow

```text
ModelRouterService.chatStreamWithImage(...)
  └─ LlmService.chatStreamWithImage(...)
       ├─ normalizeImageFormat(imageFormat) → 规范化格式名（png/jpeg/gif/webp/bmp）
       ├─ prepareImageMessages(messages, imageData, imageFormat)
       │    └─ 将纯文本 user 消息转换为多模态消息（文本+图片，data URL 格式）
       └─ chatStreamInternalWithModel(...) → 复用流式实现
```

### 混合多模态（音频+图片）流式 Workflow

```text
ModelRouterService.chatStreamWithAudioAndImage(...)
  └─ LlmService.chatStreamWithAudioAndImage(...)
       ├─ 音频格式转换、规范化
       ├─ 图片格式规范化
       ├─ 模型能力检查（不支持音频自动回退）
       ├─ prepareMultiModalMessages(...)
       │    └─ 将纯文本 user 消息转换为多模态消息（文本+音频+图片）
       └─ chatStreamInternalWithModel(...) → 复用流式实现
```

## 工具调用 Workflow

支持模型调用外部工具，两种格式兼容：
1. 原生 OpenAI tool_calls 格式（推荐，优先）
2. 兼容 TOOL_CALL: 标记格式（用于不支持原生工具调用的模型）

```text
业务层调用 ModelRouterService.chatWithTools(model, messages, tools)
  └─ ModelRouterService.chatWithTools()
       ├─ 记录路由日志
       └─ LlmService.chatWithTools(model, messages, tools)
            ├─ hasTools = true
            ├─ llmProperties.getFullModelName(model)
            ├─ translateForModel(messages, tools) → 消息格式转换
            ├─ 构建 OpenAiChatRequest
            │    ├─ stream=false
            │    ├─ tools=tools
            │    └─ toolChoice="auto"
            ├─ callApi(request) → 发送请求
            └─ parseToolCallFromResponse(response) 【工具调用解析】
                 ├─ 检查是否有原生 tool_calls → 有则直接返回
                 ├─ 检查 content 中是否有 TOOL_CALL: 标记
                 │    ├─ 解析 JSON：{name, arguments} 或简化格式
                 │    ├─ 构造 ToolCall 对象
                 │    └─ 包装为 OpenAiChatResponse（finishReason="tool_calls"）
                 └─ 返回响应（可能包含 tool_calls）
```

业务层收到响应后：
- 如果 `finishReason == "tool_calls"`：执行工具调用，将结果以 tool 角色消息追加到 messages，再次调用 chatWithTools
- 否则：直接使用文本内容

## 重要类

| 类 | 作用 |
|----|------|
| `ModelRouterService` | LLM 调用统一入口，负责日志记录和请求路由 |
| `LlmService` | LLM 交互核心实现，包含 API 调用、格式转换、工具解析 |
| `OpenAiChatMessage` | 聊天消息 DTO，支持纯文本和多模态（音频/图片） |
| `OpenAiChatRequest` | 聊天请求 DTO，对应 OpenAI API 请求体 |
| `OpenAiChatResponse` | 聊天响应 DTO，对应 OpenAI API 响应体 |
| `OpenAiStreamResponse` | 流式响应 DTO（当前未直接使用，内部手动解析） |
| `OpenAiTool` | 工具定义 DTO，遵循 JSON Schema 规范 |

## 关键约定

- **OpenAI 兼容格式**：所有请求和响应统一使用 OpenAI 格式，便于切换不同模型提供商
- **模型短名**：外部使用短名（如 "gpt4o-mini"），内部通过 `llmProperties.getFullModelName()` 转换为完整名
- **音频自动转换**：通过 `AudioConversionService` 自动将不支持的格式转换为支持的格式
- **自动回退**：指定模型不支持音频/图片时，自动切换到配置的专用模型
- **流式调度**：所有流式请求运行在 `boundedElastic` 调度器上，避免阻塞 Netty 事件循环
- **重试机制**：非流式请求对瞬时连接错误（"header parser received no bytes"）最多重试 3 次
- **工具调用兼容**：同时支持原生 tool_calls 格式和 TOOL_CALL: 标记格式，优先使用原生格式
- **多模态消息构建**：通过静态工厂方法 `createTextMessage/createAudioMessage/createImageMessage/createMultiModalMessage` 创建
- **格式规范化**：音频/图片格式自动映射为标准格式名，未知格式使用默认值（wav/png）
