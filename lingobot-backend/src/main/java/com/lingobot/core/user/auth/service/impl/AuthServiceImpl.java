package com.lingobot.core.user.auth.service.impl;

import com.lingobot.infrastructure.common.config.RateLimitProperties;
import com.lingobot.core.user.auth.dto.AuthResponse;
import com.lingobot.core.user.auth.dto.ChangePasswordRequest;
import com.lingobot.core.user.auth.dto.LoginRequest;
import com.lingobot.core.user.auth.dto.RegisterRequest;
import com.lingobot.core.user.auth.dto.RegisterWithCodeRequest;
import com.lingobot.core.user.auth.dto.UserDTO;
import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.core.user.auth.service.EmailVerificationService;
import com.lingobot.core.user.auth.service.JwtService;
import com.lingobot.core.user.auth.service.LoginAttemptService;
import com.lingobot.infrastructure.common.exception.ChatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService, UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitProperties rateLimitProperties;
    private final EmailVerificationService emailVerificationService;
    
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        if (loginAttemptService.isIpBlocked(clientIp)) {
            LocalDateTime expiry = loginAttemptService.getIpBlockExpiry(clientIp);
            String expiryStr = expiry != null 
                ? expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "未知";
            throw new ChatException("操作过于频繁，请稍后再试。限制解除时间 " + expiryStr);
        }
        
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new ChatException("用户名不能为空");
        }
        
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ChatException("密码不能为空");
        }
        
        if (request.getUsername().length() < 3 || request.getUsername().length() > 20) {
            throw new ChatException("用户名长度必须在3-20个字符之间");
        }

        if (request.getPassword().length() < 6) {
            throw new ChatException("密码长度至少6个字符");
        }
        
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ChatException("用户名已存在");
        }
        
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (!isValidEmail(request.getEmail())) {
                throw new ChatException("无效的邮箱地址");
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ChatException("该邮箱已被注册");
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        
        User savedUser = userRepository.save(user);
        
        String token = jwtService.generateToken(savedUser.getUsername(), savedUser.getId());
        
        log.info("用户注册成功: {}, IP: {}", savedUser.getUsername(), clientIp);
        
        return AuthResponse.builder()
                .token(token)
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .userId(savedUser.getId())
                .role(savedUser.getRole().name())
                .avatar(savedUser.getAvatar())
                .build();
    }
    
    @Override
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.getEmail();
        String password = request.getPassword();
        
        if (email == null || email.trim().isEmpty()) {
            loginAttemptService.applyDelayOnFailure();
            throw new ChatException("邮箱不能为空");
        }
        
        if (password == null || password.trim().isEmpty()) {
            loginAttemptService.recordLoginAttempt(clientIp, email, false, "密码不能为空");
            loginAttemptService.applyDelayOnFailure();
            throw new ChatException("密码不能为空");
        }
        
        if (loginAttemptService.isIpBlocked(clientIp)) {
            LocalDateTime expiry = loginAttemptService.getIpBlockExpiry(clientIp);
            String expiryStr = expiry != null 
                ? expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "未知";
            log.warn("被阻止的登录尝试 - IP: {}, 邮箱: {}, 解除时间: {}", clientIp, email, expiryStr);
            throw new ChatException("登录尝试次数过多，请稍后再试。限制解除时间 " + expiryStr);
        }
        
        java.util.Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            loginAttemptService.recordLoginAttempt(clientIp, email, false, "用户不存在");
            loginAttemptService.applyDelayOnFailure();
            
            int remaining = loginAttemptService.getRemainingAttempts(clientIp, email);
            if (remaining > 0) {
                throw new ChatException("邮箱或密码错误。剩余尝试次数 " + remaining);
            }
            throw new ChatException("邮箱或密码错误");
        }
        
        User user = userOpt.get();
        
        if (loginAttemptService.isUserBlocked(user.getId())) {
            LocalDateTime expiry = loginAttemptService.getUserBlockExpiry(user.getId());
            String expiryStr = expiry != null 
                ? expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "未知";
            log.warn("被阻止的登录尝试 - 用户: {}, IP: {}, 解除时间: {}", user.getUsername(), clientIp, expiryStr);
            throw new ChatException("账户已被临时锁定，请稍后再试。锁定解除时间 " + expiryStr);
        }
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginAttemptService.recordLoginAttempt(clientIp, email, false, "密码错误");
            loginAttemptService.applyDelayOnFailure();
            
            int remaining = loginAttemptService.getRemainingAttempts(clientIp, email);
            if (remaining > 0) {
                throw new ChatException("邮箱或密码错误。剩余尝试次数 " + remaining);
            }
            throw new ChatException("邮箱或密码错误");
        }
        
        loginAttemptService.recordLoginAttempt(clientIp, user.getUsername(), true, null);
        
        String token = jwtService.generateToken(user.getUsername(), user.getId());
        
        log.info("用户登录成功: {}, IP: {}", user.getUsername(), clientIp);
        
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .userId(user.getId())
                .role(user.getRole().name())
                .avatar(user.getAvatar())
                .build();
    }
    
    @Override
    public UserDTO getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
        return toDTO(user);
    }
    
    @Override
    public Long getCurrentUserId() {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ChatException("用户不存在"));
            return user.getId();
        } catch (Exception e) {
            log.warn("获取当前用户ID失败: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public void logout() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("用户退出登录 {}", username);
        SecurityContextHolder.clearContext();
    }
    
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
        
        if (request.getCurrentPassword() == null || request.getCurrentPassword().trim().isEmpty()) {
            throw new ChatException("当前密码不能为空");
        }

        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new ChatException("新密码不能为空");
        }

        if (request.getNewPassword().length() < 6) {
            throw new ChatException("新密码长度至少为6个字符");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ChatException("两次输入的新密码不一致");
        }
        
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ChatException("当前密码错误");
        }
        
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ChatException("新密码不能与当前密码相同");
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("用户修改密码成功: {}", username);
    }
    
    @Override
    @Transactional
    public void deactivateAccount() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
        
        log.info("用户注销账户，删除用户数据 {}", user.getUsername());
        
        userRepository.delete(user);
        
        SecurityContextHolder.clearContext();
    }
    
    @Override
    @Transactional
    public void updateAvatar(String avatarBase64) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
        
        user.setAvatar(avatarBase64);
        userRepository.save(user);
        
        log.info("用户更新头像成功: {}", username);
    }
    
    @Override
    @Transactional
    public AuthResponse updateUsername(String newUsername) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ChatException("用户不存在"));
        
        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new ChatException("昵称不能为空");
        }
        
        String trimmedUsername = newUsername.trim();
        
        if (trimmedUsername.length() < 3 || trimmedUsername.length() > 20) {
            throw new ChatException("昵称长度必须3-20个字符之间");
        }

        if (!currentUsername.equals(trimmedUsername)) {
            if (userRepository.existsByUsername(trimmedUsername)) {
                throw new ChatException("该昵称已被使用");
            }
        }
        
        user.setUsername(trimmedUsername);
        User savedUser = userRepository.save(user);
        
        String newToken = jwtService.generateToken(savedUser.getUsername(), savedUser.getId());
        
        log.info("用户修改昵称成功: {} -> {}", currentUsername, trimmedUsername);
        
        return AuthResponse.builder()
                .token(newToken)
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .userId(savedUser.getId())
                .role(savedUser.getRole().name())
                .avatar(savedUser.getAvatar())
                .build();
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在 " + username));
        
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
    
    @Override
    @Transactional
    public AuthResponse registerWithCode(RegisterWithCodeRequest request, String clientIp) {
        if (loginAttemptService.isIpBlocked(clientIp)) {
            LocalDateTime expiry = loginAttemptService.getIpBlockExpiry(clientIp);
            String expiryStr = expiry != null 
                ? expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "未知";
            throw new ChatException("操作过于频繁，请稍后再试。限制解除时间 " + expiryStr);
        }
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ChatException("邮箱不能为空");
        }
        
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ChatException("密码不能为空");
        }
        
        if (request.getVerificationCode() == null || request.getVerificationCode().trim().isEmpty()) {
            throw new ChatException("验证码不能为空");
        }

        if (request.getPassword().length() < 6) {
            throw new ChatException("密码长度至少6个字符");
        }

        if (!isValidEmail(request.getEmail())) {
            throw new ChatException("无效的邮箱地址");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ChatException("该邮箱已被注册");
        }

        String baseUsername = extractUsernameFromEmail(request.getEmail());
        String username = generateUniqueUsername(baseUsername);

        if (!emailVerificationService.verifyCode(request.getEmail(), request.getVerificationCode())) {
            throw new ChatException("验证码无效或已过期");
        }
        
        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        
        User savedUser = userRepository.save(user);
        
        String token = jwtService.generateToken(savedUser.getUsername(), savedUser.getId());
        
        log.info("用户通过验证码注册成功: {}, 邮箱: {}, IP: {}", savedUser.getUsername(), savedUser.getEmail(), clientIp);
        
        return AuthResponse.builder()
                .token(token)
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .userId(savedUser.getId())
                .role(savedUser.getRole().name())
                .avatar(savedUser.getAvatar())
                .build();
    }
    
    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatar(user.getAvatar())
                .createdAt(user.getCreatedAt())
                .balance(user.getBalance() != null ? user.getBalance() : 0)
                .build();
    }
    
    private boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email.matches(emailRegex);
    }
    
    private String extractUsernameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String username = email.substring(0, email.indexOf("@"));
        if (username.length() < 3) {
            username = username + "user";
        }
        if (username.length() > 20) {
            username = username.substring(0, 20);
        }
        return username;
    }
    
    private String generateUniqueUsername(String baseUsername) {
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            if (username.length() > 20) {
                username = baseUsername.substring(0, 20 - String.valueOf(counter).length()) + counter;
            }
            counter++;
        }
        return username;
    }
}
