package com.lingobot.core.user.redemption.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRedemptionCodeRequest {
    
    @NotNull(message = "点数不能为空")
    @Min(value = 1, message = "点数必须大于0")
    private Integer points;
    
    @Min(value = 1, message = "过期时间必须大于0秒")
    private Long expiresInSeconds;
    
    @Min(value = 1, message = "最大使用次数必须大于0")
    private Integer maxUsages;
}
