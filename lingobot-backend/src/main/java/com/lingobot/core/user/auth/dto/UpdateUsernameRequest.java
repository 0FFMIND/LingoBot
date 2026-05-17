package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户名请求 DTO。
 *
 * 用于接收用户更新用户名的请求参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUsernameRequest {
    // 新的用户名
    private String username;
}
