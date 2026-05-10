package com.lingobot.core.user.auth.service;

import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;

public interface EmailVerificationService {
    void sendVerificationCode(SendVerificationCodeRequest request);
    
    void sendLoginVerificationCode(String email);
    
    boolean verifyCode(String email, String code);
    
    boolean verifyLoginCode(String email, String code);
}