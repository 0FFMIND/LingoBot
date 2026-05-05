package com.lingobot.redemption.service;

import com.lingobot.redemption.dto.RedemptionCodeDTO;

import java.util.List;

public interface RedemptionCodeService {
    
    RedemptionCodeDTO createCode(Integer points, Long creatorId);
    
    RedemptionCodeDTO redeemCode(String code, Long userId);
    
    List<RedemptionCodeDTO> getAllCodes();
    
    RedemptionCodeDTO getCodeById(Long id);
    
    Integer getUserBalance(Long userId);
}
