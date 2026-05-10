package com.lingobot.core.user.redemption.dto;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.redemption.entity.RedemptionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 兑换码数据传输对象。
 *
 * 用于在各层之间传递兑换码信息，将实体对象转换为前端友好的格式。
 * 主要转换内容：
 * - User 对象拆解为 userId 和 username，避免直接暴露实体关系
 * - 添加 isExpired 字段，由实体的 isExpired() 方法计算得出
 *
 * fromEntity 静态方法：将 RedemptionCode 实体转换为 DTO，
 * 处理所有懒加载关联的 null 情况。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeDTO {
    
    // 兑换码ID
    private Long id;
    // 兑换码字符串
    private String code;
    // 可兑换点数
    private Integer points;
    // 是否已使用
    private Boolean isUsed;
    // 使用者ID
    private Long usedByUserId;
    // 使用者用户名
    private String usedByUsername;
    // 使用时间
    private LocalDateTime usedAt;
    // 创建者ID
    private Long createdByUserId;
    // 创建者用户名
    private String createdByUsername;
    // 创建时间
    private LocalDateTime createdAt;
    // 过期时间
    private LocalDateTime expiresAt;
    // 是否已过期（由 isExpired() 方法计算）
    private Boolean isExpired;
    
    // 实体转DTO：处理懒加载关系的null情况，添加计算字段isExpired
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
                .expiresAt(entity.getExpiresAt())
                .isExpired(entity.isExpired())
                .build();
    }
}
