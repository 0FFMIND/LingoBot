package com.lingobot.core.user.redemption.dto;

import com.lingobot.core.user.redemption.entity.RedemptionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 兑换码传输对象。
 *
 * 用于将 RedemptionCode 实体转换为 API 响应格式，
 * 包含兑换码的完整信息、使用统计和使用记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeDTO {
    
    // 兑换码 ID
    private Long id;
    // 兑换码字符串
    private String code;
    // 可兑换的点数
    private Integer points;
    // 最大使用次数
    private Integer maxUsages;
    // 当前已使用次数
    private Integer usageCount;
    // 是否已达到最大使用次数
    private Boolean isFullyUsed;
    // 创建者用户 ID
    private Long createdByUserId;
    // 创建者用户名
    private String createdByUsername;
    // 创建时间
    private LocalDateTime createdAt;
    // 过期时间
    private LocalDateTime expiresAt;
    // 是否已过期
    private Boolean isExpired;
    // 是否已被使用（预留字段）
    private Boolean isUsed;
    // 使用记录列表
    private List<RedemptionCodeUsageDTO> usages;
    
    // 将 RedemptionCode 实体转换为 RedemptionCodeDTO
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
