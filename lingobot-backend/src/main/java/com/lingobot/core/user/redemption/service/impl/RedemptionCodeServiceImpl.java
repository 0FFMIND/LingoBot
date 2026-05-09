package com.lingobot.core.user.redemption.service.impl;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.BalanceService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;
import com.lingobot.core.user.redemption.entity.RedemptionCode;
import com.lingobot.core.user.redemption.repository.RedemptionCodeRepository;
import com.lingobot.core.user.redemption.service.RedemptionCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedemptionCodeServiceImpl implements RedemptionCodeService {
    
    private final RedemptionCodeRepository redemptionCodeRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final BalanceService balanceService;
    
    private static final String LOCK_PREFIX = "redemption:lock:";
    private static final long LOCK_EXPIRE_SECONDS = 10;
    
    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = "sk-" + UUID.randomUUID().toString().replace("-", "");
            attempts++;
            if (attempts > 100) {
                throw new RuntimeException("生成唯一兑换码失败，请稍后重试");
            }
        } while (redemptionCodeRepository.existsByCode(code));
        return code;
    }
    
    @Override
    @Transactional
    public RedemptionCodeDTO createCode(Integer points, Long creatorId, Long expiresInSeconds) {
        if (points == null || points <= 0) {
            throw ChatException.badRequest("兑换码点数必须大于0");
        }
        
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("创建者不存在"));
        
        String code = generateUniqueCode();
        
        RedemptionCode.RedemptionCodeBuilder builder = RedemptionCode.builder()
                .code(code)
                .points(points)
                .isUsed(false)
                .createdBy(creator);
        
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            builder.expiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
        }
        
        RedemptionCode redemptionCode = builder.build();
        
        RedemptionCode saved = redemptionCodeRepository.save(redemptionCode);
        log.info("管理员 {} 生成了兑换码 {}, 点数: {}, 过期时间: {}", 
                creator.getUsername(), code, points, 
                expiresInSeconds != null ? expiresInSeconds + "秒后" : "永不过期");
        
        return RedemptionCodeDTO.fromEntity(saved);
    }
    
    @Override
    @Transactional
    public RedemptionCodeDTO redeemCode(String code, Long userId) {
        String trimmedCode = code.trim();
        String lockKey = LOCK_PREFIX + trimmedCode;
        
        Boolean lockAcquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("兑换码 {} 正在处理中，拒绝重复请求", trimmedCode);
            throw new IllegalStateException("兑换码正在处理中，请稍后重试");
        }
        
        try {
            RedemptionCode redemptionCode = redemptionCodeRepository.findByCode(trimmedCode)
                    .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
            
            if (redemptionCode.getIsUsed()) {
                throw new IllegalArgumentException("兑换码已被使用");
            }
            
            if (redemptionCode.isExpired()) {
                throw new IllegalArgumentException("兑换码已过期");
            }
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            double newBalance = balanceService.addBalance(userId, redemptionCode.getPoints().doubleValue());
            
            redemptionCode.setIsUsed(true);
            redemptionCode.setUsedBy(user);
            redemptionCode.setUsedAt(LocalDateTime.now());
            
            RedemptionCode saved = redemptionCodeRepository.save(redemptionCode);
            
            log.info("用户 {} 使用兑换码 {}, 获得 {} 点, 新余额: {}", user.getUsername(), trimmedCode, redemptionCode.getPoints(), newBalance);
            
            return RedemptionCodeDTO.fromEntity(saved);
        } finally {
            stringRedisTemplate.delete(lockKey);
            log.debug("释放兑换码 {} 的Redis锁", trimmedCode);
        }
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
    public Double getUserBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return user.getBalance() != null ? user.getBalance() : 0.0;
    }
    
    @Override
    @Transactional
    public void deleteCode(Long id) {
        RedemptionCode code = redemptionCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
        
        if (code.getIsUsed()) {
            throw new IllegalArgumentException("已使用的兑换码无法删除");
        }
        
        redemptionCodeRepository.delete(code);
        log.info("兑换码 {} 已被删除", code.getCode());
    }
}
