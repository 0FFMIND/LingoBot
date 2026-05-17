package com.lingobot.infrastructure.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiChatResponse {
    
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Integer index;
        private OpenAiChatMessage message;
        @JsonProperty("finish_reason")
        @JsonAlias("finishReason")
        private String finishReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        @JsonAlias("promptTokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        @JsonAlias("completionTokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        @JsonAlias("totalTokens")
        private Integer totalTokens;
    }
}
