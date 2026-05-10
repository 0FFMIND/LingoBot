package com.lingobot.core.user.auth.service;

import com.lingobot.infrastructure.common.config.RateLimitProperties;
import com.lingobot.core.user.auth.entity.LoginAttempt;
import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.LoginAttemptRepository;
import com.lingobot.core.user.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    
    private static final String IP_BLOCK_PREFIX = "rate_limit:ip:";
    private static final String USER_BLOCK_PREFIX = "rate_limit:user:";
    private static final String LOGIN_FAIL_IP_PREFIX = "login_fail:ip:";
    private static final String LOGIN_FAIL_USER_PREFIX = "login_fail:user:";
    private static final String LOGIN_FAIL_USERNAME_PREFIX = "login_fail:username:";
    
    private static final String CUMULATIVE_FAIL_IP_PREFIX = "cumulative_fail:ip:";
    private static final String CUMULATIVE_FAIL_USERNAME_PREFIX = "cumulative_fail:username:";
    private static final String CUMULATIVE_FAIL_USER_PREFIX = "cumulative_fail:user:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
    private final RateLimitProperties rateLimitProperties;
    
    private boolean redisAvailable = true;
    
    public void recordLoginAttempt(String ipAddress, String username, 
                                    boolean success, String failureReason) {
        LoginAttempt attempt = LoginAttempt.builder()
                .ipAddress(ipAddress)
                .username(username)
                .success(success)
                .failureReason(failureReason)
                .build();
        
        Optional<User> userOpt = Optional.empty();
        if (username != null && !username.isEmpty()) {
            userOpt = userRepository.findByUsername(username);
            userOpt.ifPresent(attempt::setUser);
        }
        
        loginAttemptRepository.save(attempt);
        
        if (success) {
            resetLoginFailures(ipAddress, username, userOpt.orElse(null));
        } else {
            incrementLoginFailures(ipAddress, username, userOpt.orElse(null));
            log.warn("登录失败 - IP: {}, 用户名: {}, 原因: {}", ipAddress, username, failureReason);
        }
    }
    
    private void incrementLoginFailures(String ipAddress, String username, User user) {
        if (!redisAvailable) {
            log.debug("Redis不可用，跳过Redis计数");
            return;
        }
        
        try {
            Duration window = rateLimitProperties.getWindowDuration();
            long windowSeconds = window.getSeconds();
            
            String ipKey = LOGIN_FAIL_IP_PREFIX + ipAddress;
            String usernameKey = username != null ? LOGIN_FAIL_USERNAME_PREFIX + username : null;
            
            String cumulativeIpKey = CUMULATIVE_FAIL_IP_PREFIX + ipAddress;
            String cumulativeUsernameKey = username != null ? CUMULATIVE_FAIL_USERNAME_PREFIX + username : null;
            
            Long ipFailCount = redisTemplate.opsForValue()
                    .increment(ipKey, 1);
            redisTemplate.expire(ipKey, windowSeconds, TimeUnit.SECONDS);
            
            Long cumulativeIpFailCount = redisTemplate.opsForValue()
                    .increment(cumulativeIpKey, 1);
            
            Long usernameFailCount = null;
            Long cumulativeUsernameFailCount = null;
            if (usernameKey != null) {
                usernameFailCount = redisTemplate.opsForValue()
                        .increment(usernameKey, 1);
                redisTemplate.expire(usernameKey, windowSeconds, TimeUnit.SECONDS);
                
                cumulativeUsernameFailCount = redisTemplate.opsForValue()
                        .increment(cumulativeUsernameKey, 1);
            }
            
            if (user != null) {
                String userKey = LOGIN_FAIL_USER_PREFIX + user.getId();
                String cumulativeUserKey = CUMULATIVE_FAIL_USER_PREFIX + user.getId();
                
                Long userFailCount = redisTemplate.opsForValue()
                        .increment(userKey, 1);
                redisTemplate.expire(userKey, windowSeconds, TimeUnit.SECONDS);
                
                Long cumulativeUserFailCount = redisTemplate.opsForValue()
                        .increment(cumulativeUserKey, 1);
                
                if (userFailCount != null && userFailCount >= 5) {
                    lockUserWithIncrementalDuration(user, cumulativeUserFailCount != null ? cumulativeUserFailCount.intValue() : 5);
                }
            }
            
            int maxAttempts = rateLimitProperties.getMaxLoginAttempts();
            if (ipFailCount != null && ipFailCount >= maxAttempts) {
                blockIpWithIncrementalDuration(ipAddress, cumulativeIpFailCount != null ? cumulativeIpFailCount.intValue() : 5);
            }
            
            log.debug("登录失败计数 - IP: {} (当前:{}, 累计:{}), 用户名: {} (当前:{}, 累计:{})",
                      ipAddress, ipFailCount, cumulativeIpFailCount,
                      username, usernameFailCount, cumulativeUsernameFailCount);
                      
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败，暂时禁用Redis速率限制: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    private void resetLoginFailures(String ipAddress, String username, User user) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            redisTemplate.delete(LOGIN_FAIL_IP_PREFIX + ipAddress);
            redisTemplate.delete(CUMULATIVE_FAIL_IP_PREFIX + ipAddress);
            
            if (username != null) {
                redisTemplate.delete(LOGIN_FAIL_USERNAME_PREFIX + username);
                redisTemplate.delete(CUMULATIVE_FAIL_USERNAME_PREFIX + username);
            }
            
            if (user != null) {
                redisTemplate.delete(LOGIN_FAIL_USER_PREFIX + user.getId());
                redisTemplate.delete(CUMULATIVE_FAIL_USER_PREFIX + user.getId());
                redisTemplate.delete(USER_BLOCK_PREFIX + user.getId());
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    public boolean isIpBlocked(String ipAddress) {
        if (!redisAvailable) {
            return false;
        }
        
        try {
            String key = IP_BLOCK_PREFIX + ipAddress;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
            return false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isUserBlocked(Long userId) {
        if (userId == null || !redisAvailable) {
            return false;
        }
        
        try {
            String key = USER_BLOCK_PREFIX + userId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
            return false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
            return false;
        }
    }
    
    private void lockUserWithIncrementalDuration(User user, int cumulativeFailCount) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            String key = USER_BLOCK_PREFIX + user.getId();
            Duration duration = rateLimitProperties.getLockDurationForAttempt(cumulativeFailCount);
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), 
                                             duration.getSeconds(), TimeUnit.SECONDS);
            log.warn("用户账户被临时锁定: {}, 累计失败次数: {}, 持续时间: {}秒",
                     user.getUsername(), cumulativeFailCount, duration.getSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    private void blockIpWithIncrementalDuration(String ipAddress, int cumulativeFailCount) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            String key = IP_BLOCK_PREFIX + ipAddress;
            Duration duration = rateLimitProperties.getIpLockDurationForAttempt(cumulativeFailCount);
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), 
                                             duration.getSeconds(), TimeUnit.SECONDS);
            log.warn("IP被临时阻止: {}, 累计失败次数: {}, 持续时间: {}秒",
                     ipAddress, cumulativeFailCount, duration.getSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    public void blockIp(String ipAddress) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            String key = IP_BLOCK_PREFIX + ipAddress;
            Duration duration = rateLimitProperties.getLockDuration();
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), 
                                             duration.getSeconds(), TimeUnit.SECONDS);
            log.warn("IP被临时阻止: {}, 持续时间: {}秒", ipAddress, duration.getSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    public void lockUser(User user) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            String key = USER_BLOCK_PREFIX + user.getId();
            Duration duration = rateLimitProperties.getAccountLockDuration();
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), 
                                             duration.getSeconds(), TimeUnit.SECONDS);
            log.warn("用户账户被临时锁定: {}, 持续时间: {}秒",
                     user.getUsername(), duration.getSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    public int getRemainingAttempts(String ipAddress, String username) {
        if (!redisAvailable) {
            return rateLimitProperties.getMaxLoginAttempts();
        }
        
        try {
            String ipKey = LOGIN_FAIL_IP_PREFIX + ipAddress;
            String usernameKey = username != null ? LOGIN_FAIL_USERNAME_PREFIX + username : null;
            
            Number ipCount = (Number) redisTemplate.opsForValue().get(ipKey);
            Number usernameCount = usernameKey != null ? (Number) redisTemplate.opsForValue().get(usernameKey) : null;
            
            long currentCount = 0;
            if (ipCount != null) currentCount = Math.max(currentCount, ipCount.longValue());
            if (usernameCount != null) currentCount = Math.max(currentCount, usernameCount.longValue());
            
            int remaining = rateLimitProperties.getMaxLoginAttempts() - (int) currentCount;
            return Math.max(0, remaining);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
            return rateLimitProperties.getMaxLoginAttempts();
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage(), e);
            return rateLimitProperties.getMaxLoginAttempts();
        }
    }
    
    public LocalDateTime getIpBlockExpiry(String ipAddress) {
        if (!redisAvailable) {
            return null;
        }
        
        try {
            String key = IP_BLOCK_PREFIX + ipAddress;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) return null;
            return LocalDateTime.now().plusSeconds(ttl);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
            return null;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
            return null;
        }
    }
    
    public LocalDateTime getUserBlockExpiry(Long userId) {
        if (userId == null || !redisAvailable) {
            return null;
        }
        
        try {
            String key = USER_BLOCK_PREFIX + userId;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) return null;
            return LocalDateTime.now().plusSeconds(ttl);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
            return null;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
            return null;
        }
    }
    
    public void applyDelayOnFailure() {
        long delayMs = rateLimitProperties.getDelayOnFailureMs();
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public long getRecentDbFailedCountByIp(String ipAddress, Duration window) {
        LocalDateTime since = LocalDateTime.now().minus(window);
        return loginAttemptRepository.countFailedAttemptsByIp(ipAddress, since);
    }
    
    public long getRecentDbFailedCountByUsername(String username, Duration window) {
        LocalDateTime since = LocalDateTime.now().minus(window);
        return loginAttemptRepository.countFailedAttemptsByUsername(username, since);
    }
    
    public void unlockUser(Long userId) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            redisTemplate.delete(USER_BLOCK_PREFIX + userId);
            redisTemplate.delete(LOGIN_FAIL_USER_PREFIX + userId);
            redisTemplate.delete(CUMULATIVE_FAIL_USER_PREFIX + userId);
            log.info("管理员解锁用户: userId={}", userId);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    public void unblockIp(String ipAddress) {
        if (!redisAvailable) {
            return;
        }
        
        try {
            redisTemplate.delete(IP_BLOCK_PREFIX + ipAddress);
            redisTemplate.delete(LOGIN_FAIL_IP_PREFIX + ipAddress);
            redisTemplate.delete(CUMULATIVE_FAIL_IP_PREFIX + ipAddress);
            log.info("管理员解除IP封锁: ip={}", ipAddress);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
    }
    
    public List<BlockedUserInfo> getAllBlockedUsers() {
        List<BlockedUserInfo> blockedUsers = new ArrayList<>();
        
        if (!redisAvailable) {
            return blockedUsers;
        }
        
        try {
            Set<String> keys = redisTemplate.keys(USER_BLOCK_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return blockedUsers;
            }
            
            for (String key : keys) {
                String userIdStr = key.substring(USER_BLOCK_PREFIX.length());
                try {
                    Long userId = Long.parseLong(userIdStr);
                    Optional<User> userOpt = userRepository.findById(userId);
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    
                    if (ttl != null && ttl > 0) {
                        BlockedUserInfo info = new BlockedUserInfo();
                        info.setUserId(userId);
                        info.setUsername(userOpt.map(User::getUsername).orElse("未知用户"));
                        info.setBlockedAt(LocalDateTime.now().minusSeconds(
                            rateLimitProperties.getLockDuration().getSeconds() - ttl
                        ));
                        info.setExpiresAt(LocalDateTime.now().plusSeconds(ttl));
                        info.setRemainingSeconds(ttl);
                        blockedUsers.add(info);
                    }
                } catch (NumberFormatException e) {
                    log.warn("解析用户ID失败: {}", userIdStr);
                }
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
        
        return blockedUsers;
    }
    
    public List<BlockedIpInfo> getAllBlockedIps() {
        List<BlockedIpInfo> blockedIps = new ArrayList<>();
        
        if (!redisAvailable) {
            return blockedIps;
        }
        
        try {
            Set<String> keys = redisTemplate.keys(IP_BLOCK_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return blockedIps;
            }
            
            for (String key : keys) {
                String ipAddress = key.substring(IP_BLOCK_PREFIX.length());
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                
                if (ttl != null && ttl > 0) {
                    BlockedIpInfo info = new BlockedIpInfo();
                    info.setIpAddress(ipAddress);
                    info.setBlockedAt(LocalDateTime.now().minusSeconds(
                        rateLimitProperties.getLockDuration().getSeconds() - ttl
                    ));
                    info.setExpiresAt(LocalDateTime.now().plusSeconds(ttl));
                    info.setRemainingSeconds(ttl);
                    blockedIps.add(info);
                }
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis连接失败: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
        }
        
        return blockedIps;
    }
    
    @lombok.Data
    public static class BlockedUserInfo {
        private Long userId;
        private String username;
        private LocalDateTime blockedAt;
        private LocalDateTime expiresAt;
        private Long remainingSeconds;
    }
    
    @lombok.Data
    public static class BlockedIpInfo {
        private String ipAddress;
        private LocalDateTime blockedAt;
        private LocalDateTime expiresAt;
        private Long remainingSeconds;
    }
}
