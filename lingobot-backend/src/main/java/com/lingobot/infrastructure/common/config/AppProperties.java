package com.lingobot.infrastructure.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String env = "production";

    public boolean isDev() {
        String normalizedEnv = env != null ? env.trim() : "";
        return "dev".equalsIgnoreCase(normalizedEnv) || "development".equalsIgnoreCase(normalizedEnv);
    }
}
