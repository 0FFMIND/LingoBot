package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 认证响应 DTO。
 *
 * 用于返回用户登录/注册成功后的认证信息，
 * 包含 JWT Token、用户基本信息和余额信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    // JWT 访问令牌
    private String token;
    // 用户名
    private String username;
    // 用户邮箱
    private String email;
    // 用户 ID
    private Long userId;
    // 用户角色
    private String role;
    // 用户头像
    private String avatar;
    // 可用余额
    private BigDecimal balance;
    // 冻结余额
    private BigDecimal frozenBalance;
}
