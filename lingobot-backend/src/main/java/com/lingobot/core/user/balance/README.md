# User Balance Module

## 模块概述

余额模块负责用户可用余额、冻结余额和余额流水。

主要职责：

- 用户：查询当前余额、查询交易记录。
- AI 业务：调用前冻结余额，调用成功确认扣费，调用失败退回冻结金额。
- 充值/入账：由兑换码、支付回调、人工补偿等外部行为调用充值方法增加余额。
- 管理员：直接设置指定用户余额。

## API / 调用流转

余额接口统一挂载在 `/api/balance` 路径下，响应体通过 `ApiResponse<T>` 包装。

```text
请求
  ├─ 用户接口（登录即可访问）
  │
  ├─ GET /api/balance → 查询当前用户可用余额
  │    ├─ 校验登录
  │    ├─ BalanceController.getBalance()
  │    ├─ BalanceService.getCurrentUserBalance()
  │    └─ 成功 → HTTP 200 + ApiResponse.success()
  │
  ├─ GET /api/balance/transactions → 分页查询当前用户交易记录
  │    ├─ 校验登录
  │    ├─ page / size 参数默认值处理
  │    ├─ BalanceService.getCurrentUserTransactions()
  │    └─ 成功 → HTTP 200 + ApiResponse.success()
  │
  └─ 管理员接口（需要 ADMIN 角色）
       └─ PUT /api/balance/users/{userId} → 直接设置用户余额
            ├─ @PreAuthorize("hasRole('ADMIN')")
            ├─ @Valid 校验请求体
            ├─ BalanceService.setUserBalance(userId, newBalance)
            │    ├─ 校验 newBalance >= 0
            │    ├─ findByUserIdForUpdate() 加悲观写锁
            │    ├─ 设置可用余额
            │    ├─ 创建交易记录：type=ADMIN_ADJUSTMENT, status=SUCCEEDED
            │    └─ 返回新余额
            ├─ 成功 → HTTP 200 + ApiResponse.success()
            └─ 失败 → HTTP 400 + ApiResponse.error()
```

## AI 扣费 Workflow

AI 扣费不是直接扣余额，而是冻结-确认模式。适用于“先占用额度，业务失败时可退回”的调用。

```text
AI 业务请求（例如生成词汇卡）
  ├─ 计算 cost / apiCategory / apiEndpoint / description / conversationId
  │
  ├─ BalanceService.freezeBalance(...)
  │    ├─ 从 SecurityContext 获取当前用户
  │    ├─ findByUserIdForUpdate() 加悲观写锁
  │    ├─ 校验可用余额 >= cost
  │    ├─ 可用余额 -= cost
  │    ├─ 冻结余额 += cost
  │    ├─ 创建交易记录：type=CHARGE, status=PENDING
  │    └─ 返回 transactionId
  │
  ├─ 执行 AI 业务逻辑
  │
  ├─ AI 调用成功
  │    └─ BalanceService.confirmTransaction(transactionId)
  │         ├─ 查询交易记录
  │         ├─ 校验 status=PENDING
  │         ├─ findByUserIdForUpdate() 加悲观写锁
  │         ├─ 冻结余额 -= amount
  │         └─ 更新交易记录：status=SUCCEEDED
  │
  └─ AI 调用失败
       └─ BalanceService.cancelTransaction(transactionId)
            ├─ 查询交易记录
            ├─ 校验 status=PENDING
            ├─ findByUserIdForUpdate() 加悲观写锁
            ├─ 冻结余额 -= amount
            ├─ 可用余额 += amount
            └─ 更新交易记录：status=RELEASED
```

## 充值 / 入账 Workflow

充值不是 AI 扣费链路的一部分，它是独立的余额入账行为。

当前 `BalanceController` 没有公开用户自助充值接口。现有入账入口来自业务内部调用，例如兑换码兑换、支付回调、人工补偿等。

```text
外部入账行为（兑换码 / 支付回调 / 人工补偿）
  ├─ 校验业务来源是否合法
  ├─ 确定 userId / amount / description
  │
  └─ BalanceService.addBalanceWithLog(userId, amount, description)
       ├─ 校验 amount > 0
       ├─ 查询用户是否存在
       ├─ findByUserIdForUpdate() 加悲观写锁
       ├─ 可用余额 += amount
       ├─ 创建交易记录：type=RECHARGE, status=SUCCEEDED
       └─ 返回新余额
```

典型调用：

```text
RedemptionCodeService.redeemCode()
  ├─ 校验兑换码存在 / 未使用 / 未过期
  ├─ BalanceService.addBalance(userId, amount)
  │    └─ BalanceService.addBalanceWithLog(userId, amount, "充值")
  └─ 标记兑换码已使用
```

后续如果接支付充值，应新增支付回调或充值订单模块，由该模块在支付成功后调用 `addBalanceWithLog(...)`。不要把支付充值接到 `freezeBalance -> confirmTransaction/cancelTransaction` 这条 AI 扣费链路上。

## 管理员调账 Workflow

管理员直接设置用户余额，适用于人工调整、补偿、清零等场景。调账时会创建交易流水记录，便于审计追踪。

```text
管理员后台调账请求
  ├─ @PreAuthorize("hasRole('ADMIN')") 校验管理员权限
  ├─ 校验 userId / newBalance（newBalance >= 0）
  │
  └─ BalanceService.setUserBalance(userId, newBalance)
       ├─ 查询用户是否存在
       ├─ findByUserIdForUpdate() 加悲观写锁
       ├─ 获取旧余额（oldBalance）
       ├─ 只有余额有变化时才保存
       │    ├─ 设置可用余额 = newBalance
       │    ├─ userBalanceRepository.saveAndFlush()
       │    ├─ 创建交易记录：
       │    │    ├─ type=ADMIN_ADJUSTMENT
       │    │    ├─ amount=|newBalance - oldBalance|
       │    │    ├─ balanceBefore=oldBalance
       │    │    ├─ balanceAfter=newBalance
       │    │    ├─ status=SUCCEEDED
       │    │    ├─ description="管理员修改余额"
       │    │    └─ transactionRepository.save()
       │    └─ 记录日志
       └─ 返回 newBalance
```

前端展示：

- 交易类型标签显示为"管理员调账"
- 图标显示为 ⚙️
- 颜色为橙色 (#f39c12)
- 金额正负号根据余额变化自动判断（增加显示 +，减少显示 -）
- 描述信息显示"管理员修改余额"

## 重要类

| 类 | 作用 |
|----|------|
| `BalanceController` | 余额 REST 入口：余额查询、交易记录查询、管理员调账 |
| `BalanceService` | 余额业务接口：查询、冻结、确认、取消、充值、调账 |
| `BalanceServiceImpl` | 余额业务实现，负责锁、余额计算和交易记录写入 |
| `UserBalanceRepository` | 用户余额数据访问，提供 `findByUserIdForUpdate()` |
| `BalanceTransactionRepository` | 交易记录数据访问 |
| `UserBalance` | 用户余额实体：可用余额和冻结余额 |
| `BalanceTransaction` | 余额交易实体：扣费、充值、冻结确认等流水 |

## 关键约定

- 所有余额变动必须使用 `findByUserIdForUpdate()` 加悲观写锁。
- AI 扣费必须走 `freezeBalance -> confirmTransaction/cancelTransaction`。
- 充值/入账必须走 `addBalanceWithLog(...)`，并创建 `RECHARGE / SUCCEEDED` 交易记录。
- 管理员调账使用 `setUserBalance(...)`，会创建 `ADMIN_ADJUSTMENT / SUCCEEDED` 交易记录。
- 金额统一使用 `BigDecimal`，保留 2 位小数，采用 `RoundingMode.HALF_UP`。
- 余额字段读取时需要处理 null，默认按 `BigDecimal.ZERO` 计算。
