# Vocabulary Module

## 模块概述

词汇学习模块负责词汇卡的生成、管理、学习和复习流程。

主要职责：

- 词汇卡批量生成（Generate Batch）：通过 AI 一次性生成多张词汇卡
- 词汇卡重新生成（Regenerate）：在指定位置重新生成一张词汇卡
- 词汇卡导航：上一张、下一张、当前卡片等
- 学习交互：用户释义猜测、句子翻译、AI 分析等
- 用户词汇本：记录用户学习过的词汇，跟踪学习状态

## API / 调用流转

词汇接口统一挂载在 `/api/vocabulary` 路径下，响应体通过 `ApiResponse<T>` 包装。

```text
请求
  ├─ POST /api/vocabulary/conversations/{conversationPublicId}/generate-batch → 批量生成词汇卡
  │    └─ VocabularyCardController.generateBatchCards()
  │         ├─ 解析参数（category/difficulty/batchSize）
  │         ├─ 冻结余额（cost=1.0）
  │         ├─ 调用 VocabularyCardService.generateBatchCards() 【业务编排入口】
  │         │    └─ VocabularyBatchGraph.execute() 【真正的 AI 生成流程】
  │         ├─ 成功 → 确认扣费
  │         ├─ 失败 → 返还余额
  │         └─ 返回批量生成结果
  │
  ├─ POST /api/vocabulary/conversations/{conversationPublicId}/regenerate-at-position → 重新生成词汇卡
  │    └─ VocabularyCardController.regenerateCardAtPosition()
  │         ├─ 解析参数（position/category/difficulty）
  │         ├─ 冻结余额（cost=0.1）
  │         ├─ 调用 VocabularyCardService.regenerateCardAtPosition() 【业务编排入口】
  │         │    ├─ 标记旧卡片 isRegenerated=true
  │         │    └─ VocabularyGraph.execute(intent=REGENERATE) 【真正的 AI 生成流程】
  │         │    └─ 创建新词汇卡
  │         ├─ 成功 → 确认扣费
  │         ├─ 失败 → 返还余额
  │         └─ 返回新词汇卡
  │
  ├─ PUT /api/vocabulary/cards/{cardId}/meaning → 更新用户释义猜测
  │    └─ VocabularyCardController.updateUserMeaning()
  │         ├─ 冻结余额（cost=0.1）
  │         ├─ VocabularyCardService.updateUserMeaning()
  │         ├─ 触发异步 AI 释义检查
  │         ├─ 成功 → 确认扣费 / 失败 → 返还余额
  │         └─ 返回更新后的词汇卡
  │
  └─ POST /api/vocabulary/cards/{cardId}/analyze-sentence → AI 分析用户翻译的英文句子
       └─ VocabularyCardController.analyzeUserSentence()
            ├─ 冻结余额（cost=0.1）
            ├─ VocabularyCardService.analyzeUserSentenceAsync()
            ├─ 触发异步 AI 句子分析
            ├─ 成功 → 确认扣费 / 失败 → 返还余额
            └─ 返回触发成功
```

## Generate Batch Workflow

批量生成词汇卡使用 `VocabularyBatchGraph`（LangGraph4j 状态机），默认一次性生成 10 张卡片，第一张自动揭露，其余为隐藏状态。

```text
POST /api/vocabulary/conversations/{conversationPublicId}/generate-batch
  └─ VocabularyCardController.generateBatchCards()
       ├─ 解析 conversationPublicId → conversationId
       ├─ 从 request 读取 category/difficulty/batchSize（默认10）
       ├─ BalanceService.freezeBalance(cost=1.0) → 冻结余额
       │
       ├─ 调用 VocabularyCardService.generateBatchCards() 【业务编排层】
       │    ├─ 获取 Conversation 实体
       │    ├─ 从 VocabularyConversationData 读取 vocabularyIntent（默认 NEW_WORD）
       │    │
       │    └─ VocabularyBatchGraph.execute() 【AI 生成状态机，真正执行生成】
       │         ├─ MEMORY_RECALL
       │         │    ├─ 查询用户已学习词汇（避免重复）
       │         │    ├─ 查询对话历史中的词汇
       │         │    └─ 读取用户偏好设置
       │         ├─ PLANNING
       │         │    ├─ 确定生成策略（NEW_WORD / REVIEW / HYBRID）
       │         │    ├─ 构建 AI Prompt
       │         │    └─ 设置 batchSize
       │         ├─ GENERATION
       │         │    ├─ 调用 AI 批量生成 batchSize 个单词
       │         │    └─ 解析 AI 返回的 JSON 数组
       │         ├─ VALIDATION
       │         │    ├─ 校验必填字段、去重
       │         │    ├─ 通过 → PERSISTENCE
       │         │    ├─ 失败可重试 → GENERATION
       │         │    └─ 失败不可重试 → END
       │         └─ PERSISTENCE
       │              ├─ 批量创建 VocabularyCard 实体
       │              ├─ 第一张 isRevealed=true，其余 false
       │              ├─ 创建/查找 VocabularyWord
       │              ├─ 创建 UserVocabulary 记录
       │              └─ 清除对话缓存
       │
       ├─ 成功 → BalanceService.confirmTransaction()
       ├─ 失败 → BalanceService.cancelTransaction()
       └─ 返回 VocabularyBatchGenerationResult
```

### 自动批量生成触发时机

除了用户主动调用 `/generate-batch`，系统还会在以下场景自动触发批量生成：

1. **首次获取下一张卡片**：`getNextCard()` 时如果对话没有词汇卡，自动生成 10 张
2. **学习到最后一张**：`getNextCard()` 时已经是最后一张，自动生成下一批 10 张

## Regenerate Workflow

在指定位置重新生成词汇卡，旧卡片被标记为 `isRegenerated=true`（软删除），新卡片继承原位置。

```text
POST /api/vocabulary/conversations/{conversationPublicId}/regenerate-at-position
  └─ VocabularyCardController.regenerateCardAtPosition()
       ├─ 解析 conversationPublicId → conversationId
       ├─ 从 request 读取 position/category/difficulty
       ├─ BalanceService.freezeBalance(cost=0.1) → 冻结余额
       │
       ├─ 调用 VocabularyCardService.regenerateCardAtPosition() 【业务编排层】
       │    ├─ 获取 Conversation 实体
       │    ├─ 查询该 position 的 active 卡片
       │    │    └─ 不存在 → 抛出异常
       │    ├─ 标记旧卡片 isRegenerated=true（软删除）
       │    ├─ 记录 VocabularyMemoryEvent（REGENERATED）
       │    │
       │    ├─ VocabularyGraph.execute(intent=REGENERATE) 【AI 生成状态机，真正执行生成】
       │    │    ├─ MEMORY_RECALL
       │    │    │    ├─ 查询该位置的重新生成历史（避免重复）
       │    │    │    ├─ 传入旧单词信息（oldWord、oldPartOfSpeech、oldMeaning）
       │    │    │    └─ 读取用户已学习词汇
       │    │    ├─ PLANNING
       │    │    │    ├─ 构建 Prompt：要求生成不同于旧单词的新词
       │    │    │    └─ 指定 regeneratePosition
       │    │    ├─ GENERATION
       │    │    │    └─ 调用 AI 生成单个单词
       │    │    ├─ VALIDATION
       │    │    │    ├─ 校验必填字段
       │    │    │    ├─ 检查是否与旧单词相同
       │    │    │    ├─ 检查是否与该位置历史单词重复
       │    │    │    ├─ 通过 → PERSISTENCE
       │    │    │    ├─ 失败可重试 → GENERATION
       │    │    │    └─ 失败不可重试 → FALLBACK（返回默认词）
       │    │    └─ PERSISTENCE / FALLBACK
       │    │         └─ 返回 WordCardData
       │    │
       │    ├─ 创建新 VocabularyCard 实体
       │    │    ├─ 继承原 position
       │    │    ├─ regenerationIndex = 旧卡片.regenerationIndex + 1
       │    │    ├─ isRevealed = true
       │    │    └─ isCompleted = false
       │    ├─ 创建/查找 VocabularyWord
       │    ├─ 创建 UserVocabulary 记录
       │    ├─ 记录 VocabularyMemoryEvent（SEEN）
       │    ├─ 清除旧卡片缓存和对话缓存
       │    └─ 返回新词汇卡 DTO
       │
       ├─ 成功 → BalanceService.confirmTransaction()
       ├─ 失败 → BalanceService.cancelTransaction()
       └─ 返回新的 VocabularyCardDTO
```

### 重新生成历史

每个位置可以多次重新生成，旧卡片保留在数据库中用于历史追踪：

- `isRegenerated=true` 标记为已重新生成，不再出现在 active 列表中
- `regenerationIndex` 记录该位置的第几次生成（从 0 开始）
- `regeneratedWords` 字段返回该位置所有历史生成的单词列表

## 重要类

| 类 | 作用 |
|----|------|
| `VocabularyCardController` | 词汇卡 REST 入口：生成、查询、更新、学习交互 |
| `VocabularyCardService` | 词汇卡业务接口 |
| `VocabularyCardServiceImpl` | 词汇卡业务实现，含缓存管理 |
| `VocabularyBatchGraph` | 批量生成状态机（LangGraph4j） |
| `VocabularyGraph` | 单个词汇生成状态机（LangGraph4j） |
| `VocabularyBatchNodeActions` | 批量生成状态节点动作实现 |
| `VocabularyNodeActions` | 单个词汇生成状态节点动作实现 |
| `VocabularyCardRepository` | 词汇卡数据访问 |
| `VocabularyWordRepository` | 词汇词条数据访问 |
| `UserVocabularyService` | 用户词汇本管理 |
| `MeaningCheckService` | AI 释义检查异步服务 |
| `SentenceAnalysisService` | AI 句子分析异步服务 |
| `VocabularyMemoryService` | 词汇记忆和学习事件记录 |

## 关键约定

- **软删除机制**：重新生成时旧卡片不物理删除，而是标记 `isRegenerated=true`
- **位置继承**：新生成的卡片继承原位置 `position`，通过 `regenerationIndex` 区分版本
- **缓存策略**：词汇卡、卡片列表、卡片数量分别缓存，更新时清除相关缓存
- **AI 重试**：生成验证失败时自动重试生成，超过最大重试次数返回 fallback 或空结果
- **余额冻结**：所有 AI 收费接口必须先冻结余额，成功确认扣费，失败返还
- **对话 publicId**：Controller 层接收 `conversationPublicId`，内部转换为 `conversationId`
- **active 卡片**：查询时只返回 `isRegenerated=false` 的卡片（通过 `findActiveCardsByConversationId`）
