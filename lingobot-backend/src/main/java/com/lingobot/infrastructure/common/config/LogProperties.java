package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "log")
public class LogProperties {

    private int maxHistorySize;
}
