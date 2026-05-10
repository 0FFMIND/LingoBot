package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置属性类，从配置文件读取 security.rate-limit.* 前缀的配置项。
 * 用于配置登录、注册和接口请求的频率限制，防止暴力破解和恶意请求。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {
    
    // 最大登录尝试次数，超过后触发锁定
    private int maxLoginAttempts = 4;
    
    // 最大注册尝试次数，超过后触发锁定
    private int maxRegisterAttempts = 4;
    
    // 默认锁定时长
    private Duration lockDuration = Duration.ofMinutes(15);
    
    // 限流窗口时长，在此时间窗口内统计请求次数
    private Duration windowDuration = Duration.ofMinutes(5);
    
    // 每个 IP 每分钟最大请求数
    private int maxIpRequestsPerMinute = 30;
    
    // 每个用户每分钟最大请求数
    private int maxUserRequestsPerMinute = 10;
    
    // 账户锁定时长
    private Duration accountLockDuration = Duration.ofHours(1);
    
    // 触发账户锁定前的最大失败尝试次数
    private int maxFailedAttemptsBeforeLock = 8;
    
    // 失败时的延迟毫秒数，用于增加暴力破解的成本
    private long delayOnFailureMs = 1000;
    
    // IP 锁定时长
    private Duration ipLockDuration = Duration.ofDays(1);
    
    // 触发永久锁定的连续失败次数
    private int consecutiveFailuresForPermanentLock = 8;
    
    // 增量锁定时长映射，根据尝试次数设置不同的锁定时长
    private Map<Integer, Duration> incrementalLockDurations = new HashMap<>();
    
    // 根据登录失败尝试次数返回对应的锁定时长，实现渐进式锁定策略
    public Duration getLockDurationForAttempt(int attemptCount) {
        if (attemptCount <= 5) {
            return Duration.ofMinutes(15);
        } else if (attemptCount == 6) {
            return Duration.ofMinutes(30);
        } else if (attemptCount == 7) {
            return Duration.ofHours(1);
        } else {
            return Duration.ofDays(1);
        }
    }
    
    // 获取 IP 锁定时长，与账户锁定使用相同的渐进式策略
    public Duration getIpLockDurationForAttempt(int attemptCount) {
        return getLockDurationForAttempt(attemptCount);
    }
}
