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

    private ChatException(ErrorCode errorCode) {
        super(errorCode);
    }

    private ChatException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static ChatException of(ErrorCode errorCode) {
        return new ChatException(errorCode);
    }

    public static ChatException of(ErrorCode errorCode, String customMessage) {
        return new ChatException(errorCode, customMessage);
    }

    public static ChatException badRequest() {
        return new ChatException(ErrorCode.BAD_REQUEST);
    }

    public static ChatException badRequest(String customMessage) {
        return new ChatException(ErrorCode.BAD_REQUEST, customMessage);
    }

    public static ChatException messageContentEmpty() {
        return new ChatException(ErrorCode.MESSAGE_CONTENT_EMPTY);
    }

    public static ChatException messageContentEmpty(String customMessage) {
        return new ChatException(ErrorCode.MESSAGE_CONTENT_EMPTY, customMessage);
    }

    public static ChatException conversationNotFound() {
        return new ChatException(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    public static ChatException conversationNotFound(String customMessage) {
        return new ChatException(ErrorCode.CONVERSATION_NOT_FOUND, customMessage);
    }

    public static ChatException messageNotFound() {
        return new ChatException(ErrorCode.MESSAGE_NOT_FOUND);
    }

    public static ChatException messageNotFound(String customMessage) {
        return new ChatException(ErrorCode.MESSAGE_NOT_FOUND, customMessage);
    }

    public static ChatException aiResponseEmpty() {
        return new ChatException(ErrorCode.AI_RESPONSE_EMPTY);
    }

    public static ChatException aiResponseEmpty(String customMessage) {
        return new ChatException(ErrorCode.AI_RESPONSE_EMPTY, customMessage);
    }

    public static ChatException toolCallExceeded() {
        return new ChatException(ErrorCode.TOOL_CALL_EXCEEDED);
    }

    public static ChatException toolCallExceeded(String customMessage) {
        return new ChatException(ErrorCode.TOOL_CALL_EXCEEDED, customMessage);
    }

    public static ChatException audioDataInvalid() {
        return new ChatException(ErrorCode.AUDIO_DATA_INVALID);
    }

    public static ChatException audioDataInvalid(String customMessage) {
        return new ChatException(ErrorCode.AUDIO_DATA_INVALID, customMessage);
    }
}
