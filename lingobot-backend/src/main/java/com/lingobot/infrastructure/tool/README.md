# Tool Module

## 模块概述

工具模块负责管理系统中所有可被 AI 调用的工具，提供工具注册、查询、调用和格式转换能力。

主要职责：

- 工具注册（Tool Registration）：应用启动时自动扫描并注册所有 ToolHandler 实现
- 工具查询（Tool Query）：根据对话模式返回可用的工具列表
- 工具调用（Tool Execution）：接收 AI 发起的工具调用请求，转发给对应处理器执行
- 格式转换（Format Conversion）：支持将内部 ToolDefinition 转换为 OpenAI Function Calling 格式
- 模式控制（Mode Control）：根据对话模式（chat/agent/vocabulary）过滤可用工具

## 核心概念

### ToolCategory（工具类别）

控制工具在不同模式下的可见性：

| 类别 | 说明 | 可用模式 |
|------|------|----------|
| `ONE_TIME` | 一次性工具，执行一次即完成 | chat、agent、vocabulary |
| `AGENT_ONLY` | Agent 专用工具，支持多轮调用链 | agent |
| `ALL` | 通用型工具，任何模式都可用 | 所有模式 |

### ToolMode（对话模式）

系统支持的对话模式，决定哪些工具可用：

| 模式 | 说明 | 可用工具类别 |
|------|------|--------------|
| `chat` | 普通聊天模式 | ONE_TIME、ALL |
| `agent` | Agent 模式，支持多轮工具调用链 | 所有类别 |
| `vocabulary` | 词汇学习专有模式 | ONE_TIME、ALL（需 supportsMode 支持） |

## API / 调用流转

工具接口统一挂载在 `/api/tools` 路径下，响应体通过 `ResponseEntity<T>` 包装。

```text
请求
  ├─ GET /api/tools → 获取所有可用工具列表
  │    └─ ToolController.listTools()
  │         └─ ToolService.listTools()
  │              └─ ToolRegistry.getAllTools()
  │                   └─ 返回所有已注册的 ToolDefinition
  │
  └─ POST /api/tools/call → 调用单个工具
       └─ ToolController.callTool()
            └─ ToolService.callTool()
                 └─ ToolRegistry.executeTool()
                      ├─ 根据 name 查找 ToolHandler
                      ├─ 调用 handler.execute(call)
                      └─ 返回 ToolResult
```

## 工具注册流程

应用启动时，通过 Spring 自动装配完成工具注册：

```text
Spring 容器启动
  └─ 扫描所有实现 ToolHandler 接口的 Bean
       ├─ VocabularyToolAdapter（词汇学习工具）
       └─ （其他工具适配器...）
            └─ ToolRegistry.registerTool(handler)
                 ├─ 将 handler 放入 toolHandlers Map（name → handler）
                 ├─ 将 handler.getToolDefinition() 加入 toolDefinitions 列表
                 └─ 打印注册日志：Registered tool: {name} (category: {category})
```

## 工具调用流程

```text
AI 发起工具调用请求（ToolCall）
  └─ ToolController.callTool(ToolCall call)
       └─ ToolService.callTool(call)
            └─ ToolRegistry.executeTool(call)
                 ├─ 根据 call.getName() 查找 ToolHandler
                 │    ├─ 未找到 → 返回 ToolResult(success=false, error="Tool not found")
                 │    └─ 找到 → 继续执行
                 ├─ 调用 handler.execute(call)
                 │    ├─ 正常返回 → 包装为 ToolResult(success=true, content=...)
                 │    └─ 抛出异常 → 捕获并返回 ToolResult(success=false, error=...)
                 └─ 返回 ToolResult
```

### VocabularyToolAdapter 执行流程

```text
VocabularyToolAdapter.execute(ToolCall call)
  ├─ 从 call.getArguments() 中提取 action 参数（默认 display_flashcard）
  ├─ 解析 conversationId（String → Long）
  ├─ 根据 action 分发到对应业务方法：
  │    ├─ display_flashcard → vocabularyToolService.displayFlashcard()
  │    ├─ display_flashcard_batch → vocabularyToolService.displayFlashcardBatch()
  │    ├─ check_meaning_accuracy → vocabularyToolService.checkMeaningAccuracy()
  │    └─ analyze_sentence → vocabularyToolService.analyzeSentence()
  ├─ 将返回的 Map<String, Object> 序列化为 JSON 字符串
  └─ 包装为 ToolResult(success=true, content=[{type="text", text=json}])
```

### 词汇工具参数窄化

在 vocabulary 模式下，根据当前操作类型窄化工具参数定义，减少 AI 的输出空间：

```text
VocabularyToolAdapter.narrowVocabularyTool(tool, "check_meaning_accuracy")
  ├─ 仅保留必要参数：action、word、user_meaning、is_correct、check_feedback
  ├─ 约束 action 的枚举值仅为 "check_meaning_accuracy"
  ├─ 更新工具描述为"检查用户中文释义是否准确。"
  └─ 返回窄化后的 OpenAiTool
```

## OpenAI 格式转换

为了支持 OpenAI Function Calling 协议，ToolService 提供格式转换能力：

```text
ToolDefinition（内部格式）
  ↓ convertToOpenAiTool()
OpenAiTool（OpenAI 格式）
  ├─ type: "function"
  └─ function:
       ├─ name: {工具名称}
       ├─ description: {工具描述}
       └─ parameters: {JSON Schema 格式的参数定义}
            ├─ type: "object"
            ├─ properties: {参数名 → Property}
            └─ required: [必填参数列表]
```



## 重要类

| 类 | 作用 |
|----|------|
| `ToolController` | 工具 REST 入口：列表查询、单个工具调用 |
| `ToolService` | 工具服务门面：列表查询、格式转换、调用转发 |
| `ToolRegistry` | 工具注册表：工具注册、按模式过滤、执行调度 |
| `ToolHandler` | 工具处理器接口：所有工具必须实现的契约 |
| `VocabularyToolAdapter` | 词汇学习工具适配器：实现 ToolHandler，转发词汇业务调用 |
| `ToolCall` | 工具调用请求 DTO：id、name、arguments、conversationId |
| `ToolResult` | 工具执行结果 DTO：id、name、success、content/error |
| `ToolDefinition` | 工具定义 DTO：name、description、arguments（JSON Schema） |
| `ToolCategory` | 工具类别枚举：ONE_TIME、AGENT_ONLY、ALL |
| `ToolMode` | 对话模式常量：chat、agent、vocabulary |

## 关键约定

- **工具名称唯一**：每个 ToolHandler 的 getName() 必须全局唯一，作为注册和查找的 key
- **模式过滤规则**：agent 模式返回全部工具，其他模式仅返回 ONE_TIME 和 ALL 类别且 supportsMode=true 的工具
- **参数 Schema 规范**：ToolDefinition.arguments 采用 JSON Schema 风格，AI 根据此生成调用参数
- **执行异常捕获**：ToolRegistry.executeTool() 捕获所有异常，统一包装为失败的 ToolResult
- **静态导入**：ToolMode 常量通过静态导入使用（ToolMode.CHAT、ToolMode.AGENT 等）
