package com.lingobot.core.user.redemption.dto;

import com.lingobot.core.user.redemption.entity.RedemptionCodeUsage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 兑换码使用记录传输对象。
 *
 * 用于将 RedemptionCodeUsage 实体转换为 API 响应格式，
 * 隐藏不必要的关联信息，仅暴露使用记录的核心字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeUsageDTO {
    
    // 使用记录 ID
    private Long id;
    // 兑换码 ID
    private Long redemptionCodeId;
    // 使用用户 ID
    private Long userId;
    // 使用用户名
    private String username;
    // 使用时间
    private LocalDateTime usedAt;
    
    // 将 RedemptionCodeUsage 实体转换为 RedemptionCodeUsageDTO
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
