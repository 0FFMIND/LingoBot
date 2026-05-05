package com.lingobot.chat.service;

import com.lingobot.common.exception.ChatException;
import com.lingobot.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.llm.dto.openai.OpenAiChatResponse;
import com.lingobot.llm.dto.openai.OpenAiTool;
import com.lingobot.llm.service.ModelRouterService;
import com.lingobot.mcp.dto.McpToolCall;
import com.lingobot.mcp.dto.McpToolResult;
import com.lingobot.mcp.service.McpService;
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
    private final McpService mcpService;
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
                throw new ChatException("AI 返回空响�?);
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

                    McpToolResult result = executeToolCall(conversationId, toolCall);
                    
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
                throw new ChatException("AI 返回空响�?);
            }
        }

        throw new ChatException("工具调用次数超过限制 (" + MAX_TOOL_CALLS + ")");
    }
    
    public ToolLoopResult executeOneTimeToolCall(Long conversationId,
            List<OpenAiChatMessage> messages, List<OpenAiTool> tools, String model) {
        int retryCount = 0;
        List<OpenAiChatMessage> currentMessages = new ArrayList<>(messages);
        List<McpToolResult> toolResults = new ArrayList<>();

        while (retryCount < MAX_ONE_TIME_TOOL_CALLS) {
            log.info("=== One-time tool call attempt {} with model {} ===", retryCount + 1, model);

            OpenAiChatResponse response = modelRouterService.chatWithTools(model, currentMessages, tools);

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
                    McpToolResult result = executeToolCall(conversationId, toolCall);
                    toolResults.add(result);
                }

                if (!toolResults.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (McpToolResult result : toolResults) {
                        sb.append(extractToolResultForDisplay(result));
                        if (toolResults.size() > 1) {
                            sb.append("\n\n");
                        }
                    }
                    log.info("One-time tool call completed successfully");
                    return new ToolLoopResult(true, sb.toString(), currentMessages, null);
                }
            } else if (textContent != null && !textContent.isEmpty()) {
                log.info("AI returned text response directly");
                return new ToolLoopResult(false, null, currentMessages, textContent);
            } else {
                retryCount++;
                log.warn("AI returned empty response with no tool calls, retry count: {}", retryCount);
            }
        }
        throw new ChatException("工具调用重试次数超过限制 (" + MAX_ONE_TIME_TOOL_CALLS + ")");
    }

    @Deprecated
    public ToolLoopResult executeToolLoopAndCheck(Long conversationId,
            List<OpenAiChatMessage> messages, List<OpenAiTool> tools, String model) {
        return executeOneTimeToolCall(conversationId, messages, tools, model);
    }
    
    public McpToolResult executeToolCall(Long conversationId, OpenAiChatMessage.ToolCall toolCall) {
        if (toolCall.getFunction() == null) {
            return McpToolResult.builder()
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
            return McpToolResult.builder()
                    .id(toolCall.getId())
                    .name(toolName)
                    .success(false)
                    .error("Failed to parse arguments: " + e.getMessage())
                    .build();
        }
        
        McpToolCall mcpCall = McpToolCall.builder()
                .id(toolCall.getId() != null ? toolCall.getId() : UUID.randomUUID().toString())
                .name(toolName)
                .arguments(arguments)
                .conversationId(conversationId != null ? conversationId.toString() : null)
                .build();
        
        return mcpService.callTool(mcpCall);
    }
    
    private String formatToolResult(McpToolResult result) {
        if (result.isSuccess()) {
            if (result.getContent() != null && !result.getContent().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (McpToolResult.Content content : result.getContent()) {
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
    
    public String formatToolResultForMessage(McpToolResult result) {
        return formatToolResult(result);
    }
    
    private String extractToolResultForDisplay(McpToolResult result) {
        if (!result.isSuccess()) {
            return "Error: " + result.getError();
        }
        
        if (result.getContent() == null || result.getContent().isEmpty()) {
            return "Tool executed successfully";
        }
        
        for (McpToolResult.Content content : result.getContent()) {
            if ("text".equals(content.getType()) && content.getText() != null) {
                String text = content.getText();
                try {
                    JsonNode jsonNode = objectMapper.readTree(text);
                    
                    boolean isSentenceFeedback = false;
                    if (jsonNode.has("action")) {
                        String action = jsonNode.get("action").asText();
                        isSentenceFeedback = "display_sentence_feedback".equals(action);
                    }
                    if (!isSentenceFeedback && jsonNode.has("display_mode")) {
                        String displayMode = jsonNode.get("display_mode").asText();
                        isSentenceFeedback = "sentence_feedback".equals(displayMode);
                    }
                    
                    if (isSentenceFeedback) {
                        if (jsonNode.has("feedback") && !jsonNode.get("feedback").isNull()) {
                            String feedback = jsonNode.get("feedback").asText();
                            if (feedback != null && !feedback.trim().isEmpty()) {
                                return feedback;
                            }
                        }
                    }
                    
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
        
        public ToolLoopResult(boolean hasToolCalls, String toolResultText, 
                               List<OpenAiChatMessage> messages, String textResponse) {
            this.hasToolCalls = hasToolCalls;
            this.toolResultText = toolResultText;
            this.messages = messages;
            this.textResponse = textResponse;
        }
        
        public boolean hasToolCalls() { return hasToolCalls; }
        public String getToolResultText() { return toolResultText; }
        public List<OpenAiChatMessage> getMessages() { return messages; }
        public String getTextResponse() { return textResponse; }
        public boolean hasTextResponse() { return textResponse != null && !textResponse.isEmpty(); }
    }
}
