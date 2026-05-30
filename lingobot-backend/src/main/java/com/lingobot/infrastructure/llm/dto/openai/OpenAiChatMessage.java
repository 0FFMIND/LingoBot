package com.lingobot.infrastructure.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 聊天消息 DTO。
 *
 * 对应 OpenAI Chat Completions API 中的 message 对象，支持多种消息类型：
 * - 纯文本消息：content 为 String 类型
 * - 多模态消息：content 为 List<ContentPart> 类型，可包含文本、音频、图片
 * - 工具调用消息：包含 tool_calls 字段
 * - 工具结果消息：包含 tool_call_id 字段
 *
 * 通过静态工厂方法创建不同类型的消息，确保格式正确。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatMessage {

    // 消息角色：system、user、assistant、tool
    private String role;
    // 消息内容：String（纯文本）或 List<ContentPart>（多模态）
    private Object content;
    // 工具调用列表（assistant 消息使用）
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
    // 工具调用 ID（tool 消息使用，关联对应的 tool_call）
    @JsonProperty("tool_call_id")
    private String toolCallId;
    // 工具名称（tool 消息使用）
    private String name;

    // 工具调用对象，表示模型发起的一次工具调用请求
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        // 工具调用唯一标识
        private String id;
        // 调用类型，目前仅支持 "function"
        private String type;
        // 函数调用详情
        private FunctionCall function;
    }

    // 函数调用详情，包含要调用的函数名称和 JSON 格式的参数字符串
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCall {
        // 函数名称
        private String name;
        // JSON 格式的参数字符串
        private String arguments;
    }

    // 多模态消息内容片段，支持三种类型：text（文本）、input_audio（音频）、image_url（图片）
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentPart {
        // 内容类型：text、input_audio、image_url
        private String type;
        // 文本内容（type=text 时使用）
        private String text;
        // 音频内容（type=input_audio 时使用）
        @JsonProperty("input_audio")
        private AudioContent inputAudio;
        // 图片内容（type=image_url 时使用）
        @JsonProperty("image_url")
        private ImageUrlContent imageUrl;
    }

    // 音频内容，包含 Base64 编码的音频数据和格式信息
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioContent {
        // Base64 编码的音频数据
        private String data;
        // 音频格式：wav、mp3、webm、m4a、flac、ogg、aiff
        private String format;
    }

    // 图片内容，包含图片 URL（支持 data URL 格式）和详情级别
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageUrlContent {
        // 图片 URL，支持 data:image/xxx;base64,xxx 格式
        private String url;
        // 图片处理细节级别：auto、low、high
        private String detail;
    }

    // 创建纯文本消息
    public static OpenAiChatMessage createTextMessage(String role, String text) {
        return OpenAiChatMessage.builder()
                .role(role)
                .content(text)
                .build();
    }

    // 创建带音频的多模态消息，内容包含文本和音频两部分（如果文本不为空）
    public static OpenAiChatMessage createAudioMessage(String role, String text, String audioData, String audioFormat) {
        List<ContentPart> contentParts = new ArrayList<>();

        if (text != null && !text.trim().isEmpty()) {
            ContentPart textPart = ContentPart.builder()
                    .type("text")
                    .text(text)
                    .build();
            contentParts.add(textPart);
        }

        ContentPart audioPart = ContentPart.builder()
                .type("input_audio")
                .inputAudio(AudioContent.builder()
                        .data(audioData)
                        .format(audioFormat != null ? audioFormat : "wav")
                        .build())
                .build();
        contentParts.add(audioPart);

        return OpenAiChatMessage.builder()
                .role(role)
                .content(contentParts)
                .build();
    }

    // 创建带图片的多模态消息，内容包含文本和图片两部分（如果文本不为空）
    public static OpenAiChatMessage createImageMessage(String role, String text, String imageBase64, String imageFormat) {
        List<ContentPart> contentParts = new ArrayList<>();

        String dataUrl = String.format("data:image/%s;base64,%s",
                imageFormat != null ? imageFormat : "png", imageBase64);

        if (text != null && !text.trim().isEmpty()) {
            ContentPart textPart = ContentPart.builder()
                    .type("text")
                    .text(text)
                    .build();
            contentParts.add(textPart);
        }

        ContentPart imagePart = ContentPart.builder()
                .type("image_url")
                .imageUrl(ImageUrlContent.builder()
                        .url(dataUrl)
                        .detail("auto")
                        .build())
                .build();
        contentParts.add(imagePart);

        return OpenAiChatMessage.builder()
                .role(role)
                .content(contentParts)
                .build();
    }

    // 创建混合多模态消息（音频+图片），内容可包含文本、音频、图片的任意组合
    public static OpenAiChatMessage createMultiModalMessage(String role, String text,
            String audioData, String audioFormat,
            String imageBase64, String imageFormat) {
        List<ContentPart> contentParts = new ArrayList<>();

        if (text != null && !text.trim().isEmpty()) {
            ContentPart textPart = ContentPart.builder()
                    .type("text")
                    .text(text)
                    .build();
            contentParts.add(textPart);
        }

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            String dataUrl = String.format("data:image/%s;base64,%s",
                    imageFormat != null ? imageFormat : "png", imageBase64);
            ContentPart imagePart = ContentPart.builder()
                    .type("image_url")
                    .imageUrl(ImageUrlContent.builder()
                            .url(dataUrl)
                            .detail("auto")
                            .build())
                    .build();
            contentParts.add(imagePart);
        }

        if (audioData != null && !audioData.isEmpty()) {
            ContentPart audioPart = ContentPart.builder()
                    .type("input_audio")
                    .inputAudio(AudioContent.builder()
                            .data(audioData)
                            .format(audioFormat != null ? audioFormat : "wav")
                            .build())
                    .build();
            contentParts.add(audioPart);
        }

        return OpenAiChatMessage.builder()
                .role(role)
                .content(contentParts)
                .build();
    }

    // 检查消息是否包含音频内容
    public boolean hasAudioContent() {
        if (content instanceof List<?>) {
            List<?> list = (List<?>) content;
            return list.stream()
                    .anyMatch(item -> {
                        if (item instanceof ContentPart) {
                            return "input_audio".equals(((ContentPart) item).getType());
                        }
                        return false;
                    });
        }
        return false;
    }

    // 从消息内容中提取音频数据（Base64）
    @JsonIgnore
    public String getAudioData() {
        if (content instanceof List<?>) {
            List<?> list = (List<?>) content;
            for (Object item : list) {
                if (item instanceof ContentPart) {
                    ContentPart part = (ContentPart) item;
                    if ("input_audio".equals(part.getType()) && part.getInputAudio() != null) {
                        return part.getInputAudio().getData();
                    }
                }
            }
        }
        return null;
    }

    // 从消息内容中提取音频格式
    @JsonIgnore
    public String getAudioFormat() {
        if (content instanceof List<?>) {
            List<?> list = (List<?>) content;
            for (Object item : list) {
                if (item instanceof ContentPart) {
                    ContentPart part = (ContentPart) item;
                    if ("input_audio".equals(part.getType()) && part.getInputAudio() != null) {
                        return part.getInputAudio().getFormat();
                    }
                }
            }
        }
        return null;
    }

    // 检查消息是否包含图片内容
    public boolean hasImageContent() {
        if (content instanceof List<?>) {
            List<?> list = (List<?>) content;
            return list.stream()
                    .anyMatch(item -> {
                        if (item instanceof ContentPart) {
                            return "image_url".equals(((ContentPart) item).getType());
                        }
                        return false;
                    });
        }
        return false;
    }

    // 从消息内容中提取纯文本，支持 String、List<ContentPart> 等多种格式
    @JsonIgnore
    public String getContentAsString() {
        if (content == null) {
            return null;
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof List<?>) {
            StringBuilder sb = new StringBuilder();
            List<?> list = (List<?>) content;
            for (Object item : list) {
                if (item instanceof ContentPart) {
                    ContentPart part = (ContentPart) item;
                    if ("text".equals(part.getType()) && part.getText() != null) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(part.getText());
                    }
                } else if (item instanceof String) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append((String) item);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return content.toString();
    }
}
