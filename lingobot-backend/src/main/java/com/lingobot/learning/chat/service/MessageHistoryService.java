package com.lingobot.learning.chat.service;

import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
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
    
    public List<OpenAiChatMessage> buildConversationHistoryWithMode(Long conversationId, String learningMode) {
        return buildConversationHistoryWithMode(conversationId, learningMode, null, null);
    }

    public List<OpenAiChatMessage> buildConversationHistoryWithMode(Long conversationId, String learningMode, 
                                                                     String vocabularyCategory, String vocabularyDifficulty) {
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        log.info("=== 构建对话历史 ===");
        log.info("Conversation ID: {}, Learning Mode: {}, Category: {}, Difficulty: {}", 
                conversationId, learningMode, vocabularyCategory, vocabularyDifficulty);
        log.info("数据库中消息总数: {}", allMessages.size());
        
        for (int i = 0; i < allMessages.size(); i++) {
            Message msg = allMessages.get(i);
            log.info("消息 {}: role={}, content={}", i, msg.getRole(), 
                    msg.getContent().length() > 50 ? msg.getContent().substring(0, 50) + "..." : msg.getContent());
        }
        
        List<OpenAiChatMessage> result = buildConversationHistoryUpToIndexWithMode(allMessages, allMessages.size(), 
                learningMode, vocabularyCategory, vocabularyDifficulty, conversationId);
        
        log.info("=== 发送给 AI 的完整对话历史 ===");
        for (int i = 0; i < result.size(); i++) {
            OpenAiChatMessage msg = result.get(i);
            String contentStr = msg.getContentAsString();
            log.info("消息 {}: role={}, content={}", i, msg.getRole(), 
                    contentStr != null && contentStr.length() > 50 ? contentStr.substring(0, 50) + "..." : contentStr);
        }
        
        return result;
    }
    
    public List<OpenAiChatMessage> buildConversationHistoryUpToIndex(List<Message> allMessages, int endIndex) {
        return buildConversationHistoryUpToIndexWithMode(allMessages, endIndex, "chat", null, null, null);
    }
    
    public List<OpenAiChatMessage> buildConversationHistoryUpToIndexWithMode(List<Message> allMessages, int endIndex, String learningMode) {
        return buildConversationHistoryUpToIndexWithMode(allMessages, endIndex, learningMode, null, null, null);
    }

    public List<OpenAiChatMessage> buildConversationHistoryUpToIndexWithMode(List<Message> allMessages, int endIndex, 
                                                                             String learningMode, String vocabularyCategory, 
                                                                             String vocabularyDifficulty) {
        return buildConversationHistoryUpToIndexWithMode(allMessages, endIndex, learningMode, vocabularyCategory, vocabularyDifficulty, null);
    }

    public List<OpenAiChatMessage> buildConversationHistoryUpToIndexWithMode(List<Message> allMessages, int endIndex, 
                                                                             String learningMode, String vocabularyCategory, 
                                                                             String vocabularyDifficulty, Long conversationId) {
        List<OpenAiChatMessage> messages = new ArrayList<>();
        
        String systemPrompt = chatPromptBuilder.getSystemPrompt(learningMode);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
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
            
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            log.info("已添加 System Prompt 用于模式: {}, Category: {}, Difficulty: {}",
                    learningMode, vocabularyCategory, vocabularyDifficulty);
        }
        
        for (int i = 0; i < endIndex; i++) {
            Message msg = allMessages.get(i);
            messages.add(OpenAiChatMessage.builder()
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
        }
        
        log.info("构建对话历史，共 {} 条消息（包含 System Prompt）", messages.size());
        
        return messages;
    }
    
    public int calculateContextLength(Long conversationId, String learningMode) {
        return calculateContextLength(conversationId, learningMode, null, null);
    }

    public int calculateContextLength(Long conversationId, String learningMode,
                                       String vocabularyCategory, String vocabularyDifficulty) {
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        int totalLength = 0;

        String systemPrompt = chatPromptBuilder.getSystemPrompt(learningMode);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            if ("vocabulary".equals(learningMode) && conversationId != null) {
                String vocabularyHistoryInfo = vocabularyConversationDataService.buildVocabularyHistoryForPrompt(conversationId);
                if (vocabularyHistoryInfo != null && !vocabularyHistoryInfo.isEmpty()) {
                    systemPrompt = systemPrompt + vocabularyHistoryInfo;
                }
            }

            if (conversationId != null) {
                String compactedSummary = vocabularyConversationDataService.getCompactedSummary(conversationId);
                if (compactedSummary != null && !compactedSummary.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n## 历史对话摘要\n" + compactedSummary;
                }
            }

            totalLength += systemPrompt.length();
        }

        for (Message msg : allMessages) {
            if (msg.getContent() != null) {
                totalLength += msg.getContent().length();
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
}
