package com.lingobot.infrastructure.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.util.JsonLogUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 工具类。
 *
 * 提供 LLM 交互相关的通用工具方法，包括格式规范化、消息构建、内容提取等。
 * 所有方法均为无状态的静态方法，不依赖 Spring 容器。
 */
@Slf4j
public class LlmUtil {

    private LlmUtil() {
        // 工具类，禁止实例化
    }

    // 检查消息列表中是否包含音频内容
    public static boolean hasAudioMessage(List<OpenAiChatMessage> messages) {
        return messages.stream()
                .anyMatch(OpenAiChatMessage::hasAudioContent);
    }

    // 检查消息列表中是否包含图片内容
    public static boolean hasImageMessage(List<OpenAiChatMessage> messages) {
        return messages.stream()
                .anyMatch(OpenAiChatMessage::hasImageContent);
    }

    // 从消息内容中提取纯文本，支持 String 和其他可 toString 的对象类型
    public static String extractTextContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String) {
            return (String) content;
        }
        return content.toString();
    }

    // 规范化音频格式字符串，将各种格式别名映射为标准格式名，未知格式默认使用 wav
    public static String normalizeAudioFormat(String format) {
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

    // 规范化图片格式字符串，将各种格式别名映射为标准格式名，未知格式默认使用 png
    public static String normalizeImageFormat(String format) {
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

    // 准备带音频的消息列表，将纯文本 user 消息转换为包含音频内容的多模态消息
    public static List<OpenAiChatMessage> prepareAudioMessages(
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

    // 准备带图片的消息列表，将纯文本 user 消息转换为包含图片内容的多模态消息
    public static List<OpenAiChatMessage> prepareImageMessages(
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

    // 准备混合多模态消息列表（音频+图片），将纯文本 user 消息转换为同时包含音频和图片的多模态消息
    public static List<OpenAiChatMessage> prepareMultiModalMessages(
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

    // 将消息转换为模型理解的格式，当前实现直接返回原消息列表，使用原生 OpenAI 格式
    public static List<OpenAiChatMessage> translateForModel(List<OpenAiChatMessage> messages,
                                                             List<OpenAiTool> tools) {
        List<OpenAiChatMessage> result = new ArrayList<>();

        for (OpenAiChatMessage msg : messages) {
            result.add(msg);
        }

        return result;
    }



    // 记录请求日志，使用 JsonLogUtils 对敏感信息进行脱敏处理后再记录
    public static void logRequest(Object request, ObjectMapper objectMapper) {
        try {
            log.info("请求 JSON: {}", JsonLogUtils.toLogString(objectMapper, request));
        } catch (Exception e) {
            log.error("序列化请求失败: {}", e.getMessage());
        }
    }
}
