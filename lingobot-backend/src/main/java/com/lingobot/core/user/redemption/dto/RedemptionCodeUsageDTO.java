package com.lingobot.core.user.redemption.dto;

import com.lingobot.core.user.redemption.entity.RedemptionCodeUsage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeUsageDTO {
    
    private Long id;
    private Long redemptionCodeId;
    private Long userId;
    private String username;
    private LocalDateTime usedAt;
    
    public static RedemptionCodeUsageDTO fromEntity(RedemptionCodeUsage entity) {
        return RedemptionCodeUsageDTO.builder()
                .id(entity.getId())
                .redemptionCodeId(entity.getRedemptionCode() != null ? entity.getRedemptionCode().getId() : null)
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .username(entity.getUser() != null ? entity.getUser().getUsername() : null)
                .usedAt(entity.getUsedAt())
                .build();
    }
}
