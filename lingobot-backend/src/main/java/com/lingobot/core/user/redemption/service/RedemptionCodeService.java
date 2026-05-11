package com.lingobot.core.user.redemption.service;

import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;
import com.lingobot.core.user.redemption.dto.RedemptionCodeUsageDTO;

import java.util.List;

public interface RedemptionCodeService {
    
    RedemptionCodeDTO createCode(Integer points, Long creatorId, Long expiresInSeconds, Integer maxUsages);
    
    RedemptionCodeDTO redeemCode(String code, Long userId);
    
    List<RedemptionCodeDTO> getAllCodes();
    
    RedemptionCodeDTO getCodeById(Long id);
    
    void deleteCode(Long id);
    
    List<RedemptionCodeUsageDTO> getCodeUsages(Long codeId);
}
