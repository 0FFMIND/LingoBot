package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送登录验证码请求 DTO。
 *
 * 用于接收发送登录验证码的请求参数，
 * 需要先验证邮箱和密码是否正确，再发送验证码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendLoginCodeRequest {
    // 用户邮箱
    private String email;
    // 用户密码（用于验证身份）
    private String password;
}
