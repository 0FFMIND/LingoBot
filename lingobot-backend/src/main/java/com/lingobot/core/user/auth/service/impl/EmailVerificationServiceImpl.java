package com.lingobot.core.user.auth.service.impl;

import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;
import com.lingobot.core.user.auth.service.EmailVerificationService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {
    
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    
    private static final String VERIFICATION_CODE_PREFIX = "email:verify:";
    private static final String SEND_COUNT_PREFIX = "email:send:count:";
    private static final String LOCK_PREFIX = "email:lock:";
    private static final String LAST_SEND_PREFIX = "email:lastsend:";
    
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final int MAX_SEND_COUNT_PER_DAY = 5;
    private static final long LOCK_DURATION_HOURS = 24;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );
    
    @Override
    public void sendVerificationCode(SendVerificationCodeRequest request) {
        String email = request.getEmail();
        
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("无效的邮箱地址");
        }
        
        checkLockStatus(email);
        
        checkSendInterval(email);
        
        incrementSendCountAndCheckLimit(email);
        
        String code = generateVerificationCode();
        
        String key = VERIFICATION_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        try {
            sendEmail(email, code);
            log.info("验证码已发送到邮箱: {}", email);
        } catch (MessagingException e) {
            log.error("发送邮件失败: {}", e.getMessage());
            throw new RuntimeException("发送验证码失败，请稍后重试");
        }
        
        updateLastSendTime(email);
        
        log.info("发送验证码到邮箱: {}: {}", email, code);
        log.info("验证码有效期: {}分钟", CODE_EXPIRE_MINUTES);
    }
    
    private void checkLockStatus(String email) {
        String lockKey = LOCK_PREFIX + email;
        Boolean isLocked = redisTemplate.hasKey(lockKey);
        
        if (Boolean.TRUE.equals(isLocked)) {
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.HOURS);
            throw new IllegalStateException(
                String.format("该邮箱发送验证码次数过多，已被锁定 %d 小时，请稍后重试", 
                    ttl != null && ttl > 0 ? ttl : LOCK_DURATION_HOURS)
            );
        }
    }
    
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
    
    private void incrementSendCountAndCheckLimit(String email) {
        String countKey = SEND_COUNT_PREFIX + email;
        
        Long currentCount = redisTemplate.opsForValue().increment(countKey);
        
        if (currentCount == null || currentCount == 1) {
            redisTemplate.expire(countKey, 1, TimeUnit.DAYS);
        }
        
        if (currentCount != null && currentCount >= MAX_SEND_COUNT_PER_DAY) {
            String lockKey = LOCK_PREFIX + email;
            redisTemplate.opsForValue().set(lockKey, "locked", LOCK_DURATION_HOURS, TimeUnit.HOURS);
            throw new IllegalStateException(
                String.format("今日发送验证码次数已达上限(%d次)，该邮箱已被锁定 %d 小时", 
                    MAX_SEND_COUNT_PER_DAY, LOCK_DURATION_HOURS)
            );
        }
    }
    
    private void updateLastSendTime(String email) {
        String lastSendKey = LAST_SEND_PREFIX + email;
        redisTemplate.opsForValue().set(
            lastSendKey, 
            String.valueOf(System.currentTimeMillis()),
            SEND_INTERVAL_SECONDS + 10,
            TimeUnit.SECONDS
        );
    }
    
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
    
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
    
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
}