package com.lingobot.redemption.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemCodeRequest {
    
    @NotBlank(message = "兑换码不能为�?)
    private String code;
}
