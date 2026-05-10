package com.lingobot.core.user.redemption.service;

import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;

import java.util.List;

/**
 * 兑换码服务接口。
 *
 * 定义兑换码模块的核心业务操作：
 * - 管理员操作：创建、查询列表、查询详情、删除
 * - 用户操作：使用兑换码
 *
 * 所有操作都返回 DTO 或抛出 IllegalArgumentException，
 * 由 Controller 层统一处理异常并包装为 ApiResponse。
 */
public interface RedemptionCodeService {
    
    // 管理员创建兑换码，指定点数、创建者ID、过期秒数（可选）
    RedemptionCodeDTO createCode(Integer points, Long creatorId, Long expiresInSeconds);
    
    // 用户使用兑换码，使用 Redis 分布式锁防止重复兑换
    RedemptionCodeDTO redeemCode(String code, Long userId);
    
    // 管理员查询所有兑换码列表（含关联信息）
    List<RedemptionCodeDTO> getAllCodes();
    
    // 管理员按ID查询单个兑换码详情（含关联信息）
    RedemptionCodeDTO getCodeById(Long id);
    
    // 管理员删除未使用的兑换码，已使用的不允许删除
    void deleteCode(Long id);
}
