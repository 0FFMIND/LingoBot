package com.lingobot.core.user.redemption.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建兑换码请求对象。
 *
 * 由管理员在创建兑换码时提交，包含：
 * - points：兑换码可兑换的点数（必填，必须>0）
 * - expiresInSeconds：过期秒数（可选，不传或null表示永不过期）
 *
 * 使用 jakarta.validation 注解进行参数校验，
 * 在 Controller 层配合 @Valid 注解自动触发校验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRedemptionCodeRequest {
    
    // 兑换码可兑换的点数，必填且必须大于0
    @NotNull(message = "点数不能为空")
    @Min(value = 1, message = "点数必须大于0")
    private Integer points;
    
    // 过期秒数，可选，不传或null表示永不过期
    @Min(value = 1, message = "过期时间必须大于0秒")
    private Long expiresInSeconds;
}
