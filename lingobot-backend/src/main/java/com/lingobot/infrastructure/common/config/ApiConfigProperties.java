package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * API配置属性类
 * 用于管理各API端点的点数消耗配置 */
@Data
@Component
@ConfigurationProperties(prefix = "api")
public class ApiConfigProperties {
    
    /**
     * 默认点数消耗     */
    private double defaultCost = 0.1;
    
    /**
     * API端点配置
     */
    private Map<String, Map<String, ApiEndpointConfig>> endpoints = new HashMap<>();
    
    /**
     * 获取指定API的点数消耗     * @param category API类别（如vocabulary）     * @param endpointName 端点名称（如create-card）     * @return 点数消耗     */
    public double getCost(String category, String endpointName) {
        if (endpoints.containsKey(category)) {
            Map<String, ApiEndpointConfig> categoryEndpoints = endpoints.get(category);
            if (categoryEndpoints.containsKey(endpointName)) {
                return categoryEndpoints.get(endpointName).getCost();
            }
        }
        return defaultCost;
    }
    
    /**
     * API端点配置
     */
    @Data
    public static class ApiEndpointConfig {
        private String path;
        private String method;
        private double cost;
        private String description;
    }
}
