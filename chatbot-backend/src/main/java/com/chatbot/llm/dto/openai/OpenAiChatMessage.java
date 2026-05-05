package com.lingobot.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatMessage {
    
    private String role;
    private Object content;
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
    @JsonProperty("tool_call_id")
    private String toolCallId;
    private String name;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentPart {
        private String type;
        private String text;
        @JsonProperty("input_audio")
        private AudioContent inputAudio;
        @JsonProperty("image_url")
        private ImageUrlContent imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioContent {
        private String data;
        private String format;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageUrlContent {
        private String url;
        private String detail;
    }

    public static OpenAiChatMessage createTextMessage(String role, String text) {
        return OpenAiChatMessage.builder()
                .role(role)
                .content(text)
                .build();
    }

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
