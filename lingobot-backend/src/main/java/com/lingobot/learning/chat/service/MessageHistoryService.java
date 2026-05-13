package com.lingobot.learning.chat.service;

import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.learning.mode.service.SystemPromptService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
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
    private final SystemPromptService systemPromptService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final ConversationRepository conversationRepository;
    
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int RECENT_CARDS_TO_KEEP_IN_DETAIL = 5;
    
    public List<OpenAiChatMessage> buildConversationHistory(Long conversationId) {
        return buildConversationHistoryWithMode(conversationId, "chat");
    }
    
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
        
        String systemPrompt = systemPromptService.getSystemPrompt(learningMode, vocabularyCategory, vocabularyDifficulty);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            if ("vocabulary".equals(learningMode) && conversationId != null) {
                String vocabularyHistoryInfo = buildVocabularyHistoryForPrompt(conversationId);
                if (vocabularyHistoryInfo != null && !vocabularyHistoryInfo.isEmpty()) {
                    systemPrompt = systemPrompt + vocabularyHistoryInfo;
                    log.info("已添加词汇历史信息到 System Prompt，conversationId: {}", conversationId);
                }
            }
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            log.info("已添加 System Prompt 用于模式: {}, Category: {}, Difficulty: {}",
                    learningMode, vocabularyCategory, vocabularyDifficulty);
        }
        
        int startIndex = Math.max(0, endIndex - MAX_HISTORY_MESSAGES);
        
        for (int i = startIndex; i < endIndex; i++) {
            Message msg = allMessages.get(i);
            messages.add(OpenAiChatMessage.builder()
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
        }
        
        log.info("构建对话历史，共 {} 条消息（包含 System Prompt，从 {} 到 {}）", messages.size(), startIndex, endIndex);
        
        return messages;
    }
    
    private String buildVocabularyHistoryForPrompt(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ChatException.badRequest("对话不存在: " + conversationId));
        
        String compactedSummary = conversation.getCompactedSummary();
        
        List<VocabularyCard> allCards = vocabularyCardRepository.findByConversationIdOrderByPositionAsc(conversationId);
        
        if (allCards == null || allCards.isEmpty()) {
            return "";
        }
        
        List<VocabularyCard> activeCards = allCards.stream()
                .filter(card -> !card.getIsRegenerated())
                .collect(java.util.stream.Collectors.toList());
        
        if (activeCards.isEmpty()) {
            return "";
        }
        
        List<VocabularyCard> completedCards = activeCards.stream()
                .filter(VocabularyCard::getIsCompleted)
                .collect(java.util.stream.Collectors.toList());
        
        List<VocabularyCard> incompleteCards = activeCards.stream()
                .filter(card -> !card.getIsCompleted())
                .collect(java.util.stream.Collectors.toList());
        
        StringBuilder sb = new StringBuilder();
        
        if (compactedSummary != null && !compactedSummary.isEmpty()) {
            sb.append("\n\n## [已Compact] 历史单词卡学习摘要\n");
            sb.append("以下是之前学习的单词摘要（已压缩以节省上下文）：\n\n");
            sb.append(compactedSummary);
            sb.append("\n\n");
            
            log.info("使用Compacted摘要构建词汇历史，conversationId: {}", conversationId);
            
            if (!completedCards.isEmpty()) {
                int recentCount = Math.min(completedCards.size(), RECENT_CARDS_TO_KEEP_IN_DETAIL);
                if (recentCount > 0) {
                    List<VocabularyCard> recentCompleted = completedCards.subList(
                            completedCards.size() - recentCount, 
                            completedCards.size()
                    );
                    
                    sb.append("## 最近完成的单词（详细信息）\n");
                    for (int i = 0; i < recentCompleted.size(); i++) {
                        VocabularyCard card = recentCompleted.get(i);
                        sb.append(i + 1).append(". **").append(card.getWord() != null ? card.getWord() : "").append("**\n");
                        if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                            sb.append("   - 释义: ").append(card.getMeaning()).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }
        } else {
            if (!completedCards.isEmpty()) {
                sb.append("\n\n## 历史单词卡学习记录\n");
                sb.append("用户之前已经学习完成了以下单词，请在生成新单词时确保不重复：\n\n");
                
                for (int i = 0; i < completedCards.size(); i++) {
                    VocabularyCard card = completedCards.get(i);
                    sb.append(i + 1).append(". **").append(card.getWord() != null ? card.getWord() : "").append("**\n");
                    if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                        sb.append("   - 释义: ").append(card.getMeaning()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        
        if (!incompleteCards.isEmpty()) {
            sb.append("\n\n## 当前学习的单词（未完成）\n");
            sb.append("⚠️ 重要提示：用户有未完成学习的单词，请优先处理这些单词，不要生成新单词！\n\n");
            sb.append("以下是用户当前正在学习但尚未完成的单词：\n\n");
            
            for (int i = 0; i < incompleteCards.size(); i++) {
                VocabularyCard card = incompleteCards.get(i);
                sb.append(i + 1).append(". **").append(card.getWord() != null ? card.getWord() : "").append("**\n");
                if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                    sb.append("   - 释义: ").append(card.getMeaning()).append("\n");
                }
                if (card.getCategory() != null && !card.getCategory().isEmpty()) {
                    sb.append("   - 词汇类别: ").append(card.getCategory()).append("\n");
                }
                if (card.getDifficulty() != null && !card.getDifficulty().isEmpty()) {
                    sb.append("   - 难度: ").append(card.getDifficulty()).append("\n");
                }
                
                if (card.getUserMeaningGuess() != null && !card.getUserMeaningGuess().isEmpty()) {
                    sb.append("   - 学习进度: 用户已猜测意思（").append(card.getUserMeaningGuess()).append("），待完成造句练习\n");
                } else {
                    sb.append("   - 学习进度: 用户还未猜测意思\n");
                }
                
                if (card.getUserSentence() != null && !card.getUserSentence().isEmpty()) {
                    sb.append("   - 用户造的句子: ").append(card.getUserSentence()).append("\n");
                    if (card.getSentenceAnalysisCompleted() == null || !card.getSentenceAnalysisCompleted()) {
                        sb.append("   - 状态: 句子已提交，等待 AI 分析\n");
                    } else {
                        sb.append("   - 状态: AI 已完成句子分析\n");
                    }
                }
                
                sb.append("\n");
            }
            
            sb.append("## 处理规则（重要！）\n");
            sb.append("1. 只有当用户发送`[intent:next_word]` 时，才生成新的单词卡\n");
            sb.append("2. **绝对不能**跳过未完成的单词直接生成新单词\n");
            sb.append("\n");
        }
        
        log.info("已为 conversationId {} 构建词汇历史信息：hasCompactedSummary={}, 已完成 {} 个，未完成 {} 个",
                conversationId, 
                compactedSummary != null && !compactedSummary.isEmpty(),
                completedCards.size(), 
                incompleteCards.size());
        
        return sb.toString();
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
