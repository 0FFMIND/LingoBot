package com.lingobot.learning.chat.service;

import com.lingobot.learning.chat.dto.StreamEvent;
import com.lingobot.learning.chat.service.ToolLoopService.ToolLoopResult;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.llm.service.LlmService;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import com.lingobot.infrastructure.tool.service.ToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SseEmitterService {
    
    private final ConversationService conversationService;
    private final LlmService llmService;
    private final ToolService toolService;
    private final ToolLoopService toolLoopService;
    private final ObjectMapper objectMapper;
    private final Executor sseExecutor;
    private final TransactionTemplate transactionTemplate;
    
    public SseEmitterService(ConversationService conversationService,
                              LlmService llmService,
                              ToolService toolService,
                              ToolLoopService toolLoopService,
                              ObjectMapper objectMapper,
                              @Qualifier("sseExecutor") Executor sseExecutor,
                              TransactionTemplate transactionTemplate) {
        this.conversationService = conversationService;
        this.llmService = llmService;
        this.toolService = toolService;
        this.toolLoopService = toolLoopService;
        this.objectMapper = objectMapper;
        this.sseExecutor = sseExecutor;
        this.transactionTemplate = transactionTemplate;
    }
    
    private static final long SSE_TIMEOUT = 180000L;
    private static final int MAX_TOOL_CALLS = 10;
    
    public SseEmitter createStreamEmitterWithTools(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model) {
        return createStreamEmitterWithTools(conversationId, messages, tools, mode, model, null);
    }

    public SseEmitter createStreamEmitterWithTools(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   Runnable onSuccessCallback) {
        if (tools != null && !tools.isEmpty()) {
            if ("agent".equals(mode)) {
                return createEmitterWithConversation(conversationId, onSuccessCallback, (emitter, convId, fullResponse, disposableRef, cancelled) -> {
                    log.info("Agent mode: executing full tool loop with SSE events, model: {}", model);
                    setupEmitterCallbacks(emitter, disposableRef, cancelled);
                    sseExecutor.execute(() -> {
                        executeAgentToolLoopWithSse(conversationId, messages, tools, emitter,
                                fullResponse, disposableRef, model, cancelled, onSuccessCallback);
                    });
                });
            } else {
                return createEmitterWithConversation(conversationId, onSuccessCallback, (emitter, convId, fullResponse, disposableRef, cancelled) -> {
                    log.info("Chat mode: executing single tool call (one-time, max 3 retries), model: {}", model);
                    setupEmitterCallbacks(emitter, disposableRef, cancelled);
                    sseExecutor.execute(() -> {
                        try {
                            ToolLoopResult toolLoopResult = toolLoopService.executeOneTimeToolCall(conversationId, messages, tools, model);

                            if (toolLoopResult.hasToolCalls()) {
                                log.info("Tool calls were executed, returning tool results directly");
                                sendContentAndDone(emitter, conversationId, toolLoopResult.getToolResultText(), toolLoopResult.getTokenUsage(), fullResponse, onSuccessCallback);
                            } else if (toolLoopResult.hasTextResponse()) {
                                log.info("AI returned text response directly, using cached response");
                                sendContentAndDone(emitter, conversationId, toolLoopResult.getTextResponse(), toolLoopResult.getTokenUsage(), fullResponse, onSuccessCallback);
                            } else {
                                log.warn("One-time tool call returned no result, completing emitter with empty response");
                                sendContentAndDone(emitter, conversationId, "", toolLoopResult.getTokenUsage(), fullResponse, onSuccessCallback);
                            }
                        } catch (Exception e) {
                            log.error("One-time tool call failed", e);
                            sendErrorAndComplete(emitter, e);
                        }
                    });
                });
            }
        }

        return createEmitterWithConversation(conversationId, onSuccessCallback, (emitter, convId, fullResponse, disposableRef, cancelled) -> {
            Flux<String> responseFlux = llmService.chatStream(model, messages);
            subscribeFluxToEmitter(responseFlux, emitter, conversationId, fullResponse, disposableRef, "", cancelled, onSuccessCallback);
        });
    }
    
    public SseEmitter createStreamEmitterWithAudio(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   String audioData,
                                                   String audioFormat) {
        return createStreamEmitterWithAudio(conversationId, messages, tools, mode, model, audioData, audioFormat, null);
    }

    public SseEmitter createStreamEmitterWithAudio(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   String audioData,
                                                   String audioFormat,
                                                   Runnable onSuccessCallback) {
        return createEmitterWithConversation(conversationId, onSuccessCallback, (emitter, convId, fullResponse, disposableRef, cancelled) -> {
            log.info("开始音频流式处理，使用 Qwen-Omni 模型");
            Flux<String> responseFlux = llmService.chatStreamWithAudio(messages, audioData, audioFormat, model);
            subscribeFluxToEmitter(responseFlux, emitter, conversationId, fullResponse, disposableRef, "音频", cancelled, onSuccessCallback);
        });
    }
    
    public SseEmitter createStreamEmitterWithImage(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   String imageData,
                                                   String imageFormat) {
        return createStreamEmitterWithImage(conversationId, messages, tools, mode, model, imageData, imageFormat, null);
    }

    public SseEmitter createStreamEmitterWithImage(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   String imageData,
                                                   String imageFormat,
                                                   Runnable onSuccessCallback) {
        return createEmitterWithConversation(conversationId, onSuccessCallback, (emitter, convId, fullResponse, disposableRef, cancelled) -> {
            log.info("开始图片流式处理，使用支持图片的多模态模型");
            Flux<String> responseFlux = llmService.chatStreamWithImage(messages, imageData, imageFormat, model);
            subscribeFluxToEmitter(responseFlux, emitter, conversationId, fullResponse, disposableRef, "图片", cancelled, onSuccessCallback);
        });
    }

    @FunctionalInterface
    private interface EmitterInitializer {
        void initialize(SseEmitter emitter, Long conversationId,
                        AtomicReference<String> fullResponse, AtomicReference<Disposable> disposableRef,
                        java.util.concurrent.atomic.AtomicBoolean cancelled);
    }

    private SseEmitter createEmitterWithConversation(Long conversationId, Runnable onSuccessCallback, EmitterInitializer initializer) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

        try {
            conversationService.getConversationEntityById(conversationId);
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", conversationId, e);
            sendErrorAndComplete(emitter, e);
            return emitter;
        }

        try {
            initializer.initialize(emitter, conversationId, fullResponse, disposableRef, cancelled);
        } catch (Exception e) {
            log.error("Emitter initialization failed", e);
            sendErrorAndComplete(emitter, e);
        }

        return emitter;
    }

    private void subscribeFluxToEmitter(Flux<String> responseFlux,
                                         SseEmitter emitter,
                                         Long conversationId,
                                         AtomicReference<String> fullResponse,
                                         AtomicReference<Disposable> disposableRef,
                                         String label,
                                         java.util.concurrent.atomic.AtomicBoolean cancelled,
                                         Runnable onSuccessCallback) {
        setupEmitterCallbacks(emitter, disposableRef, cancelled);

        Disposable disposable = responseFlux
                .doOnNext(chunk -> {
                    try {
                        fullResponse.updateAndGet(s -> s + chunk);
                        StreamEvent event = StreamEvent.content(chunk);
                        String json = objectMapper.writeValueAsString(event);
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        log.warn("发送SSE事件失败，客户端可能已断开连接: {}", e.getMessage());
                        if (disposableRef.get() != null) {
                            disposableRef.get().dispose();
                        }
                        cancelled.set(true);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        log.info("{}流式响应完成，总长度 {}", label.isEmpty() ? "" : label, fullResponse.get().length());
                        MessageDTO finalMessage = transactionTemplate.execute(status -> {
                            if (onSuccessCallback != null) {
                                onSuccessCallback.run();
                            }
                            return conversationService.addAssistantMessage(conversationId, fullResponse.get());
                        });
                        sendDoneEvent(emitter, finalMessage);
                        emitter.complete();
                        log.info("{}流式处理完成", label.isEmpty() ? "" : label);
                    } catch (Exception e) {
                        log.error("完成{}流式处理时出错", label.isEmpty() ? "" : label, e);
                        sendErrorAndComplete(emitter, e);
                    }
                })
                .doOnError(e -> {
                    log.error("{}流式 API 调用失败", label.isEmpty() ? "" : label, e);
                    sendErrorAndComplete(emitter, e);
                })
                .subscribe();

        disposableRef.set(disposable);
    }

    private void sendContentAndDone(SseEmitter emitter, Long conversationId,
                                     String text, TokenUsageDTO tokenUsage,
                                     AtomicReference<String> fullResponse) {
        sendContentAndDone(emitter, conversationId, text, tokenUsage, fullResponse, null);
    }

    private void sendContentAndDone(SseEmitter emitter, Long conversationId,
                                     String text, TokenUsageDTO tokenUsage,
                                     AtomicReference<String> fullResponse,
                                     Runnable onSuccessCallback) {
        try {
            fullResponse.set(text);
            StreamEvent contentEvent = StreamEvent.content(text);
            String contentJson = objectMapper.writeValueAsString(contentEvent);
            emitter.send(SseEmitter.event().data(contentJson));

            MessageDTO finalMessage = transactionTemplate.execute(status -> {
                if (onSuccessCallback != null) {
                    onSuccessCallback.run();
                }
                return conversationService.addAssistantMessage(conversationId, text, tokenUsage);
            });
            sendDoneEvent(emitter, finalMessage);
            emitter.complete();
        } catch (Exception e) {
            log.error("Error sending content via SSE", e);
            sendErrorAndComplete(emitter, e);
        }
    }

    private void sendDoneEvent(SseEmitter emitter, MessageDTO finalMessage) {
        try {
            StreamEvent doneEvent = StreamEvent.done(finalMessage);
            String doneJson = objectMapper.writeValueAsString(doneEvent);
            emitter.send(SseEmitter.event().data(doneJson));
        } catch (IOException e) {
            log.warn("发送 done 事件失败", e);
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, Throwable e) {
        try {
            StreamEvent errorEvent = StreamEvent.error(e.getMessage());
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
        } catch (IOException ioEx) {
            log.warn("发送错误事件失败", ioEx);
        } catch (IllegalStateException ex) {
            log.warn("Emitter 已关闭，无法发送错误事件");
            return;
        }
        try {
            emitter.completeWithError(e);
        } catch (IllegalStateException ex) {
            log.warn("Emitter 已关闭，无法标记错误完成");
        }
    }
    
    private void executeAgentToolLoopWithSse(Long conversationId,
                                              List<OpenAiChatMessage> initialMessages,
                                              List<OpenAiTool> tools,
                                              SseEmitter emitter,
                                              AtomicReference<String> fullResponse,
                                              AtomicReference<Disposable> disposableRef,
                                              String model,
                                              java.util.concurrent.atomic.AtomicBoolean cancelled,
                                              Runnable onSuccessCallback) {
        try {
            int toolCallCount = 0;
            List<OpenAiChatMessage> currentMessages = new ArrayList<>(initialMessages);
            int totalPromptTokens = 0;
            int totalCompletionTokens = 0;
            int totalTokens = 0;

            while (toolCallCount < MAX_TOOL_CALLS && !cancelled.get()) {
                log.info("=== Agent iteration {} with model {} ===", toolCallCount, model);

                if (cancelled.get()) {
                    log.info("Agent 循环已被取消，停止执行");
                    return;
                }

                try {
                    sendSseEvent(emitter, StreamEvent.thinking("思考中..."));
                } catch (IOException e) {
                    log.warn("客户端已断开，终止 Agent 循环");
                    return;
                }

                var response = llmService.chatWithTools(model, currentMessages, tools);

                if (response.getUsage() != null) {
                    if (response.getUsage().getPromptTokens() != null) {
                        totalPromptTokens += response.getUsage().getPromptTokens();
                    }
                    if (response.getUsage().getCompletionTokens() != null) {
                        totalCompletionTokens += response.getUsage().getCompletionTokens();
                    }
                    if (response.getUsage().getTotalTokens() != null) {
                        totalTokens += response.getUsage().getTotalTokens();
                    }
                    log.info("LLM Response token usage: prompt={}, completion={}, total={}",
                            response.getUsage().getPromptTokens(),
                            response.getUsage().getCompletionTokens(),
                            response.getUsage().getTotalTokens());
                }

                if (response.getChoices() == null || response.getChoices().isEmpty()) {
                    throw ChatException.badRequest("AI 返回空响应");
                }

                OpenAiChatMessage assistantMsg = response.getChoices().get(0).getMessage();
                List<OpenAiChatMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                String textContent = assistantMsg.getContentAsString();

                if (toolCalls != null && !toolCalls.isEmpty()) {
                    log.info("AI requested {} tool calls", toolCalls.size());
                    currentMessages.add(assistantMsg);

                    for (OpenAiChatMessage.ToolCall toolCall : toolCalls) {
                        String toolName = toolCall.getFunction() != null ?
                                toolCall.getFunction().getName() : "unknown";
                        String toolId = toolCall.getId();

                        log.info("Calling tool: {} with id: {}", toolName, toolId);
                        try {
                            sendSseEvent(emitter, StreamEvent.toolCall(toolName, toolId));
                        } catch (IOException e) {
                            log.warn("客户端已断开，终止 Agent 循环");
                            return;
                        }

                        ToolResult result = toolLoopService.executeToolCall(conversationId, toolCall);

                        String resultContent = toolLoopService.formatToolResultForMessage(result);

                        log.info("Tool {} result: success={}", toolName, result.isSuccess());
                        try {
                            sendSseEvent(emitter, StreamEvent.toolResult(
                                    toolName, toolId, result.isSuccess(), resultContent, result.getError()));
                        } catch (IOException e) {
                            log.warn("客户端已断开，终止 Agent 循环");
                            return;
                        }

                        OpenAiChatMessage toolMessage = OpenAiChatMessage.builder()
                                .role("tool")
                                .content(resultContent)
                                .toolCallId(toolCall.getId())
                                .build();
                        currentMessages.add(toolMessage);
                    }

                    toolCallCount++;
                    log.info("Tool call count: {}, continuing loop", toolCallCount);
                } else if (textContent != null && !textContent.isEmpty()) {
                    log.info("AI returned text response, streaming to client");

                    TokenUsageDTO tokenUsage = TokenUsageDTO.builder()
                            .promptTokens(totalPromptTokens > 0 ? totalPromptTokens : null)
                            .completionTokens(totalCompletionTokens > 0 ? totalCompletionTokens : null)
                            .totalTokens(totalTokens > 0 ? totalTokens : null)
                            .build();

                    sendContentAndDone(emitter, conversationId, textContent, tokenUsage, fullResponse, onSuccessCallback);
                    return;
                } else {
                    log.warn("AI returned empty response with no tool calls");
                    throw ChatException.badRequest("AI 返回空响应");
                }
            }
            
            throw ChatException.badRequest("工具调用次数超过限制 (" + MAX_TOOL_CALLS + ")");
            
        } catch (Exception e) {
            log.error("Agent tool loop failed", e);
            sendErrorAndComplete(emitter, e);
        }
    }
    
    private void setupEmitterCallbacks(SseEmitter emitter, AtomicReference<Disposable> disposableRef) {
        setupEmitterCallbacks(emitter, disposableRef, null);
    }
    
    private void setupEmitterCallbacks(SseEmitter emitter, AtomicReference<Disposable> disposableRef,
                                        java.util.concurrent.atomic.AtomicBoolean cancelled) {
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            if (disposableRef.get() != null) {
                disposableRef.get().dispose();
            }
            if (cancelled != null) {
                cancelled.set(true);
            }
            emitter.complete();
        });
        
        emitter.onError(e -> {
            log.warn("SSE 连接错误: {}", e.getMessage());
            if (disposableRef.get() != null) {
                disposableRef.get().dispose();
            }
            if (cancelled != null) {
                cancelled.set(true);
            }
            emitter.completeWithError(e);
        });
        
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成");
            if (disposableRef.get() != null) {
                disposableRef.get().dispose();
            }
            if (cancelled != null) {
                cancelled.set(true);
            }
        });
    }
    
    private void sendSseEvent(SseEmitter emitter, StreamEvent event) throws IOException {
        String json = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event().data(json));
        log.debug("Sent SSE event: type={}", event.getType());
    }
}
