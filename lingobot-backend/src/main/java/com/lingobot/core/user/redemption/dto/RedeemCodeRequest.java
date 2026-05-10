package com.lingobot.core.user.redemption.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 兑换码使用请求对象。
 *
 * 由用户在使用兑换码时提交，仅包含 code 字段。
 * code 会在 Service 层自动 trim 处理，去除首尾空格。
 *
 * 使用 @NotBlank 校验确保兑换码不为空且不全为空白字符。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemCodeRequest {
    
    // 用户输入的兑换码字符串，不能为空
    @NotBlank(message = "兑换码不能为空")
    private String code;
}
