package com.lingobot.chat.service;

import com.lingobot.conversation.dto.MessageDTO;
import com.lingobot.conversation.entity.Message;
import com.lingobot.conversation.repository.MessageRepository;
import com.lingobot.learning.service.SystemPromptService;
import com.lingobot.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.vocabulary.entity.VocabularyCard;
import com.lingobot.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.vocabulary.service.VocabularyStateService;
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
    private final VocabularyStateService vocabularyStateService;
    private final VocabularyCardRepository vocabularyCardRepository;
    
    private static final int MAX_HISTORY_MESSAGES = 20;
    
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
        
        log.info("=== 发送给 AI 的完整对话历�?===");
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
                String vocabularyStateInfo = vocabularyStateService.getCurrentWordInfoForPrompt(conversationId);
                if (vocabularyStateInfo != null && !vocabularyStateInfo.isEmpty()) {
                    systemPrompt = systemPrompt + vocabularyStateInfo;
                    log.info("已添加词汇状态信息到 System Prompt，conversationId: {}", conversationId);
                }
            }
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            log.info("已添�?System Prompt 用于模式: {}, Category: {}, Difficulty: {}", 
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
        
        log.info("构建对话历史，共 {} 条消息（包含 System Prompt，从 {} �?{}�?, messages.size(), startIndex, endIndex);
        
        return messages;
    }
    
    private String buildVocabularyHistoryForPrompt(Long conversationId) {
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
                if (card.getLevel() != null && !card.getLevel().isEmpty()) {
                    sb.append("   - 难度: ").append(card.getLevel()).append("\n");
                }
                
                if (card.getUserMeaningGuess() != null && !card.getUserMeaningGuess().isEmpty()) {
                    sb.append("   - 学习进度: 用户已猜测意思（").append(card.getUserMeaningGuess()).append("），待完成造句练习\n");
                } else {
                    sb.append("   - 学习进度: 用户还未猜测意思\n");
                }
                
                if (card.getUserSentence() != null && !card.getUserSentence().isEmpty()) {
                    sb.append("   - 用户造的句子: ").append(card.getUserSentence()).append("\n");
                    if (card.getAiFeedback() == null || card.getAiFeedback().isEmpty()) {
                        sb.append("   - 状�? 句子已提交，等待 AI 反馈\n");
                    } else {
                        sb.append("   - 状�? AI 已提供反馈\n");
                    }
                }
                
                sb.append("\n");
            }
            
            sb.append("## 处理规则（重要！）\n");
            sb.append("1. 如果用户发送的消息包含 `[intent:make_sentence]`，说明用户正在完成造句练习，请调用 `display_sentence_feedback` 工具\n");
            sb.append("2. 只有当用户发�?`[intent:next_word]` 时，才生成新的单词卡\n");
            sb.append("3. **绝对不能**跳过未完成的单词直接生成新单词\n");
            sb.append("\n");
        }
        
        log.info("已为 conversationId {} 构建历史信息：已完成 {} 个，未完�?{} �?, 
                conversationId, completedCards.size(), incompleteCards.size());
        
        return sb.toString();
    }
    
    public List<MessageDTO> getMessagesByConversationId(Long conversationId) {
        List<Message> latestMessagesDesc = messageRepository.findTop10ByConversationIdOrderByTimestampDesc(conversationId);
        List<Message> messagesAsc = new ArrayList<>(latestMessagesDesc);
        java.util.Collections.reverse(messagesAsc);
        
        log.info("获取对话消息列表（最�?0条），conversationId: {}, 消息�? {}", conversationId, messagesAsc.size());
        
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
                .build();
    }
}
