package com.lingobot.infrastructure.llm.service;

import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatResponse;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 模型路由服务。
 *
 * 作为 LLM 请求的统一入口，对外暴露所有 LLM 调用能力，内部委托给 LlmService 执行。
 * 主要职责：
 * - 日志记录：每次调用前记录请求路由信息
 * - 参数校验：根据配置决定是否启用某些功能（如音频）
 * - 模型路由：根据请求类型选择合适的模型（当前由 LlmService 内部处理）
 * - 接口聚合：将 LlmService 的各种调用方式统一暴露给上层业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRouterService {

    private final LlmService llmService;
    private final LlmProperties llmProperties;

    // 带音频输入的流式聊天请求，如果音频功能未启用则回退到普通文本聊天
    public Flux<String> chatStreamWithAudio(String model, List<OpenAiChatMessage> messages,
                                            String audioData, String audioFormat) {
        log.info("路由带音频的流式请求到模型: {}, 音频数据长度: {}, 格式: {}",
                model,
                audioData != null ? audioData.length() : 0,
                audioFormat);

        if (!llmProperties.isAudioEnabled()) {
            log.warn("音频功能未启用，使用普通文本模型");
            return chatStream(model, messages);
        }

        return llmService.chatStreamWithAudio(messages, audioData, audioFormat, model);
    }

    // 带图片输入的流式聊天请求
    public Flux<String> chatStreamWithImage(String model, List<OpenAiChatMessage> messages,
                                            String imageData, String imageFormat) {
        log.info("路由带图片的流式请求到模型: {}, 图片数据长度: {}, 格式: {}",
                model,
                imageData != null ? imageData.length() : 0,
                imageFormat);

        return llmService.chatStreamWithImage(messages, imageData, imageFormat, model);
    }

    // 带音频和图片混合输入的流式聊天请求
    public Flux<String> chatStreamWithAudioAndImage(String model, List<OpenAiChatMessage> messages,
                                                     String audioData, String audioFormat,
                                                     String imageData, String imageFormat) {
        log.info("路由带音频和图片的混合流式请求到模型: {}", model);

        return llmService.chatStreamWithAudioAndImage(messages, audioData, audioFormat, imageData, imageFormat, model);
    }

    // 普通非流式聊天请求
    public String chat(String model, List<OpenAiChatMessage> messages) {
        log.info("路由普通聊天请求到模型: {}", model);
        return llmService.chat(model, messages);
    }

    // 带工具调用的非流式聊天请求
    public OpenAiChatResponse chatWithTools(String model, List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        log.info("路由带工具的聊天请求到模型: {}", model);
        return llmService.chatWithTools(model, messages, tools);
    }

    // 流式文本聊天请求
    public Flux<String> chatStream(String model, List<OpenAiChatMessage> messages) {
        log.info("路由流式请求到模型: {}", model);
        return llmService.chatStream(model, messages);
    }

    // 带工具调用的流式聊天请求
    public Flux<String> chatStreamWithTools(String model, List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        log.info("路由带工具的流式请求到模型: {}", model);
        return llmService.chatStreamWithTools(model, messages, tools);
    }
}
