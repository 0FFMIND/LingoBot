package com.lingobot.core.user.redemption.service;

import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;
import com.lingobot.core.user.redemption.dto.RedemptionCodeUsageDTO;

import java.util.List;

/**
 * 兑换码服务接口。
 *
 * 提供兑换码的创建、使用、查询、删除等核心业务功能。
 */
public interface RedemptionCodeService {
    
    // 管理员创建兑换码
    RedemptionCodeDTO createCode(Integer points, Long creatorId, Long expiresInSeconds, Integer maxUsages);
    
    // 用户使用兑换码兑换点数
    RedemptionCodeDTO redeemCode(String code, Long userId);
    
    // 获取所有兑换码列表（含使用详情）
    List<RedemptionCodeDTO> getAllCodes();
    
    // 根据 ID 获取兑换码详情
    RedemptionCodeDTO getCodeById(Long id);
    
    // 删除兑换码（仅可删除未被使用过的兑换码）
    void deleteCode(Long id);
    
    // 获取兑换码的使用记录列表
    List<RedemptionCodeUsageDTO> getCodeUsages(Long codeId);
}
