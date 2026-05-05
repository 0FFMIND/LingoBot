package com.lingobot.infrastructure.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码
    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    MESSAGE_CONTENT_EMPTY(1003, "消息内容不能为空"),
    CONVERSATION_NOT_FOUND(1004, "对话不存在或无权访问"),
    MESSAGE_NOT_FOUND(1005, "消息不存在"),
    AI_RESPONSE_EMPTY(1006, "AI 返回空响应"),
    TOOL_CALL_EXCEEDED(1007, "工具调用次数超过限制"),
    AUDIO_DATA_INVALID(1008, "音频数据不能为空或无效，请重新录制");
    
    private final int code;
    private final String message;
}
