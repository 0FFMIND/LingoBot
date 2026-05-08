package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    private long expiration = 86400000; // 默认 24 小时，单位毫秒
}
