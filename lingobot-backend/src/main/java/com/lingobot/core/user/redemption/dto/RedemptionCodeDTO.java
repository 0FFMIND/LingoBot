package com.lingobot.core.user.redemption.dto;

import com.lingobot.core.user.redemption.entity.RedemptionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeDTO {
    
    private Long id;
    private String code;
    private Integer points;
    private Integer maxUsages;
    private Integer usageCount;
    private Boolean isFullyUsed;
    private Long createdByUserId;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean isExpired;
    private Boolean isUsed;
    private List<RedemptionCodeUsageDTO> usages;
    
    public static RedemptionCodeDTO fromEntity(RedemptionCode entity) {
        List<RedemptionCodeUsageDTO> usagesDTO = entity.getUsages() != null
                ? entity.getUsages().stream()
                        .map(RedemptionCodeUsageDTO::fromEntity)
                        .collect(Collectors.toList())
                : null;
        
        return RedemptionCodeDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .points(entity.getPoints())
                .maxUsages(entity.getMaxUsages())
                .usageCount(entity.getUsageCount())
                .isFullyUsed(entity.isFullyUsed())
                .createdByUserId(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdByUsername(entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : null)
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .isExpired(entity.isExpired())
                .isUsed(entity.getIsUsed())
                .usages(usagesDTO)
                .build();
    }
}
