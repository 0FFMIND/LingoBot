package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "api")
public class ApiConfigProperties {

    // 兜底 cost，端点未配置时不扣费
    private double defaultCost = 0.0;
    
    private Map<String, Map<String, ApiEndpointConfig>> endpoints = new HashMap<>();

    // 查询指定端点的 cost，category 如 "vocabulary"，endpointName 如 "create-card"
    public double getCost(String category, String endpointName) {
        if (endpoints.containsKey(category)) {
            Map<String, ApiEndpointConfig> categoryEndpoints = endpoints.get(category);
            if (categoryEndpoints.containsKey(endpointName)) {
                return categoryEndpoints.get(endpointName).getCost();
            }
        }
        // 未命中任何配置，默认不扣费
        return defaultCost; 
    }
    
    @Data
    public static class ApiEndpointConfig {
        private String path;
        private String method;
        private double cost;
        private String description;
    }
}
