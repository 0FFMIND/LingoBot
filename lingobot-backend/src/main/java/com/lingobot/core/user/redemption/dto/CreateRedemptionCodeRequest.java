package com.lingobot.core.user.redemption.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建兑换码请求 DTO。
 *
 * 用于接收管理员创建兑换码的请求参数，
 * 点数为必填项，过期时间和最大使用次数为可选项。
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
    
    // 过期时间（秒），从创建时间开始计算，null 表示永不过期
    @Min(value = 1, message = "过期时间必须大于0秒")
    private Long expiresInSeconds;
    
    // 最大使用次数，null 表示无限制
    @Min(value = 1, message = "最大使用次数必须大于0")
    private Integer maxUsages;
}
