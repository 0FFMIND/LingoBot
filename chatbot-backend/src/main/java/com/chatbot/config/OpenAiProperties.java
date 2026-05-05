package com.lingobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {
    
    private String baseUrl = "https://openrouter.ai/api";
    private String apiKey;
    private String model = "openai/gpt-3.5-turbo-0125";
    private String reasoningEffort = "medium";
    private double temperature = 0.7;
    private int maxTokens = 4096;
    private long timeout = 60000;

    public String getCompletionsUrl() {
        return baseUrl + "/v1/chat/completions";
    }
}
