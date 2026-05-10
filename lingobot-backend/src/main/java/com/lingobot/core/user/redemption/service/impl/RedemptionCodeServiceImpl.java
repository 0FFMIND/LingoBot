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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 兑换码服务实现类。
 *
 * 实现 RedemptionCodeService 接口，包含以下核心逻辑：
 *
 * 1. createCode：创建兑换码，生成唯一的 sk- 格式编码
 *    - 使用 UUID + 循环校验确保唯一性（最多100次重试）
 *    - 支持设置过期时间（秒），不设置则永不过期
 *
 * 2. redeemCode：使用兑换码，采用 Redis 分布式锁防止并发重复兑换
 *    - 先获取锁，超时10秒
 *    - 校验：存在 → 未使用 → 未过期 → 用户存在
 *    - 调用 BalanceService 增加余额
 *    - finally 块确保锁释放
 *
 * 3. 其他方法：查询列表、查询详情、查询余额、删除（仅允许删除未使用的码）
 *
 * 所有修改操作都标注 @Transactional，确保事务一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedemptionCodeServiceImpl implements RedemptionCodeService {
    
    private final RedemptionCodeRepository redemptionCodeRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final BalanceService balanceService;
    
    // Redis 分布式锁前缀，防止重复兑换
    private static final String LOCK_PREFIX = "redemption:lock:";
    // 锁过期时间，防止死锁
    private static final long LOCK_EXPIRE_SECONDS = 10;
    
    // 生成唯一兑换码：sk- + UUID（去横杠），循环校验确保不重复
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
    
    // 管理员创建兑换码：校验参数 → 生成唯一码 → 设置属性（可选过期时间）→ 保存 → 返回DTO
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
    
    // 用户使用兑换码：Redis分布式锁 → 三重校验 → 增加余额 → 标记已使用 → finally释放锁
    @Override
    @Transactional
    public RedemptionCodeDTO redeemCode(String code, Long userId) {
        String trimmedCode = code.trim();
        String lockKey = LOCK_PREFIX + trimmedCode;
        
        // 获取 Redis 分布式锁，使用 setIfAbsent + 过期时间
        Boolean lockAcquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("兑换码 {} 正在处理中，拒绝重复请求", trimmedCode);
            throw new IllegalStateException("兑换码正在处理中，请稍后重试");
        }
        
        try {
            // 校验兑换码存在
            RedemptionCode redemptionCode = redemptionCodeRepository.findByCode(trimmedCode)
                    .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
            
            // 校验未使用
            if (redemptionCode.getIsUsed()) {
                throw new IllegalArgumentException("兑换码已被使用");
            }
            
            // 校验未过期
            if (redemptionCode.isExpired()) {
                throw new IllegalArgumentException("兑换码已过期");
            }
            
            // 校验用户存在
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            // 调用余额服务增加点数
            BigDecimal newBalance = balanceService.addBalance(userId, BigDecimal.valueOf(redemptionCode.getPoints()));
            
            // 标记兑换码已使用
            redemptionCode.setIsUsed(true);
            redemptionCode.setUsedBy(user);
            redemptionCode.setUsedAt(LocalDateTime.now());
            
            RedemptionCode saved = redemptionCodeRepository.save(redemptionCode);
            
            log.info("用户 {} 使用兑换码 {}, 获得 {} 点, 新余额: {}", user.getUsername(), trimmedCode, redemptionCode.getPoints(), newBalance);
            
            return RedemptionCodeDTO.fromEntity(saved);
        } finally {
            // 无论成功失败都释放锁
            stringRedisTemplate.delete(lockKey);
            log.debug("释放兑换码 {} 的Redis锁", trimmedCode);
        }
    }
    
    // 查询所有兑换码，带关联对象，转DTO返回
    @Override
    public List<RedemptionCodeDTO> getAllCodes() {
        List<RedemptionCode> codes = redemptionCodeRepository.findAllWithDetails();
        return codes.stream()
                .map(RedemptionCodeDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    // 按ID查询兑换码，带关联对象，不存在抛异常
    @Override
    public RedemptionCodeDTO getCodeById(Long id) {
        RedemptionCode code = redemptionCodeRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("兑换码不存在"));
        return RedemptionCodeDTO.fromEntity(code);
    }
    
    // 查询用户余额，null时返回BigDecimal.ZERO
    @Override
    public BigDecimal getUserBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
    }
    
    // 删除兑换码：仅允许删除未使用的，已使用的抛异常
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
