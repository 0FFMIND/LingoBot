package com.lingobot.redemption.dto;

import com.lingobot.auth.entity.User;
import com.lingobot.redemption.entity.RedemptionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeDTO {
    
    private Long id;
    private String code;
    private Integer points;
    private Boolean isUsed;
    private Long usedByUserId;
    private String usedByUsername;
    private LocalDateTime usedAt;
    private Long createdByUserId;
    private String createdByUsername;
    private LocalDateTime createdAt;
    
    public static RedemptionCodeDTO fromEntity(RedemptionCode entity) {
        return RedemptionCodeDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .points(entity.getPoints())
                .isUsed(entity.getIsUsed())
                .usedByUserId(entity.getUsedBy() != null ? entity.getUsedBy().getId() : null)
                .usedByUsername(entity.getUsedBy() != null ? entity.getUsedBy().getUsername() : null)
                .usedAt(entity.getUsedAt())
                .createdByUserId(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdByUsername(entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
