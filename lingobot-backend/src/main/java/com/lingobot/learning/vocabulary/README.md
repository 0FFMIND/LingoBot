# Vocabulary Module

## 模块概述

词汇学习模块提供基于对话的单词学习功能，支持 AI 生成单词卡、用户交互式学习、释义检查、造句反馈和个人词汇本管理。

主要职责：
- **词汇卡管理**：创建、查询、删除词汇卡，支持前后导航
- **AI 生成**：通过 AI Agent 智能生成新单词，支持难度级别选择
- **重新生成**：用户不满意时可重新生成当前单词，保留历史记录
- **释义检查**：用户输入单词释义后，AI 异步检查正确性
- **造句反馈**：用户用单词造句后，AI 提供语法和用法反馈
- **词汇本管理**：追踪用户学习进度，统计掌握程度，调度复习计划
- **状态管理**：使用 Redis 缓存当前学习的单词信息，优化 AI 上下文

## 模块架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Controller Layer                      │
│  ┌──────────────────────────┐  ┌─────────────────────────┐  │
│  │ VocabularyCardController │  │ UserVocabularyController │  │
│  └─────────────┬────────────┘  └──────────┬──────────────┘  │
└────────────────┼──────────────────────────┼─────────────────┘
                 │                          │
                 ▼                          ▼
┌─────────────────────────────────────────────────────────────┐
│                         Service Layer                        │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │ VocabularyCardService│  │ UserVocabularyService        │ │
│  │ ┌──────────────────┐ │  │ ┌──────────────────────────┐ │ │
│  │ │VocabularyCard    │ │  │ │UserVocabularyServiceImpl │ │ │
│  │ │ServiceImpl       │ │  │ └──────────────────────────┘ │ │
│  │ └──────────────────┘ │  │                              │ │
│  └──────────────────────┘  └──────────────────────────────┘ │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │ MeaningCheckService  │  │ VocabularyStateService      │ │
│  └──────────────────────┘  └──────────────────────────────┘ │
│  ┌──────────────────────┐                                  │
│  │ VocabularyWordService│                                  │
│  │ ┌──────────────────┐ │                                  │
│  │ │VocabularyWord    │ │                                  │
│  │ │ServiceImpl       │ │                                  │
│  │ └──────────────────┘ │                                  │
│  └──────────────────────┘                                  │
└────────────┬──────────────────────────┬────────────────────┘
             │                          │
             ▼                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      Repository Layer                        │
│  ┌─────────────────────────┐ ┌───────────────────────────┐ │
│  │ VocabularyCardRepository│ │ UserVocabularyRepository  │ │
│  └─────────────────────────┘ └───────────────────────────┘ │
│  ┌─────────────────────────┐                                │
│  │ VocabularyWordRepository│                                │
│  └─────────────────────────┘                                │
└────────────┬──────────────────────────┬────────────────────┘
             │                          │
             ▼                          ▼
┌─────────────────────────────────────────────────────────────┐
│                        Entity Layer                          │
│  ┌────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │ VocabularyCard │  │ UserVocabulary   │  │VocabularyWord│ │
│  └────────────────┘  └──────────────────┘  └──────────────┘ │
│  ┌────────────────┐  ┌──────────────────┐                    │
│  │VocabularyStatus│  │VocabularyEventType│                   │
│  └────────────────┘  └──────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

## 外部依赖

本模块依赖以下外部服务和模块：

| 依赖 | 用途 | 调用位置 |
|------|------|----------|
| `BalanceService` | 点数管理（冻结、确认、取消扣费） | VocabularyCardController（generate、regenerate、meaning） |
| `AuthService` | 获取当前登录用户ID | UserVocabularyController（stats、list、ignore） |
| `ConversationRepository` | 对话数据访问 | VocabularyCardServiceImpl |
| `UserPreferenceService` | 获取用户偏好（词汇标准、难度、模型） | VocabularyCardServiceImpl |
| `ToolLoopService` | AI Agent 工具调用执行 | VocabularyCardServiceImpl、MeaningCheckService |
| `McpService` | 获取 vocabulary 模式的 MCP 工具列表 | VocabularyCardServiceImpl、MeaningCheckService |
| `SystemPromptService` | 获取 System Prompt | VocabularyCardServiceImpl、MeaningCheckService |
| `StringRedisTemplate` | Redis 缓存操作 | VocabularyCardServiceImpl、VocabularyStateService、MeaningCheckService |
| `ObjectMapper` | JSON 序列化/反序列化 | VocabularyCardServiceImpl、MeaningCheckService、VocabularyStateService |

## API 接口流转

所有接口响应体通过 `ApiResponse<T>` 包装。

### 词汇卡接口（/api/vocabulary）

```
请求
  │
  ├─ 词汇卡管理
  │   ├─ POST /cards → 创建新词汇卡
  │   │     ├─ Controller: createCard()
  │   │     ├─ Service: VocabularyCardService.createCard()
  │   │     │     ├─ 校验对话存在（ConversationRepository）
  │   │     │     ├─ 标准化单词（VocabularyWordService.normalizeWord）
  │   │     │     ├─ 查找或创建标准化单词（VocabularyWordService.findOrCreateWord）
  │   │     │     ├─ 更新用户学习进度（UserVocabularyService.upsertProgress）
  │   │     │     ├─ 保存词汇卡（VocabularyCardRepository.save）
  │   │     │     └─ 清除对话缓存（evictConversationCache）
  │   │     └─ 返回 201 + ApiResponse.success()
  │   │
  │   ├─ GET /cards/{cardId} → 获取单个词汇卡
  │   │     ├─ Controller: getCardById()
  │   │     ├─ Service: VocabularyCardService.getCardById()
  │   │     │     ├─ 先查 Redis 缓存（getCardFromCache）
  │   │     │     ├─ 缓存命中 → 直接返回
  │   │     │     ├─ 缓存未命中 → 查询数据库（VocabularyCardRepository.findById）
  │   │     │     └─ 写入缓存（cacheCard）
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ GET /conversations/{id}/cards → 获取对话所有词汇卡
  │   │     ├─ Controller: getAllCards()
  │   │     ├─ Service: VocabularyCardService.getAllCards()
  │   │     │     ├─ 先查 Redis 缓存（getCardsListFromCache）
  │   │     │     ├─ 缓存未命中 → 查询数据库（findActiveCardsByConversationId）
  │   │     │     ├─ 只返回有效卡片（isRegenerated=false）
  │   │     │     └─ 写入缓存（cacheCardsList）
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ GET /conversations/{id}/current → 获取当前学习卡片
  │   │     ├─ Controller: getCurrentCard()
  │   │     ├─ Service: VocabularyCardService.getCurrentCard()
  │   │     │     ├─ 获取所有有效卡片（getAllCards）
  │   │     │     ├─ 优先返回第一个未完成的
  │   │     │     ├─ 全部完成则返回最后一个
  │   │     │     └─ 补充导航信息（hasPrev、hasNext、currentIndex、totalCount）
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ GET /conversations/{id}/next → 下一张卡片
  │   │     ├─ Controller: getNextCard()
  │   │     ├─ Service: VocabularyCardService.getNextCard()
  │   │     │     ├─ 获取所有有效卡片
  │   │     │     ├─ 已是最后一张 → 自动调用 generateNextCard() 生成新卡片
  │   │     │     └─ 补充导航信息
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ GET /conversations/{id}/prev → 上一张卡片
  │   │     ├─ Controller: getPrevCard()
  │   │     ├─ Service: VocabularyCardService.getPrevCard()
  │   │     │     ├─ 获取所有有效卡片
  │   │     │     ├─ 已是第一张 → 抛出 ChatException.badRequest()
  │   │     │     └─ 补充导航信息
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ POST /conversations/{id}/generate → AI生成新卡片
  │   │     ├─ Controller: generateNextCard()
  │   │     │     ├─ 冻结点数（BalanceService.freezeBalance）
  │   │     │     └─ try-catch 确保异常时返还余额
  │   │     ├─ Service: VocabularyCardService.generateNextCard()
  │   │     │     ├─ 调用 generateRandomWord() 生成单词
  │   │     │     │     ├─ 获取对话实体
  │   │     │     │     ├─ 获取用户偏好
  │   │     │     │     ├─ 构建 System Prompt（含历史单词记录）
  │   │     │     │     ├─ 获取 MCP 工具列表
  │   │     │     │     ├─ 调用 ToolLoopService.executeOneTimeToolCall()
  │   │     │     │     └─ 解析 AI 返回结果
  │   │     │     ├─ 标准化单词 → findOrCreateWord()
  │   │     │     ├─ 更新用户学习进度 → upsertProgress()
  │   │     │     ├─ 保存词汇卡
  │   │     │     └─ 清除缓存
  │   │     ├─ 成功 → BalanceService.confirmTransaction()
  │   │     ├─ 失败 → BalanceService.cancelTransaction() + 抛出异常
  │   │     └─ 返回 201 + ApiResponse.success()
  │   │
  │   ├─ POST /conversations/{id}/regenerate → 重新生成当前卡片
  │   │     ├─ Controller: regenerateCard()
  │   │     │     ├─ 冻结点数
  │   │     │     └─ try-catch 确保异常时返还余额
  │   │     ├─ Service: VocabularyCardService.regenerateCard()
  │   │     │     ├─ 获取最后一张有效卡片
  │   │     │     ├─ 标记旧卡片 isRegenerated=true
  │   │     │     ├─ regenerationIndex +1
  │   │     │     ├─ 调用 generateRandomWord() 生成新单词
  │   │     │     ├─ 标准化单词 → findOrCreateWord()
  │   │     │     ├─ 更新用户学习进度 → upsertProgress()
  │   │     │     ├─ 保存新词汇卡（同一 position）
  │   │     │     └─ 清除缓存（evictCardCache + evictConversationCache）
  │   │     ├─ 成功 → confirmTransaction
  │   │     └─ 返回 201 + ApiResponse.success()
  │   │
  │   ├─ PUT /cards/{id}/meaning → 更新用户释义猜测
  │   │     ├─ Controller: updateUserMeaning()
  │   │     │     ├─ 先获取卡片获取 conversationId
  │   │     │     ├─ 冻结点数
  │   │     │     └─ try-catch 确保异常时返还余额
  │   │     ├─ Service: VocabularyCardService.updateUserMeaning()
  │   │     │     ├─ 查询并更新词汇卡
  │   │     │     ├─ 保存 userMeaningGuess
  │   │     │     ├─ 重置 meaningCheckCompleted/meaningIsCorrect/meaningCheckResult
  │   │     │     ├─ 异步触发 AI 检查（MeaningCheckService.checkUserMeaningAsync）
  │   │     │     │     └─ @Async("meaningCheckExecutor")
  │   │     │     └─ 清除缓存
  │   │     ├─ 成功 → confirmTransaction
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ PUT /cards/{id}/sentence → 更新用户造句
  │   │     ├─ Controller: updateUserSentence()
  │   │     ├─ Service: VocabularyCardService.updateUserSentence()
  │   │     │     ├─ 查询并更新词汇卡
  │   │     │     ├─ 保存 userSentence
  │   │     │     └─ 清除缓存
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ PUT /cards/{id}/feedback → 更新 AI 造句反馈
  │   │     ├─ Controller: updateAIFeedback()
  │   │     ├─ Service: VocabularyCardService.updateAIFeedback()
  │   │     │     ├─ 查询并更新词汇卡
  │   │     │     ├─ 保存 aiFeedback
  │   │     │     └─ 清除缓存
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ PUT /cards/{id}/complete → 标记为完成
  │   │     ├─ Controller: markAsCompleted()
  │   │     ├─ Service: VocabularyCardService.markAsCompleted()
  │   │     │     ├─ 查询并更新词汇卡
  │   │     │     ├─ 设置 isCompleted=true
  │   │     │     └─ 清除缓存
  │   │     └─ 返回 200 + ApiResponse.success()
  │   │
  │   ├─ DELETE /conversations/{id}/cards → 删除对话所有卡片
  │   │     ├─ Controller: deleteAllCards()
  │   │     ├─ Service: VocabularyCardService.deleteAllCards()
  │   │     │     ├─ 删除数据库记录（VocabularyCardRepository.deleteByConversationId）
  │   │     │     └─ 清除缓存
  │   │     └─ 返回 204 无响应体
  │   │
  │   └─ GET /cards/{id}/meaning-check → 查询释义检查状态
  │         ├─ Controller: getMeaningCheckStatus()
  │         ├─ Service: VocabularyCardService.getCardById()
  │         └─ 返回检查结果（isCorrect、checkResult、isCompleted）
  │
  └─ 用户词汇本接口（/api/user-vocabulary）
      ├─ GET /stats → 获取学习统计
      │     ├─ Controller: getStats()
      │     ├─ 校验登录（AuthService.getCurrentUserId）
      │     ├─ 未登录 → 返回空统计（emptyStats）
      │     ├─ Service: UserVocabularyService.getStats()
      │     │     └─ 统计各状态数量和待复习数量
      │     └─ 返回 200 + ApiResponse.success()
      │
      ├─ GET /list → 分页查询词汇列表
      │     ├─ Controller: getVocabularies()
      │     ├─ 校验登录
      │     ├─ 未登录 → 返回空列表
      │     ├─ Service: UserVocabularyService.getUserVocabularies()
      │     │     ├─ 支持 status 状态筛选
      │     │     ├─ 支持 filterType（to_review、difficult）
      │     │     ├─ 支持 sortBy 排序（first_seen、last_seen、mastery_asc 等）
      │     │     └─ 转换为 DTO（关联查询最新词汇卡）
      │     └─ 返回 PageResponseDTO<UserVocabularyDTO>
      │
      └─ PUT /{id}/ignore → 忽略某个词汇
            ├─ Controller: ignoreVocabulary()
            ├─ 校验登录
            └─ 标记为 IGNORED 状态
```

### 异步释义检查流程

```
用户调用 PUT /cards/{id}/meaning
        │
        ▼
VocabularyCardController.updateUserMeaning()
        │
        ├─ 1. 获取卡片（getCardById）→ 用于获取 conversationId
        ├─ 2. 冻结点数（BalanceService.freezeBalance）
        ├─ 3. 调用 VocabularyCardService.updateUserMeaning()
        │       │
        │       ├─ a. 查询词汇卡
        │       ├─ b. 设置 userMeaningGuess
        │       ├─ c. 重置 meaningCheckCompleted=false, meaningIsCorrect=null
        │       ├─ d. 保存到数据库
        │       ├─ e. 异步调用 MeaningCheckService.checkUserMeaningAsync()
        │       │       │
        │       │       └─ @Async("meaningCheckExecutor")
        │       │           │
        │       │           ├─ i.   构建 AI 消息（单词、用户释义、正确释义）
        │       │           ├─ ii.  获取 System Prompt
        │       │           ├─ iii. 获取 vocabulary 模式 MCP 工具
        │       │           ├─ iv.  调用 ToolLoopService.executeOneTimeToolCall()
        │       │           ├─ v.   解析 is_correct 和 check_feedback
        │       │           ├─ vi.  持久化结果到词汇卡
        │       │           ├─ vii. 更新用户学习进度（updateProgress）
        │       │           │       │
        │       │           │       ├─ 设置 lastEventType=QUIZ
        │       │           │       ├─ 增加 correctCount 或 wrongCount
        │       │           │       ├─ 计算 masteryScore = correct/total
        │       │           │       ├─ 根据 masteryScore 更新 status
        │       │           │       └─ 计算 nextReviewAt
        │       │           └─ viii.清除相关缓存
        │       │
        │       └─ f. 清除缓存
        │
        ├─ 4. 确认扣费（BalanceService.confirmTransaction）
        └─ 5. 返回 200 + ApiResponse.success()

前端轮询 GET /cards/{id}/meaning-check 检查结果
```

## 各类职责

| 类 | 类型 | 职责 | 事务 | 缓存 |
|----|------|------|------|------|
| `VocabularyCardController` | Controller | 词汇卡 REST API 入口，处理请求、扣费、响应包装 | - | - |
| `UserVocabularyController` | Controller | 用户词汇本 REST API 入口，提供统计和列表查询 | - | - |
| `VocabularyCardService` | Service接口 | 词汇卡业务接口定义 | - | - |
| `VocabularyCardServiceImpl` | Service实现 | 词汇卡业务实现（创建、查询、AI生成、导航、重新生成） | @Transactional | ✅ Redis |
| `UserVocabularyService` | Service接口 | 用户词汇本业务接口定义 | - | - |
| `UserVocabularyServiceImpl` | Service实现 | 用户词汇本业务实现（进度管理、统计、复习调度） | @Transactional | ❌ |
| `VocabularyWordService` | Service接口 | 标准化单词服务接口定义 | - | - |
| `VocabularyWordServiceImpl` | Service实现 | 标准化单词服务实现（归一化、去重、查找/创建） | @Transactional | ❌ |
| `MeaningCheckService` | Service | 异步释义检查服务 | @Transactional | ✅ 清除缓存 |
| `VocabularyStateService` | Service | 词汇学习状态服务（Redis缓存当前单词） | - | ✅ Redis |
| `VocabularyCardRepository` | Repository | 词汇卡数据访问层 | - | - |
| `UserVocabularyRepository` | Repository | 用户词汇本数据访问层 | - | - |
| `VocabularyWordRepository` | Repository | 标准化单词数据访问层 | - | - |
| `VocabularyCard` | Entity | 词汇卡实体 | - | - |
| `UserVocabulary` | Entity | 用户词汇实体 | - | - |
| `VocabularyWord` | Entity | 标准化单词实体 | - | - |
| `VocabularyStatus` | Enum | 学习状态枚举（NEW/LEARNING/REVIEWING/MASTERED/IGNORED） | - | - |
| `VocabularyEventType` | Enum | 学习事件类型枚举（NEW_LEARNING/REVIEW/QUIZ） | - | - |
| `VocabularyCardDTO` | DTO | 词汇卡数据传输对象（含导航信息） | - | - |
| `UserVocabularyDTO` | DTO | 用户词汇数据传输对象（含掌握程度） | - | - |
| `VocabularyStatsDTO` | DTO | 学习统计数据传输对象 | - | - |
| `CreateVocabularyCardRequest` | DTO | 创建词汇卡请求体 | - | - |
| `WordCardData` | DTO | AI 生成的单词卡片数据 | - | - |

## 事务管理

### 使用 @Transactional 的方法

| 方法 | 所在类 | 事务类型 | 说明 |
|------|--------|----------|------|
| `createCard()` | VocabularyCardServiceImpl | 读写 | 创建词汇卡，涉及多次数据库操作 |
| `getNextCard()` | VocabularyCardServiceImpl | 读写 | 可能触发 generateNextCard() |
| `updateUserMeaning()` | VocabularyCardServiceImpl | 读写 | 更新词汇卡，异步触发释义检查 |
| `updateUserSentence()` | VocabularyCardServiceImpl | 读写 | 更新用户造句 |
| `updateAIFeedback()` | VocabularyCardServiceImpl | 读写 | 更新AI反馈 |
| `markAsCompleted()` | VocabularyCardServiceImpl | 读写 | 标记完成 |
| `generateNextCard()` | VocabularyCardServiceImpl | 读写 | AI生成新词汇卡 |
| `regenerateCard()` | VocabularyCardServiceImpl | 读写 | 重新生成词汇卡 |
| `deleteAllCards()` | VocabularyCardServiceImpl | 读写 | 删除所有词汇卡 |
| `getStats()` | UserVocabularyServiceImpl | 只读 | `@Transactional(readOnly = true)` |
| `getUserVocabularies()` | UserVocabularyServiceImpl | 只读 | `@Transactional(readOnly = true)` |
| `upsertProgress()` | UserVocabularyServiceImpl | 读写 | 新增或更新学习记录 |
| `updateProgress()` | UserVocabularyServiceImpl | 读写 | 根据测验结果更新进度 |
| `findOrCreateWord()` | VocabularyWordServiceImpl | 读写 | 查找或创建标准化单词 |
| `checkUserMeaningAsync()` | MeaningCheckService | 读写 | 异步释义检查 |

## 错误处理

### 异常类型及处理

| 异常类型 | 触发场景 | 处理方式 | HTTP状态码 |
|----------|----------|----------|------------|
| `ChatException.badRequest()` | 词汇卡不存在、对话不存在、已是第一张卡片等 | GlobalExceptionHandler.handleChatException() | 400 |
| `IllegalArgumentException` | 单词为空等 | GlobalExceptionHandler.handleIllegalArgumentException() | 400 |
| `RuntimeException` | UserVocabulary 记录不存在等 | GlobalExceptionHandler.handleRuntimeException() | 500 |
| `Exception` | 其他未知异常 | GlobalExceptionHandler.handleException() | 500 |

### 异常兜底机制

| 场景 | 兜底策略 |
|------|----------|
| AI 生成单词失败 | 返回默认单词 "hello"（A1难度） |
| 同义词/反义词语义化失败 | 返回空列表 `new ArrayList<>()` |
| 解析 AI 返回结果失败 | 使用默认单词 |
| 用户未登录 | 返回空统计或空列表 |
| 释义检查 AI 返回无效结果 | 跳过，不更新词汇卡 |

## 关键设计

### 1. 标准化单词去重

```
策略：normalizeWord() → trim + 转小写
数据库：vocabulary_words 表的 normalized_word 唯一索引
效果：同一单词（如 "Hello"、"hello"、"HELLO "）只存储一条记录
用户词汇：UserVocabulary 通过 vocabulary_word_id 关联，避免重复学习同一单词
```

### 2. 词汇卡重新生成机制

```
字段设计：
- isRegenerated: 标记卡片是否已被重新生成（前端不展示）
- regenerationIndex: 重新生成次数索引（0=原始，1=第一次重新生成...）
- position: 对话中的位置，同一位置可有多张卡片（不同 regenerationIndex）

历史展示：
- DTO 包含 regeneratedWords 列表，展示用户不满意的历史单词
- 构建 AI System Prompt 时包含历史记录，避免 AI 重复生成

数据保留：
- 旧卡片不物理删除，保留 isRegenerated=true
- 用于追踪用户学习历史和 AI 生成偏好
```

### 3. Redis 缓存策略

```
缓存键：
- vocabulary:card:{cardId} → 单个词汇卡 DTO（1小时过期）
- vocabulary:cards:{conversationId} → 对话有效卡片列表（1小时过期）
- vocabulary:count:{conversationId} → 对话有效卡片数量（1小时过期）
- vocabulary:state:{conversationId} → 当前学习单词状态（24小时过期，VocabularyStateService）

缓存读取：
- 先查缓存，命中直接返回
- 未命中查数据库，结果写入缓存

缓存清除：
- 创建词汇卡：evictConversationCache()
- 更新词汇卡：evictCardAndConversationCache()
- 删除词汇卡：evictConversationCache()
- 释义检查完成：evictCardAndConversationCache()
```

### 4. 异步释义检查

```
触发：用户调用 PUT /cards/{id}/meaning 时
执行：@Async("meaningCheckExecutor") 线程池异步执行
流程：
  1. 构建 AI 消息（单词、用户释义、正确释义）
  2. 获取 System Prompt
  3. 获取 vocabulary 模式 MCP 工具列表
  4. 调用 ToolLoopService.executeOneTimeToolCall()
  5. 解析 is_correct 和 check_feedback
  6. 持久化结果到词汇卡
  7. 更新用户学习进度（updateProgress）
  8. 清除相关缓存

前端：轮询 GET /cards/{id}/meaning-check 获取检查结果
```

### 5. 掌握程度与复习调度

```
掌握程度计算：
  masteryScore = correctCount / (correctCount + wrongCount)
  保留两位小数，HALF_UP 四舍五入

状态转换：
  新学习 → status = NEW → LEARNING（seenCount > 0）
  测验后：
    - masteryScore >= 0.90 → MASTERED
    - masteryScore >= 0.60 → REVIEWING
    - masteryScore < 0.60 且 seenCount > 1 → LEARNING

复习时间调度（艾宾浩斯简化版）：
  - 答错 → 4小时后复习
  - 答对且 masteryScore >= 0.90 → 14天后复习
  - 答对且 masteryScore >= 0.60 → 3天后复习
  - 答对且 masteryScore < 0.60 → 1天后复习
```

### 6. AI 生成单词流程

```
输入：conversationId, level（可选）
依赖：用户偏好（vocabularyCategory、vocabularyDifficulty、vocabularyModel）

步骤：
  1. 获取对话实体，校验存在（ConversationRepository.findById）
  2. 确保对话仍存在（ensureConversationStillExists）
  3. 获取用户偏好（如未指定则使用默认）
     - vocabularyCategory: 默认 "cefr"
     - vocabularyDifficulty: 默认 "b2"
     - vocabularyModel: 默认 "qwen"
  4. 构建 System Prompt
     - 调用 SystemPromptService.getSystemPrompt()
     - 追加历史单词记录（避免重复）
  5. 构建用户消息：[intent:next_word] 或 [intent:regenerate]
  6. 获取 vocabulary 模式的 MCP 工具列表（McpService.getOpenAiToolsForMode）
  7. 调用 ToolLoopService.executeOneTimeToolCall()
  8. 记录 Token 使用量（recordVocabularyTokenUsage）
  9. 解析 AI 返回的单词信息
     - word, phonetic, meaning, example, exampleTranslation
     - synonyms, antonyms, vocabularyDifficulty
  10. 标准化单词 → findOrCreateWord()
  11. 更新用户学习进度 → upsertProgress()
  12. 保存词汇卡
  13. 清除缓存

兜底：AI 生成失败时返回默认单词 "hello"
```

### 7. 历史单词记录构建

```
用途：注入到 AI System Prompt，避免 AI 生成重复单词

构建流程（buildVocabularyHistoryForPrompt）：
  1. 获取对话所有词汇卡（包括已重新生成的）
  2. 按 position 分组
  3. 对每个 position：
     - 按 regenerationIndex 排序
     - 标记重新生成的单词为"用户不满意"
     - 包含单词、音标、释义信息
  4. 追加到 System Prompt

效果：
  - AI 不会重复生成用户已学过的单词
  - AI 不会重复生成用户不满意的单词（重新生成过的）
```

### 8. 词汇学习状态缓存

```
服务：VocabularyStateService
用途：缓存当前学习的单词信息，用于 AI 生成造句反馈时的上下文

缓存键：vocabulary:state:{conversationId}
过期时间：24小时

缓存内容：
  - word: 当前单词
  - phonetic: 音标
  - partOfSpeech: 词性
  - meaning: 释义
  - synonyms: 同义词列表
  - vocabularyCategory: 词汇标准（cefr/ielts/toefl等）
  - vocabularyDifficulty: 难度级别

使用场景：
  - AI 调用 display_flashcard 工具时调用 saveCurrentWord()
  - AI 需要生成造句反馈时调用 getCurrentWordInfoForPrompt()
  - 注入到 System Prompt，避免 AI 重复询问单词信息
```

## 数据库表结构

### vocabulary_cards（词汇卡表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| conversation_id | BIGINT | FK, NOT NULL | 关联对话 |
| vocabulary_word_id | BIGINT | FK | 关联标准化单词 |
| word | VARCHAR(100) | NOT NULL | 单词 |
| phonetic | VARCHAR(100) | - | 音标 |
| meaning | TEXT | - | 中文释义 |
| example | TEXT | - | 英文例句 |
| example_translation | TEXT | - | 例句翻译 |
| synonyms_json | TEXT | - | 同义词JSON |
| antonyms_json | TEXT | - | 反义词JSON |
| level | VARCHAR(10) | - | 难度级别 |
| position | INT | NOT NULL | 对话中的位置 |
| user_meaning_guess | TEXT | - | 用户猜测的释义 |
| meaning_check_result | TEXT | - | AI检查结果 |
| meaning_is_correct | BOOLEAN | - | 释义是否正确 |
| meaning_check_completed | BOOLEAN | DEFAULT false | 检查是否完成 |
| user_sentence | TEXT | - | 用户造句 |
| ai_feedback | TEXT | - | AI造句反馈 |
| is_completed | BOOLEAN | NOT NULL, DEFAULT false | 是否已完成 |
| is_regenerated | BOOLEAN | NOT NULL, DEFAULT false | 是否已重新生成 |
| regeneration_index | INT | NOT NULL, DEFAULT 0 | 重新生成次数索引 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

### user_vocabularies（用户词汇表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | 用户ID |
| vocabulary_word_id | BIGINT | NOT NULL | 标准化单词ID |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'NEW' | 学习状态 |
| mastery_score | DECIMAL(5,2) | NOT NULL, DEFAULT 0.00 | 掌握程度 |
| seen_count | INT | NOT NULL, DEFAULT 0 | 已见次数 |
| correct_count | INT | NOT NULL, DEFAULT 0 | 正确次数 |
| wrong_count | INT | NOT NULL, DEFAULT 0 | 错误次数 |
| first_seen_at | DATETIME | - | 首次见时间 |
| last_seen_at | DATETIME | - | 最近见时间 |
| next_review_at | DATETIME | - | 下次复习时间 |
| last_event_type | VARCHAR(20) | - | 最后事件类型 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

唯一约束：(user_id, vocabulary_word_id)

### vocabulary_words（标准化单词表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| normalized_word | VARCHAR(100) | UNIQUE, NOT NULL | 归一化单词 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

## 约定

### HTTP 状态码

| 操作 | 状态码 | 响应体 |
|------|--------|--------|
| 成功创建（POST generate/regenerate/cards） | 201 | ApiResponse.success() |
| 成功查询/更新 | 200 | ApiResponse.success() |
| 成功删除 | 204 | 无响应体 |
| 业务异常（词汇卡不存在、对话不存在等） | 400 | ApiResponse.error(ErrorCode.BAD_REQUEST, msg) |
| 参数校验失败 | 400 | GlobalExceptionHandler 统一处理 |
| 运行时异常 | 500 | ApiResponse.error(ErrorCode.INTERNAL_ERROR) |

### 业务约定

- **释义检查**：异步执行，前端轮询 GET /cards/{id}/meaning-check
- **重新生成**：旧卡片保留（isRegenerated=true），不物理删除
- **有效卡片**：isRegenerated=false 才计入列表和导航
- **扣费模式**：AI 相关操作使用 freezeBalance + confirmTransaction/cancelTransaction
- **对话一致性**：生成新词汇卡前后都检查对话是否存在（防止并发删除）
- **词汇去重**：同一单词的不同形式（大小写、空格）归一化后只存储一条记录

### 缓存约定

- **缓存键前缀**：vocabulary:
- **缓存过期时间**：词汇卡相关 1 小时，状态相关 24 小时
- **缓存清除时机**：创建、更新、删除操作后立即清除
- **缓存穿透**：通过数据库唯一约束保证单词去重

## 扩展点

### 添加新的学习状态

1. 在 `VocabularyStatus` 枚举中添加新值
2. 在 `UserVocabularyServiceImpl.getStats()` 中添加新的统计逻辑
3. 在 `UserVocabularyController` 中添加对应的筛选逻辑

### 添加新的筛选类型

1. 在 `UserVocabularyServiceImpl.getUserVocabularies()` 中添加新的 if-else 分支
2. 在 `UserVocabularyRepository` 中添加对应的查询方法
3. 更新 README.md 中的文档

### 添加新的排序方式

1. 在 `UserVocabularyServiceImpl.getSort()` 中添加新的 switch 分支
2. 更新 README.md 中的文档

### 自定义复习调度算法

1. 修改 `UserVocabularyServiceImpl.calculateNextReviewAt()` 方法
2. 更新 README.md 中的"掌握程度与复习调度"部分

### 添加新的 AI 生成参数

1. 在 `WordCardData` DTO 中添加新字段
2. 在 `VocabularyCardServiceImpl.parseWordCardDataFromToolResult()` 中解析新字段
3. 在 `VocabularyCard` 实体中添加对应字段
4. 执行数据库迁移

## 维护指南

### 需要同步更新的内容

当代码发生以下变化时，需要更新此文档：

| 代码变化 | 需要更新的文档章节 |
|----------|-------------------|
| 新增/修改 API 接口 | API 接口流转、约定（HTTP状态码） |
| 新增/修改 Service 方法 | 各类职责、事务管理 |
| 新增/修改 Repository 查询方法 | 数据库表结构、各类职责 |
| 新增/修改 Entity 字段 | 数据库表结构、关键设计 |
| 新增/修改枚举值 | 扩展点、关键设计 |
| 修改缓存策略 | 关键设计（Redis缓存策略）、缓存约定 |
| 修改 AI 生成流程 | 关键设计（AI生成单词流程） |
| 修改复习调度算法 | 关键设计（掌握程度与复习调度） |
| 新增外部依赖 | 外部依赖、模块架构 |
| 修改事务管理 | 事务管理、各类职责 |

### 文档维护检查清单

每次代码提交前，检查以下内容是否需要更新：

- [ ] API 接口列表是否完整
- [ ] 数据库表结构是否与代码同步
- [ ] 关键设计是否描述了最新实现
- [ ] 扩展点是否覆盖了常见的自定义场景
- [ ] 维护指南是否包含了需要同步的内容

## 相关文件

| 文件路径 | 说明 |
|----------|------|
| `controller/VocabularyCardController.java` | 词汇卡 API 控制器 |
| `controller/UserVocabularyController.java` | 用户词汇本 API 控制器 |
| `service/VocabularyCardService.java` | 词汇卡服务接口 |
| `service/impl/VocabularyCardServiceImpl.java` | 词汇卡服务实现 |
| `service/UserVocabularyService.java` | 用户词汇服务接口 |
| `service/impl/UserVocabularyServiceImpl.java` | 用户词汇服务实现 |
| `service/MeaningCheckService.java` | 释义检查服务 |
| `service/VocabularyStateService.java` | 词汇状态服务 |
| `service/VocabularyWordService.java` | 标准化单词服务接口 |
| `service/impl/VocabularyWordServiceImpl.java` | 标准化单词服务实现 |
| `repository/VocabularyCardRepository.java` | 词汇卡 Repository |
| `repository/UserVocabularyRepository.java` | 用户词汇 Repository |
| `repository/VocabularyWordRepository.java` | 标准化单词 Repository |
| `entity/VocabularyCard.java` | 词汇卡实体 |
| `entity/UserVocabulary.java` | 用户词汇实体 |
| `entity/VocabularyWord.java` | 标准化单词实体 |
| `entity/VocabularyStatus.java` | 学习状态枚举 |
| `entity/VocabularyEventType.java` | 事件类型枚举 |
| `dto/VocabularyCardDTO.java` | 词汇卡 DTO |
| `dto/UserVocabularyDTO.java` | 用户词汇 DTO |
| `dto/VocabularyStatsDTO.java` | 统计 DTO |
| `dto/CreateVocabularyCardRequest.java` | 创建请求 DTO |
| `dto/WordCardData.java` | AI 生成数据 DTO |
