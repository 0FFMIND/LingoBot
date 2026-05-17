package com.lingobot.core.user.auth.controller;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.EmailVerificationService;
import com.lingobot.core.user.auth.service.LoginAttemptService;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.AppProperties;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.ErrorCode;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 管理员控制器。
 *
 * 提供管理员专属的 REST 接口，用于系统管理功能。
 * 所有接口均需 ADMIN 角色权限，包含用户管理、登录限制管理、验证码限制管理等功能。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    // 登录尝试服务，管理登录失败限制和封锁
    private final LoginAttemptService loginAttemptService;
    // 邮箱验证码服务，管理验证码发送限制和封锁
    private final EmailVerificationService emailVerificationService;
    // 用户仓库，用于用户数据的增删改查
    private final UserRepository userRepository;
    // 余额服务，用于查询用户余额
    private final BalanceService balanceService;
    // 密码编码器，用于重置用户密码时加密
    private final PasswordEncoder passwordEncoder;
    // 应用配置属性，用于判断运行环境
    private final AppProperties appProperties;

    // 检查当前是否为开发环境
    @GetMapping("/dev-check")
    public boolean isDev() {
        return appProperties.isDev();
    }
    
    // 获取所有被临时锁定的用户列表
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/blocked-users")
    public ResponseEntity<ApiResponse<List<LoginAttemptService.BlockedUserInfo>>> getBlockedUsers() {
        List<LoginAttemptService.BlockedUserInfo> blockedUsers = loginAttemptService.getAllBlockedUsers();
        return ResponseEntity.ok(ApiResponse.success("获取锁定用户列表成功", blockedUsers));
    }
    
    // 获取所有被临时封锁的 IP 列表
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/blocked-ips")
    public ResponseEntity<ApiResponse<List<LoginAttemptService.BlockedIpInfo>>> getBlockedIps() {
        List<LoginAttemptService.BlockedIpInfo> blockedIps = loginAttemptService.getAllBlockedIps();
        return ResponseEntity.ok(ApiResponse.success("获取锁定IP列表成功", blockedIps));
    }
    
    // 解锁指定用户（清除登录失败限制和锁定状态）
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/unlock-user/{userId}")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable Long userId) {
        log.info("管理员解锁用户: userId={}", userId);
        loginAttemptService.unlockUser(userId);
        return ResponseEntity.ok(ApiResponse.success("用户解锁成功", null));
    }
    
    // 解除指定 IP 的封锁状态
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/unblock-ip/{ipAddress}")
    public ResponseEntity<ApiResponse<Void>> unblockIp(@PathVariable String ipAddress) {
        log.info("管理员解除IP封锁: ip={}", ipAddress);
        loginAttemptService.unblockIp(ipAddress);
        return ResponseEntity.ok(ApiResponse.success("IP解锁成功", null));
    }
    
    // 获取系统管理状态概览（锁定用户数、封锁 IP 数）
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
    
    // 获取所有用户列表（包含用户信息和余额信息）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserAdminDTO>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        List<UserAdminDTO> userDTOs = users.stream()
                .map(user -> UserAdminDTO.fromEntity(user, balanceService, currentAdmin))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("获取用户列表成功", userDTOs));
    }
    
    // 删除指定用户（不能删除当前登录的管理员账户）
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户不存在"));
        }
        
        User user = userOpt.get();
        
        if (user.getUsername().equals(currentAdmin)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "不能删除当前登录的管理员账户"));
        }
        
        loginAttemptService.unlockUser(userId);
        
        userRepository.delete(user);
        log.info("管理员删除用户: userId={}, username={}, 操作管理员: {}",
                userId, user.getUsername(), currentAdmin);

        return ResponseEntity.noContent().build();
    }
    
    // 重置指定用户的密码
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetUserPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户不存在"));
        }

        User user = userOpt.get();

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "密码长度至少6个字符"));
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("管理员重置用户密码: userId={}, username={}, 操作管理员: {}",
                userId, user.getUsername(), currentAdmin);
        
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }
    
    // 管理员状态概览 DTO
    @lombok.Data
    public static class AdminStatus {
        // 被锁定的用户数量
        private int blockedUserCount;
        // 被封锁的 IP 数量
        private int blockedIpCount;
    }
    
    // 管理员视图用户信息 DTO（包含余额和是否为当前管理员标记）
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserAdminDTO {
        // 用户 ID
        private Long id;
        // 用户名
        private String username;
        // 用户邮箱
        private String email;
        // 用户角色
        private String role;
        // 创建时间
        private String createdAt;
        // 可用余额
        private BigDecimal balance;
        // 冻结余额
        private BigDecimal frozenBalance;
        // 是否为当前登录的管理员
        private boolean isCurrentAdmin;

        public static UserAdminDTO fromEntity(User user, BalanceService balanceService, String currentAdminUsername) {
            BigDecimal balance = balanceService.getUserBalance(user.getId());
            BigDecimal frozenBalance = balanceService.getUserFrozenBalance(user.getId());
            
            return UserAdminDTO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole() != null ? user.getRole().name() : User.Role.ROLE_USER.name())
                    .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                    .balance(balance)
                    .frozenBalance(frozenBalance)
                    .isCurrentAdmin(user.getUsername().equals(currentAdminUsername))
                    .build();
        }
    }
    
    // 管理员修改指定用户的用户名
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}/username")
    public ResponseEntity<ApiResponse<Void>> updateUserUsername(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUsernameRequest request) {
        
        String currentAdmin = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户不存在"));
        }

        User user = userOpt.get();

        String newUsername = request.getNewUsername();
        if (newUsername == null || newUsername.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户名不能为空"));
        }

        if (newUsername.length() < 2 || newUsername.length() > 50) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户名长度必须在2-50个字符之间"));
        }

        Optional<User> existingUser = userRepository.findByUsername(newUsername.trim());
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户名已被使用"));
        }

        user.setUsername(newUsername.trim());
        userRepository.save(user);

        log.info("管理员修改用户用户名: userId={}, 旧用户名={}, 新用户名={}, 操作管理员: {}",
                userId, user.getUsername(), newUsername, currentAdmin);

        return ResponseEntity.ok(ApiResponse.success("用户名修改成功", null));
    }

    // 重置密码请求 DTO
    @lombok.Data
    public static class ResetPasswordRequest {
        // 新密码，不能为空且至少 6 个字符
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "密码长度至少6个字符")
        private String newPassword;
    }

    // 管理员修改用户名请求 DTO
    @lombok.Data
    public static class AdminUpdateUsernameRequest {
        // 新的用户名
        @NotBlank(message = "新用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度必须在2-50个字符之间")
        private String newUsername;
    }
    
    // 获取所有被限制发送验证码的邮箱列表
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/locked-emails")
    public ResponseEntity<ApiResponse<List<EmailVerificationService.LockedEmailInfo>>> getLockedEmails() {
        List<EmailVerificationService.LockedEmailInfo> lockedEmails = emailVerificationService.getAllLockedEmails();
        return ResponseEntity.ok(ApiResponse.success("获取锁定邮箱列表成功", lockedEmails));
    }
    
    // 获取所有被限制发送验证码的 IP 列表
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/locked-email-ips")
    public ResponseEntity<ApiResponse<List<EmailVerificationService.LockedIpInfo>>> getLockedEmailIps() {
        List<EmailVerificationService.LockedIpInfo> lockedIps = emailVerificationService.getAllLockedIps();
        return ResponseEntity.ok(ApiResponse.success("获取锁定IP列表成功", lockedIps));
    }
    
    // 解锁指定邮箱的验证码发送限制
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/unlock-email/{email}")
    public ResponseEntity<ApiResponse<Void>> unlockEmail(@PathVariable String email) {
        log.info("管理员解锁邮箱验证码限制: email={}", email);
        emailVerificationService.unlockEmail(email);
        return ResponseEntity.ok(ApiResponse.success("邮箱解锁成功", null));
    }
    
    // 解锁指定 IP 的验证码发送限制
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/unlock-email-ip/{ipAddress}")
    public ResponseEntity<ApiResponse<Void>> unlockEmailIp(@PathVariable String ipAddress) {
        log.info("管理员解锁IP验证码限制: ip={}", ipAddress);
        emailVerificationService.unlockEmailCodeIp(ipAddress);
        return ResponseEntity.ok(ApiResponse.success("IP解锁成功", null));
    }
}
