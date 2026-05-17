package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送邮箱验证码请求 DTO。
 *
 * 用于接收发送注册验证码的邮箱地址。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendVerificationCodeRequest {
    // 接收验证码的邮箱地址
    private String email;
}