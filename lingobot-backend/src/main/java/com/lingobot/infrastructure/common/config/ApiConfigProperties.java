package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * API 端点配置属性类，从配置文件读取 api.* 前缀的配置项。
 * 用于管理各个 API 端点的调用成本，支持按 category 和 endpointName 分级配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "api")
public class ApiConfigProperties {

    // 默认扣费金额，当端点未配置 cost 时使用，默认不扣费
    private double defaultCost = 0.0;
    
    // API 端点配置，按 category -> endpointName 层级存储
    private Map<String, Map<String, ApiEndpointConfig>> endpoints = new HashMap<>();

    // 查询指定端点的扣费金额，category 如 "vocabulary"，endpointName 如 "create-card"
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
    
    /**
     * 单个 API 端点的配置信息。
     * 包含路径、HTTP 方法、扣费金额和描述信息。
     */
    @Data
    public static class ApiEndpointConfig {
        // 端点路径
        private String path;
        // HTTP 请求方法
        private String method;
        // 调用该端点的扣费金额
        private double cost;
        // 端点功能描述
        private String description;
    }
}
