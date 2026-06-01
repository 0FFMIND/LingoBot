package com.lingobot.learning.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EditMessageRequest extends BaseChatRequest {
    
    private Long userMessageId;
    private String newContent;
    private String audioData;
    private String audioFormat;
    private Integer audioDuration;
    private String imageData;
    private String imageFormat;

    public boolean hasAudio() {
        return audioData != null && !audioData.isEmpty();
    }

    public boolean hasImage() {
        return imageData != null && !imageData.isEmpty();
    }
}
