package com.lingobot.core.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息数据传输对象。
 *
 * 用于在 API 接口中返回消息信息，
 * 支持文本、音频、图片等多种消息类型，
 * 包含 token 使用统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    // 消息 ID
    private Long id;
    // 所属会话 ID
    private Long conversationId;
    // 消息内容
    private String content;
    // 发送者角色：user/assistant
    private String role;
    // 发送时间
    private LocalDateTime timestamp;
    // 消息类型：text/audio/image
    private String messageType;
    // 音频数据（Base64）
    private String audioData;
    // 音频格式
    private String audioFormat;
    // 音频时长（秒）
    private Integer audioDuration;
    // 图片数据（Base64）
    private String imageData;
    // 图片格式
    private String imageFormat;
    // 输入 token 数
    private Integer promptTokens;
    // 输出 token 数
    private Integer completionTokens;
    // 总 token 数
    private Integer totalTokens;

    // 判断是否为音频消息
    public boolean isAudioMessage() {
        return "audio".equals(messageType);
    }

    // 判断是否为图片消息
    public boolean isImageMessage() {
        return "image".equals(messageType);
    }
}
