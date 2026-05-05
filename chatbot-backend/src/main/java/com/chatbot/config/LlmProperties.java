package com.lingobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    
    public static final String MODEL_QWEN_OMNI = "qwen/qwen3.5-omni";
    public static final String MODEL_QWEN_FLASH = "qwen/qwen3.5-flash-02-23";
    public static final String MODEL_XIAOMI_MIMO_OMNI = "xiaomi/mimo-v2-omni";
    
    private String baseUrl = "https://openrouter.ai/api";
    private String apiKey;
    
    private String model = MODEL_QWEN_FLASH;
    
    private String audioModel = MODEL_XIAOMI_MIMO_OMNI;
    
    private String reasoningEffort = "medium";
    private double temperature = 0.7;
    private int maxTokens = 4096;
    private long timeout = 120000;
    private boolean audioEnabled = true;

    public String getCompletionsUrl() {
        return baseUrl + "/v1/chat/completions";
    }
    
    public String getModelForAudio() {
        if (audioModel != null && !audioModel.isEmpty()) {
            return audioModel;
        }
        return model;
    }
    
    public String getFullModelName(String shortModelName) {
        if (shortModelName == null || shortModelName.isEmpty()) {
            return getModelForAudio();
        }
        
        String lowerName = shortModelName.toLowerCase();
        
        if (lowerName.equals("qwen") || lowerName.contains("qwen3.5") || lowerName.contains("qwen-omni")) {
            return MODEL_QWEN_OMNI;
        }
        
        if (lowerName.equals("xiaomi") || lowerName.contains("mimo")) {
            return MODEL_XIAOMI_MIMO_OMNI;
        }
        
        return shortModelName;
    }
    
    public AudioModelConfig getAudioModelConfigForModel(String modelName) {
        String fullModelName = getFullModelName(modelName);
        return AudioModelConfig.fromModelName(fullModelName);
    }
    
    public AudioModelConfig getAudioModelConfig() {
        return AudioModelConfig.fromModelName(getModelForAudio());
    }
    
    @Data
    public static class AudioModelConfig {
        private final String modelName;
        private final String displayName;
        private final String provider;
        private final boolean supportsAudio;
        private final boolean supportsImage;
        private final boolean supportsVideo;
        private final double inputPricePerMillion;
        private final double outputPricePerMillion;
        
        public static final AudioModelConfig QWEN_OMNI = new AudioModelConfig(
            MODEL_QWEN_OMNI,
            "Qwen 3.5",
            "Alibaba Cloud",
            true, true, true,
            1.0, 4.0
        );
        
        public static final AudioModelConfig XIAOMI_MIMO_OMNI = new AudioModelConfig(
            MODEL_XIAOMI_MIMO_OMNI,
            "MiMo V2 Omni",
            "Xiaomi",
            true, true, true,
            0.40, 2.0
        );
        
        public static AudioModelConfig fromModelName(String modelName) {
            if (modelName == null) {
                return XIAOMI_MIMO_OMNI;
            }
            String lowerName = modelName.toLowerCase();
            if (lowerName.contains("mimo") || lowerName.contains("xiaomi")) {
                return XIAOMI_MIMO_OMNI;
            }
            if (lowerName.contains("qwen") || lowerName.contains("qwen3.5")) {
                return QWEN_OMNI;
            }
            return XIAOMI_MIMO_OMNI;
        }
    }
}
