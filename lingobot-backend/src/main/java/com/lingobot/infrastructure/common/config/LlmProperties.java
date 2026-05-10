package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM（大语言模型）配置属性类，从配置文件读取 llm.* 前缀的配置项。
 * 配置 API 地址、密钥、模型选择以及各种生成参数，
 * 支持文本和音频多模态模型的灵活切换。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    
    // 通义千问全功能模型，支持音频、图像、视频
    public static final String MODEL_QWEN_OMNI = "qwen/qwen3.5-omni";
    // 通义千问轻量模型，响应速度快，适合日常对话
    public static final String MODEL_QWEN_FLASH = "qwen/qwen3.5-flash-02-23";
    // 小米 MiMo 全功能模型，支持音频、图像、视频
    public static final String MODEL_XIAOMI_MIMO_OMNI = "xiaomi/mimo-v2-omni";
    
    // LLM API 基础地址，默认使用 OpenRouter
    private String baseUrl = "https://openrouter.ai/api";
    // API 密钥，用于身份验证
    private String apiKey;
    
    // 默认使用的文本模型
    private String model = MODEL_QWEN_FLASH;
    
    // 音频处理使用的模型
    private String audioModel = MODEL_XIAOMI_MIMO_OMNI;
    
    // 推理强度，影响模型输出的深度和复杂度
    private String reasoningEffort = "medium";
    // 温度参数，控制输出的随机性，值越高输出越多样
    private double temperature = 0.7;
    // 最大生成 token 数
    private int maxTokens = 4096;
    // 请求超时时间，单位毫秒
    private long timeout = 120000;
    // 是否启用音频功能
    private boolean audioEnabled = true;

    // 获取聊天补全接口的完整 URL
    public String getCompletionsUrl() {
        return baseUrl + "/v1/chat/completions";
    }
    
    // 获取音频处理使用的模型，如果未配置则使用默认模型
    public String getModelForAudio() {
        if (audioModel != null && !audioModel.isEmpty()) {
            return audioModel;
        }
        return model;
    }
    
    // 将短模型名转换为完整模型名，支持别名映射
    public String getFullModelName(String shortModelName) {
        if (shortModelName == null || shortModelName.isEmpty()) {
            return getModelForAudio();
        }
        
        String lowerName = shortModelName.toLowerCase();
        
        // 通义千问相关别名
        if (lowerName.equals("qwen") || lowerName.contains("qwen3.5") || lowerName.contains("qwen-omni")) {
            return MODEL_QWEN_OMNI;
        }
        
        // 小米 MiMo 相关别名
        if (lowerName.equals("xiaomi") || lowerName.contains("mimo")) {
            return MODEL_XIAOMI_MIMO_OMNI;
        }
        
        return shortModelName;
    }
    
    // 根据模型名获取对应的音频模型配置
    public AudioModelConfig getAudioModelConfigForModel(String modelName) {
        String fullModelName = getFullModelName(modelName);
        return AudioModelConfig.fromModelName(fullModelName);
    }
    
    // 获取当前音频模型的配置
    public AudioModelConfig getAudioModelConfig() {
        return AudioModelConfig.fromModelName(getModelForAudio());
    }
    
    /**
     * 音频模型配置类。
     * 包含模型名称、显示名、提供商、支持的功能和价格信息。
     */
    @Data
    public static class AudioModelConfig {
        // 模型完整名称
        private final String modelName;
        // 模型显示名称
        private final String displayName;
        // 模型提供商
        private final String provider;
        // 是否支持音频输入
        private final boolean supportsAudio;
        // 是否支持图像输入
        private final boolean supportsImage;
        // 是否支持视频输入
        private final boolean supportsVideo;
        // 输入价格，每百万 token
        private final double inputPricePerMillion;
        // 输出价格，每百万 token
        private final double outputPricePerMillion;
        
        // 通义千问全功能模型配置
        public static final AudioModelConfig QWEN_OMNI = new AudioModelConfig(
            MODEL_QWEN_OMNI,
            "Qwen 3.5",
            "Alibaba Cloud",
            true, true, true,
            1.0, 4.0
        );
        
        // 小米 MiMo 全功能模型配置
        public static final AudioModelConfig XIAOMI_MIMO_OMNI = new AudioModelConfig(
            MODEL_XIAOMI_MIMO_OMNI,
            "MiMo V2 Omni",
            "Xiaomi",
            true, true, true,
            0.40, 2.0
        );
        
        // 根据模型名获取对应的配置，默认返回小米 MiMo
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
