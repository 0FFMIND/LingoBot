package com.lingobot.core.user.auth.init;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {
    
    @Value("${ADMIN_EMAIL:admin@example.com}")
    private String adminEmail;
    
    @Value("${ADMIN_PASSWORD:password}")
    private String adminPassword;
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    @Override
    public void run(String... args) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Optional<User> existingAdminOpt = userRepository.findByEmail(adminEmail);
        
        if (existingAdminOpt.isPresent()) {
            User existingAdmin = existingAdminOpt.get();
            
            if (existingAdmin.getRole() != User.Role.ROLE_ADMIN) {
                existingAdmin.setRole(User.Role.ROLE_ADMIN);
                existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(existingAdmin);
                log.info("==========================================");
                log.info("已有管理员用户已更新:");
                log.info("  邮箱: {}", adminEmail);
                log.info("  用户名: {}", existingAdmin.getUsername());
                log.info("  密码已重置为: {}", adminPassword);
                log.info("  角色已更新为: ROLE_ADMIN");
                log.info("==========================================");
            } else {
                if (!passwordEncoder.matches(adminPassword, existingAdmin.getPassword())) {
                    existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
                    userRepository.save(existingAdmin);
                    log.info("==========================================");
                    log.info("管理员密码已重置:");
                    log.info("  邮箱: {}", adminEmail);
                    log.info("  用户名: {}", existingAdmin.getUsername());
                    log.info("  密码已重置为: {}", adminPassword);
                    log.info("  角色: ROLE_ADMIN");
                    log.info("==========================================");
                } else {
                    log.info("==========================================");
                    log.info("管理员账号已存在且配置正确");
                    log.info("  邮箱: {}", adminEmail);
                    log.info("  用户名: {}", existingAdmin.getUsername());
                    log.info("  密码: {}", adminPassword);
                    log.info("  角色: ROLE_ADMIN");
                    log.info("==========================================");
                }
            }
        } else {
            Optional<User> existingByUsername = userRepository.findByUsername(adminEmail);
            if (existingByUsername.isPresent()) {
                User existingAdmin = existingByUsername.get();
                existingAdmin.setEmail(adminEmail);
                existingAdmin.setRole(User.Role.ROLE_ADMIN);
                existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(existingAdmin);
                log.info("==========================================");
                log.info("已有用户已升级为管理员");
                log.info("  邮箱: {}", adminEmail);
                log.info("  用户名: {}", adminEmail);
                log.info("  密码已重置为: {}", adminPassword);
                log.info("  角色已更新为: ROLE_ADMIN");
                log.info("==========================================");
            } else {
                User admin = User.builder()
                        .username(adminEmail)
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(User.Role.ROLE_ADMIN)
                        .build();
                
                userRepository.save(admin);
                log.info("==========================================");
                log.info("管理员账号已创建:");
                log.info("  邮箱: {}", adminEmail);
                log.info("  用户名: {}", adminEmail);
                log.info("  密码: {}", adminPassword);
                log.info("  角色: ROLE_ADMIN");
                log.info("==========================================");
            }
        }
    }
}
