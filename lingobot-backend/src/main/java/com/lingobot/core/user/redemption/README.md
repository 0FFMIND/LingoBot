# Redemption Code Module

## 模块概述

兑换码模块提供管理员创建和管理兑换码、用户使用兑换码兑换点数的功能。

主要职责：
- 管理员：创建兑换码、查询兑换码列表、查询兑换码详情、删除未使用的兑换码
- 用户：使用兑换码、查询账户余额

## API 接口流转

所有接口统一挂载在 `/api/redemption` 路径下，响应体通过 `ApiResponse<T>` 包装。

```
请求
  │
  ├─ 用户接口（登录即可访问）
  │   ├─ GET /balance → 查询当前用户余额
  │   │     └─ 校验登录 → 调用Service → 返回 ApiResponse.success()
  │   │
  │   └─ POST /redeem → 使用兑换码
  │         ├─ @Valid 校验请求体
  │         ├─ 校验登录
  │         ├─ 调用 Service.redeemCode()
  │         │     ├─ 获取 Redis 分布式锁（防止重复兑换）
  │         │     ├─ 三重校验：存在 → 未使用 → 未过期
  │         │     ├─ 调用 BalanceService.addBalance() 增加余额
  │         │     ├─ 标记兑换码已使用（usedBy / usedAt）
  │         │     └─ finally 释放锁
  │         ├─ 成功 → HTTP 201 + ApiResponse.created()
  │         └─ 失败（IllegalArgumentException）→ HTTP 400 + ApiResponse.error()
  │
  └─ 管理员接口（需 ADMIN 角色）
      ├─ POST /codes → 创建兑换码
      │     ├─ @PreAuthorize("hasRole('ADMIN')") 权限校验
      │     ├─ @Valid 校验请求体
      │     ├─ 调用 Service.createCode()
      │     │     ├─ 生成唯一码：sk- + UUID（循环校验最多100次）
      │     │     └─ 设置属性，可选过期时间（expiresInSeconds）
      │     └─ 成功 → HTTP 201 + ApiResponse.created()
      │
      ├─ GET /codes → 查询所有兑换码列表
      │     ├─ @PreAuthorize("hasRole('ADMIN')")
      │     ├─ 调用 Service.getAllCodes() → JOIN FETCH 关联对象
      │     └─ 成功 → HTTP 200 + ApiResponse.success()
      │
      ├─ GET /codes/{id} → 查询单个兑换码详情
      │     ├─ @PreAuthorize("hasRole('ADMIN')")
      │     ├─ 调用 Service.getCodeById() → JOIN FETCH 关联对象
      │     ├─ 成功 → HTTP 200 + ApiResponse.success()
      │     └─ 不存在 → HTTP 404 + ApiResponse.error()
      │
      └─ DELETE /codes/{id} → 删除兑换码
            ├─ @PreAuthorize("hasRole('ADMIN')")
            ├─ 调用 Service.deleteCode() → 仅允许删除未使用的
            ├─ 成功 → HTTP 204 无响应体
            └─ 失败（IllegalArgumentException）→ HTTP 400 + ApiResponse.error()
```

## 各类职责

| 类 | 职责 |
|----|------|
| `RedemptionCodeController` | REST API 入口，处理请求参数、权限校验、异常捕获、响应包装 |
| `RedemptionCodeService` | 业务接口，定义兑换码模块的核心操作 |
| `RedemptionCodeServiceImpl` | 业务实现，包含创建/兑换/查询/删除的完整逻辑，使用 Redis 锁防止并发重复兑换 |
| `RedemptionCodeRepository` | 数据访问层，继承 JpaRepository，提供带 JOIN FETCH 的查询方法避免 N+1 |
| `RedemptionCode` | JPA 实体类，映射 redemption_codes 表，包含兑换码的全部属性和关系 |
| `RedemptionCodeDTO` | 数据传输对象，将实体转换为前端友好格式（拆解 User 对象、添加 isExpired 字段） |
| `CreateRedemptionCodeRequest` | 创建兑换码的请求体 DTO，带 jakarta.validation 校验 |
| `RedeemCodeRequest` | 使用兑换码的请求体 DTO，带 @NotBlank 校验 |

## 关键设计

### 1. 唯一兑换码生成

```
格式：sk- + UUID（去除横杠）
策略：循环生成 + existsByCode 校验
上限：最多重试 100 次，超过抛出 RuntimeException
```

### 2. 并发安全（防止重复兑换）

```
使用 Redis 分布式锁：
- key: redemption:lock:{code}
- 超时: 10 秒（防止死锁）
- 实现: stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS)
- 释放: finally 块中 stringRedisTemplate.delete(lockKey)
- 锁冲突: 抛出 IllegalStateException → GlobalExceptionHandler 转 HTTP 429
```

### 3. 数据查询优化（避免 N+1）

```
RedemptionCode 中的 createdBy 和 usedBy 都是 FetchType.LAZY
Repository 使用 LEFT JOIN FETCH 在一次查询中加载关联：
- findAllWithDetails(): 查询所有码时 FETCH 关联
- findByIdWithDetails(): 查询单个码时 FETCH 关联
DTO 转换时可安全访问 id/username，无需额外查询
```

### 4. 过期时间处理

```
- expiresAt 可为 null → 永不过期
- 创建时：expiresInSeconds > 0 时设置 expiresAt = now + expiresInSeconds
- 兑换时：调用实体 isExpired() 方法判断，已过期抛出 IllegalArgumentException
- DTO：添加 isExpired 字段，由 fromEntity 方法计算后返回给前端
```

## 约定

- **成功创建/兑换**：`ApiResponse.created()`，HTTP 201
- **成功查询**：`ApiResponse.success()`，HTTP 200
- **成功删除**：`ResponseEntity.noContent().build()`，HTTP 204 无响应体
- **参数校验失败**：由 `@Valid` 触发，GlobalExceptionHandler 统一处理
- **业务校验失败**：Service 层抛出 `IllegalArgumentException`，Controller 捕获转 `ApiResponse.error(ErrorCode.BAD_REQUEST, msg)`
- **并发冲突（锁获取失败）**：Service 层抛出 `IllegalStateException`，GlobalExceptionHandler 转 HTTP 429
- **DELETE 操作**：仅允许删除 `isUsed = false` 的兑换码
