package com.lingobot.core.user.auth.init;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 管理员账号初始化器
 * 
 * 功能说明：
 * 在应用启动时自动检查并初始化管理员账号，确保系统有一个可用的管理员账户。
 * 
 * 设计思路：
 * 1. 从配置文件读取管理员邮箱和密码（支持环境变量覆盖）
 * 2. 检查是否存在使用该邮箱的用户
 * 3. 如果不存在，创建一个全新的管理员账号
 * 4. 如果已存在，确保其具有管理员角色和正确的密码
 */
@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {
    
    /**
     * 管理员邮箱配置
     */
    @Value("${ADMIN_EMAIL:admin@example.com}")
    private String adminEmail;
    
    /**
     * 管理员密码配置
     */
    @Value("${ADMIN_PASSWORD:password}")
    private String adminPassword;
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * 应用启动时执行的初始化逻辑
     * 
     * 执行流程：
     * 1. 等待1秒（确保数据库连接已就绪）
     * 2. 根据邮箱查找现有管理员用户
     * 3. 如果不存在 → 创建新的管理员账号
     * 4. 如果已存在 → 检查并更新角色/密码
     */
    @Override
    public void run(String... args) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        initializeAdminAccount();
    }
    
    /**
     * 初始化管理员账号的核心逻辑
     * 
     */
    private void initializeAdminAccount() {
        Optional<User> existingAdminOpt = userRepository.findByEmail(adminEmail);
        
        if (existingAdminOpt.isPresent()) {
            handleExistingAdmin(existingAdminOpt.get());
        } else {
            createNewAdmin();
        }
    }
    
    /**
     * 处理已存在的管理员账号
     * 
     * 检查内容：
     * 1. 是否具有 ROLE_ADMIN 角色
     * 2. 密码是否与配置一致
     */
    private void handleExistingAdmin(User existingAdmin) {
        boolean needsUpdate = false;
        
        if (existingAdmin.getRole() != User.Role.ROLE_ADMIN) {
            existingAdmin.setRole(User.Role.ROLE_ADMIN);
            existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
            needsUpdate = true;
            log.info("==========================================");
            log.info("已有管理员用户已更新");
            log.info("  角色已更新为: ROLE_ADMIN");
            log.info("==========================================");
        } else if (!passwordEncoder.matches(adminPassword, existingAdmin.getPassword())) {
            existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
            needsUpdate = true;
            log.info("==========================================");
            log.info("管理员密码已重置");
            log.info("==========================================");
        } else {
            log.info("==========================================");
            log.info("管理员账号已存在且配置正确");
            log.info("==========================================");
        }
        
        if (needsUpdate) {
            userRepository.save(existingAdmin);
        }
    }
    
    /**
     * 创建新的管理员账号
     * 
     * 创建规则：
     * - 用户名 = 邮箱（简化登录体验）
     * - 角色 = ROLE_ADMIN
     * - 密码从配置读取并加密存储
     */
    private void createNewAdmin() {
        User admin = User.builder()
                .username(adminEmail)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(User.Role.ROLE_ADMIN)
                .build();
        
        userRepository.save(admin);
        log.info("==========================================");
        log.info("管理员账号已创建");
        log.info("  角色: ROLE_ADMIN");
        log.info("==========================================");
    }
}
