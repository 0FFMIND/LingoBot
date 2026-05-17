package com.lingobot.core.conversation.service;

import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.entity.Message;

import java.util.List;
import java.util.Optional;

/**
 * 会话服务接口。
 *
 * 提供会话和消息的核心业务逻辑，
 * 包括会话的增删改查、消息的添加和删除、
 * 当前会话管理等功能。
 *
 * 仅包含通用会话逻辑，学习相关功能在 learning 模块中实现。
 */
public interface ConversationService {

    // 创建新会话
    ConversationDTO createConversation(CreateConversationRequest request);

    // 根据 publicId 查询会话
    ConversationDTO getConversationByPublicId(String publicId);

    // 查询所有会话（最近 20 条）
    List<ConversationDTO> getAllConversations();

    // 分页查询会话
    PageResponseDTO<ConversationDTO> getConversationsByPage(int page, int size);

    // 更新会话标题
    ConversationDTO updateConversationTitle(String publicId, String title);

    // 删除会话
    void deleteConversation(String publicId);

    // 根据 ID 获取会话实体（内部使用）
    Conversation getConversationEntityById(Long id);

    // 获取当前选中的会话
    Optional<ConversationDTO> getCurrentConversation();

    // 设置当前选中的会话
    void setCurrentConversation(String publicId);

    // 将 publicId 转换为数据库 ID
    Long resolvePublicIdToId(String publicId);

    // 添加用户文本消息
    MessageDTO addUserMessage(Long conversationId, String content);

    // 添加用户文本消息（带会话实体）
    MessageDTO addUserMessage(Conversation conversation, String content);

    // 添加用户音频消息
    MessageDTO addUserMessageWithAudio(Long conversationId, String content,
                                        String audioData, String audioFormat, Integer audioDuration);

    // 添加用户音频消息（带会话实体）
    MessageDTO addUserMessageWithAudio(Conversation conversation, String content,
                                        String audioData, String audioFormat, Integer audioDuration);

    // 添加用户图片消息
    MessageDTO addUserMessageWithImage(Long conversationId, String content,
                                        String imageData, String imageFormat);

    // 添加用户图片消息（带会话实体）
    MessageDTO addUserMessageWithImage(Conversation conversation, String content,
                                        String imageData, String imageFormat);

    // 添加 AI 助手消息
    MessageDTO addAssistantMessage(Long conversationId, String content);

    // 添加 AI 助手消息（带会话实体）
    MessageDTO addAssistantMessage(Conversation conversation, String content);

    // 添加 AI 助手消息（带 token 使用统计）
    MessageDTO addAssistantMessage(Long conversationId, String content, TokenUsageDTO tokenUsage);

    // 添加 AI 助手消息（带会话实体和 token 使用统计）
    MessageDTO addAssistantMessage(Conversation conversation, String content, TokenUsageDTO tokenUsage);

    // 删除单条消息
    void deleteMessage(Long messageId);

    // 从指定索引开始删除后续所有消息
    void deleteMessagesFromIndex(Long conversationId, int startIndex);

    // 获取最近一条 AI 助手消息
    Optional<Message> getLastAssistantMessage(Long conversationId);

    // 获取最近一条用户消息
    Optional<Message> getLastUserMessage(Long conversationId);

    // 获取最近 N 条消息
    List<Message> getLastMessages(Long conversationId, int count);
}
