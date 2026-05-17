package com.lingobot.core.user.auth.service;

import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;

import java.util.List;

/**
 * 邮箱验证码服务接口。
 *
 * 提供邮箱验证码的发送和验证功能，支持注册验证码和登录验证码两种类型。
 * 包含频率限制机制，防止验证码被滥用。
 */
public interface EmailVerificationService {
    // 发送注册用邮箱验证码
    void sendVerificationCode(SendVerificationCodeRequest request, String clientIp);
    
    // 发送登录用邮箱验证码
    void sendLoginVerificationCode(String email, String clientIp);
    
    // 验证注册验证码是否正确
    boolean verifyCode(String email, String code);
    
    // 验证登录验证码是否正确
    boolean verifyLoginCode(String email, String code);
    
    // 获取所有被限制发送验证码的邮箱列表
    List<LockedEmailInfo> getAllLockedEmails();
    
    // 获取所有被限制发送验证码的 IP 列表
    List<LockedIpInfo> getAllLockedIps();
    
    // 解锁指定邮箱的验证码发送限制
    void unlockEmail(String email);
    
    // 解锁指定 IP 的验证码发送限制
    void unlockEmailCodeIp(String ipAddress);
    
    /**
     * 被锁定邮箱信息 DTO。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class LockedEmailInfo {
        // 邮箱地址
        private String email;
        // 锁定时间
        private String lockedAt;
        // 到期时间
        private String expiresAt;
        // 剩余秒数
        private Long remainingSeconds;
        // 锁定类型（日限制/窗口限制）
        private String lockType;
    }
    
    /**
     * 被锁定 IP 信息 DTO。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class LockedIpInfo {
        // IP 地址
        private String ipAddress;
        // 锁定时间
        private String lockedAt;
        // 到期时间
        private String expiresAt;
        // 剩余秒数
        private Long remainingSeconds;
        // 锁定类型（日限制/窗口限制）
        private String lockType;
    }
}