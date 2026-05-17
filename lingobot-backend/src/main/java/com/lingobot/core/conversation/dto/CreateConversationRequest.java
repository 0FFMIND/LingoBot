package com.lingobot.core.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求对象。
 *
 * 接收前端创建新会话时提交的参数，
 * 目前仅支持自定义会话标题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    // 会话标题，为空时使用默认标题
    private String title;
}
