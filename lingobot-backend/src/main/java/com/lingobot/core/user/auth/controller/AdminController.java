package com.lingobot.core.user.auth.controller;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.LoginAttemptService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/blocked-users")
    public ResponseEntity<ApiResponse<List<LoginAttemptService.BlockedUserInfo>>> getBlockedUsers() {
        List<LoginAttemptService.BlockedUserInfo> blockedUsers = loginAttemptService.getAllBlockedUsers();
        return ResponseEntity.ok(ApiResponse.success("获取锁定用户列表成功", blockedUsers));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/blocked-ips")
    public ResponseEntity<ApiResponse<List<LoginAttemptService.BlockedIpInfo>>> getBlockedIps() {
        List<LoginAttemptService.BlockedIpInfo> blockedIps = loginAttemptService.getAllBlockedIps();
        return ResponseEntity.ok(ApiResponse.success("获取锁定IP列表成功", blockedIps));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/unlock-user/{userId}")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable Long userId) {
        log.info("管理员解锁用户: userId={}", userId);
        loginAttemptService.unlockUser(userId);
        return ResponseEntity.ok(ApiResponse.success("用户解锁成功", null));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/unblock-ip/{ipAddress}")
    public ResponseEntity<ApiResponse<Void>> unblockIp(@PathVariable String ipAddress) {
        log.info("管理员解除IP封锁: ip={}", ipAddress);
        loginAttemptService.unblockIp(ipAddress);
        return ResponseEntity.ok(ApiResponse.success("IP解锁成功", null));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AdminStatus>> getStatus() {
        List<LoginAttemptService.BlockedUserInfo> blockedUsers = loginAttemptService.getAllBlockedUsers();
        List<LoginAttemptService.BlockedIpInfo> blockedIps = loginAttemptService.getAllBlockedIps();
        
        AdminStatus status = new AdminStatus();
        status.setBlockedUserCount(blockedUsers.size());
        status.setBlockedIpCount(blockedIps.size());
        
        return ResponseEntity.ok(ApiResponse.success("获取状态成功", status));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserAdminDTO>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        List<UserAdminDTO> userDTOs = users.stream()
                .map(user -> UserAdminDTO.fromEntity(user, currentAdmin))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("获取用户列表成功", userDTOs));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在", null));
        }
        
        User user = userOpt.get();
        
        if (user.getUsername().equals(currentAdmin)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("不能删除当前登录的管理员账户", null));
        }
        
        loginAttemptService.unlockUser(userId);
        
        userRepository.delete(user);
        log.info("管理员删除用户: userId={}, username={}, 操作管理员: {}", 
                userId, user.getUsername(), currentAdmin);
        
        return ResponseEntity.ok(ApiResponse.success("用户删除成功", null));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetUserPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在", null));
        }

        User user = userOpt.get();

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("密码长度至少6个字符", null));
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("管理员重置用户密码: userId={}, username={}, 操作管理员: {}",
                userId, user.getUsername(), currentAdmin);
        
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }
    
    @lombok.Data
    public static class AdminStatus {
        private int blockedUserCount;
        private int blockedIpCount;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserAdminDTO {
        private Long id;
        private String username;
        private String email;
        private String role;
        private String createdAt;
        private Double balance;
        private boolean isCurrentAdmin;
        
        public static UserAdminDTO fromEntity(User user, String currentAdminUsername) {
            return UserAdminDTO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole() != null ? user.getRole().name() : User.Role.ROLE_USER.name())
                    .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                    .balance(user.getBalance() != null ? user.getBalance() : 0.0)
                    .isCurrentAdmin(user.getUsername().equals(currentAdminUsername))
                    .build();
        }
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}/username")
    public ResponseEntity<ApiResponse<Void>> updateUserUsername(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUsernameRequest request) {
        
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在", null));
        }

        User user = userOpt.get();

        String newUsername = request.getNewUsername();
        if (newUsername == null || newUsername.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户名不能为空", null));
        }

        if (newUsername.length() < 2 || newUsername.length() > 50) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户名长度必须在2-50个字符之间", null));
        }

        Optional<User> existingUser = userRepository.findByUsername(newUsername.trim());
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户名已被使用", null));
        }

        user.setUsername(newUsername.trim());
        userRepository.save(user);

        log.info("管理员修改用户用户名: userId={}, 旧用户名={}, 新用户名={}, 操作管理员: {}",
                userId, user.getUsername(), newUsername, currentAdmin);

        return ResponseEntity.ok(ApiResponse.success("用户名修改成功", null));
    }

    @lombok.Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "密码长度至少6个字符")
        private String newPassword;
    }

    @lombok.Data
    public static class UpdateUsernameRequest {
        @NotBlank(message = "新用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度必须在2-50个字符之间")
        private String newUsername;
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}/balance")
    public ResponseEntity<ApiResponse<Void>> updateUserBalance(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateBalanceRequest request) {
        
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在", null));
        }

        User user = userOpt.get();

        Double newBalance = request.getNewBalance();
        if (newBalance == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("余额不能为空", null));
        }
        
        if (newBalance < 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("余额不能为负数", null));
        }

        user.setBalance(newBalance);
        userRepository.save(user);

        log.info("管理员修改用户余额: userId={}, username={}, 旧余额={}, 新余额={}, 操作管理员: {}",
                userId, user.getUsername(), 
                user.getBalance() != null ? user.getBalance() : 0.0, 
                newBalance, currentAdmin);

        return ResponseEntity.ok(ApiResponse.success("余额修改成功", null));
    }
    
    @lombok.Data
    public static class UpdateBalanceRequest {
        private Double newBalance;
    }
}
