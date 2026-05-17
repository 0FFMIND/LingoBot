package com.lingobot.infrastructure.llm.service;

import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatResponse;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.common.exception.ChatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRouterService {
    
    private final LlmService llmService;
    private final LlmProperties llmProperties;
    
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
    
    public Flux<String> chatStreamWithImage(String model, List<OpenAiChatMessage> messages, 
                                            String imageData, String imageFormat) {
        log.info("路由带图片的流式请求到模型: {}, 图片数据长度: {}, 格式: {}",
                model,
                imageData != null ? imageData.length() : 0,
                imageFormat);
        
        return llmService.chatStreamWithImage(messages, imageData, imageFormat, model);
    }
    
    public Flux<String> chatStreamWithAudioAndImage(String model, List<OpenAiChatMessage> messages,
                                                     String audioData, String audioFormat,
                                                     String imageData, String imageFormat) {
        log.info("路由带音频和图片的混合流式请求到模型: {}", model);
        
        return llmService.chatStreamWithAudioAndImage(messages, audioData, audioFormat, imageData, imageFormat, model);
    }
    
    public String chat(String model, List<OpenAiChatMessage> messages) {
        log.info("路由普通聊天请求到模型: {}", model);
        return llmService.chat(model, messages);
    }
    
    public OpenAiChatResponse chatWithTools(String model, List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        log.info("路由带工具的聊天请求到模型: {}", model);
        return llmService.chatWithTools(model, messages, tools);
    }
    
    public Flux<String> chatStream(String model, List<OpenAiChatMessage> messages) {
        log.info("路由流式请求到模型: {}", model);
        return llmService.chatStream(model, messages);
    }
    
    public Flux<String> chatStreamWithTools(String model, List<OpenAiChatMessage> messages, List<OpenAiTool> tools) {
        log.info("路由带工具的流式请求到模型: {}", model);
        return llmService.chatStreamWithTools(model, messages, tools);
    }
}
