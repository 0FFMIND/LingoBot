package com.lingobot.core.user.auth.service.impl;

import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;
import com.lingobot.core.user.auth.service.EmailVerificationService;
import com.lingobot.infrastructure.common.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 邮箱验证码服务实现类。
 *
 * 实现 EmailVerificationService 接口，提供邮箱验证码的生成、发送和验证功能。
 * 使用 Redis 存储验证码和发送频率限制，防止验证码被滥用。
 * 支持发送频率限制：间隔限制、窗口限制、日限制，多重防护防止恶意请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {
    
    // Redis 字符串模板，用于存储验证码和计数
    private final StringRedisTemplate redisTemplate;
    // 邮件发送器，用于发送验证码邮件
    private final JavaMailSender mailSender;
    
    // 管理员邮箱，从配置文件读取
    @Value("${ADMIN_EMAIL:admin@example.com}")
    private String adminEmail;
    
    // 管理员私人邮箱，管理员登录验证码可发送到此邮箱
    @Value("${ADMIN_PERSONAL_EMAIL:}")
    private String adminPersonalEmail;
    
    // Redis Key 前缀：注册验证码
    private static final String VERIFICATION_CODE_PREFIX = "email:verify:";
    // Redis Key 前缀：登录验证码
    private static final String LOGIN_CODE_PREFIX = "login:verify:";
    // Redis Key 前缀：邮箱日发送次数计数
    private static final String SEND_COUNT_PREFIX = "email:send:count:";
    // Redis Key 前缀：IP 日发送次数计数
    private static final String IP_SEND_COUNT_PREFIX = "ip:send:count:";
    // Redis Key 前缀：邮箱窗口发送次数计数
    private static final String WINDOW_SEND_COUNT_PREFIX = "email:window:count:";
    // Redis Key 前缀：IP 窗口发送次数计数
    private static final String IP_WINDOW_SEND_COUNT_PREFIX = "ip:window:count:";
    // Redis Key 前缀：邮箱窗口锁定
    private static final String WINDOW_LOCK_PREFIX = "email:window:lock:";
    // Redis Key 前缀：IP 窗口锁定
    private static final String IP_WINDOW_LOCK_PREFIX = "ip:window:lock:";
    // Redis Key 前缀：邮箱日锁定
    private static final String LOCK_PREFIX = "email:lock:";
    // Redis Key 前缀：IP 日锁定
    private static final String IP_LOCK_PREFIX = "ip:lock:";
    // Redis Key 前缀：上次发送时间
    private static final String LAST_SEND_PREFIX = "email:lastsend:";
    
    // 验证码有效期（分钟）
    private static final long CODE_EXPIRE_MINUTES = 5;
    // 发送间隔限制（秒）
    private static final long SEND_INTERVAL_SECONDS = 60;
    // 窗口内最大发送次数
    private static final int MAX_SEND_PER_WINDOW = 5;
    // 窗口时长（分钟）
    private static final long WINDOW_DURATION_MINUTES = 10;
    // 每日最大发送次数
    private static final int MAX_SEND_PER_DAY = 15;
    // 日锁定时长（小时）
    private static final long LOCK_DURATION_HOURS = 24;
    
    // 邮箱格式正则表达式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );
    
    // 发送注册用邮箱验证码：检查锁定 → 检查间隔 → 检查频率限制 → 生成并发送验证码
    @Override
    public void sendVerificationCode(SendVerificationCodeRequest request, String clientIp) {
        String email = request.getEmail();
        
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("无效的邮箱地址");
        }
        
        checkLockStatus(email, clientIp);
        
        checkSendInterval(email);
        
        incrementSendCountAndCheckLimit(email, clientIp);
        
        String code = generateVerificationCode();
        
        String key = VERIFICATION_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        try {
            sendEmail(email, code);
            log.info("验证码已发送到邮箱: {}, IP: {}", email, clientIp);
        } catch (MessagingException e) {
            log.error("发送邮件失败: {}", e.getMessage());
            throw BusinessException.badRequest("发送验证码失败，请稍后重试");
        }
        
        updateLastSendTime(email);
        
        log.info("发送验证码到邮箱: {}: {}", email, code);
        log.info("验证码有效期: {}分钟", CODE_EXPIRE_MINUTES);
    }
    
    // 检查邮箱和 IP 的锁定状态（日锁定和窗口锁定）
    private void checkLockStatus(String email, String clientIp) {
        String emailLockKey = LOCK_PREFIX + email;
        Boolean emailLocked = redisTemplate.hasKey(emailLockKey);
        
        if (Boolean.TRUE.equals(emailLocked)) {
            Long ttl = redisTemplate.getExpire(emailLockKey, TimeUnit.HOURS);
            throw new IllegalStateException(
                String.format("该邮箱发送验证码次数过多，已被锁定 %d 小时，请稍后重试", 
                    ttl != null && ttl > 0 ? ttl : LOCK_DURATION_HOURS)
            );
        }
        
        if (clientIp != null) {
            String ipLockKey = IP_LOCK_PREFIX + clientIp;
            Boolean ipLocked = redisTemplate.hasKey(ipLockKey);
            
            if (Boolean.TRUE.equals(ipLocked)) {
                Long ttl = redisTemplate.getExpire(ipLockKey, TimeUnit.HOURS);
                throw new IllegalStateException(
                    String.format("您的IP发送验证码次数过多，已被锁定 %d 小时，请稍后重试", 
                        ttl != null && ttl > 0 ? ttl : LOCK_DURATION_HOURS)
                );
            }
        }
        
        checkWindowLockStatus(email, clientIp);
    }
    
    // 检查邮箱和 IP 的窗口锁定状态
    private void checkWindowLockStatus(String email, String clientIp) {
        String emailWindowLockKey = WINDOW_LOCK_PREFIX + email;
        Boolean emailWindowLocked = redisTemplate.hasKey(emailWindowLockKey);
        
        if (Boolean.TRUE.equals(emailWindowLocked)) {
            Long ttl = redisTemplate.getExpire(emailWindowLockKey, TimeUnit.MINUTES);
            throw new IllegalStateException(
                String.format("验证码发送过于频繁，请 %d 分钟后再试", 
                    ttl != null && ttl > 0 ? ttl : WINDOW_DURATION_MINUTES)
            );
        }
        
        if (clientIp != null) {
            String ipWindowLockKey = IP_WINDOW_LOCK_PREFIX + clientIp;
            Boolean ipWindowLocked = redisTemplate.hasKey(ipWindowLockKey);
            
            if (Boolean.TRUE.equals(ipWindowLocked)) {
                Long ttl = redisTemplate.getExpire(ipWindowLockKey, TimeUnit.MINUTES);
                throw new IllegalStateException(
                    String.format("验证码发送过于频繁，请 %d 分钟后再试", 
                        ttl != null && ttl > 0 ? ttl : WINDOW_DURATION_MINUTES)
                );
            }
        }
    }
    
    // 检查发送间隔，防止发送过于频繁
    private void checkSendInterval(String email) {
        String lastSendKey = LAST_SEND_PREFIX + email;
        String lastSendTimeStr = redisTemplate.opsForValue().get(lastSendKey);
        
        if (lastSendTimeStr != null) {
            long lastSendTime = Long.parseLong(lastSendTimeStr);
            long currentTime = System.currentTimeMillis();
            long elapsedSeconds = (currentTime - lastSendTime) / 1000;
            
            if (elapsedSeconds < SEND_INTERVAL_SECONDS) {
                long remainingSeconds = SEND_INTERVAL_SECONDS - elapsedSeconds;
                throw new IllegalStateException(
                    String.format("验证码发送过于频繁，%d 秒后再试", remainingSeconds)
                );
            }
        }
    }
    
    // 增加发送计数并检查是否超过频率限制（窗口限制和日限制）
    private void incrementSendCountAndCheckLimit(String email, String clientIp) {
        Long emailWindowCount = incrementWindowCount(email, null);
        if (emailWindowCount != null && emailWindowCount > MAX_SEND_PER_WINDOW) {
            lockWindow(email, null);
            throw new IllegalStateException(
                String.format("%d分钟内发送验证码次数过多，请 %d 分钟后再试", 
                    WINDOW_DURATION_MINUTES, WINDOW_DURATION_MINUTES)
            );
        }
        
        if (clientIp != null) {
            Long ipWindowCount = incrementWindowCount(null, clientIp);
            if (ipWindowCount != null && ipWindowCount > MAX_SEND_PER_WINDOW) {
                lockWindow(null, clientIp);
                throw new IllegalStateException(
                    String.format("%d分钟内发送验证码次数过多，请 %d 分钟后再试", 
                        WINDOW_DURATION_MINUTES, WINDOW_DURATION_MINUTES)
                );
            }
        }
        
        Long emailDayCount = incrementDayCount(email, null);
        if (emailDayCount != null && emailDayCount > MAX_SEND_PER_DAY) {
            lockDay(email, null);
            throw new IllegalStateException(
                String.format("今日发送验证码次数已达上限(%d次)，该邮箱已被锁定 %d 小时", 
                    MAX_SEND_PER_DAY, LOCK_DURATION_HOURS)
            );
        }
        
        if (clientIp != null) {
            Long ipDayCount = incrementDayCount(null, clientIp);
            if (ipDayCount != null && ipDayCount > MAX_SEND_PER_DAY) {
                lockDay(null, clientIp);
                throw new IllegalStateException(
                    String.format("今日发送验证码次数已达上限(%d次)，您的IP已被锁定 %d 小时", 
                        MAX_SEND_PER_DAY, LOCK_DURATION_HOURS)
                );
            }
        }
    }
    
    // 增加窗口发送计数，首次计数时设置过期时间
    private Long incrementWindowCount(String email, String clientIp) {
        if (email != null) {
            String windowCountKey = WINDOW_SEND_COUNT_PREFIX + email;
            Long count = redisTemplate.opsForValue().increment(windowCountKey);
            if (count == null || count == 1) {
                redisTemplate.expire(windowCountKey, WINDOW_DURATION_MINUTES, TimeUnit.MINUTES);
            }
            return count;
        } else if (clientIp != null) {
            String windowCountKey = IP_WINDOW_SEND_COUNT_PREFIX + clientIp;
            Long count = redisTemplate.opsForValue().increment(windowCountKey);
            if (count == null || count == 1) {
                redisTemplate.expire(windowCountKey, WINDOW_DURATION_MINUTES, TimeUnit.MINUTES);
            }
            return count;
        }
        return null;
    }
    
    // 增加日发送计数，首次计数时设置过期时间
    private Long incrementDayCount(String email, String clientIp) {
        if (email != null) {
            String dayCountKey = SEND_COUNT_PREFIX + email;
            Long count = redisTemplate.opsForValue().increment(dayCountKey);
            if (count == null || count == 1) {
                redisTemplate.expire(dayCountKey, 1, TimeUnit.DAYS);
            }
            return count;
        } else if (clientIp != null) {
            String dayCountKey = IP_SEND_COUNT_PREFIX + clientIp;
            Long count = redisTemplate.opsForValue().increment(dayCountKey);
            if (count == null || count == 1) {
                redisTemplate.expire(dayCountKey, 1, TimeUnit.DAYS);
            }
            return count;
        }
        return null;
    }
    
    // 锁定邮箱或 IP 一段时间（窗口锁定）
    private void lockWindow(String email, String clientIp) {
        if (email != null) {
            String lockKey = WINDOW_LOCK_PREFIX + email;
            redisTemplate.opsForValue().set(lockKey, "locked", WINDOW_DURATION_MINUTES, TimeUnit.MINUTES);
        }
        if (clientIp != null) {
            String lockKey = IP_WINDOW_LOCK_PREFIX + clientIp;
            redisTemplate.opsForValue().set(lockKey, "locked", WINDOW_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }
    
    // 锁定邮箱或 IP 一天（日限制锁定）
    private void lockDay(String email, String clientIp) {
        if (email != null) {
            String lockKey = LOCK_PREFIX + email;
            redisTemplate.opsForValue().set(lockKey, "locked", LOCK_DURATION_HOURS, TimeUnit.HOURS);
        }
        if (clientIp != null) {
            String lockKey = IP_LOCK_PREFIX + clientIp;
            redisTemplate.opsForValue().set(lockKey, "locked", LOCK_DURATION_HOURS, TimeUnit.HOURS);
        }
    }
    
    // 更新上次发送时间，用于间隔限制检查
    private void updateLastSendTime(String email) {
        String lastSendKey = LAST_SEND_PREFIX + email;
        redisTemplate.opsForValue().set(
            lastSendKey, 
            String.valueOf(System.currentTimeMillis()),
            SEND_INTERVAL_SECONDS + 10,
            TimeUnit.SECONDS
        );
    }
    
    // 验证注册验证码是否正确，验证成功后删除验证码
    @Override
    public boolean verifyCode(String email, String code) {
        if (!isValidEmail(email)) {
            return false;
        }
        
        String key = VERIFICATION_CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);
        
        if (storedCode == null) {
            return false;
        }
        
        boolean isValid = storedCode.equals(code);
        
        if (isValid) {
            redisTemplate.delete(key);
        }
        
        return isValid;
    }
    
    // 发送登录用邮箱验证码：检查锁定 → 检查间隔 → 检查频率限制 → 生成并发送验证码
    @Override
    public void sendLoginVerificationCode(String email, String clientIp) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("无效的邮箱地址");
        }
        
        checkLockStatus(email, clientIp);
        
        checkSendInterval(email);
        
        incrementSendCountAndCheckLimit(email, clientIp);
        
        String code = generateVerificationCode();
        
        String key = LOGIN_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        try {
            String targetEmail = determineTargetEmail(email);
            sendLoginEmail(targetEmail, code);
            log.info("登录验证码已发送到邮箱: {}, IP: {}", targetEmail, clientIp);
        } catch (MessagingException e) {
            log.error("发送登录验证码失败: {}", e.getMessage());
            throw BusinessException.badRequest("发送验证码失败，请稍后重试");
        }
        
        updateLastSendTime(email);
        
        log.info("发送登录验证码到邮箱: {}: {}", email, code);
        log.info("验证码有效期: {}分钟", CODE_EXPIRE_MINUTES);
    }
    
    // 确定验证码实际发送的邮箱：管理员登录时可发送到私人邮箱
    private String determineTargetEmail(String loginEmail) {
        if (adminEmail != null && adminEmail.equalsIgnoreCase(loginEmail)) {
            if (adminPersonalEmail != null && !adminPersonalEmail.isEmpty() && isValidEmail(adminPersonalEmail)) {
                log.info("管理员登录，验证码发送到私人邮箱: {}", adminPersonalEmail);
                return adminPersonalEmail;
            }
        }
        return loginEmail;
    }
    
    // 验证登录验证码是否正确，验证成功后删除验证码
    @Override
    public boolean verifyLoginCode(String email, String code) {
        if (!isValidEmail(email)) {
            return false;
        }
        
        String key = LOGIN_CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);
        
        if (storedCode == null) {
            return false;
        }
        
        boolean isValid = storedCode.equals(code);
        
        if (isValid) {
            redisTemplate.delete(key);
        }
        
        return isValid;
    }
    
    // 发送登录验证码邮件（HTML 格式）
    private void sendLoginEmail(String to, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom("gushisun123@gmail.com");
        helper.setTo(to);
        helper.setSubject("【英语学习助手】登录验证码");
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>登录验证码</title>
            </head>
            <body style="font-family: Arial, sans-serif; max-width: 400px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f5f5f5; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #333; text-align: center;">英语学习助手</h2>
                    <p style="color: #666; line-height: 1.6;">您好！</p>
                    <p style="color: #666; line-height: 1.6;">您正在登录英语学习助手账号，以下是您的登录验证码：</p>
                    <div style="text-align: center; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; color: #4CAF50;">%s</span>
                    </div>
                    <p style="color: #666; line-height: 1.6;">验证码有效期：<strong>5分钟</strong>，请尽快使用！</p>
                    <p style="color: #666; line-height: 1.6;">如果这不是您的操作，请忽略此邮件！</p>
                    <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                    <p style="color: #999; font-size: 12px; text-align: center;">© 2024 英语学习助手</p>
                </div>
            </body>
            </html>
            """, code);
        
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }
    
    // 验证邮箱格式是否合法
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    // 生成 6 位随机数字验证码
    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
    
    // 发送注册验证码邮件（HTML 格式）
    private void sendEmail(String to, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom("gushisun123@gmail.com");
        helper.setTo(to);
        helper.setSubject("【英语学习助手】验证码");
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>验证码</title>
            </head>
            <body style="font-family: Arial, sans-serif; max-width: 400px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f5f5f5; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #333; text-align: center;">英语学习助手</h2>
                    <p style="color: #666; line-height: 1.6;">您好！</p>
                    <p style="color: #666; line-height: 1.6;">您正在注册英语学习助手账号，以下是您的验证码：</p>
                    <div style="text-align: center; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; color: #4CAF50;">%s</span>
                    </div>
                    <p style="color: #666; line-height: 1.6;">验证码有效期：<strong>5分钟</strong>，请尽快使用！</p>
                    <p style="color: #666; line-height: 1.6;">如果这不是您的操作，请忽略此邮件！</p>
                    <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                    <p style="color: #999; font-size: 12px; text-align: center;">© 2024 英语学习助手</p>
                </div>
            </body>
            </html>
            """, code);
        
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }
    
    // 获取所有被限制发送验证码的邮箱列表（包括日锁定和窗口锁定）
    @Override
    public List<EmailVerificationService.LockedEmailInfo> getAllLockedEmails() {
        List<EmailVerificationService.LockedEmailInfo> lockedEmails = new ArrayList<>();
        
        try {
            Set<String> dayLockKeys = redisTemplate.keys(LOCK_PREFIX + "*");
            if (dayLockKeys != null) {
                for (String key : dayLockKeys) {
                    String email = key.substring(LOCK_PREFIX.length());
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl > 0) {
                        lockedEmails.add(buildLockedEmailInfo(email, ttl, "DAY_LOCK"));
                    }
                }
            }
            
            Set<String> windowLockKeys = redisTemplate.keys(WINDOW_LOCK_PREFIX + "*");
            if (windowLockKeys != null) {
                for (String key : windowLockKeys) {
                    String email = key.substring(WINDOW_LOCK_PREFIX.length());
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl > 0) {
                        lockedEmails.add(buildLockedEmailInfo(email, ttl, "WINDOW_LOCK"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取锁定邮箱列表失败: {}", e.getMessage());
        }
        
        return lockedEmails;
    }
    
    // 获取所有被限制发送验证码的 IP 列表（包括日锁定和窗口锁定）
    @Override
    public List<EmailVerificationService.LockedIpInfo> getAllLockedIps() {
        List<EmailVerificationService.LockedIpInfo> lockedIps = new ArrayList<>();
        
        try {
            Set<String> dayLockKeys = redisTemplate.keys(IP_LOCK_PREFIX + "*");
            if (dayLockKeys != null) {
                for (String key : dayLockKeys) {
                    String ipAddress = key.substring(IP_LOCK_PREFIX.length());
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl > 0) {
                        lockedIps.add(buildLockedIpInfo(ipAddress, ttl, "DAY_LOCK"));
                    }
                }
            }
            
            Set<String> windowLockKeys = redisTemplate.keys(IP_WINDOW_LOCK_PREFIX + "*");
            if (windowLockKeys != null) {
                for (String key : windowLockKeys) {
                    String ipAddress = key.substring(IP_WINDOW_LOCK_PREFIX.length());
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl > 0) {
                        lockedIps.add(buildLockedIpInfo(ipAddress, ttl, "WINDOW_LOCK"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取锁定IP列表失败: {}", e.getMessage());
        }
        
        return lockedIps;
    }
    
    // 解锁指定邮箱：清除所有锁定状态和计数
    @Override
    public void unlockEmail(String email) {
        try {
            redisTemplate.delete(LOCK_PREFIX + email);
            redisTemplate.delete(WINDOW_LOCK_PREFIX + email);
            redisTemplate.delete(SEND_COUNT_PREFIX + email);
            redisTemplate.delete(WINDOW_SEND_COUNT_PREFIX + email);
            redisTemplate.delete(LAST_SEND_PREFIX + email);
            log.info("管理员解锁邮箱验证码限制: email={}", email);
        } catch (Exception e) {
            log.error("解锁邮箱失败: {}", e.getMessage());
        }
    }
    
    // 解锁指定 IP：清除所有锁定状态和计数
    @Override
    public void unlockEmailCodeIp(String ipAddress) {
        try {
            redisTemplate.delete(IP_LOCK_PREFIX + ipAddress);
            redisTemplate.delete(IP_WINDOW_LOCK_PREFIX + ipAddress);
            redisTemplate.delete(IP_SEND_COUNT_PREFIX + ipAddress);
            redisTemplate.delete(IP_WINDOW_SEND_COUNT_PREFIX + ipAddress);
            log.info("管理员解锁IP验证码限制: ip={}", ipAddress);
        } catch (Exception e) {
            log.error("解锁IP失败: {}", e.getMessage());
        }
    }
    
    // 构建被锁定邮箱信息 DTO
    private EmailVerificationService.LockedEmailInfo buildLockedEmailInfo(String email, Long ttl, String lockType) {
        LocalDateTime now = LocalDateTime.now();
        long totalSeconds = lockType.equals("DAY_LOCK") ? LOCK_DURATION_HOURS * 3600 : WINDOW_DURATION_MINUTES * 60;
        
        return EmailVerificationService.LockedEmailInfo.builder()
                .email(email)
                .lockedAt(now.minusSeconds(totalSeconds - ttl).toString())
                .expiresAt(now.plusSeconds(ttl).toString())
                .remainingSeconds(ttl)
                .lockType(lockType)
                .build();
    }
    
    // 构建被锁定 IP 信息 DTO
    private EmailVerificationService.LockedIpInfo buildLockedIpInfo(String ipAddress, Long ttl, String lockType) {
        LocalDateTime now = LocalDateTime.now();
        long totalSeconds = lockType.equals("DAY_LOCK") ? LOCK_DURATION_HOURS * 3600 : WINDOW_DURATION_MINUTES * 60;
        
        return EmailVerificationService.LockedIpInfo.builder()
                .ipAddress(ipAddress)
                .lockedAt(now.minusSeconds(totalSeconds - ttl).toString())
                .expiresAt(now.plusSeconds(ttl).toString())
                .remainingSeconds(ttl)
                .lockType(lockType)
                .build();
    }
}