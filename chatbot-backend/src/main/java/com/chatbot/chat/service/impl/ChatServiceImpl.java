package com.lingobot.chat.service.impl;

import com.lingobot.chat.dto.ChatRequest;
import com.lingobot.chat.dto.EditMessageRequest;
import com.lingobot.chat.dto.RetryMessageRequest;
import com.lingobot.chat.service.ChatService;
import com.lingobot.chat.service.MessageHistoryService;
import com.lingobot.chat.service.SseEmitterService;
import com.lingobot.chat.service.ToolLoopService;
import com.lingobot.common.exception.ChatException;
import com.lingobot.conversation.dto.MessageDTO;
import com.lingobot.conversation.entity.Message;
import com.lingobot.conversation.repository.MessageRepository;
import com.lingobot.conversation.service.ConversationService;
import com.lingobot.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.llm.dto.openai.OpenAiTool;
import com.lingobot.mcp.service.McpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    
    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final McpService mcpService;
    private final ToolLoopService toolLoopService;
    private final MessageHistoryService messageHistoryService;
    private final SseEmitterService sseEmitterService;
    
    private static final String DEFAULT_MODE = "chat";

    @Override
    @Transactional
    public MessageDTO sendMessage(ChatRequest request) {
        validateChatRequest(request);
        
        addUserMessageToConversation(request);
        
        if (request.shouldUseOnetimeExecution()) {
            return executeOnetimeFlow(request);
        } else {
            return executeLoopFlow(request);
        }
    }

    @Override
    @Transactional
    public SseEmitter sendMessageStream(ChatRequest request) {
        validateChatRequestStream(request);
        
        addUserMessageToConversation(request);
        
        if (request.shouldUseOnetimeExecution()) {
            return executeOnetimeFlowStream(request);
        } else {
            return executeLoopFlowStream(request);
        }
    }

    @Override
    @Transactional
    public MessageDTO sendOnetimeMessage(ChatRequest request) {
        log.info("=== 执行 Onetime 消息流程 ===");
        log.info("messageType: {}, executionMode: {}", 
                request.getMessageTypeOrDefault(), request.getExecutionModeOrDefault());
        
        validateChatRequest(request);
        addUserMessageToConversation(request);
        
        return executeOnetimeFlow(request);
    }

    @Override
    @Transactional
    public SseEmitter sendOnetimeMessageStream(ChatRequest request) {
        log.info("=== 执行 Onetime 流式消息流程 ===");
        log.info("messageType: {}, executionMode: {}", 
                request.getMessageTypeOrDefault(), request.getExecutionModeOrDefault());
        
        validateChatRequestStream(request);
        addUserMessageToConversation(request);
        
        return executeOnetimeFlowStream(request);
    }

    @Override
    @Transactional
    public MessageDTO sendVocabularyMessage(ChatRequest request) {
        log.info("=== 执行 Vocabulary 消息流程 ===");
        log.info("learningMode: {}, vocabularyCategory: {}, vocabularyDifficulty: {}",
                request.getLearningModeOrDefault(), 
                request.getVocabularyCategoryOrDefault(),
                request.getVocabularyDifficultyOrDefault());
        
        validateChatRequest(request);
        addUserMessageToConversation(request);
        
        return executeVocabularyFlow(request);
    }

    @Override
    @Transactional
    public SseEmitter sendVocabularyMessageStream(ChatRequest request) {
        log.info("=== 执行 Vocabulary 流式消息流程 ===");
        log.info("learningMode: {}, vocabularyCategory: {}, vocabularyDifficulty: {}",
                request.getLearningModeOrDefault(),
                request.getVocabularyCategoryOrDefault(),
                request.getVocabularyDifficultyOrDefault());
        
        validateChatRequestStream(request);
        addUserMessageToConversation(request);
        
        return executeVocabularyFlowStream(request);
    }
    
    private void validateChatRequest(ChatRequest request) {
        boolean isAudioMessage = request.isAudioMessage();
        boolean isAudioType = ChatRequest.MESSAGE_TYPE_AUDIO.equals(request.getMessageType());
        
        if (isAudioType && !isAudioMessage) {
            throw new ChatException("音频数据不能为空或无效，请重新录�?);
        }
        
        if (!isAudioMessage && (request.getContent() == null || request.getContent().trim().isEmpty())) {
            throw new ChatException("消息内容不能为空");
        }
        
        if (request.getConversationId() == null) {
            throw new ChatException("需要指�?conversationId");
        }
    }
    
    private void validateChatRequestStream(ChatRequest request) {
        if (request.getConversationId() == null) {
            throw new ChatException("需要指�?conversationId");
        }
        
        boolean isAudioMessage = request.isAudioMessage();
        boolean isImageMessage = request.isImageMessage();
        boolean isAudioType = ChatRequest.MESSAGE_TYPE_AUDIO.equals(request.getMessageType());
        boolean isImageType = ChatRequest.MESSAGE_TYPE_IMAGE.equals(request.getMessageType());
        
        if (isAudioType && !isAudioMessage) {
            throw new ChatException("音频数据不能为空或无效，请重新录�?);
        }
        
        if (isImageType && !isImageMessage) {
            throw new ChatException("图片数据不能为空或无效，请重新上�?);
        }
        
        if (!isAudioMessage && !isImageMessage && 
                (request.getContent() == null || request.getContent().trim().isEmpty())) {
            throw new ChatException("消息内容不能为空");
        }
    }
    
    private void addUserMessageToConversation(ChatRequest request) {
        boolean isAudioMessage = request.isAudioMessage();
        boolean isImageMessage = request.isImageMessage();
        
        if (isAudioMessage) {
            conversationService.addUserMessageWithAudio(
                    request.getConversationId(),
                    request.getContent(),
                    request.getAudioData(),
                    request.getAudioFormat(),
                    request.getAudioDuration()
            );
        } else if (isImageMessage) {
            conversationService.addUserMessageWithImage(
                    request.getConversationId(),
                    request.getContent(),
                    request.getImageData(),
                    request.getImageFormat()
            );
        } else {
            conversationService.addUserMessage(request.getConversationId(), request.getContent());
        }
    }
    
    private MessageDTO executeOnetimeFlow(ChatRequest request) {
        String mode = request.getMode() != null ? request.getMode() : DEFAULT_MODE;
        String model = request.getModelOrDefault();
        String learningMode = request.getLearningModeOrDefault();
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        List<OpenAiTool> tools = getToolsForRequest(request, mode);
        log.info("Onetime flow - Available tools for mode '{}', model '{}', learningMode '{}': {}", 
                mode, model, learningMode, tools != null ? tools.size() : 0);

        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                request.getConversationId(), learningMode, vocabularyCategory, vocabularyDifficulty);

        String aiResponse;
        if (tools != null && !tools.isEmpty()) {
            log.info("Onetime flow - Executing one-time tool call");
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    request.getConversationId(), messages, tools, model);
            aiResponse = result.hasToolCalls() ? result.getToolResultText() : result.getTextResponse();
        } else {
            log.info("Onetime flow - No tools available, using simple chat");
            aiResponse = toolLoopService.executeToolLoop(request.getConversationId(), messages, tools, mode, model);
        }

        return conversationService.addAssistantMessage(request.getConversationId(), aiResponse);
    }
    
    private SseEmitter executeOnetimeFlowStream(ChatRequest request) {
        Long conversationId = request.getConversationId();
        String mode = request.getMode() != null ? request.getMode() : DEFAULT_MODE;
        String model = request.getModelOrDefault();
        String learningMode = request.getLearningModeOrDefault();
        
        log.info("Onetime stream flow - conversationId: {}, mode: {}, model: {}, learningMode: {}", 
                conversationId, mode, model, learningMode);
        
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                conversationId, learningMode, vocabularyCategory, vocabularyDifficulty);
        List<OpenAiTool> tools = getToolsForRequest(request, mode);
        
        if (request.isAudioMessage()) {
            return sseEmitterService.createStreamEmitterWithAudio(conversationId, messages, tools, mode, model,
                    request.getAudioData(), request.getAudioFormat());
        }
        
        if (request.isImageMessage()) {
            return sseEmitterService.createStreamEmitterWithImage(conversationId, messages, tools, mode, model,
                    request.getImageData(), request.getImageFormat());
        }
        
        return sseEmitterService.createStreamEmitterWithTools(conversationId, messages, tools, mode, model);
    }
    
    private MessageDTO executeVocabularyFlow(ChatRequest request) {
        String mode = "vocabulary";
        String model = request.getModelOrDefault();
        String learningMode = "vocabulary";
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode(mode);
        log.info("Vocabulary flow - Available tools: {}, vocabularyCategory: {}, vocabularyDifficulty: {}", 
                tools != null ? tools.size() : 0, vocabularyCategory, vocabularyDifficulty);

        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                request.getConversationId(), learningMode, vocabularyCategory, vocabularyDifficulty);

        String aiResponse;
        if (tools != null && !tools.isEmpty()) {
            log.info("Vocabulary flow - Executing one-time tool call for vocabulary");
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    request.getConversationId(), messages, tools, model);
            aiResponse = result.hasToolCalls() ? result.getToolResultText() : result.getTextResponse();
        } else {
            log.warn("Vocabulary flow - No vocabulary tools available!");
            aiResponse = toolLoopService.executeToolLoop(request.getConversationId(), messages, tools, mode, model);
        }

        return conversationService.addAssistantMessage(request.getConversationId(), aiResponse);
    }
    
    private SseEmitter executeVocabularyFlowStream(ChatRequest request) {
        Long conversationId = request.getConversationId();
        String mode = "vocabulary";
        String model = request.getModelOrDefault();
        String learningMode = "vocabulary";
        
        log.info("Vocabulary stream flow - conversationId: {}, mode: {}, model: {}", 
                conversationId, mode, model);
        
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                conversationId, learningMode, vocabularyCategory, vocabularyDifficulty);
        List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode(mode);
        
        log.info("Vocabulary stream flow - Using {} tools", tools != null ? tools.size() : 0);
        
        if (request.isAudioMessage()) {
            return sseEmitterService.createStreamEmitterWithAudio(conversationId, messages, tools, mode, model,
                    request.getAudioData(), request.getAudioFormat());
        }
        
        if (request.isImageMessage()) {
            return sseEmitterService.createStreamEmitterWithImage(conversationId, messages, tools, mode, model,
                    request.getImageData(), request.getImageFormat());
        }
        
        return sseEmitterService.createStreamEmitterWithTools(conversationId, messages, tools, mode, model);
    }
    
    private MessageDTO executeLoopFlow(ChatRequest request) {
        String mode = request.getMode() != null ? request.getMode() : DEFAULT_MODE;
        String model = request.getModelOrDefault();
        String learningMode = request.getLearningModeOrDefault();
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        List<OpenAiTool> tools = getToolsForRequest(request, mode);
        log.info("Loop flow - Available tools for mode '{}', model '{}', learningMode '{}': {}", 
                mode, model, learningMode, tools != null ? tools.size() : 0);

        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                request.getConversationId(), learningMode, vocabularyCategory, vocabularyDifficulty);

        String aiResponse = toolLoopService.executeToolLoop(
                request.getConversationId(), messages, tools, mode, model);

        return conversationService.addAssistantMessage(request.getConversationId(), aiResponse);
    }
    
    private SseEmitter executeLoopFlowStream(ChatRequest request) {
        Long conversationId = request.getConversationId();
        String mode = request.getMode() != null ? request.getMode() : DEFAULT_MODE;
        String model = request.getModelOrDefault();
        String learningMode = request.getLearningModeOrDefault();
        
        log.info("Loop stream flow - conversationId: {}, mode: {}, model: {}, learningMode: {}, isAudio: {}, isImage: {}", 
                conversationId, mode, model, learningMode, request.isAudioMessage(), request.isImageMessage());
        
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                conversationId, learningMode, vocabularyCategory, vocabularyDifficulty);
        List<OpenAiTool> tools = getToolsForRequest(request, mode);
        
        if (request.isAudioMessage()) {
            return sseEmitterService.createStreamEmitterWithAudio(conversationId, messages, tools, mode, model,
                    request.getAudioData(), request.getAudioFormat());
        }
        
        if (request.isImageMessage()) {
            return sseEmitterService.createStreamEmitterWithImage(conversationId, messages, tools, mode, model,
                    request.getImageData(), request.getImageFormat());
        }
        
        return sseEmitterService.createStreamEmitterWithTools(conversationId, messages, tools, mode, model);
    }
    
    private List<OpenAiTool> getToolsForRequest(ChatRequest request, String mode) {
        if (request.isMessageTypeVocabulary()) {
            return mcpService.getOpenAiToolsForMode("vocabulary");
        }
        return mcpService.getOpenAiToolsForMode(mode);
    }
    
    @Override
    public List<MessageDTO> getMessagesByConversationId(Long conversationId) {
        return messageHistoryService.getMessagesByConversationId(conversationId);
    }
    
    @Override
    @Transactional
    public MessageDTO retryMessage(Long conversationId, Long assistantMessageId) {
        Optional<Message> assistantMessageOpt = messageRepository.findById(assistantMessageId);
        
        if (assistantMessageOpt.isEmpty()) {
            throw new ChatException("要重试的消息不存�? " + assistantMessageId);
        }
        
        Message assistantMessage = assistantMessageOpt.get();
        
        if (!"assistant".equals(assistantMessage.getRole())) {
            throw new ChatException("只能重试AI助手的消�?);
        }
        
        if (!assistantMessage.getConversation().getId().equals(conversationId)) {
            throw new ChatException("消息不属于该对话");
        }
        
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        int assistantIndex = -1;
        
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(assistantMessageId)) {
                assistantIndex = i;
                break;
            }
        }
        
        if (assistantIndex == -1) {
            throw new ChatException("在对话中未找到该消息");
        }
        
        if (assistantIndex == 0) {
            throw new ChatException("该消息是第一条消息，无法重试");
        }
        
        Message userMessage = allMessages.get(assistantIndex - 1);
        
        if (!"user".equals(userMessage.getRole())) {
            throw new ChatException("前一条消息不是用户消�?);
        }
        
        conversationService.deleteMessagesFromIndex(conversationId, assistantIndex);
        
        List<Message> remainingMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndex(remainingMessages, remainingMessages.size());
        List<OpenAiTool> tools = mcpService.getOpenAiTools();
        
        String aiResponse = toolLoopService.executeToolLoop(conversationId, messages, tools, DEFAULT_MODE, "gpt");
        
        return conversationService.addAssistantMessage(conversationId, aiResponse);
    }
    
    @Override
    @Transactional
    public SseEmitter retryMessageStream(Long conversationId, Long assistantMessageId) {
        return retryMessageStream(RetryMessageRequest.builder()
                .conversationId(conversationId)
                .assistantMessageId(assistantMessageId)
                .build());
    }
    
    @Override
    @Transactional
    public SseEmitter retryMessageStream(RetryMessageRequest request) {
        Long conversationId = request.getConversationId();
        Long assistantMessageId = request.getAssistantMessageId();
        String model = request.getModelOrDefault();
        String mode = request.getModeOrDefault();
        String learningMode = request.getLearningModeOrDefault();
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        Optional<Message> assistantMessageOpt = messageRepository.findById(assistantMessageId);
        
        if (assistantMessageOpt.isEmpty()) {
            throw new ChatException("要重试的消息不存�? " + assistantMessageId);
        }
        
        Message assistantMessage = assistantMessageOpt.get();
        
        if (!"assistant".equals(assistantMessage.getRole())) {
            throw new ChatException("只能重试AI助手的消�?);
        }
        
        if (!assistantMessage.getConversation().getId().equals(conversationId)) {
            throw new ChatException("消息不属于该对话");
        }
        
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        int assistantIndex = -1;
        
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(assistantMessageId)) {
                assistantIndex = i;
                break;
            }
        }
        
        if (assistantIndex == -1) {
            throw new ChatException("在对话中未找到该消息");
        }
        
        if (assistantIndex == 0) {
            throw new ChatException("该消息是第一条消息，无法重试");
        }
        
        Message userMessage = allMessages.get(assistantIndex - 1);
        
        if (!"user".equals(userMessage.getRole())) {
            throw new ChatException("前一条消息不是用户消�?);
        }
        
        conversationService.deleteMessagesFromIndex(conversationId, assistantIndex);
        
        List<Message> remainingMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndexWithMode(
                remainingMessages, remainingMessages.size(), learningMode, vocabularyCategory, vocabularyDifficulty);
        List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode(mode);
        
        return sseEmitterService.createStreamEmitterWithTools(conversationId, messages, tools, mode, model);
    }
    
    @Override
    @Transactional
    public MessageDTO editMessage(EditMessageRequest request) {
        if (request.getNewContent() == null || request.getNewContent().trim().isEmpty()) {
            throw new ChatException("消息内容不能为空");
        }
        
        Optional<Message> userMessageOpt = messageRepository.findById(request.getUserMessageId());
        
        if (userMessageOpt.isEmpty()) {
            throw new ChatException("要编辑的消息不存�? " + request.getUserMessageId());
        }
        
        Message userMessage = userMessageOpt.get();
        
        if (!"user".equals(userMessage.getRole())) {
            throw new ChatException("只能编辑用户消息");
        }
        
        if (!userMessage.getConversation().getId().equals(request.getConversationId())) {
            throw new ChatException("消息不属于该对话");
        }
        
        if (userMessage.getContent().trim().equals(request.getNewContent().trim())) {
            throw new ChatException("消息内容没有变化");
        }
        
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(request.getConversationId());
        int userMessageIndex = -1;
        
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(request.getUserMessageId())) {
                userMessageIndex = i;
                break;
            }
        }
        
        if (userMessageIndex == -1) {
            throw new ChatException("在对话中未找到该消息");
        }
        
        int deleteFromIndex = userMessageIndex + 1;
        if (deleteFromIndex < allMessages.size()) {
            conversationService.deleteMessagesFromIndex(request.getConversationId(), deleteFromIndex);
        }
        
        userMessage.setContent(request.getNewContent().trim());
        messageRepository.save(userMessage);
        
        List<Message> remainingMessages = messageRepository.findByConversationIdOrderByTimestampAsc(request.getConversationId());
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndex(remainingMessages, remainingMessages.size());
        List<OpenAiTool> tools = mcpService.getOpenAiTools();
        
        String aiResponse = toolLoopService.executeToolLoop(request.getConversationId(), messages, tools, DEFAULT_MODE, "gpt");
        
        return conversationService.addAssistantMessage(request.getConversationId(), aiResponse);
    }
    
    @Override
    @Transactional
    public SseEmitter editMessageStream(EditMessageRequest request) {
        if (request.getNewContent() == null || request.getNewContent().trim().isEmpty()) {
            throw new ChatException("消息内容不能为空");
        }
        
        Optional<Message> userMessageOpt = messageRepository.findById(request.getUserMessageId());
        
        if (userMessageOpt.isEmpty()) {
            throw new ChatException("要编辑的消息不存�? " + request.getUserMessageId());
        }
        
        Message userMessage = userMessageOpt.get();
        
        if (!"user".equals(userMessage.getRole())) {
            throw new ChatException("只能编辑用户消息");
        }
        
        if (!userMessage.getConversation().getId().equals(request.getConversationId())) {
            throw new ChatException("消息不属于该对话");
        }
        
        if (userMessage.getContent().trim().equals(request.getNewContent().trim())) {
            throw new ChatException("消息内容没有变化");
        }
        
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(request.getConversationId());
        int userMessageIndex = -1;
        
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(request.getUserMessageId())) {
                userMessageIndex = i;
                break;
            }
        }
        
        if (userMessageIndex == -1) {
            throw new ChatException("在对话中未找到该消息");
        }
        
        int deleteFromIndex = userMessageIndex + 1;
        if (deleteFromIndex < allMessages.size()) {
            conversationService.deleteMessagesFromIndex(request.getConversationId(), deleteFromIndex);
        }
        
        userMessage.setContent(request.getNewContent().trim());
        messageRepository.save(userMessage);
        
        List<Message> remainingMessages = messageRepository.findByConversationIdOrderByTimestampAsc(request.getConversationId());
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndex(remainingMessages, remainingMessages.size());
        List<OpenAiTool> tools = mcpService.getOpenAiTools();
        
        return sseEmitterService.createStreamEmitterWithTools(request.getConversationId(), messages, tools, DEFAULT_MODE, "gpt");
    }
}
