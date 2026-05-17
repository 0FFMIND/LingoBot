package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱验证码登录请求 DTO。
 *
 * 用于接收用户使用邮箱、密码和验证码登录的请求参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginWithCodeRequest {
    // 用户邮箱
    private String email;
    // 用户密码
    private String password;
    // 邮箱验证码
    private String verificationCode;
}
