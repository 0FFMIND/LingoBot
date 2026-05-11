package com.lingobot.learning.chat.service;

import com.lingobot.learning.chat.dto.StreamEvent;
import com.lingobot.learning.chat.service.ToolLoopService.ToolLoopResult;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.llm.service.ModelRouterService;
import com.lingobot.learning.llm.tool.dto.McpToolResult;
import com.lingobot.learning.llm.tool.service.McpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEmitterService {
    
    private final ConversationService conversationService;
    private final ModelRouterService modelRouterService;
    private final McpService mcpService;
    private final ToolLoopService toolLoopService;
    private final ObjectMapper objectMapper;
    
    private static final long SSE_TIMEOUT = 180000L;
    private static final int MAX_TOOL_CALLS = 10;
    
    public SseEmitter createStreamEmitterWithTools(Long conversationId, 
                                                   List<OpenAiChatMessage> messages, 
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        final var conversationRef = new AtomicReference<Conversation>();
        try {
            conversationRef.set(conversationService.getConversationEntityById(conversationId));
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", conversationId, e);
            try {
                StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
            } catch (IOException ioEx) {
                log.warn("发送错误事件失败", ioEx);
            }
            emitter.completeWithError(e);
            return emitter;
        }

        if (tools != null && !tools.isEmpty()) {
            try {
                if ("agent".equals(mode)) {
                    log.info("Agent mode: executing full tool loop with SSE events, model: {}", model);
                    executeAgentToolLoopWithSse(conversationId, messages, tools, emitter, 
                            fullResponse, disposableRef, conversationRef.get(), model);
                    return emitter;
                } else {
                    log.info("Chat mode: executing single tool call (one-time, max 3 retries), model: {}", model);
                    ToolLoopResult toolLoopResult = toolLoopService.executeOneTimeToolCall(conversationId, messages, tools, model);
                    
                    if (toolLoopResult.hasToolCalls()) {
                        log.info("Tool calls were executed, returning tool results directly");
                        final String resultText = toolLoopResult.getToolResultText();
                        TokenUsageDTO tokenUsage = toolLoopResult.getTokenUsage();
                        
                        try {
                            fullResponse.set(resultText);
                            
                            StreamEvent contentEvent = StreamEvent.content(resultText);
                            String contentJson = objectMapper.writeValueAsString(contentEvent);
                            emitter.send(SseEmitter.event().data(contentJson));
                            
                            MessageDTO finalMessage = conversationService.addAssistantMessage(
                                    conversationRef.get(), resultText, tokenUsage);
                            
                            StreamEvent doneEvent = StreamEvent.done(finalMessage);
                            String doneJson = objectMapper.writeValueAsString(doneEvent);
                            emitter.send(SseEmitter.event().data(doneJson));
                            emitter.complete();
                            
                            log.info("Tool result streaming completed");
                        } catch (Exception e) {
                            log.error("Error sending tool result via SSE", e);
                            try {
                                StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                            } catch (IOException ioEx) {
                                log.warn("发送错误事件失败", ioEx);
                            }
                            emitter.completeWithError(e);
                        }
                        return emitter;
                    } else if (toolLoopResult.hasTextResponse()) {
                        log.info("AI returned text response directly, using cached response");
                        final String textResponse = toolLoopResult.getTextResponse();
                        TokenUsageDTO tokenUsage = toolLoopResult.getTokenUsage();
                        
                        try {
                            fullResponse.set(textResponse);
                            
                            StreamEvent contentEvent = StreamEvent.content(textResponse);
                            String contentJson = objectMapper.writeValueAsString(contentEvent);
                            emitter.send(SseEmitter.event().data(contentJson));
                            
                            MessageDTO finalMessage = conversationService.addAssistantMessage(
                                    conversationRef.get(), textResponse, tokenUsage);
                            
                            StreamEvent doneEvent = StreamEvent.done(finalMessage);
                            String doneJson = objectMapper.writeValueAsString(doneEvent);
                            emitter.send(SseEmitter.event().data(doneJson));
                            emitter.complete();
                            
                            log.info("Direct text response streaming completed");
                        } catch (Exception e) {
                            log.error("Error sending direct text response via SSE", e);
                            try {
                                StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                            } catch (IOException ioEx) {
                                log.warn("发送错误事件失败", ioEx);
                            }
                            emitter.completeWithError(e);
                        }
                        return emitter;
                    }
                }
            } catch (Exception e) {
                log.error("Tool loop failed before streaming", e);
                try {
                    StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                } catch (IOException ioEx) {
                    log.warn("发送错误事件失败", ioEx);
                }
                emitter.completeWithError(e);
                return emitter;
            }
        }

        Flux<String> responseFlux = modelRouterService.chatStream(model, messages);

        Disposable disposable = responseFlux
                .doOnNext(chunk -> {
                    try {
                        fullResponse.updateAndGet(s -> s + chunk);
                        
                        StreamEvent event = StreamEvent.content(chunk);
                        String json = objectMapper.writeValueAsString(event);
                        
                        log.debug("发送流式chunk: {}",
                                chunk.length() > 50 ? chunk.substring(0, 50) + "..." : chunk);
                        
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        log.warn("发送SSE 事件失败: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        log.info("流式响应完成，总长度: {}", fullResponse.get().length());
                        
                        MessageDTO finalMessage = conversationService.addAssistantMessage(
                                conversationRef.get(), fullResponse.get());
                        
                        StreamEvent doneEvent = StreamEvent.done(finalMessage);
                        String doneJson = objectMapper.writeValueAsString(doneEvent);
                        
                        emitter.send(SseEmitter.event().data(doneJson));
                        emitter.complete();
                        
                        log.info("流式处理完成");
                    } catch (Exception e) {
                        log.error("完成流式处理时出错", e);
                        try {
                            StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                        } catch (IOException ioEx) {
                            log.warn("发送错误事件失败", ioEx);
                        }
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    log.error("流式 API 调用失败", e);
                    try {
                        StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                    } catch (IOException ioEx) {
                        log.warn("发送错误事件失败", ioEx);
                    }
                    emitter.completeWithError(e);
                })
                .subscribe();
        
        disposableRef.set(disposable);
        
        setupEmitterCallbacks(emitter, disposableRef);
        
        log.info("返回流式 SseEmitter");
        return emitter;
    }
    
    public SseEmitter createStreamEmitterWithAudio(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   String audioData,
                                                   String audioFormat) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        final var conversationRef = new AtomicReference<Conversation>();
        try {
            conversationRef.set(conversationService.getConversationEntityById(conversationId));
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", conversationId, e);
            try {
                StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
            } catch (IOException ioEx) {
                log.warn("发送错误事件失败", ioEx);
            }
            emitter.completeWithError(e);
            return emitter;
        }

        log.info("开始音频流式处理，使用 Qwen-Omni 模型");
        
        Flux<String> responseFlux = modelRouterService.chatStreamWithAudio(
                model, messages, audioData, audioFormat);

        Disposable disposable = responseFlux
                .doOnNext(chunk -> {
                    try {
                        fullResponse.updateAndGet(s -> s + chunk);
                        
                        StreamEvent event = StreamEvent.content(chunk);
                        String json = objectMapper.writeValueAsString(event);
                        
                        log.debug("发送音频流式chunk: {}",
                                chunk.length() > 50 ? chunk.substring(0, 50) + "..." : chunk);
                        
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        log.warn("发送SSE 事件失败: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        log.info("音频流式响应完成，总长度: {}", fullResponse.get().length());
                        
                        MessageDTO finalMessage = conversationService.addAssistantMessage(
                                conversationRef.get(), fullResponse.get());
                        
                        StreamEvent doneEvent = StreamEvent.done(finalMessage);
                        String doneJson = objectMapper.writeValueAsString(doneEvent);
                        
                        emitter.send(SseEmitter.event().data(doneJson));
                        emitter.complete();
                        
                        log.info("音频流式处理完成");
                    } catch (Exception e) {
                        log.error("完成音频流式处理时出错", e);
                        try {
                            StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                        } catch (IOException ioEx) {
                            log.warn("发送错误事件失败", ioEx);
                        }
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    log.error("音频流式 API 调用失败", e);
                    try {
                        StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                    } catch (IOException ioEx) {
                        log.warn("发送错误事件失败", ioEx);
                    }
                    emitter.completeWithError(e);
                })
                .subscribe();
        
        disposableRef.set(disposable);
        
        setupEmitterCallbacks(emitter, disposableRef);
        
        log.info("返回音频流式 SseEmitter");
        return emitter;
    }
    
    public SseEmitter createStreamEmitterWithImage(Long conversationId,
                                                   List<OpenAiChatMessage> messages,
                                                   List<OpenAiTool> tools,
                                                   String mode,
                                                   String model,
                                                   String imageData,
                                                   String imageFormat) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        final var conversationRef = new AtomicReference<Conversation>();
        try {
            conversationRef.set(conversationService.getConversationEntityById(conversationId));
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", conversationId, e);
            try {
                StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
            } catch (IOException ioEx) {
                log.warn("发送错误事件失败", ioEx);
            }
            emitter.completeWithError(e);
            return emitter;
        }

        log.info("开始图片流式处理，使用支持图片的多模态模型");
        
        Flux<String> responseFlux = modelRouterService.chatStreamWithImage(
                model, messages, imageData, imageFormat);

        Disposable disposable = responseFlux
                .doOnNext(chunk -> {
                    try {
                        fullResponse.updateAndGet(s -> s + chunk);
                        
                        StreamEvent event = StreamEvent.content(chunk);
                        String json = objectMapper.writeValueAsString(event);
                        
                        log.debug("发送图片流式chunk: {}",
                                chunk.length() > 50 ? chunk.substring(0, 50) + "..." : chunk);
                        
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        log.warn("发送SSE 事件失败: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        log.info("图片流式响应完成，总长度: {}", fullResponse.get().length());
                        
                        MessageDTO finalMessage = conversationService.addAssistantMessage(
                                conversationRef.get(), fullResponse.get());
                        
                        StreamEvent doneEvent = StreamEvent.done(finalMessage);
                        String doneJson = objectMapper.writeValueAsString(doneEvent);
                        
                        emitter.send(SseEmitter.event().data(doneJson));
                        emitter.complete();
                        
                        log.info("图片流式处理完成");
                    } catch (Exception e) {
                        log.error("完成图片流式处理时出错", e);
                        try {
                            StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                        } catch (IOException ioEx) {
                            log.warn("发送错误事件失败", ioEx);
                        }
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    log.error("图片流式 API 调用失败", e);
                    try {
                        StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                    } catch (IOException ioEx) {
                        log.warn("发送错误事件失败", ioEx);
                    }
                    emitter.completeWithError(e);
                })
                .subscribe();
        
        disposableRef.set(disposable);
        
        setupEmitterCallbacks(emitter, disposableRef);
        
        log.info("返回图片流式 SseEmitter");
        return emitter;
    }
    
    private void executeAgentToolLoopWithSse(Long conversationId,
                                              List<OpenAiChatMessage> initialMessages,
                                              List<OpenAiTool> tools,
                                              SseEmitter emitter,
                                              AtomicReference<String> fullResponse,
                                              AtomicReference<Disposable> disposableRef,
                                              Conversation conversation,
                                              String model) {
        try {
            int toolCallCount = 0;
            List<OpenAiChatMessage> currentMessages = new ArrayList<>(initialMessages);
            int totalPromptTokens = 0;
            int totalCompletionTokens = 0;
            int totalTokens = 0;
            
            while (toolCallCount < MAX_TOOL_CALLS) {
                log.info("=== Agent iteration {} with model {} ===", toolCallCount, model);
                
                sendSseEvent(emitter, StreamEvent.thinking("思考中..."));
                
                var response = modelRouterService.chatWithTools(model, currentMessages, tools);
                
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
                        sendSseEvent(emitter, StreamEvent.toolCall(toolName, toolId));
                        
                        McpToolResult result = toolLoopService.executeToolCall(conversationId, toolCall);
                        
                        String resultContent = toolLoopService.formatToolResultForMessage(result);
                        
                        log.info("Tool {} result: success={}", toolName, result.isSuccess());
                        sendSseEvent(emitter, StreamEvent.toolResult(
                                toolName, toolId, result.isSuccess(), resultContent, result.getError()));
                        
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
                    
                    try {
                        fullResponse.set(textContent);
                        
                        StreamEvent contentEvent = StreamEvent.content(textContent);
                        String contentJson = objectMapper.writeValueAsString(contentEvent);
                        emitter.send(SseEmitter.event().data(contentJson));
                        
                        TokenUsageDTO tokenUsage = TokenUsageDTO.builder()
                                .promptTokens(totalPromptTokens > 0 ? totalPromptTokens : null)
                                .completionTokens(totalCompletionTokens > 0 ? totalCompletionTokens : null)
                                .totalTokens(totalTokens > 0 ? totalTokens : null)
                                .build();
                        
                        MessageDTO finalMessage = conversationService.addAssistantMessage(
                                conversation, fullResponse.get(), tokenUsage);
                        
                        StreamEvent doneEvent = StreamEvent.done(finalMessage);
                        String doneJson = objectMapper.writeValueAsString(doneEvent);
                        emitter.send(SseEmitter.event().data(doneJson));
                        emitter.complete();
                        
                        log.info("Agent 模式处理完成，响应长度: {}", textContent.length());
                    } catch (Exception e) {
                        log.error("发送文本响应失败", e);
                        try {
                            StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
                        } catch (IOException ioEx) {
                            log.warn("发送错误事件失败", ioEx);
                        }
                        emitter.completeWithError(e);
                    }
                    return;
                } else {
                    log.warn("AI returned empty response with no tool calls");
                    throw ChatException.badRequest("AI 返回空响应");
                }
            }
            
            throw ChatException.badRequest("工具调用次数超过限制 (" + MAX_TOOL_CALLS + ")");
            
        } catch (Exception e) {
            log.error("Agent tool loop failed", e);
            try {
                StreamEvent errorEvent = StreamEvent.error(e.getMessage());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorEvent)));
            } catch (IOException ioEx) {
                log.warn("发送错误事件失败", ioEx);
            }
            emitter.completeWithError(e);
        }
    }
    
    private void setupEmitterCallbacks(SseEmitter emitter, AtomicReference<Disposable> disposableRef) {
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            if (disposableRef.get() != null) {
                disposableRef.get().dispose();
            }
            emitter.complete();
        });
        
        emitter.onError(e -> {
            log.warn("SSE 连接错误: {}", e.getMessage());
            if (disposableRef.get() != null) {
                disposableRef.get().dispose();
            }
            emitter.completeWithError(e);
        });
        
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成");
            if (disposableRef.get() != null) {
                disposableRef.get().dispose();
            }
        });
    }
    
    private void sendSseEvent(SseEmitter emitter, StreamEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json));
            log.debug("Sent SSE event: type={}", event.getType());
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
        }
    }
}
