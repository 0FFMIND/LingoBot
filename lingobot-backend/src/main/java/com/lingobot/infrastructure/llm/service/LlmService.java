package com.lingobot.infrastructure.llm.service;

import com.lingobot.media.audio.service.AudioConversionService;
import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatRequest;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatResponse;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static com.lingobot.infrastructure.llm.service.LlmUtil.*;

/**
 * LLM 服务层。
 *
 * 封装与大语言模型 API 的所有交互，提供统一的调用接口：
 * - 非流式文本聊天（chat）
 * - 带工具调用的非流式聊天（chatWithTools）
 * - 流式文本聊天（chatStream）
 * - 多模态聊天（音频、图片、音视频混合）
 * - 工具调用解析（兼容原生格式与提示词格式）
 *
 * 所有请求统一使用 OpenAI 兼容格式，支持通过 LlmProperties 配置不同的模型提供商。
 * 音频输入会自动转换为支持的格式，不支持多模态的模型会自动回退到文本模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {



    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final AudioConversionService audioConversionService;

    // 单次非流式文本请求入口，普通文本调用也复用 chatWithTools(messages, null)
    public String chat(String model, List<OpenAiChatMessage> messages) {
        OpenAiChatResponse response = chatWithTools(model, messages, null);
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            throw ChatException.badRequest("AI 返回空响应");
        }
        String text = response.getChoices().get(0).getMessage().getContentAsString();
        if (text == null || text.isEmpty()) {
            throw ChatException.badRequest("AI 返回空响应");
        }
        return text;
    }

    // 带工具调用的单次非流式请求入口，tools 为空时就是普通聊天
    public OpenAiChatResponse chatWithTools(String model, List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        boolean hasTools = tools != null && !tools.isEmpty();
        String fullModelName = llmProperties.getFullModelName(model);
        log.info("调用 Chat Completions API，模型: {}, 消息数: {}, tools: {}",
                fullModelName, messages.size(), hasTools ? tools.size() : 0);

        List<OpenAiChatMessage> sendMessages = translateForModel(messages, tools);

        OpenAiChatRequest request = OpenAiChatRequest.builder()
                .model(fullModelName)
                .messages(sendMessages)
                .temperature(llmProperties.getTemperature())
                .maxTokens(llmProperties.getMaxTokens())
                .stream(false)
                .tools(hasTools ? tools : null)
                .toolChoice(hasTools ? "auto" : null)
                .build();

        logRequest(request, objectMapper);

        OpenAiChatResponse response = callApi(request);

        return hasTools ? parseToolCallFromResponse(response) : response;
    }

    // 流式文本聊天请求，通过 SSE 方式逐字返回 AI 响应
    public Flux<String> chatStream(String model, List<OpenAiChatMessage> messages) {
        String fullModelName = llmProperties.getFullModelName(model);
        return chatStreamInternalWithModel(translateForModel(messages, null), fullModelName);
    }

    // 带工具调用的流式聊天请求，当前实现与普通流式聊天相同
    public Flux<String> chatStreamWithTools(String model, List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        String fullModelName = llmProperties.getFullModelName(model);
        return chatStreamInternalWithModel(translateForModel(messages, null), fullModelName);
    }

    // 异步流式聊天请求，通过回调处理响应，订阅后立即返回不阻塞
    public void chatStreamAsync(String model, List<OpenAiChatMessage> messages,
                                Consumer<String> onChunk,
                                Runnable onComplete,
                                Consumer<Throwable> onError) {
        chatStream(model, messages).doOnNext(onChunk).doOnComplete(onComplete).doOnError(onError).subscribe();
    }

    // 转换消息列表中所有音频消息的格式，确保发给 LLM 的音频都是支持的格式
    public List<OpenAiChatMessage> convertAllAudioMessages(List<OpenAiChatMessage> messages) {
        return messages.stream().map(msg -> {
            if (!"user".equals(msg.getRole()) || !msg.hasAudioContent()) {
                return msg;
            }

            String originalFormat = msg.getAudioFormat();
            String originalAudioData = msg.getAudioData();
            if (originalFormat == null || originalAudioData == null) {
                return msg;
            }

            AudioConversionService.ConversionResult conversionResult =
                    audioConversionService.convertIfNeeded(originalAudioData, originalFormat);

            String convertedAudioData = conversionResult.base64Audio();
            String convertedFormat = conversionResult.format();

            if (!convertedFormat.equals(normalizeAudioFormat(originalFormat))) {
                log.info("历史音频格式已转换: {} -> {}", originalFormat, convertedFormat);
            }

            String textContent = extractTextContent(msg.getContent());
            return OpenAiChatMessage.createAudioMessage(
                    msg.getRole(),
                    textContent,
                    convertedAudioData,
                    convertedFormat
            );
        }).toList();
    }

    // 带音频输入的流式聊天（使用默认模型）
    public Flux<String> chatStreamWithAudio(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat) {
        return chatStreamWithAudio(messages, audioData, audioFormat, null);
    }

    // 带音频输入的流式聊天（指定模型），自动处理音频格式转换和模型回退
    public Flux<String> chatStreamWithAudio(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String model) {

        if (!llmProperties.isAudioEnabled()) {
            log.warn("音频功能未启用，使用文本模式");
            return chatStream(model, messages);
        }

        List<OpenAiChatMessage> messagesWithConvertedHistory = convertAllAudioMessages(messages);

        AudioConversionService.ConversionResult conversionResult =
                audioConversionService.convertIfNeeded(audioData, audioFormat);

        String finalAudioData = conversionResult.base64Audio();
        String finalFormat = conversionResult.format();

        if (!finalFormat.equals(normalizeAudioFormat(audioFormat))) {
            log.info("音频格式已转换: {} -> {}", audioFormat, finalFormat);
        }

        String fullModelName = llmProperties.getFullModelName(model);
        LlmProperties.ModelCapabilityConfig config = llmProperties.getAudioModelConfigForModel(model);
        if (!config.isSupportsAudio()) {
            String fallbackModel = llmProperties.getModelForAudio();
            log.warn("模型 {} 不支持音频输入，已切换到音频模型 {}", fullModelName, fallbackModel);
            fullModelName = fallbackModel;
            config = llmProperties.getAudioModelConfig();
        }

        log.info("调用多模态音频处理API - 模型: {} ({}), 消息数: {}, 音频格式: {}, 支持: 音频={}, 图像={}, 视频={}",
                config.getDisplayName(),
                fullModelName,
                messages.size(),
                finalFormat,
                config.isSupportsAudio(),
                config.isSupportsImage(),
                config.isSupportsVideo());

        log.info("价格参数 - 输入: ${}/M tokens, 输出: ${}/M tokens",
                config.getInputPricePerMillion(),
                config.getOutputPricePerMillion());

        List<OpenAiChatMessage> sendMessages = prepareAudioMessages(messagesWithConvertedHistory, finalAudioData, finalFormat);

        return chatStreamInternalWithModel(sendMessages, fullModelName);
    }

    // 带图片输入的流式聊天（使用默认模型）
    public Flux<String> chatStreamWithImage(
            List<OpenAiChatMessage> messages,
            String imageData,
            String imageFormat) {
        return chatStreamWithImage(messages, imageData, imageFormat, null);
    }

    // 带图片输入的流式聊天（指定模型）
    public Flux<String> chatStreamWithImage(
            List<OpenAiChatMessage> messages,
            String imageData,
            String imageFormat,
            String model) {

        String fullModelName = llmProperties.getFullModelName(model);
        LlmProperties.ModelCapabilityConfig config = llmProperties.getAudioModelConfigForModel(model);

        if (!config.isSupportsImage()) {
            log.warn("当前模型 {} 不支持图片输入，尝试使用支持图片的模型", config.getDisplayName());
        }

        log.info("调用多模态图片处理API - 模型: {} ({}), 消息数: {}, 图片格式: {}, 支持: 音频={}, 图像={}, 视频={}",
                config.getDisplayName(),
                fullModelName,
                messages.size(),
                imageFormat,
                config.isSupportsAudio(),
                config.isSupportsImage(),
                config.isSupportsVideo());

        log.info("价格参数 - 输入: ${}/M tokens, 输出: ${}/M tokens",
                config.getInputPricePerMillion(),
                config.getOutputPricePerMillion());

        List<OpenAiChatMessage> sendMessages = prepareImageMessages(messages, imageData, imageFormat);

        return chatStreamInternalWithModel(sendMessages, fullModelName);
    }

    // 带音频和图片混合输入的流式聊天（使用默认模型）
    public Flux<String> chatStreamWithAudioAndImage(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String imageData,
            String imageFormat) {
        return chatStreamWithAudioAndImage(messages, audioData, audioFormat, imageData, imageFormat, null);
    }

    // 带音频和图片混合输入的流式聊天（指定模型），不支持音频的模型会自动回退
    public Flux<String> chatStreamWithAudioAndImage(
            List<OpenAiChatMessage> messages,
            String audioData,
            String audioFormat,
            String imageData,
            String imageFormat,
            String model) {

        String fullModelName = llmProperties.getFullModelName(model);
        LlmProperties.ModelCapabilityConfig config = llmProperties.getAudioModelConfigForModel(model);
        if (!config.isSupportsAudio()) {
            String fallbackModel = llmProperties.getModelForAudio();
            log.warn("模型 {} 不支持音频输入，已切换到音频模型 {}", fullModelName, fallbackModel);
            fullModelName = fallbackModel;
            config = llmProperties.getAudioModelConfig();
        }

        log.info("调用多模态混合输入API - 模型: {} ({}), 消息数: {}, 支持: 音频={}, 图像={}, 视频={}",
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

    // 内部流式请求实现（指定模型名），使用 Java HttpClient 发送 SSE 请求
    private Flux<String> chatStreamInternalWithModel(List<OpenAiChatMessage> messages, String model) {
        log.info("调用 Chat Completions API 流式，模型: {}, 消息数: {}",
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
                log.info("流式请求 JSON: {}", json);

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

                HttpClient httpClient = clientBuilder.build();

                java.net.http.HttpResponse<java.io.InputStream> response =
                        httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 400) {
                    byte[] body = response.body().readAllBytes();
                    String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                    log.error("流式 API 错误 {}: {}", response.statusCode(), bodyStr);
                    emitter.error(ChatException.badRequest("API错误 " + response.statusCode() + ": " + bodyStr));
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
                            log.debug("跳过非JSON格式: {}", data);
                        }
                    }
                }

                if (!hasContent) {
                    emitter.error(ChatException.badRequest(
                            "API 未返回内容，请检查模型: " + model + " 和密钥"));
                    return;
                }

                log.info("响应完整 JSON: ");
                log.info(fullResponse.toString());
                emitter.complete();

            } catch (ChatException e) {
                emitter.error(e);
            } catch (Exception e) {
                log.error("流式 API 调用失败", e);
                emitter.error(ChatException.badRequest("AI 流式服务调用失败: " + e.getMessage()));
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    // 从 AI 响应中解析工具调用，仅支持原生 tool_calls 格式
    private OpenAiChatResponse parseToolCallFromResponse(OpenAiChatResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) return response;

        log.info("=== AI 原始响应 ===");
        try {
            log.info("响应完整 JSON: ");
            log.info(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.info("无法序列化响应");
        }
        log.info("=== 原始响应结束 ===");

        OpenAiChatMessage message = response.getChoices().get(0).getMessage();

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            log.info("检测到原生 tool_calls 格式，工具调用数: {}", message.getToolCalls().size());
            for (OpenAiChatMessage.ToolCall toolCall : message.getToolCalls()) {
                log.info("  工具: {}", toolCall.getFunction() != null ? toolCall.getFunction().getName() : "unknown");
            }
        }

        return response;
    }

    // 调用 LLM API（带重试机制），最多重试 3 次，仅对瞬时连接错误进行重试
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
                    log.warn("API 调用遇到瞬时连接错误，第 {}/{} 次重试: {}", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ChatException.badRequest("AI 服务调用被中断");
                    }
                } else {
                    log.error("API 调用失败", e);
                    throw ChatException.badRequest("AI 服务调用失败: " + e.getMessage());
                }
            }
        }
        throw ChatException.badRequest("AI 服务调用失败：超过最大重试次数");
    }

    // 实际执行 LLM API 调用（无重试），使用 Java HttpClient 发送 POST 请求
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

        HttpClient httpClient = clientBuilder.build();

        java.net.http.HttpResponse<String> response =
                httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

        log.info("HTTP 响应状态: {}", response.statusCode());

        if (response.statusCode() >= 400) {
            log.error("API 错误 {}: {}", response.statusCode(), response.body());
            throw ChatException.badRequest("API错误 " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), OpenAiChatResponse.class);
    }

}
