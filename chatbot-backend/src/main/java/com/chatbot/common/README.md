# Common 模块

## 模块概述

Common 模块是系统的通用组件层，提供跨模块共享的基础功能。该模块包含统一的异常处理、标准化的 API 响应格式、通用配置等，确保系统各模块的一致性和可维护性。

## 主要功能

- **统一异常处理**：全局异常捕获和处理
- **标准化响应**：统一的 API 响应格式
- **错误码定义**：集中管理系统错误码
- **通用配置**：跨模块共享的配置类

## 模块结构

### exception/ 异常层

#### `ChatException` 聊天异常
- 自定义业务异常类
- 继承 RuntimeException
- 用于业务逻辑中的错误抛出
- 支持消息和原因参数

#### `GlobalExceptionHandler` 全局异常处理器
- 使用 `@ControllerAdvice` 全局捕获异常
- 处理自定义业务异常 `ChatException`
- 处理通用异常 `Exception`
- 统一返回标准化错误响应
- 记录异常日志

### response/ 响应层

#### `ApiResponse` API 响应
- 统一的 API 响应格式
- 支持泛型数据类型
- 包含状态码、消息、数据字段
- 提供便捷的静态工厂方法：
  - `success()`：成功响应（无数据）
  - `success(data)`：成功响应（带数据）
  - `error(code, message)`：错误响应

#### `ErrorCode` 错误码枚举
- 集中定义系统错误码
- 包含错误码数值和消息
- 错误码示例：
  - `SUCCESS(200, "操作成功")`
  - `BAD_REQUEST(400, "请求参数错误")`
  - `UNAUTHORIZED(401, "未授权")`
  - `FORBIDDEN(403, "禁止访问")`
  - `NOT_FOUND(404, "资源不存在")`
  - `INTERNAL_ERROR(500, "服务器内部错误")`

### config/ 配置层

#### `JwtProperties` JWT 配置属性
- JWT 相关配置项
- 使用 `@ConfigurationProperties` 绑定
- 配置前缀：`jwt`
- 配置项：
  - `secret`：JWT 签名密钥
  - `expiration`：令牌过期时间（毫秒）
  - `issuer`：令牌签发者

## 统一响应格式

### 成功响应
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "field1": "value1",
    "field2": "value2"
  }
}
```

### 错误响应
```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": null
}
```

## 异常处理流程

### 1. 业务层抛出异常
```java
throw new ChatException("用户不存在");
```

### 2. 全局异常处理器捕获
```java
@ExceptionHandler(ChatException.class)
public ResponseEntity<ApiResponse<Void>> handleChatException(ChatException e) {
    log.error("业务异常: {}", e.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage()));
}
```

### 3. 返回标准化响应
- 自动设置 HTTP 状态码
- 统一响应体格式
- 记录错误日志

## 错误码规范

### HTTP 状态码对应
- **2xx**：成功
- **4xx**：客户端错误
- **5xx**：服务端错误

### 错误码使用原则
- 使用预定义的错误码枚举
- 业务异常优先使用 400 系列
- 未知异常使用 500
- 错误消息要清晰，便于定位问题

## JWT 配置

### 配置项说明
- **secret**：用于签名和验证 JWT 令牌的密钥
  - 建议使用足够长度的随机字符串
  - 生产环境应从环境变量或密钥管理服务获取
  - 不要硬编码在代码中

- **expiration**：令牌过期时间
  - 单位：毫秒
  - 默认：86400000（24 小时）
  - 根据安全需求调整

- **issuer**：令牌签发者标识
  - 用于验证令牌来源
  - 建议设置为应用名称或域名

### 配置示例
```properties
# application.properties
jwt.secret=your-super-secret-key-at-least-256-bits-long
jwt.expiration=86400000
jwt.issuer=chatbot-application
```

## 使用指南

### 抛出业务异常
```java
// 简单消息
throw new ChatException("消息内容不能为空");

// 带原因
throw new ChatException("文件读取失败", e);
```

### 返回成功响应
```java
// 无数据
return ApiResponse.success();

// 带数据
return ApiResponse.success(userDTO);
```

### 返回错误响应
```java
// 使用错误码
return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), "用户不存在");

// 直接使用状态码和消息
return ApiResponse.error(404, "资源不存在");
```

## 模块依赖

Common 模块被所有其他模块依赖：
- **Auth 模块**：使用异常和响应
- **Chat 模块**：使用异常和响应
- **Conversation 模块**：使用异常和响应
- **LLM 模块**：使用异常
- **MCP 模块**：使用异常
- **Audio 模块**：使用异常
- **Log 模块**：无直接依赖
- **Config 模块**：依赖 JwtProperties
- **Util 模块**：无直接依赖
