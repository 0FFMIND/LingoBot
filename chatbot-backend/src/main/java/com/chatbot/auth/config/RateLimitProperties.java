package com.lingobot.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {
    
    private int maxLoginAttempts = 5;
    
    private int maxRegisterAttempts = 3;
    
    private Duration lockDuration = Duration.ofMinutes(15);
    
    private Duration windowDuration = Duration.ofMinutes(5);
    
    private int maxIpRequestsPerMinute = 30;
    
    private int maxUserRequestsPerMinute = 10;
    
    private Duration accountLockDuration = Duration.ofHours(1);
    
    private int maxFailedAttemptsBeforeLock = 8;
    
    private long delayOnFailureMs = 1000;
    
    private Duration ipLockDuration = Duration.ofDays(1);
    
    private int consecutiveFailuresForPermanentLock = 8;
    
    private Map<Integer, Duration> incrementalLockDurations = new HashMap<>();
    
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
    
    public Duration getIpLockDurationForAttempt(int attemptCount) {
        return getLockDurationForAttempt(attemptCount);
    }
}
