package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户信息传输对象。
 *
 * 用于返回用户的公开信息，包含基本资料、
 * 创建时间和余额信息，隐藏密码等敏感字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    // 用户 ID
    private Long id;
    // 用户名
    private String username;
    // 用户邮箱
    private String email;
    // 用户角色
    private String role;
    // 用户头像
    private String avatar;
    // 创建时间
    private LocalDateTime createdAt;
    // 可用余额
    private BigDecimal balance;
    // 冻结余额
    private BigDecimal frozenBalance;
}
