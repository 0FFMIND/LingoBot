package com.lingobot.learning.chat.service.impl;

import com.lingobot.learning.chat.dto.ChatRequest;
import com.lingobot.learning.chat.dto.EditMessageRequest;
import com.lingobot.learning.chat.dto.RetryMessageRequest;
import com.lingobot.learning.chat.service.ChatService;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.chat.service.MemoryCompactService;
import com.lingobot.learning.chat.service.MessageHistoryService;
import com.lingobot.learning.chat.service.SseEmitterService;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.llm.tool.service.McpService;
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
    private final ContextManagerService contextManagerService;
    private final MemoryCompactService memoryCompactService;
    
    private static final String DEFAULT_MODE = "chat";
    
    private static class FlowContext {
        final Long conversationId;
        final String mode;
        final String model;
        final String learningMode;
        final String vocabularyCategory;
        final String vocabularyDifficulty;
        final List<OpenAiChatMessage> messages;
        final List<OpenAiTool> tools;
        
        FlowContext(Long conversationId, String mode, String model, String learningMode,
                    String vocabularyCategory, String vocabularyDifficulty,
                    List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
            this.conversationId = conversationId;
            this.mode = mode;
            this.model = model;
            this.learningMode = learningMode;
            this.vocabularyCategory = vocabularyCategory;
            this.vocabularyDifficulty = vocabularyDifficulty;
            this.messages = messages;
            this.tools = tools;
        }
    }
    
    private FlowContext buildFlowContext(ChatRequest request, String forceMode, String forceLearningMode) {
        String mode = forceMode != null ? forceMode : 
            (request.getMode() != null ? request.getMode() : DEFAULT_MODE);
        String learningMode = forceLearningMode != null ? forceLearningMode :
            request.getLearningModeOrDefault();
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        
        checkAndExecuteCompactIfNeeded(request.getConversationId());
        
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryWithMode(
                request.getConversationId(), learningMode, vocabularyCategory, vocabularyDifficulty);
        
        List<OpenAiTool> tools;
        if (forceMode != null) {
            tools = mcpService.getOpenAiToolsForMode(forceMode);
        } else {
            tools = getToolsForRequest(request, mode);
        }
        
        return new FlowContext(
                request.getConversationId(),
                mode,
                request.getModelOrDefault(),
                learningMode,
                vocabularyCategory,
                vocabularyDifficulty,
                messages,
                tools
        );
    }
    
    private MessageDTO executeFlowInternal(ChatRequest request, String forceMode, String forceLearningMode, boolean useOnetimeExecution) {
        FlowContext ctx = buildFlowContext(request, forceMode, forceLearningMode);
        
        log.info("{} flow - mode: {}, model: {}, learningMode: {}, tools: {}",
                useOnetimeExecution ? "Onetime/Vocabulary" : "Loop",
                ctx.mode, ctx.model, ctx.learningMode, 
                ctx.tools != null ? ctx.tools.size() : 0);
        
        String aiResponse;
        if (useOnetimeExecution && ctx.tools != null && !ctx.tools.isEmpty()) {
            log.info("Executing one-time tool call");
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    ctx.conversationId, ctx.messages, ctx.tools, ctx.model);
            aiResponse = result.hasToolCalls() ? result.getToolResultText() : result.getTextResponse();
        } else {
            log.info("Executing loop tool call");
            aiResponse = toolLoopService.executeToolLoop(ctx.conversationId, ctx.messages, ctx.tools, ctx.mode, ctx.model);
        }
        
        return conversationService.addAssistantMessage(ctx.conversationId, aiResponse);
    }
    
    private SseEmitter executeFlowStreamInternal(ChatRequest request, String forceMode, String forceLearningMode) {
        FlowContext ctx = buildFlowContext(request, forceMode, forceLearningMode);
        
        log.info("Stream flow - conversationId: {}, mode: {}, model: {}, learningMode: {}, isAudio: {}, isImage: {}",
                ctx.conversationId, ctx.mode, ctx.model, ctx.learningMode,
                request.isAudioMessage(), request.isImageMessage());
        
        if (request.isAudioMessage()) {
            return sseEmitterService.createStreamEmitterWithAudio(ctx.conversationId, ctx.messages, ctx.tools, ctx.mode, ctx.model,
                    request.getAudioData(), request.getAudioFormat());
        }
        
        if (request.isImageMessage()) {
            return sseEmitterService.createStreamEmitterWithImage(ctx.conversationId, ctx.messages, ctx.tools, ctx.mode, ctx.model,
                    request.getImageData(), request.getImageFormat());
        }
        
        return sseEmitterService.createStreamEmitterWithTools(ctx.conversationId, ctx.messages, ctx.tools, ctx.mode, ctx.model);
    }

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
            throw new ChatException("音频数据不能为空或无效，请重新录制");
        }

        if (!isAudioMessage && (request.getContent() == null || request.getContent().trim().isEmpty())) {
            throw new ChatException("消息内容不能为空");
        }

        if (request.getConversationId() == null) {
            throw new ChatException("需要指定conversationId");
        }
    }

    private void validateChatRequestStream(ChatRequest request) {
        if (request.getConversationId() == null) {
            throw new ChatException("需要指定conversationId");
        }

        boolean isAudioMessage = request.isAudioMessage();
        boolean isImageMessage = request.isImageMessage();
        boolean isAudioType = ChatRequest.MESSAGE_TYPE_AUDIO.equals(request.getMessageType());
        boolean isImageType = ChatRequest.MESSAGE_TYPE_IMAGE.equals(request.getMessageType());

        if (isAudioType && !isAudioMessage) {
            throw new ChatException("音频数据不能为空或无效，请重新录制");
        }

        if (isImageType && !isImageMessage) {
            throw new ChatException("图片数据不能为空或无效，请重新上传");
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
        return executeFlowInternal(request, null, null, true);
    }
    
    private SseEmitter executeOnetimeFlowStream(ChatRequest request) {
        return executeFlowStreamInternal(request, null, null);
    }
    
    private MessageDTO executeVocabularyFlow(ChatRequest request) {
        return executeFlowInternal(request, "vocabulary", "vocabulary", true);
    }
    
    private SseEmitter executeVocabularyFlowStream(ChatRequest request) {
        return executeFlowStreamInternal(request, "vocabulary", "vocabulary");
    }
    
    private MessageDTO executeLoopFlow(ChatRequest request) {
        return executeFlowInternal(request, null, null, false);
    }
    
    private SseEmitter executeLoopFlowStream(ChatRequest request) {
        return executeFlowStreamInternal(request, null, null);
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
            throw new ChatException("要重试的消息不存在 " + assistantMessageId);
        }

        Message assistantMessage = assistantMessageOpt.get();

        if (!"assistant".equals(assistantMessage.getRole())) {
            throw new ChatException("只能重试AI助手的消息");
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
            throw new ChatException("前一条消息不是用户消息");
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
            throw new ChatException("要重试的消息不存在 " + assistantMessageId);
        }

        Message assistantMessage = assistantMessageOpt.get();

        if (!"assistant".equals(assistantMessage.getRole())) {
            throw new ChatException("只能重试AI助手的消息");
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
            throw new ChatException("前一条消息不是用户消息");
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
            throw new ChatException("要编辑的消息不存在 " + request.getUserMessageId());
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
            throw new ChatException("要编辑的消息不存在 " + request.getUserMessageId());
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
    
    private void checkAndExecuteCompactIfNeeded(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        
        try {
            ContextManagerService.CompactCheckResult checkResult = 
                    contextManagerService.checkAndGetReason(conversationId);
            
            if (checkResult.isNeeded()) {
                log.info("检测到需要Compact，conversationId: {}, 原因: {}", 
                        conversationId, checkResult.getReason());
                
                MemoryCompactService.CompactResult result = 
                        memoryCompactService.executeCompact(conversationId);
                
                if (result.isExecuted()) {
                    log.info("Compact执行完成，conversationId: {}, 之前: {} tokens, 之后: {} tokens, 节省: {} tokens",
                            conversationId, 
                            result.getBeforeTokens(), 
                            result.getAfterTokens(), 
                            result.getSavedTokens());
                } else {
                    log.warn("Compact未执行，conversationId: {}, 原因: {}", 
                            conversationId, result.getReason());
                }
            } else {
                log.debug("不需要Compact，conversationId: {}", conversationId);
            }
        } catch (Exception e) {
            log.error("检查或执行Compact时发生错误，conversationId: {}", conversationId, e);
        }
    }
}
