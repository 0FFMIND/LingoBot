package com.lingobot.redemption.dto;

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
}
