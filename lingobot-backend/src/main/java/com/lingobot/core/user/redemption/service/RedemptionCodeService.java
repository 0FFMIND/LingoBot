package com.lingobot.core.user.redemption.service;

import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;

import java.util.List;

public interface RedemptionCodeService {
    
    RedemptionCodeDTO createCode(Integer points, Long creatorId);
    
    RedemptionCodeDTO redeemCode(String code, Long userId);
    
    List<RedemptionCodeDTO> getAllCodes();
    
    RedemptionCodeDTO getCodeById(Long id);
    
    Integer getUserBalance(Long userId);
    
    void deleteCode(Long id);
}
