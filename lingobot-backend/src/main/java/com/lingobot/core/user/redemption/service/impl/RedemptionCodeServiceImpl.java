package com.lingobot.core.user.redemption.service.impl;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;
import com.lingobot.core.user.redemption.dto.RedemptionCodeUsageDTO;
import com.lingobot.core.user.redemption.entity.RedemptionCode;
import com.lingobot.core.user.redemption.entity.RedemptionCodeUsage;
import com.lingobot.core.user.redemption.repository.RedemptionCodeRepository;
import com.lingobot.core.user.redemption.repository.RedemptionCodeUsageRepository;
import com.lingobot.core.user.redemption.service.RedemptionCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final RedemptionCodeUsageRepository redemptionCodeUsageRepository;
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
    public RedemptionCodeDTO createCode(Integer points, Long creatorId, Long expiresInSeconds, Integer maxUsages) {
        if (points == null || points <= 0) {
            throw ChatException.badRequest("兑换码点数必须大于0");
        }
        
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("创建者不存在"));
        
        String code = generateUniqueCode();
        
        RedemptionCode.RedemptionCodeBuilder builder = RedemptionCode.builder()
                .code(code)
                .points(points)
                .createdBy(creator);
        
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            builder.expiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
        }
        
        if (maxUsages != null && maxUsages > 0) {
            builder.maxUsages(maxUsages);
        }
        
        RedemptionCode redemptionCode = builder.build();
        
        RedemptionCode saved = redemptionCodeRepository.save(redemptionCode);
        log.info("管理员 {} 生成了兑换码 {}, 点数: {}, 最大使用次数: {}, 过期时间: {}", 
                creator.getUsername(), code, points, 
                maxUsages != null ? maxUsages : "无限制",
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
            RedemptionCode redemptionCode = redemptionCodeRepository.findByCodeWithDetails(trimmedCode)
                    .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
            
            if (redemptionCode.isExpired()) {
                throw new IllegalArgumentException("兑换码已过期");
            }
            
            if (redemptionCode.isFullyUsed()) {
                throw new IllegalArgumentException("兑换码已达到最大使用次数");
            }
            
            if (redemptionCode.hasUserUsed(userId)) {
                throw new IllegalArgumentException("您已使用过该兑换码");
            }
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            BigDecimal newBalance = balanceService.addBalance(userId, BigDecimal.valueOf(redemptionCode.getPoints()));
            
            RedemptionCodeUsage usage = RedemptionCodeUsage.builder()
                    .redemptionCode(redemptionCode)
                    .user(user)
                    .usedAt(LocalDateTime.now())
                    .build();
            
            redemptionCodeUsageRepository.save(usage);
            
            log.info("用户 {} 使用兑换码 {}, 获得 {} 点, 新余额: {}", user.getUsername(), trimmedCode, redemptionCode.getPoints(), newBalance);
            
            RedemptionCode updatedCode = redemptionCodeRepository.findByCodeWithDetails(trimmedCode)
                    .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
            
            return RedemptionCodeDTO.fromEntity(updatedCode);
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
    @Transactional
    public void deleteCode(Long id) {
        RedemptionCode code = redemptionCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
        
        long usageCount = redemptionCodeUsageRepository.countByRedemptionCodeId(id);
        if (usageCount > 0) {
            throw new IllegalArgumentException("已有用户使用过的兑换码无法删除");
        }
        
        redemptionCodeRepository.delete(code);
        log.info("兑换码 {} 已被删除", code.getCode());
    }
    
    @Override
    public List<RedemptionCodeUsageDTO> getCodeUsages(Long codeId) {
        List<RedemptionCodeUsage> usages = redemptionCodeUsageRepository.findByRedemptionCodeId(codeId);
        return usages.stream()
                .map(RedemptionCodeUsageDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
