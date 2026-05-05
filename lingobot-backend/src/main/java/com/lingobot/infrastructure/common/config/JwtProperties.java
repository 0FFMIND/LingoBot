package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性类
 * 用于配置JWT令牌的生成和验证参数
 * 配置值通过 application.yml 中的环境变量注入
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    /**
     * JWT签名密钥
     * 用于对JWT令牌进行签名和验证，确保令牌的完整性和真实性     * 配置方式：     * - 环境变量：JWT_SECRET
     * - 默认值在 application.yml 中定义，格式：{JWT_SECRET:默认值}
     */
    private String secret;
    
    /**
     * JWT令牌过期时间（毫秒）
     * 配置方式：     * - 环境变量：JWT_EXPIRATION
     * - 默认值：86400000毫秒 = 24小时 = 1天     */
    private long expiration = 86400000;
}
