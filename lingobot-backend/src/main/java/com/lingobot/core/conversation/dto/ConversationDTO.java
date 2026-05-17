package com.lingobot.core.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话数据传输对象。
 *
 * 用于在服务层和控制层之间传输会话的基本信息，
 * 不包含学习相关扩展字段（学习模式、词汇意图等）。
 *
 * 学习相关的会话视图数据使用 ConversationViewDTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {

    // 对外暴露的唯一标识，用于 API 接口
    private String publicId;
    // 会话标题，用于在列表中展示
    private String title;
    // 会话创建时间
    private LocalDateTime createdAt;
    // 会话最后更新时间
    private LocalDateTime updatedAt;
    // 会话包含的消息数量
    private int messageCount;
}
