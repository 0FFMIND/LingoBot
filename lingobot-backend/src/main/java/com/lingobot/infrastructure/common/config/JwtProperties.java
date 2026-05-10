package com.lingobot.infrastructure.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * JWT 配置属性类，从配置文件读取 jwt.* 前缀的配置项。
 * 用于配置 JWT 签名密钥和 token 过期时间。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // JWT 签名密钥，用于 token 的签发和验证
    private String secret;
    
    // Token 过期时间，默认 24 小时，单位毫秒
    private long expiration = 86400000; 
}
