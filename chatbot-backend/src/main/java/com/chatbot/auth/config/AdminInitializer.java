package com.lingobot.auth.config;

import com.lingobot.auth.entity.User;
import com.lingobot.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {
    
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "password";
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Optional<User> existingAdminOpt = userRepository.findByEmail(ADMIN_EMAIL);
        
        if (existingAdminOpt.isPresent()) {
            User existingAdmin = existingAdminOpt.get();
            
            if (existingAdmin.getRole() != User.Role.ROLE_ADMIN) {
                existingAdmin.setRole(User.Role.ROLE_ADMIN);
                existingAdmin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
                userRepository.save(existingAdmin);
                log.info("==========================================");
                log.info("已有管理员用户已更新:");
                log.info("  邮箱: {}", ADMIN_EMAIL);
                log.info("  用户�? {}", existingAdmin.getUsername());
                log.info("  密码已重置为: {}", ADMIN_PASSWORD);
                log.info("  角色已更新为: ROLE_ADMIN");
                log.info("==========================================");
            } else {
                if (!passwordEncoder.matches(ADMIN_PASSWORD, existingAdmin.getPassword())) {
                    existingAdmin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
                    userRepository.save(existingAdmin);
                    log.info("==========================================");
                    log.info("管理员密码已重置:");
                    log.info("  邮箱: {}", ADMIN_EMAIL);
                    log.info("  用户�? {}", existingAdmin.getUsername());
                    log.info("  密码已重置为: {}", ADMIN_PASSWORD);
                    log.info("  角色: ROLE_ADMIN");
                    log.info("==========================================");
                } else {
                    log.info("==========================================");
                    log.info("管理员账号已存在且配置正�?");
                    log.info("  邮箱: {}", ADMIN_EMAIL);
                    log.info("  用户�? {}", existingAdmin.getUsername());
                    log.info("  密码: {}", ADMIN_PASSWORD);
                    log.info("  角色: ROLE_ADMIN");
                    log.info("==========================================");
                }
            }
        } else {
            Optional<User> existingByUsername = userRepository.findByUsername(ADMIN_EMAIL);
            if (existingByUsername.isPresent()) {
                User existingAdmin = existingByUsername.get();
                existingAdmin.setEmail(ADMIN_EMAIL);
                existingAdmin.setRole(User.Role.ROLE_ADMIN);
                existingAdmin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
                userRepository.save(existingAdmin);
                log.info("==========================================");
                log.info("已有用户已升级为管理�?");
                log.info("  邮箱: {}", ADMIN_EMAIL);
                log.info("  用户�? {}", ADMIN_EMAIL);
                log.info("  密码已重置为: {}", ADMIN_PASSWORD);
                log.info("  角色已更新为: ROLE_ADMIN");
                log.info("==========================================");
            } else {
                User admin = User.builder()
                        .username(ADMIN_EMAIL)
                        .email(ADMIN_EMAIL)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role(User.Role.ROLE_ADMIN)
                        .build();
                
                userRepository.save(admin);
                log.info("==========================================");
                log.info("管理员账号已创建:");
                log.info("  邮箱: {}", ADMIN_EMAIL);
                log.info("  用户�? {}", ADMIN_EMAIL);
                log.info("  密码: {}", ADMIN_PASSWORD);
                log.info("  角色: ROLE_ADMIN");
                log.info("==========================================");
            }
        }
    }
}
