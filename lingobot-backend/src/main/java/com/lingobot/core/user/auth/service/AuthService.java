package com.lingobot.core.user.auth.service;

import com.lingobot.core.user.auth.dto.AuthResponse;
import com.lingobot.core.user.auth.dto.ChangePasswordRequest;
import com.lingobot.core.user.auth.dto.LoginRequest;
import com.lingobot.core.user.auth.dto.RegisterRequest;
import com.lingobot.core.user.auth.dto.RegisterWithCodeRequest;
import com.lingobot.core.user.auth.dto.UserDTO;

public interface AuthService {
    AuthResponse register(RegisterRequest request, String clientIp);
    AuthResponse registerWithCode(RegisterWithCodeRequest request, String clientIp);
    AuthResponse login(LoginRequest request, String clientIp);
    UserDTO getCurrentUser();
    Long getCurrentUserId();
    void logout();
    void deactivateAccount();
    void changePassword(ChangePasswordRequest request);
    void updateAvatar(String avatarBase64);
    AuthResponse updateUsername(String username);
}
