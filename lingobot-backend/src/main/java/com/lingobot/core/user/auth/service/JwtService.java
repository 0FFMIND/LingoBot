package com.lingobot.core.user.auth.service;

import com.lingobot.infrastructure.common.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT 令牌服务。
 *
 * 负责 JWT Token 的生成、解析和验证工作。
 * 使用配置的密钥对 Token 进行签名，Token 中包含用户 ID 和用户名等自定义声明。
 * Token 过期时间由 JwtProperties 配置决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {
    
    // JWT 配置属性，包含密钥和过期时间
    private final JwtProperties jwtProperties;
    
    // 获取签名密钥：将配置中的密钥字符串转换为 HMAC-SHA 密钥
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    // 生成 JWT Token，包含用户 ID 和用户名作为自定义声明
    public String generateToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return createToken(claims, username);
    }
    
    // 创建 JWT Token：设置声明、主题、签发时间、过期时间并签名
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }
    
    // 从 Token 中提取用户名（主题）
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    // 从 Token 中提取用户 ID（自定义声明）
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }
    
    // 从 Token 中提取过期时间
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    // 通用方法：从 Token 中提取指定的声明
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    // 解析 Token 获取所有声明（Payload 部分）
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    // 检查 Token 是否已过期
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    // 验证 Token 是否有效：检查签名和过期时间
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}
