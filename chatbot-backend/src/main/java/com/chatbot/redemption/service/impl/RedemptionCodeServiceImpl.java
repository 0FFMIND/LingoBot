package com.lingobot.redemption.service.impl;

import com.lingobot.auth.entity.User;
import com.lingobot.auth.repository.UserRepository;
import com.lingobot.redemption.dto.RedemptionCodeDTO;
import com.lingobot.redemption.entity.RedemptionCode;
import com.lingobot.redemption.repository.RedemptionCodeRepository;
import com.lingobot.redemption.service.RedemptionCodeService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedemptionCodeServiceImpl implements RedemptionCodeService {
    
    private final RedemptionCodeRepository redemptionCodeRepository;
    private final UserRepository userRepository;
    
    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = "sk-" + UUID.randomUUID().toString().replace("-", "");
            attempts++;
            if (attempts > 100) {
                throw new RuntimeException("生成唯一兑换码失败，请稍后重�?);
            }
        } while (redemptionCodeRepository.existsByCode(code));
        return code;
    }
    
    @Override
    @Transactional
    public RedemptionCodeDTO createCode(Integer points, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("创建者不存在"));
        
        String code = generateUniqueCode();
        
        RedemptionCode redemptionCode = RedemptionCode.builder()
                .code(code)
                .points(points)
                .isUsed(false)
                .createdBy(creator)
                .build();
        
        RedemptionCode saved = redemptionCodeRepository.save(redemptionCode);
        log.info("管理�?{} 生成了兑换码 {}, 点数: {}", creator.getUsername(), code, points);
        
        return RedemptionCodeDTO.fromEntity(saved);
    }
    
    @Override
    @Transactional
    public RedemptionCodeDTO redeemCode(String code, Long userId) {
        RedemptionCode redemptionCode = redemptionCodeRepository.findByCode(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
        
        if (redemptionCode.getIsUsed()) {
            throw new IllegalArgumentException("兑换码已被使�?);
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存�?));
        
        user.setBalance(user.getBalance() + redemptionCode.getPoints());
        userRepository.save(user);
        
        redemptionCode.setIsUsed(true);
        redemptionCode.setUsedBy(user);
        redemptionCode.setUsedAt(LocalDateTime.now());
        
        RedemptionCode saved = redemptionCodeRepository.save(redemptionCode);
        
        log.info("用户 {} 使用兑换�?{}, 获得 {} �?, user.getUsername(), code, redemptionCode.getPoints());
        
        return RedemptionCodeDTO.fromEntity(saved);
    }
    
    @Override
    public List<RedemptionCodeDTO> getAllCodes() {
        List<RedemptionCode> codes = redemptionCodeRepository.findAllWithDetails();
        return codes.stream()
                .map(RedemptionCodeDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public RedemptionCodeDTO getCodeById(Long id) {
        RedemptionCode code = redemptionCodeRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
        return RedemptionCodeDTO.fromEntity(code);
    }
    
    @Override
    public Integer getUserBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存�?));
        return user.getBalance() != null ? user.getBalance() : 0;
    }
}
