package com.lingobot.core.user.auth.service;

import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;

import java.util.List;

public interface EmailVerificationService {
    void sendVerificationCode(SendVerificationCodeRequest request, String clientIp);
    
    void sendLoginVerificationCode(String email, String clientIp);
    
    boolean verifyCode(String email, String code);
    
    boolean verifyLoginCode(String email, String code);
    
    List<LockedEmailInfo> getAllLockedEmails();
    
    List<LockedIpInfo> getAllLockedIps();
    
    void unlockEmail(String email);
    
    void unlockEmailCodeIp(String ipAddress);
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class LockedEmailInfo {
        private String email;
        private String lockedAt;
        private String expiresAt;
        private Long remainingSeconds;
        private String lockType;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class LockedIpInfo {
        private String ipAddress;
        private String lockedAt;
        private String expiresAt;
        private Long remainingSeconds;
        private String lockType;
    }
}