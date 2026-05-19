package com.lingobot.learning.chat.service;

import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatResponse;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.llm.service.ModelRouterService;
import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import com.lingobot.infrastructure.tool.service.ToolService;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolLoopService {
    
    private final ModelRouterService modelRouterService;
    private final ToolService toolService;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_TOOL_CALLS = 10;
    private static final int MAX_ONE_TIME_TOOL_CALLS = 3;
    
    public String executeToolLoop(Long conversationId, List<OpenAiChatMessage> messages, 
                                   List<OpenAiTool> tools, String mode, String model) {
        int toolCallCount = 0;
        List<OpenAiChatMessage> currentMessages = new ArrayList<>(messages);

        while (toolCallCount < MAX_TOOL_CALLS) {
            log.info("=== Chat iteration {} with model {} ===", toolCallCount, model);

            OpenAiChatResponse response = modelRouterService.chatWithTools(model, currentMessages, tools);

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
                    log.info("Executing tool: {} with id: {}",
                            toolCall.getFunction() != null ? toolCall.getFunction().getName() : "unknown",
                            toolCall.getId());

                    ToolResult result = executeToolCall(conversationId, toolCall);
                    
                    String toolResultContent = formatToolResultForMessage(result);
                    
                    OpenAiChatMessage toolMessage = OpenAiChatMessage.builder()
                            .role("tool")
                            .content(toolResultContent)
                            .toolCallId(toolCall.getId())
                            .build();
                    currentMessages.add(toolMessage);
                }

                toolCallCount++;
                log.info("Tool call count: {}, continuing loop", toolCallCount);
            } else if (textContent != null && !textContent.isEmpty()) {
                log.info("AI returned text response, ending loop");
                return textContent;
            } else {
                log.warn("AI returned empty response with no tool calls");
                throw ChatException.badRequest("AI 返回空响应");
            }
        }

        throw ChatException.badRequest("工具调用次数超过限制 (" + MAX_TOOL_CALLS + ")");
    }
    
    public ToolLoopResult executeOneTimeToolCall(Long conversationId,
            List<OpenAiChatMessage> messages, List<OpenAiTool> tools, String model) {
        int retryCount = 0;
        List<OpenAiChatMessage> currentMessages = new ArrayList<>(messages);
        List<ToolResult> toolResults = new ArrayList<>();
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        int totalTokens = 0;

        while (retryCount < MAX_ONE_TIME_TOOL_CALLS) {
            log.info("=== One-time tool call attempt {} with model {} ===", retryCount + 1, model);

            OpenAiChatResponse response = modelRouterService.chatWithTools(model, currentMessages, tools);

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
                retryCount++;
                log.warn("AI returned empty response, retry count: {}", retryCount);
                continue;
            }

            OpenAiChatMessage assistantMsg = response.getChoices().get(0).getMessage();
            List<OpenAiChatMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
            String textContent = assistantMsg.getContentAsString();

            if (toolCalls != null && !toolCalls.isEmpty()) {
                log.info("AI requested {} tool calls (one-time execution)", toolCalls.size());

                for (OpenAiChatMessage.ToolCall toolCall : toolCalls) {
                    ToolResult result = executeToolCall(conversationId, toolCall);
                    toolResults.add(result);
                }

                if (!toolResults.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (ToolResult result : toolResults) {
                        sb.append(extractToolResultForDisplay(result));
                        if (toolResults.size() > 1) {
                            sb.append("\n\n");
                        }
                    }
                    log.info("One-time tool call completed successfully");
                    TokenUsageDTO usage = TokenUsageDTO.builder()
                            .promptTokens(totalPromptTokens > 0 ? totalPromptTokens : null)
                            .completionTokens(totalCompletionTokens > 0 ? totalCompletionTokens : null)
                            .totalTokens(totalTokens > 0 ? totalTokens : null)
                            .build();
                    return new ToolLoopResult(true, sb.toString(), currentMessages, null, usage);
                }
            } else if (textContent != null && !textContent.isEmpty()) {
                log.info("AI returned text response directly");
                TokenUsageDTO usage = TokenUsageDTO.builder()
                        .promptTokens(totalPromptTokens > 0 ? totalPromptTokens : null)
                        .completionTokens(totalCompletionTokens > 0 ? totalCompletionTokens : null)
                        .totalTokens(totalTokens > 0 ? totalTokens : null)
                        .build();
                return new ToolLoopResult(false, null, currentMessages, textContent, usage);
            } else {
                retryCount++;
                log.warn("AI returned empty response with no tool calls, retry count: {}", retryCount);
            }
        }
        throw ChatException.badRequest("工具调用重试次数超过限制 (" + MAX_ONE_TIME_TOOL_CALLS + ")");
    }

    @Deprecated
    public ToolLoopResult executeToolLoopAndCheck(Long conversationId,
            List<OpenAiChatMessage> messages, List<OpenAiTool> tools, String model) {
        return executeOneTimeToolCall(conversationId, messages, tools, model);
    }
    
    public ToolResult executeToolCall(Long conversationId, OpenAiChatMessage.ToolCall toolCall) {
        if (toolCall.getFunction() == null) {
            return ToolResult.builder()
                    .id(toolCall.getId())
                    .name("unknown")
                    .success(false)
                    .error("Tool call has no function")
                    .build();
        }
        
        String toolName = toolCall.getFunction().getName();
        String argumentsJson = toolCall.getFunction().getArguments();
        
        Map<String, Object> arguments;
        try {
            if (argumentsJson != null && !argumentsJson.isEmpty()) {
                arguments = objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            } else {
                arguments = new java.util.HashMap<>();
            }
        } catch (Exception e) {
            log.error("Failed to parse tool arguments: {}", argumentsJson, e);
            return ToolResult.builder()
                    .id(toolCall.getId())
                    .name(toolName)
                    .success(false)
                    .error("Failed to parse arguments: " + e.getMessage())
                    .build();
        }
        
        ToolCall toolCallRequest = ToolCall.builder()
                .id(toolCall.getId() != null ? toolCall.getId() : UUID.randomUUID().toString())
                .name(toolName)
                .arguments(arguments)
                .conversationId(conversationId != null ? conversationId.toString() : null)
                .build();
        
        return toolService.callTool(toolCallRequest);
    }
    
    private String formatToolResult(ToolResult result) {
        if (result.isSuccess()) {
            if (result.getContent() != null && !result.getContent().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ToolResult.Content content : result.getContent()) {
                    if ("text".equals(content.getType()) && content.getText() != null) {
                        sb.append(content.getText());
                    }
                }
                return sb.toString();
            }
            return "Tool executed successfully";
        } else {
            return "Error: " + result.getError();
        }
    }
    
    public String formatToolResultForMessage(ToolResult result) {
        return formatToolResult(result);
    }
    
    private String extractToolResultForDisplay(ToolResult result) {
        if (!result.isSuccess()) {
            return "Error: " + result.getError();
        }
        
        if (result.getContent() == null || result.getContent().isEmpty()) {
            return "Tool executed successfully";
        }
        
        for (ToolResult.Content content : result.getContent()) {
            if ("text".equals(content.getType()) && content.getText() != null) {
                String text = content.getText();
                try {
                    JsonNode jsonNode = objectMapper.readTree(text);
                    
                    if (jsonNode.has("message") && !jsonNode.get("message").isNull()) {
                        String message = jsonNode.get("message").asText();
                        if (message != null && !message.trim().isEmpty()) {
                            return text;
                        }
                    }
                } catch (Exception ignored) {
                }
                return text;
            }
        }
        
        return "Tool executed successfully";
    }
    
    public static class ToolLoopResult {
        private final boolean hasToolCalls;
        private final String toolResultText;
        private final List<OpenAiChatMessage> messages;
        private final String textResponse;
        private final TokenUsageDTO tokenUsage;
        
        public ToolLoopResult(boolean hasToolCalls, String toolResultText, 
                               List<OpenAiChatMessage> messages, String textResponse) {
            this(hasToolCalls, toolResultText, messages, textResponse, null);
        }
        
        public ToolLoopResult(boolean hasToolCalls, String toolResultText, 
                               List<OpenAiChatMessage> messages, String textResponse,
                               TokenUsageDTO tokenUsage) {
            this.hasToolCalls = hasToolCalls;
            this.toolResultText = toolResultText;
            this.messages = messages;
            this.textResponse = textResponse;
            this.tokenUsage = tokenUsage;
        }
        
        public boolean hasToolCalls() { return hasToolCalls; }
        public String getToolResultText() { return toolResultText; }
        public List<OpenAiChatMessage> getMessages() { return messages; }
        public String getTextResponse() { return textResponse; }
        public boolean hasTextResponse() { return textResponse != null && !textResponse.isEmpty(); }
        public TokenUsageDTO getTokenUsage() { return tokenUsage; }
        public boolean hasTokenUsage() { return tokenUsage != null && !tokenUsage.isEmpty(); }
    }
}
