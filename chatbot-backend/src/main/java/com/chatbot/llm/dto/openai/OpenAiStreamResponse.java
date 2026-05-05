package com.lingobot.llm.dto.openai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiStreamResponse {
    
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<StreamChoice> choices;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamChoice {
        private Integer index;
        private Delta delta;
        private String finishReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        private String role;
        private String content;
    }
}
