package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户头像请求 DTO。
 *
 * 用于接收用户更新头像的请求参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvatarRequest {
    // 头像 URL 或 Base64 编码
    private String avatar;
}