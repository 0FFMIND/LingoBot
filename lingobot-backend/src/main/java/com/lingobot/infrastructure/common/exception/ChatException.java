package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

/**
 * 聊天相关业务异常，用于对话、消息、AI 响应等场景。
 *
 *   throw ChatException.conversationNotFound();
 *   throw ChatException.messageContentEmpty("消息不能为空");
 *   throw ChatException.of(ErrorCode.SOME_CODE, "自定义消息");
 */
public class ChatException extends BaseException {

    // 私有构造，仅由静态工厂方法调用（使用 ErrorCode 默认消息）
    private ChatException(ErrorCode errorCode) {
        super(errorCode);
    }

    // 私有构造，仅由静态工厂方法调用（使用自定义消息）
    private ChatException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    // 通用工厂：传入任意 ErrorCode，使用其默认消息
    public static ChatException of(ErrorCode errorCode) {
        return new ChatException(errorCode);
    }

    // 通用工厂：传入任意 ErrorCode，使用自定义消息
    public static ChatException of(ErrorCode errorCode, String customMessage) {
        return new ChatException(errorCode, customMessage);
    }

    // 请求参数错误
    public static ChatException badRequest() {
        return new ChatException(ErrorCode.BAD_REQUEST);
    }

    // 请求参数错误（自定义消息）
    public static ChatException badRequest(String customMessage) {
        return new ChatException(ErrorCode.BAD_REQUEST, customMessage);
    }

    // 消息内容为空
    public static ChatException messageContentEmpty() {
        return new ChatException(ErrorCode.MESSAGE_CONTENT_EMPTY);
    }

    // 消息内容为空（自定义消息）
    public static ChatException messageContentEmpty(String customMessage) {
        return new ChatException(ErrorCode.MESSAGE_CONTENT_EMPTY, customMessage);
    }

    // 对话不存在或无权访问
    public static ChatException conversationNotFound() {
        return new ChatException(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    // 对话不存在或无权访问（自定义消息）
    public static ChatException conversationNotFound(String customMessage) {
        return new ChatException(ErrorCode.CONVERSATION_NOT_FOUND, customMessage);
    }

    // 消息不存在
    public static ChatException messageNotFound() {
        return new ChatException(ErrorCode.MESSAGE_NOT_FOUND);
    }

    // 消息不存在（自定义消息）
    public static ChatException messageNotFound(String customMessage) {
        return new ChatException(ErrorCode.MESSAGE_NOT_FOUND, customMessage);
    }

    // AI 返回空响应
    public static ChatException aiResponseEmpty() {
        return new ChatException(ErrorCode.AI_RESPONSE_EMPTY);
    }

    // AI 返回空响应（自定义消息）
    public static ChatException aiResponseEmpty(String customMessage) {
        return new ChatException(ErrorCode.AI_RESPONSE_EMPTY, customMessage);
    }

    // 工具调用次数超过限制
    public static ChatException toolCallExceeded() {
        return new ChatException(ErrorCode.TOOL_CALL_EXCEEDED);
    }

    // 工具调用次数超过限制（自定义消息）
    public static ChatException toolCallExceeded(String customMessage) {
        return new ChatException(ErrorCode.TOOL_CALL_EXCEEDED, customMessage);
    }

    // 音频数据无效或为空
    public static ChatException audioDataInvalid() {
        return new ChatException(ErrorCode.AUDIO_DATA_INVALID);
    }

    // 音频数据无效或为空（自定义消息）
    public static ChatException audioDataInvalid(String customMessage) {
        return new ChatException(ErrorCode.AUDIO_DATA_INVALID, customMessage);
    }
}
