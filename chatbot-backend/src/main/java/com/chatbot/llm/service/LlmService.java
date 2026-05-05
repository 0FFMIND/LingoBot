package com.lingobot.llm.service;

import com.lingobot.audio.service.AudioConversionService;
import com.lingobot.config.ProxyProperties;
import com.lingobot.config.LlmProperties;
import com.lingobot.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.llm.dto.openai.OpenAiChatRequest;
import com.lingobot.llm.dto.openai.OpenAiChatResponse;
import com.lingobot.llm.dto.openai.OpenAiTool;
import com.lingobot.common.exception.ChatException;
import com.lingobot.util.JsonLogUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private static final String TOOL_CALL_MARKER = "TOOL_CALL:";

    private final LlmProperties llmProperties;
    private final ProxyProperties proxyProperties;
    private final ObjectMapper objectMapper;
    private final AudioConversionService audioConversionService;

    public String chat(List<OpenAiChatMessage> messages) {
        OpenAiChatResponse response = chatWithTools(messages, null);
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new ChatException("AI 返回空响�?);
        }
        String text = response.getChoices().get(0).getMessage().getContentAsString();
        if (text == null || text.isEmpty()) {
            throw new ChatException("AI 返回空响�?);
        }
        return text;
    }

    public OpenAiChatResponse chatWithTools(List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        boolean hasTools = tools != null && !tools.isEmpty();
        log.info("调用 Chat Completions API，模�? {}, 消息�? {}, tools: {}",
                llmProperties.getModel(), messages.size(), hasTools ? tools.size() : 0);

        List<OpenAiChatMessage> sendMessages = translateForModel(messages, tools);

        OpenAiChatRequest request = OpenAiChatRequest.builder()
                .model(llmProperties.getModel())
                .messages(sendMessages)
                .temperature(llmProperties.getTemperature())
                .maxTokens(llmProperties.getMaxTokens())
                .stream(false)
                .tools(hasTools ? tools : null)
                .toolChoice(hasTools ? "auto" : null)
                .build();

        logRequest(request);

        OpenAiChatResponse response = callApi(request);

        return hasTools ? parseToolCallFromResponse(response) : response;
    }

    public Flux<String> chatStream(List<OpenAiChatMessage> messages) {
        return chatStreamInternal(translateForModel(messages, null));
    }

    public Flux<String> chatStreamWithTools(List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        return chatStreamInternal(translateForModel(messages, null));
    }

    public void chatStreamAsync(List<OpenAiChatMessage> messages,
                                Consumer<String> onChunk,
                                Runnable onComplete,
                                Consumer<Throwable> onError) {
        chatStream(messages).doOnNext(onChunk).doOnComplete(onComplete).doOnError(onError).subscribe();
    }
    
    public boolean hasAudioMessage(List<OpenAiChatMessage> messages) {
        return messages.stream()
                .anyMatch(OpenAiChatMessage::hasAudioContent);
    }
    
    public Flux<String> chatStreamWithAudio(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat) {
        return chatStreamWithAudio(messages, audioData, audioFormat, null);
    }
    
    public Flux<String> chatStreamWithAudio(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String model) {
        
        if (!llmProperties.isAudioEnabled()) {
            log.warn("音频功能未启用，使用文本模式");
            return chatStream(messages);
        }
        
        AudioConversionService.ConversionResult conversionResult = 
                audioConversionService.convertIfNeeded(audioData, audioFormat);
        
        String finalAudioData = conversionResult.base64Audio();
        String finalFormat = conversionResult.format();
        
        if (!finalFormat.equals(normalizeAudioFormat(audioFormat))) {
            log.info("音频格式已转�? {} -> {}", audioFormat, finalFormat);
        }
        
        String fullModelName = llmProperties.getFullModelName(model);
        LlmProperties.AudioModelConfig config = llmProperties.getAudioModelConfigForModel(model);
        
        log.info("调用多模态音频理�?API - 模型: {} ({}), 消息�? {}, 音频格式: {}, 支持: 音频={}, 图像={}, 视频={}",
                config.getDisplayName(),
                fullModelName,
                messages.size(),
                finalFormat,
                config.isSupportsAudio(),
                config.isSupportsImage(),
                config.isSupportsVideo());
        
        log.info("价格参�?- 输入: ${}/M tokens, 输出: ${}/M tokens",
                config.getInputPricePerMillion(),
                config.getOutputPricePerMillion());
        
        List<OpenAiChatMessage> sendMessages = prepareAudioMessages(messages, finalAudioData, finalFormat);
        
        return chatStreamInternalWithModel(sendMessages, fullModelName);
    }
    
    private List<OpenAiChatMessage> prepareAudioMessages(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat) {
        
        List<OpenAiChatMessage> result = new ArrayList<>();
        
        for (OpenAiChatMessage msg : messages) {
            if ("user".equals(msg.getRole()) && !msg.hasAudioContent()) {
                String textContent = extractTextContent(msg.getContent());
                OpenAiChatMessage audioMessage = OpenAiChatMessage.createAudioMessage(
                        "user",
                        textContent,
                        audioData,
                        normalizeAudioFormat(audioFormat)
                );
                result.add(audioMessage);
            } else {
                result.add(msg);
            }
        }
        
        return result;
    }
    
    private String extractTextContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String) {
            return (String) content;
        }
        return content.toString();
    }
    
    private String normalizeAudioFormat(String format) {
        if (format == null) {
            return "wav";
        }
        String lowerFormat = format.toLowerCase();
        
        if (lowerFormat.contains("webm") || lowerFormat.contains("opus")) {
            return "webm";
        }
        if (lowerFormat.contains("mp3")) {
            return "mp3";
        }
        if (lowerFormat.contains("m4a") || lowerFormat.contains("mp4")) {
            return "m4a";
        }
        if (lowerFormat.contains("wav")) {
            return "wav";
        }
        if (lowerFormat.contains("flac")) {
            return "flac";
        }
        if (lowerFormat.contains("ogg") || lowerFormat.contains("oga")) {
            return "ogg";
        }
        if (lowerFormat.contains("aiff")) {
            return "aiff";
        }
        
        log.warn("未知音频格式: {}, 使用默认 wav", format);
        return "wav";
    }
    
    public boolean hasImageMessage(List<OpenAiChatMessage> messages) {
        return messages.stream()
                .anyMatch(OpenAiChatMessage::hasImageContent);
    }
    
    public Flux<String> chatStreamWithImage(
            List<OpenAiChatMessage> messages,
            String imageData,
            String imageFormat) {
        return chatStreamWithImage(messages, imageData, imageFormat, null);
    }
    
    public Flux<String> chatStreamWithImage(
            List<OpenAiChatMessage> messages,
            String imageData,
            String imageFormat,
            String model) {
        
        String fullModelName = llmProperties.getFullModelName(model);
        LlmProperties.AudioModelConfig config = llmProperties.getAudioModelConfigForModel(model);
        
        if (!config.isSupportsImage()) {
            log.warn("当前模型 {} 不支持图片输入，尝试使用支持图片的模�?, config.getDisplayName());
        }
        
        log.info("调用多模态图片理�?API - 模型: {} ({}), 消息�? {}, 图片格式: {}, 支持: 音频={}, 图像={}, 视频={}",
                config.getDisplayName(),
                fullModelName,
                messages.size(),
                imageFormat,
                config.isSupportsAudio(),
                config.isSupportsImage(),
                config.isSupportsVideo());
        
        log.info("价格参�?- 输入: ${}/M tokens, 输出: ${}/M tokens",
                config.getInputPricePerMillion(),
                config.getOutputPricePerMillion());
        
        List<OpenAiChatMessage> sendMessages = prepareImageMessages(messages, imageData, imageFormat);
        
        return chatStreamInternalWithModel(sendMessages, fullModelName);
    }
    
    public Flux<String> chatStreamWithAudioAndImage(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String imageData,
            String imageFormat) {
        return chatStreamWithAudioAndImage(messages, audioData, audioFormat, imageData, imageFormat, null);
    }
    
    public Flux<String> chatStreamWithAudioAndImage(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String imageData,
            String imageFormat,
            String model) {
        
        String fullModelName = llmProperties.getFullModelName(model);
        LlmProperties.AudioModelConfig config = llmProperties.getAudioModelConfigForModel(model);
        
        log.info("调用多模态混合输�?API - 模型: {} ({}), 消息�? {}, 支持: 音频={}, 图像={}, 视频={}",
                config.getDisplayName(),
                fullModelName,
                messages.size(),
                config.isSupportsAudio(),
                config.isSupportsImage(),
                config.isSupportsVideo());
        
        List<OpenAiChatMessage> sendMessages = prepareMultiModalMessages(
                messages, audioData, audioFormat, imageData, imageFormat);
        
        return chatStreamInternalWithModel(sendMessages, fullModelName);
    }
    
    private List<OpenAiChatMessage> prepareImageMessages(
            List<OpenAiChatMessage> messages,
            String imageData,
            String imageFormat) {
        
        List<OpenAiChatMessage> result = new ArrayList<>();
        
        for (OpenAiChatMessage msg : messages) {
            if ("user".equals(msg.getRole()) && !msg.hasImageContent()) {
                String textContent = extractTextContent(msg.getContent());
                OpenAiChatMessage imageMessage = OpenAiChatMessage.createImageMessage(
                        "user",
                        textContent,
                        imageData,
                        normalizeImageFormat(imageFormat)
                );
                result.add(imageMessage);
            } else {
                result.add(msg);
            }
        }
        
        return result;
    }
    
    private List<OpenAiChatMessage> prepareMultiModalMessages(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String imageData,
            String imageFormat) {
        
        List<OpenAiChatMessage> result = new ArrayList<>();
        
        for (OpenAiChatMessage msg : messages) {
            if ("user".equals(msg.getRole()) && !msg.hasAudioContent() && !msg.hasImageContent()) {
                String textContent = extractTextContent(msg.getContent());
                OpenAiChatMessage multiModalMessage = OpenAiChatMessage.createMultiModalMessage(
                        "user",
                        textContent,
                        audioData,
                        audioFormat != null ? normalizeAudioFormat(audioFormat) : null,
                        imageData,
                        imageFormat != null ? normalizeImageFormat(imageFormat) : null
                );
                result.add(multiModalMessage);
            } else {
                result.add(msg);
            }
        }
        
        return result;
    }
    
    private String normalizeImageFormat(String format) {
        if (format == null) {
            return "png";
        }
        String lowerFormat = format.toLowerCase();
        
        if (lowerFormat.contains("png")) {
            return "png";
        }
        if (lowerFormat.contains("jpeg") || lowerFormat.contains("jpg")) {
            return "jpeg";
        }
        if (lowerFormat.contains("gif")) {
            return "gif";
        }
        if (lowerFormat.contains("webp")) {
            return "webp";
        }
        if (lowerFormat.contains("bmp")) {
            return "bmp";
        }
        
        log.warn("未知图片格式: {}, 使用默认 png", format);
        return "png";
    }
    
    private Flux<String> chatStreamInternalWithModel(List<OpenAiChatMessage> messages, String model) {
        log.info("调用 Chat Completions API 流式，模�? {}, 消息�? {}",
                model, messages.size());

        return Flux.<String>create(emitter -> {
            try {
                OpenAiChatRequest request = OpenAiChatRequest.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(llmProperties.getTemperature())
                        .maxTokens(llmProperties.getMaxTokens())
                        .stream(true)
                        .build();

                String json = objectMapper.writeValueAsString(request);
                log.info("流式请求 JSON: {}", JsonLogUtils.toLogString(objectMapper, request));

                java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(llmProperties.getBaseUrl() + "/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream, application/json")
                        .header("Authorization", "Bearer " + llmProperties.getApiKey())
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                        .timeout(Duration.ofMillis(llmProperties.getTimeout()))
                        .build();

                HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(30));

                if (proxyProperties.isValid()) {
                    log.info("LLM API 流式调用使用代理: {}:{}", proxyProperties.getHost(), proxyProperties.getPort());
                    InetSocketAddress proxyAddress = new InetSocketAddress(proxyProperties.getHost(), proxyProperties.getPort());
                    clientBuilder.proxy(ProxySelector.of(proxyAddress));
                }

                HttpClient httpClient = clientBuilder.build();

                java.net.http.HttpResponse<java.io.InputStream> response =
                        httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 400) {
                    byte[] body = response.body().readAllBytes();
                    String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                    log.error("流式 API 错误 {}: {}", response.statusCode(), bodyStr);
                    emitter.error(new ChatException("API错误 " + response.statusCode() + ": " + bodyStr));
                    return;
                }

                boolean hasContent = false;
                StringBuilder fullResponse = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                        if (data.isEmpty() || data.equals("[DONE]")) continue;
                        if (!data.startsWith("{")) continue;
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode choices = node.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode content = choices.get(0).path("delta").path("content");
                                if (!content.isMissingNode() && !content.isNull()) {
                                    String text = content.asText();
                                    if (!text.isEmpty()) {
                                        emitter.next(text);
                                        fullResponse.append(text);
                                        hasContent = true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("跳过非JSON�? {}", data);
                        }
                    }
                }

                if (!hasContent) {
                    emitter.error(new ChatException(
                            "API 未返回内容，请检查模�?" + model + ")和密�?));
                    return;
                }

                log.info("响应完整 JSON: ");
                log.info(fullResponse.toString());
                emitter.complete();

            } catch (ChatException e) {
                emitter.error(e);
            } catch (Exception e) {
                log.error("流式 API 调用失败", e);
                emitter.error(new ChatException("AI 流式服务调用失败: " + e.getMessage()));
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 将消息转换为模型理解的格�?     * 使用原生 OpenAI 格式，不再做文本格式转换�?     * 1. tool 角色消息保持原生格式（不转换�?user 角色�?     * 2. assistant �?toolCalls 保持原生格式（不转换�?TOOL_CALL: 文本�?     * 3. 不再通过系统提示中添加工具调用格式说明，因为使用原生 tools 参数
     */
    private List<OpenAiChatMessage> translateForModel(List<OpenAiChatMessage> messages,
                                                       List<OpenAiTool> tools) {
        List<OpenAiChatMessage> result = new ArrayList<>();

        for (OpenAiChatMessage msg : messages) {
            result.add(msg);
        }

        return result;
    }

    private String buildToolCallJson(OpenAiChatMessage.ToolCall tc) {
        try {
            return "{\"name\":\"" + tc.getFunction().getName()
                    + "\",\"arguments\":" + tc.getFunction().getArguments() + "}";
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildToolSystemPrompt(List<OpenAiTool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("你可以使用以下工具来帮助用户解决问题。\n");
        sb.append("当你需要调用工具时，必须严格按照以下格式输出，不要添加任何其他内容：\n");
        sb.append(TOOL_CALL_MARKER + " {\"name\": \"<工具名称>\", \"arguments\": {<参数JSON>}}\n\n");
        sb.append("可用工具列表：\n");
        
        for (OpenAiTool tool : tools) {
            OpenAiTool.Function func = tool.getFunction();
            sb.append("- ").append(func.getName()).append(": ").append(func.getDescription()).append("\n");
            
            OpenAiTool.Parameters params = func.getParameters();
            if (params != null && params.getProperties() != null && !params.getProperties().isEmpty()) {
                sb.append("  参数：\n");
                for (Map.Entry<String, OpenAiTool.Property> entry : params.getProperties().entrySet()) {
                    String paramName = entry.getKey();
                    OpenAiTool.Property prop = entry.getValue();
                    
                    sb.append("    - ").append(paramName);
                    if (prop.getType() != null) {
                        sb.append(" (").append(prop.getType()).append(")");
                    }
                    if (prop.getDescription() != null) {
                        sb.append(": ").append(prop.getDescription());
                    }
                    
                    if (prop.getEnums() != null && !prop.getEnums().isEmpty()) {
                        sb.append("。可选值：").append(String.join(", ", prop.getEnums()));
                    }
                    
                    if (params.getRequired() != null && params.getRequired().contains(paramName)) {
                        sb.append(" [必需]");
                    }
                    
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    private OpenAiChatResponse parseToolCallFromResponse(OpenAiChatResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) return response;

        log.info("=== AI 原始响应 ===");
        try {
            log.info("响应完整 JSON: ");
            log.info(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.info("无法序列化响�?);
        }
        log.info("=== 原始响应结束 ===");

        OpenAiChatMessage message = response.getChoices().get(0).getMessage();
        
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            log.info("检测到原生 tool_calls 格式，工具调用数�? {}", message.getToolCalls().size());
            for (OpenAiChatMessage.ToolCall toolCall : message.getToolCalls()) {
                log.info("  工具: {}", toolCall.getFunction() != null ? toolCall.getFunction().getName() : "unknown");
            }
            return response;
        }

        String content = message.getContentAsString();
        if (content == null) {
            log.info("AI 响应中没有工具调用，也没有文本内�?);
            return response;
        }

        int idx = content.indexOf(TOOL_CALL_MARKER);
        if (idx < 0) {
            log.info("AI 响应中没有找到工具调用标�?({}), 将作为普通文本响应处�?, TOOL_CALL_MARKER);
            return response;
        }
        
        log.info("检测到兼容格式 TOOL_CALL: 标记，开始解�?..");

        try {
            String after = content.substring(idx + TOOL_CALL_MARKER.length()).trim();
            int start = after.indexOf('{');
            if (start < 0) return response;

            int depth = 0, end = -1;
            for (int i = start; i < after.length(); i++) {
                char c = after.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    if (--depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            if (end < 0) return response;

            String jsonObj = after.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(jsonObj);
            String toolName = node.path("name").asText();
            String arguments;

            if (!toolName.isEmpty()) {
                log.info("检测到标准工具调用格式，工具名: {}", toolName);
                arguments = objectMapper.writeValueAsString(node.path("arguments"));
            } else {
                JsonNode actionNode = node.path("action");
                if (actionNode.isMissingNode() || actionNode.asText().isEmpty()) {
                    log.warn("无法确定工具调用格式，既没有 name 字段也没�?action 字段");
                    return response;
                }
                
                log.info("检测到简化工具调用格式（vocabulary 模式），action: {}", actionNode.asText());
                toolName = "vocabulary";
                arguments = jsonObj;
            }

            String toolId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            OpenAiChatMessage.ToolCall toolCall = OpenAiChatMessage.ToolCall.builder()
                    .id(toolId)
                    .type("function")
                    .function(OpenAiChatMessage.FunctionCall.builder()
                            .name(toolName)
                            .arguments(arguments)
                            .build())
                    .build();

            OpenAiChatMessage assistantMsg = OpenAiChatMessage.builder()
                    .role("assistant")
                    .toolCalls(Collections.singletonList(toolCall))
                    .build();

            log.info("Parsed prompt-based tool call: {}", toolName);

            return OpenAiChatResponse.builder()
                    .choices(Collections.singletonList(
                            OpenAiChatResponse.Choice.builder()
                                    .index(0)
                                    .message(assistantMsg)
                                    .finishReason("tool_calls")
                                    .build()))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse tool call from model response", e);
            return response;
        }
    }

    private OpenAiChatResponse callApi(OpenAiChatRequest request) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doCallApi(request);
            } catch (ChatException e) {
                throw e;
            } catch (Exception e) {
                boolean isTransient = e.getMessage() != null &&
                        e.getMessage().contains("header parser received no bytes");
                if (isTransient && attempt < maxRetries) {
                    log.warn("API 调用遇到瞬时连接错误，第 {}/{} 次重�? {}", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ChatException("AI 服务调用被中�?);
                    }
                } else {
                    log.error("API 调用失败", e);
                    throw new ChatException("AI 服务调用失败: " + e.getMessage());
                }
            }
        }
        throw new ChatException("AI 服务调用失败：超过最大重试次�?);
    }

    private OpenAiChatResponse doCallApi(OpenAiChatRequest request) throws Exception {
        String json = objectMapper.writeValueAsString(request);

        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(llmProperties.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + llmProperties.getApiKey())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                .timeout(Duration.ofMillis(llmProperties.getTimeout()))
                .build();

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30));

        if (proxyProperties.isValid()) {
            log.info("LLM API 使用代理: {}:{}", proxyProperties.getHost(), proxyProperties.getPort());
            InetSocketAddress proxyAddress = new InetSocketAddress(proxyProperties.getHost(), proxyProperties.getPort());
            clientBuilder.proxy(ProxySelector.of(proxyAddress));
        }

        HttpClient httpClient = clientBuilder.build();

        java.net.http.HttpResponse<String> response =
                httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

        log.info("HTTP 响应状�? {}", response.statusCode());

        if (response.statusCode() >= 400) {
            log.error("API 错误 {}: {}", response.statusCode(), response.body());
            throw new ChatException("API错误 " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), OpenAiChatResponse.class);
    }

    private Flux<String> chatStreamInternal(List<OpenAiChatMessage> messages) {
        log.info("调用 Chat Completions API 流式，模�? {}, 消息�? {}",
                llmProperties.getModel(), messages.size());

        return Flux.<String>create(emitter -> {
            try {
                OpenAiChatRequest request = OpenAiChatRequest.builder()
                        .model(llmProperties.getModel())
                        .messages(messages)
                        .temperature(llmProperties.getTemperature())
                        .maxTokens(llmProperties.getMaxTokens())
                        .stream(true)
                        .build();

                String json = objectMapper.writeValueAsString(request);
                log.info("流式请求 JSON: {}", JsonLogUtils.toLogString(objectMapper, request));

                java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(llmProperties.getBaseUrl() + "/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream, application/json")
                        .header("Authorization", "Bearer " + llmProperties.getApiKey())
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                        .timeout(Duration.ofMillis(llmProperties.getTimeout()))
                        .build();

                HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(30));

                if (proxyProperties.isValid()) {
                    log.info("LLM API 流式调用使用代理: {}:{}", proxyProperties.getHost(), proxyProperties.getPort());
                    InetSocketAddress proxyAddress = new InetSocketAddress(proxyProperties.getHost(), proxyProperties.getPort());
                    clientBuilder.proxy(ProxySelector.of(proxyAddress));
                }

                HttpClient httpClient = clientBuilder.build();

                java.net.http.HttpResponse<java.io.InputStream> response =
                        httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 400) {
                    byte[] body = response.body().readAllBytes();
                    String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                    log.error("流式 API 错误 {}: {}", response.statusCode(), bodyStr);
                    emitter.error(new ChatException("API错误 " + response.statusCode() + ": " + bodyStr));
                    return;
                }

                boolean hasContent = false;
                StringBuilder fullResponse = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                        if (data.isEmpty() || data.equals("[DONE]")) continue;
                        if (!data.startsWith("{")) continue;
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode choices = node.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode content = choices.get(0).path("delta").path("content");
                                if (!content.isMissingNode() && !content.isNull()) {
                                    String text = content.asText();
                                    if (!text.isEmpty()) {
                                        emitter.next(text);
                                        fullResponse.append(text);
                                        hasContent = true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("跳过非JSON�? {}", data);
                        }
                    }
                }

                if (!hasContent) {
                    emitter.error(new ChatException(
                            "API 未返回内容，请检查模�?" + llmProperties.getModel() + ")和密�?));
                    return;
                }

                log.info("响应完整 JSON: ");
                log.info(fullResponse.toString());
                emitter.complete();

            } catch (ChatException e) {
                emitter.error(e);
            } catch (Exception e) {
                log.error("流式 API 调用失败", e);
                emitter.error(new ChatException("AI 流式服务调用失败: " + e.getMessage()));
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private void logRequest(OpenAiChatRequest request) {
        try {
            log.info("请求 JSON: {}", JsonLogUtils.toLogString(objectMapper, request));
        } catch (Exception e) {
            log.error("序列化请求失�? {}", e.getMessage());
        }
    }
}
