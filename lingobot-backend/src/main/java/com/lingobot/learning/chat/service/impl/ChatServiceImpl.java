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
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.service.ToolService;
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
    private final ToolService toolService;
    private final ToolLoopService toolLoopService;
    private final MessageHistoryService messageHistoryService;
    private final SseEmitterService sseEmitterService;
    private final ContextManagerService contextManagerService;
    private final MemoryCompactService memoryCompactService;
    private final VocabularyConversationDataService vocabularyConversationDataService;
    
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
            tools = toolService.getOpenAiToolsForMode(forceMode);
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
        TokenUsageDTO tokenUsage = null;
        if (useOnetimeExecution && ctx.tools != null && !ctx.tools.isEmpty()) {
            log.info("Executing one-time tool call");
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    ctx.conversationId, ctx.messages, ctx.tools, ctx.model);
            aiResponse = result.hasToolCalls() ? result.getToolResultText() : result.getTextResponse();
            if (result.hasTokenUsage()) {
                tokenUsage = result.getTokenUsage();
                log.info("Token usage from tool loop: prompt={}, completion={}, total={}",
                        tokenUsage.getPromptTokens(), tokenUsage.getCompletionTokens(), tokenUsage.getTotalTokens());
            }
        } else {
            log.info("Executing loop tool call");
            aiResponse = toolLoopService.executeToolLoop(ctx.conversationId, ctx.messages, ctx.tools, ctx.mode, ctx.model);
        }
        
        return conversationService.addAssistantMessage(ctx.conversationId, aiResponse, tokenUsage);
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
        log.info("=== 执行消息流程 ===");
        log.info("messageType: {}, executionMode: {}, learningMode: {}", 
                request.getMessageTypeOrDefault(), 
                request.getExecutionModeOrDefault(),
                request.getLearningModeOrDefault());
        
        validateChatRequest(request);
        
        addUserMessageToConversation(request);
        
        String forceLearningMode = request.isMessageTypeVocabulary() ? "vocabulary" : null;
        boolean useOnetimeExecution = request.shouldUseOnetimeExecution();
        
        return executeFlowInternal(request, null, forceLearningMode, useOnetimeExecution);
    }

    @Override
    @Transactional
    public SseEmitter sendMessageStream(ChatRequest request) {
        log.info("=== 执行流式消息流程 ===");
        log.info("messageType: {}, executionMode: {}, learningMode: {}", 
                request.getMessageTypeOrDefault(), 
                request.getExecutionModeOrDefault(),
                request.getLearningModeOrDefault());
        
        validateChatRequestStream(request);
        
        addUserMessageToConversation(request);
        
        String forceLearningMode = request.isMessageTypeVocabulary() ? "vocabulary" : null;
        
        return executeFlowStreamInternal(request, null, forceLearningMode);
    }
    
    private void validateChatRequest(ChatRequest request) {
        boolean isAudioMessage = request.isAudioMessage();
        boolean isAudioType = ChatRequest.MESSAGE_TYPE_AUDIO.equals(request.getMessageType());
        
        if (isAudioType && !isAudioMessage) {
            throw ChatException.badRequest("音频数据不能为空或无效，请重新录制");
        }

        if (!isAudioMessage && (request.getContent() == null || request.getContent().trim().isEmpty())) {
            throw ChatException.badRequest("消息内容不能为空");
        }

        if (request.getConversationId() == null) {
            throw ChatException.badRequest("需要指定conversationId");
        }
    }

    private void validateChatRequestStream(ChatRequest request) {
        if (request.getConversationId() == null) {
            throw ChatException.badRequest("需要指定conversationId");
        }

        boolean isAudioMessage = request.isAudioMessage();
        boolean isImageMessage = request.isImageMessage();
        boolean isAudioType = ChatRequest.MESSAGE_TYPE_AUDIO.equals(request.getMessageType());
        boolean isImageType = ChatRequest.MESSAGE_TYPE_IMAGE.equals(request.getMessageType());

        if (isAudioType && !isAudioMessage) {
            throw ChatException.badRequest("音频数据不能为空或无效，请重新录制");
        }

        if (isImageType && !isImageMessage) {
            throw ChatException.badRequest("图片数据不能为空或无效，请重新上传");
        }
        
        if (!isAudioMessage && !isImageMessage && 
                (request.getContent() == null || request.getContent().trim().isEmpty())) {
            throw ChatException.badRequest("消息内容不能为空");
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
    

    
    private List<OpenAiTool> getToolsForRequest(ChatRequest request, String mode) {
        if (request.isMessageTypeVocabulary()) {
            return toolService.getOpenAiTool("vocabulary", null, mode);
        }
        return toolService.getOpenAiToolsForMode(mode);
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
            throw ChatException.badRequest("要重试的消息不存在: " + assistantMessageId);
        }

        Message assistantMessage = assistantMessageOpt.get();

        if (!"assistant".equals(assistantMessage.getRole())) {
            throw ChatException.badRequest("只能重试AI助手的消息");
        }

        if (!assistantMessage.getConversation().getId().equals(conversationId)) {
            throw ChatException.badRequest("消息不属于该对话");
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
            throw ChatException.badRequest("在对话中未找到该消息");
        }

        if (assistantIndex == 0) {
            throw ChatException.badRequest("该消息是第一条消息，无法重试");
        }

        Message userMessage = allMessages.get(assistantIndex - 1);

        if (!"user".equals(userMessage.getRole())) {
            throw ChatException.badRequest("前一条消息不是用户消息");
        }

        conversationService.deleteMessagesFromIndex(conversationId, assistantIndex);

        String learningMode = resolveLearningMode(conversationId);
        String mode = "vocabulary".equals(learningMode) ? "vocabulary" : DEFAULT_MODE;
        List<Message> remainingMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndexWithMode(
                remainingMessages, remainingMessages.size(), learningMode);
        List<OpenAiTool> tools = toolService.getOpenAiToolsForMode(mode);

        String aiResponse = toolLoopService.executeToolLoop(conversationId, messages, tools, mode, "qwen/qwen3.5-flash-20260224");

        return conversationService.addAssistantMessage(conversationId, aiResponse);
    }

    @Override
    @Transactional
    public SseEmitter retryMessageStream(Long conversationId, Long assistantMessageId) {
        String learningMode = resolveLearningMode(conversationId);
        String mode = "vocabulary".equals(learningMode) ? "vocabulary" : DEFAULT_MODE;
        return retryMessageStream(RetryMessageRequest.builder()
                .conversationId(conversationId)
                .assistantMessageId(assistantMessageId)
                .mode(mode)
                .learningMode(learningMode)
                .build());
    }
    
    @Override
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
            throw ChatException.badRequest("要重试的消息不存在: " + assistantMessageId);
        }

        Message assistantMessage = assistantMessageOpt.get();

        if (!"assistant".equals(assistantMessage.getRole())) {
            throw ChatException.badRequest("只能重试AI助手的消息");
        }

        if (!assistantMessage.getConversation().getId().equals(conversationId)) {
            throw ChatException.badRequest("消息不属于该对话");
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
            throw ChatException.badRequest("在对话中未找到该消息");
        }

        if (assistantIndex == 0) {
            throw ChatException.badRequest("该消息是第一条消息，无法重试");
        }

        Message userMessage = allMessages.get(assistantIndex - 1);

        if (!"user".equals(userMessage.getRole())) {
            throw ChatException.badRequest("前一条消息不是用户消息");
        }

        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndexWithMode(
                allMessages, assistantIndex, learningMode, vocabularyCategory, vocabularyDifficulty);
        List<OpenAiTool> tools = toolService.getOpenAiToolsForMode(mode);

        final int deleteFromIndex = assistantIndex;
        Runnable onSuccessCallback = () -> {
            try {
                conversationService.deleteMessagesFromIndex(conversationId, deleteFromIndex);
                log.info("重试消息成功，已删除从索引 {} 开始的旧消息，conversationId: {}", deleteFromIndex, conversationId);
            } catch (Exception e) {
                log.error("重试成功后删除旧消息失败，conversationId: {}", conversationId, e);
            }
        };

        return sseEmitterService.createStreamEmitterWithTools(conversationId, messages, tools, mode, model, onSuccessCallback);
    }
    
    @Override
    @Transactional
    public MessageDTO editMessage(EditMessageRequest request) {
        if (request.getNewContent() == null || request.getNewContent().trim().isEmpty()) {
            throw ChatException.badRequest("消息内容不能为空");
        }
        
        Optional<Message> userMessageOpt = messageRepository.findById(request.getUserMessageId());
        
        if (userMessageOpt.isEmpty()) {
            throw ChatException.badRequest("要编辑的消息不存在: " + request.getUserMessageId());
        }
        
        Message userMessage = userMessageOpt.get();
        
        if (!"user".equals(userMessage.getRole())) {
            throw ChatException.badRequest("只能编辑用户消息");
        }
        
        if (!userMessage.getConversation().getId().equals(request.getConversationId())) {
            throw ChatException.badRequest("消息不属于该对话");
        }
        
        boolean contentChanged = !userMessage.getContent().trim().equals(request.getNewContent().trim());
        boolean audioChanged = request.hasAudio() && !request.getAudioData().equals(userMessage.getAudioData());
        boolean imageChanged = request.hasImage() && !request.getImageData().equals(userMessage.getImageData());
        
        if (!contentChanged && !audioChanged && !imageChanged) {
            throw ChatException.badRequest("消息内容没有变化");
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
            throw ChatException.badRequest("在对话中未找到该消息");
        }
        
        int deleteFromIndex = userMessageIndex + 1;
        if (deleteFromIndex < allMessages.size()) {
            conversationService.deleteMessagesFromIndex(request.getConversationId(), deleteFromIndex);
        }
        
        userMessage.setContent(request.getNewContent().trim());
        if (request.hasAudio()) {
            userMessage.setMessageType(Message.MESSAGE_TYPE_AUDIO);
            userMessage.setAudioData(request.getAudioData());
            userMessage.setAudioFormat(request.getAudioFormat());
            userMessage.setAudioDuration(request.getAudioDuration());
        }
        if (request.hasImage()) {
            userMessage.setMessageType(Message.MESSAGE_TYPE_IMAGE);
            userMessage.setImageData(request.getImageData());
            userMessage.setImageFormat(request.getImageFormat());
        }
        messageRepository.save(userMessage);
        
        String learningMode = request.getLearningModeOrDefault();
        String mode = request.getModeOrDefault();
        String model = request.getModelOrDefault();
        List<Message> remainingMessages = messageRepository.findByConversationIdOrderByTimestampAsc(request.getConversationId());
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndexWithMode(
                remainingMessages, remainingMessages.size(), learningMode);
        List<OpenAiTool> tools = toolService.getOpenAiToolsForMode(mode);
        
        String aiResponse = toolLoopService.executeToolLoop(request.getConversationId(), messages, tools, mode, model);
        
        return conversationService.addAssistantMessage(request.getConversationId(), aiResponse);
    }
    
    @Override
    public SseEmitter editMessageStream(EditMessageRequest request) {
        if (request.getNewContent() == null || request.getNewContent().trim().isEmpty()) {
            throw ChatException.badRequest("消息内容不能为空");
        }

        Optional<Message> userMessageOpt = messageRepository.findById(request.getUserMessageId());

        if (userMessageOpt.isEmpty()) {
            throw ChatException.badRequest("要编辑的消息不存在: " + request.getUserMessageId());
        }

        Message userMessage = userMessageOpt.get();

        if (!"user".equals(userMessage.getRole())) {
            throw ChatException.badRequest("只能编辑用户消息");
        }

        if (!userMessage.getConversation().getId().equals(request.getConversationId())) {
            throw ChatException.badRequest("消息不属于该对话");
        }

        boolean contentChanged = !userMessage.getContent().trim().equals(request.getNewContent().trim());
        boolean audioChanged = request.hasAudio() && !request.getAudioData().equals(userMessage.getAudioData());
        boolean imageChanged = request.hasImage() && !request.getImageData().equals(userMessage.getImageData());

        if (!contentChanged && !audioChanged && !imageChanged) {
            throw ChatException.badRequest("消息内容没有变化");
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
            throw ChatException.badRequest("在对话中未找到该消息");
        }

        List<Message> messagesUpToUser = allMessages.subList(0, userMessageIndex + 1);
        Message editMessage = new Message();
        editMessage.setId(userMessage.getId());
        editMessage.setRole(userMessage.getRole());
        editMessage.setContent(request.getNewContent().trim());
        if (request.hasAudio()) {
            editMessage.setMessageType(Message.MESSAGE_TYPE_AUDIO);
            editMessage.setAudioData(request.getAudioData());
            editMessage.setAudioFormat(request.getAudioFormat());
            editMessage.setAudioDuration(request.getAudioDuration());
        } else if (request.hasImage()) {
            editMessage.setMessageType(Message.MESSAGE_TYPE_IMAGE);
            editMessage.setImageData(request.getImageData());
            editMessage.setImageFormat(request.getImageFormat());
        } else {
            editMessage.setMessageType(userMessage.getMessageType());
        }
        messagesUpToUser.set(userMessageIndex, editMessage);

        String learningMode = request.getLearningModeOrDefault();
        String mode = request.getModeOrDefault();
        String model = request.getModelOrDefault();
        String vocabularyCategory = request.getVocabularyCategoryOrDefault();
        String vocabularyDifficulty = request.getVocabularyDifficultyOrDefault();
        List<OpenAiChatMessage> messages = messageHistoryService.buildConversationHistoryUpToIndexWithMode(
                messagesUpToUser, messagesUpToUser.size(), learningMode, vocabularyCategory, vocabularyDifficulty);
        List<OpenAiTool> tools = toolService.getOpenAiToolsForMode(mode);

        final Long conversationId = request.getConversationId();
        final int deleteFromIndex = userMessageIndex + 1;
        final Long userMessageId = request.getUserMessageId();
        final String newContent = request.getNewContent().trim();
        final boolean hasAudio = request.hasAudio();
        final boolean hasImage = request.hasImage();
        final String audioData = request.getAudioData();
        final String audioFormat = request.getAudioFormat();
        final Integer audioDuration = request.getAudioDuration();
        final String imageData = request.getImageData();
        final String imageFormat = request.getImageFormat();
        Runnable onSuccessCallback = () -> {
            try {
                if (deleteFromIndex < allMessages.size()) {
                    conversationService.deleteMessagesFromIndex(conversationId, deleteFromIndex);
                }
                Message msgToUpdate = messageRepository.findById(userMessageId).orElse(null);
                if (msgToUpdate != null) {
                    msgToUpdate.setContent(newContent);
                    if (hasAudio) {
                        msgToUpdate.setMessageType(Message.MESSAGE_TYPE_AUDIO);
                        msgToUpdate.setAudioData(audioData);
                        msgToUpdate.setAudioFormat(audioFormat);
                        msgToUpdate.setAudioDuration(audioDuration);
                    }
                    if (hasImage) {
                        msgToUpdate.setMessageType(Message.MESSAGE_TYPE_IMAGE);
                        msgToUpdate.setImageData(imageData);
                        msgToUpdate.setImageFormat(imageFormat);
                    }
                    messageRepository.save(msgToUpdate);
                }
                log.info("编辑消息成功，已更新用户消息并删除旧回复，conversationId: {}", conversationId);
            } catch (Exception e) {
                log.error("编辑成功后更新消息失败，conversationId: {}", conversationId, e);
            }
        };

        if (request.hasAudio()) {
            return sseEmitterService.createStreamEmitterWithAudio(conversationId, messages, tools, mode, model,
                    request.getAudioData(), request.getAudioFormat(), onSuccessCallback);
        }

        if (request.hasImage()) {
            return sseEmitterService.createStreamEmitterWithImage(conversationId, messages, tools, mode, model,
                    request.getImageData(), request.getImageFormat(), onSuccessCallback);
        }

        return sseEmitterService.createStreamEmitterWithTools(conversationId, messages, tools, mode, model, onSuccessCallback);
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

    private String resolveLearningMode(Long conversationId) {
        if (conversationId == null) return "chat";
        return vocabularyConversationDataService.getByConversationId(conversationId)
                .map(data -> "vocabulary")
                .orElse("chat");
    }
}
