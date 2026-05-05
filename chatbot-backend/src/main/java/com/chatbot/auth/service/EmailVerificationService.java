package com.lingobot.auth.service;

import com.lingobot.auth.dto.SendVerificationCodeRequest;

public interface EmailVerificationService {
    void sendVerificationCode(SendVerificationCodeRequest request);
    
    boolean verifyCode(String email, String code);
}