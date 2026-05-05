# LLM 模块

## 模块概述

LLM (Large Language Model) 模块是聊天机器人的核心 AI 模型接入层，负责与多种大型语言模型进行交互。该模块提供了统一的 API 接口，支持多种主流 AI 模型提供商（OpenAI、Anthropic、Qwen 等），并封装了流式输出、工具调用、多模态输入等复杂功能。

## 主要功能

- **多模型支持**：统一接口对接多种 AI 模型提供商
- **流式输出**：支持 Server-Sent Events (SSE) 流式响应
- **工具调用**：支持 Function Calling 和提示词驱动的工具调用
- **模型路由**：根据配置自动路由到相应的模型服务
- **多模态输入**：支持音频输入（语音理解）
- **错误处理**：统一的异常处理和错误信息格式化

## 模块结构

### service/ 服务层

#### `ModelRouterService` 模型路由服务
- 统一入口，根据模型类型路由到相应的服务
- 支持带音频的流式请求
- 支持带工具调用的请求

#### `OpenAiService` OpenAI 模型服务
- 对接 OpenAI 兼容 API（包括 OpenRouter 等兼容平台）
- 支持文本对话和流式输出
- 支持提示词驱动的工具调用
- 支持代理配置

#### `AnthropicService` Anthropic 模型服务
- 对接 Claude API
- 支持原生工具调用格式
- 支持 WebClient 异步 HTTP 客户端
- 支持系统提示词分离

#### `QwenService` Qwen 模型服务
- 对接 Qwen 兼容 API
- 支持多模态音频输入
- 集成音频格式转换
- 支持多种音频模型配置

### dto/ 数据传输对象

#### openai/ OpenAI 格式 DTO
- `OpenAiChatMessage`：聊天消息
  - 支持文本、工具调用、多模态内容
  - 包含角色、内容、工具调用 ID 等字段

- `OpenAiChatRequest`：聊天请求
  - 模型选择、消息列表、参数配置
  - 流式标记、工具列表

- `OpenAiChatResponse`：聊天响应
  - 响应内容、使用情况统计
  - 完成原因（停止、工具调用等）

- `OpenAiTool`：工具定义
  - 函数名称、描述、参数 Schema

- `OpenAiStreamResponse`：流式响应
  - 增量内容 Delta
  - 流式处理专用格式

- `OpenAiResponsesRequest` / `OpenAiResponsesResponse`：Responses API 格式
  - 支持新的 OpenAI Responses API
  - 支持推理配置

#### anthropic/ Anthropic 格式 DTO
- `AnthropicChatRequest`：Claude 聊天请求
  - 分离的系统提示词
  - 消息列表、工具定义

- `AnthropicChatResponse`：Claude 聊天响应
  - 多部分内容（文本、工具调用）
  - 使用情况统计

- `AnthropicMessage`：Claude 消息
  - 支持多种内容类型
  - 工具使用、工具结果专用格式

- `AnthropicTool`：Claude 工具定义
  - 输入 Schema 格式

## 模型配置

### 支持的模型类型

1. **GPT 模型**（OpenAI 兼容）
   - 前缀：`gpt`、`openai`、`gpt-`
   - 提供商：OpenAI、OpenRouter 等

2. **Claude 模型**（Anthropic）
   - 前缀：`claude`、`anthropic`
   - 提供商：Anthropic

3. **Qwen 模型**（阿里通义千问）
   - 前缀：`qwen`、`tongyi`
   - 提供商：阿里云、OpenRouter 等

### 配置属性

每个模型服务支持以下配置（通过 application.properties 或环境变量）：

- `baseUrl`：API 基础 URL
- `apiKey`：API 密钥
- `model`：默认模型名称
- `temperature`：温度参数（0.0-2.0）
- `maxTokens`：最大输出 token 数
- `timeout`：请求超时时间（毫秒）

## 工具调用机制

模块支持两种工具调用模式：

### 1. 原生工具调用 (Native Function Calling)
- 适用于支持原生工具调用的模型（如 Claude）
- 直接发送工具定义给 API
- API 返回结构化的工具调用请求

### 2. 提示词驱动工具调用 (Prompt-based Tool Calling)
- 适用于不支持原生工具调用的模型
- 在系统提示词中描述可用工具
- 解析模型输出中的工具调用标记
- 格式：`TOOL_CALL: { "name": "<工具名>", "arguments": {...} }`

## 流式输出

所有模型服务都支持流式输出：

- 使用 SSE (Server-Sent Events) 协议
- 客户端可以实时接收响应片段
- 支持错误处理和连接管理
- 响应完成后返回完整消息

## 多模态支持

Qwen 服务支持多模态音频输入：

- 接收 Base64 编码的音频数据
- 自动检测和转换音频格式
- 支持多种音频模型（Qwen Omni、MiMo Omni、GPT-4o Audio）
- 提供模型价格参考信息
