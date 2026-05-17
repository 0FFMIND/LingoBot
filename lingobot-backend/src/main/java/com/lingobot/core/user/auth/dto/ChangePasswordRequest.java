package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改密码请求 DTO。
 *
 * 用于接收用户修改密码的请求参数，需要验证当前密码，
 * 并两次输入新密码进行确认。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    // 当前密码
    private String currentPassword;
    // 新密码
    private String newPassword;
    // 确认新密码
    private String confirmPassword;
}
