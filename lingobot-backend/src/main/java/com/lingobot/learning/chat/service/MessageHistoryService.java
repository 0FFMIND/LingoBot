package com.lingobot.learning.chat.service;

import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.learning.chat.dto.HistoryBuildRequest;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.learning.prompt.chat.ChatPromptBuilder;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHistoryService {
    
    private final MessageRepository messageRepository;
    private final ChatPromptBuilder chatPromptBuilder;
    private final VocabularyConversationDataService vocabularyConversationDataService;
    
    public List<OpenAiChatMessage> buildConversationHistory(HistoryBuildRequest request) {
        List<Message> allMessages;
        Long conversationId = request.getConversationId();
        String learningMode = request.getLearningModeOrDefault();
        
        if (request.getMessages() != null) {
            allMessages = request.getMessages();
        } else if (conversationId != null) {
            allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
            log.info("=== 构建对话历史 ===");
            log.info("Conversation ID: {}, Learning Mode: {}, Category: {}, Difficulty: {}", 
                    conversationId, learningMode, 
                    request.getVocabularyCategory(), request.getVocabularyDifficulty());
            log.info("数据库中消息总数: {}", allMessages.size());
            
            for (int i = 0; i < allMessages.size(); i++) {
                Message msg = allMessages.get(i);
                log.info("消息 {}: role={}, content={}", i, msg.getRole(), 
                        msg.getContent().length() > 50 ? msg.getContent().substring(0, 50) + "..." : msg.getContent());
            }
        } else {
            throw new IllegalArgumentException("必须提供 messages 或 conversationId");
        }
        
        int endIndex = request.getEndIndex() != null ? request.getEndIndex() : allMessages.size();
        
        List<OpenAiChatMessage> result = buildConversationHistoryInternal(
                allMessages, endIndex, learningMode, 
                request.getVocabularyCategory(), 
                request.getVocabularyDifficulty(), 
                conversationId);
        
        if (conversationId != null) {
            log.info("=== 发送给 AI 的完整对话历史 ===");
            for (int i = 0; i < result.size(); i++) {
                OpenAiChatMessage msg = result.get(i);
                String contentStr = msg.getContentAsString();
                log.info("消息 {}: role={}, content={}", i, msg.getRole(), 
                        contentStr != null && contentStr.length() > 50 ? contentStr.substring(0, 50) + "..." : contentStr);
            }
        }
        
        return result;
    }
    
    private List<OpenAiChatMessage> buildConversationHistoryInternal(List<Message> allMessages, int endIndex,
                                                                      String learningMode, String vocabularyCategory,
                                                                      String vocabularyDifficulty, Long conversationId) {
        List<OpenAiChatMessage> messages = new ArrayList<>();
        
        String systemPrompt = buildSystemPrompt(learningMode, conversationId);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            log.info("已添加 System Prompt 用于模式: {}, Category: {}, Difficulty: {}",
                    learningMode, vocabularyCategory, vocabularyDifficulty);
        }
        
        for (int i = 0; i < endIndex; i++) {
            Message msg = allMessages.get(i);
            OpenAiChatMessage chatMsg;
            if (msg.getAudioData() != null && !msg.getAudioData().isEmpty()) {
                chatMsg = OpenAiChatMessage.createAudioMessage(
                        msg.getRole(),
                        msg.getContent(),
                        msg.getAudioData(),
                        msg.getAudioFormat()
                );
            } else if (msg.getImageData() != null && !msg.getImageData().isEmpty()) {
                chatMsg = OpenAiChatMessage.createImageMessage(
                        msg.getRole(),
                        msg.getContent(),
                        msg.getImageData(),
                        msg.getImageFormat()
                );
            } else {
                chatMsg = OpenAiChatMessage.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .build();
            }
            messages.add(chatMsg);
        }
        
        log.info("构建对话历史，共 {} 条消息（包含 System Prompt）", messages.size());
        
        return messages;
    }
    
    private String buildSystemPrompt(String learningMode, Long conversationId) {
        String systemPrompt = chatPromptBuilder.getSystemPrompt(learningMode);
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return null;
        }
        
        if ("vocabulary".equals(learningMode) && conversationId != null) {
            String vocabularyHistoryInfo = vocabularyConversationDataService.buildVocabularyHistoryForPrompt(conversationId);
            if (vocabularyHistoryInfo != null && !vocabularyHistoryInfo.isEmpty()) {
                systemPrompt = systemPrompt + vocabularyHistoryInfo;
                log.info("已添加词汇历史信息到 System Prompt，conversationId: {}", conversationId);
            }
        }
        
        if (conversationId != null) {
            String compactedSummary = vocabularyConversationDataService.getCompactedSummary(conversationId);
            if (compactedSummary != null && !compactedSummary.isEmpty()) {
                systemPrompt = systemPrompt + "\n\n## 历史对话摘要\n" + compactedSummary;
                log.info("已添加压缩对话摘要到 System Prompt，conversationId: {}", conversationId);
            }
        }
        
        return systemPrompt;
    }
    
    public int calculateContextLength(HistoryBuildRequest request) {
        List<OpenAiChatMessage> messages = buildConversationHistory(request);
        
        int totalLength = 0;
        for (OpenAiChatMessage msg : messages) {
            String content = msg.getContentAsString();
            if (content != null) {
                totalLength += content.length();
            }
        }
        
        return totalLength;
    }
    
    public List<MessageDTO> getMessagesByConversationId(Long conversationId) {
        List<Message> latestMessagesDesc = messageRepository.findTop10ByConversationIdOrderByTimestampDesc(conversationId);
        List<Message> messagesAsc = new ArrayList<>(latestMessagesDesc);
        java.util.Collections.reverse(messagesAsc);
        
        log.info("获取对话消息列表（最近10条），conversationId: {}, 消息数: {}", conversationId, messagesAsc.size());
        
        return messagesAsc.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public MessageDTO toDTO(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .content(message.getContent())
                .role(message.getRole())
                .timestamp(message.getTimestamp())
                .messageType(message.getMessageType())
                .audioData(message.getAudioData())
                .audioFormat(message.getAudioFormat())
                .audioDuration(message.getAudioDuration())
                .imageData(message.getImageData())
                .imageFormat(message.getImageFormat())
                .promptTokens(message.getPromptTokens())
                .completionTokens(message.getCompletionTokens())
                .totalTokens(message.getTotalTokens())
                .build();
    }
    
    @Deprecated
    public List<OpenAiChatMessage> buildConversationHistoryWithMode(Long conversationId, String learningMode) {
        return buildConversationHistory(HistoryBuildRequest.forConversation(conversationId, learningMode));
    }
    
    @Deprecated
    public List<OpenAiChatMessage> buildConversationHistoryWithMode(Long conversationId, String learningMode, 
                                                                     String vocabularyCategory, String vocabularyDifficulty) {
        return buildConversationHistory(HistoryBuildRequest.builder()
                .conversationId(conversationId)
                .learningMode(learningMode)
                .vocabularyCategory(vocabularyCategory)
                .vocabularyDifficulty(vocabularyDifficulty)
                .build());
    }
    
    @Deprecated
    public List<OpenAiChatMessage> buildConversationHistoryUpToIndex(List<Message> allMessages, int endIndex) {
        return buildConversationHistory(HistoryBuildRequest.forMessages(allMessages, endIndex));
    }
    
    @Deprecated
    public List<OpenAiChatMessage> buildConversationHistoryUpToIndexWithMode(List<Message> allMessages, int endIndex, String learningMode) {
        return buildConversationHistory(HistoryBuildRequest.builder()
                .messages(allMessages)
                .endIndex(endIndex)
                .learningMode(learningMode)
                .build());
    }
    
    @Deprecated
    public List<OpenAiChatMessage> buildConversationHistoryUpToIndexWithMode(List<Message> allMessages, int endIndex, 
                                                                             String learningMode, String vocabularyCategory, 
                                                                             String vocabularyDifficulty) {
        return buildConversationHistory(HistoryBuildRequest.builder()
                .messages(allMessages)
                .endIndex(endIndex)
                .learningMode(learningMode)
                .vocabularyCategory(vocabularyCategory)
                .vocabularyDifficulty(vocabularyDifficulty)
                .build());
    }
    
    @Deprecated
    public List<OpenAiChatMessage> buildConversationHistoryUpToIndexWithMode(List<Message> allMessages, int endIndex, 
                                                                             String learningMode, String vocabularyCategory, 
                                                                             String vocabularyDifficulty, Long conversationId) {
        return buildConversationHistory(HistoryBuildRequest.builder()
                .conversationId(conversationId)
                .messages(allMessages)
                .endIndex(endIndex)
                .learningMode(learningMode)
                .vocabularyCategory(vocabularyCategory)
                .vocabularyDifficulty(vocabularyDifficulty)
                .build());
    }
    
    @Deprecated
    public int calculateContextLength(Long conversationId, String learningMode) {
        return calculateContextLength(HistoryBuildRequest.forConversation(conversationId, learningMode));
    }
    
    @Deprecated
    public int calculateContextLength(Long conversationId, String learningMode,
                                       String vocabularyCategory, String vocabularyDifficulty) {
        return calculateContextLength(HistoryBuildRequest.builder()
                .conversationId(conversationId)
                .learningMode(learningMode)
                .vocabularyCategory(vocabularyCategory)
                .vocabularyDifficulty(vocabularyDifficulty)
                .build());
    }
}
