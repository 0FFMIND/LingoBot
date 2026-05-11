package com.lingobot.core.user.auth.filter;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.infrastructure.common.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class DevAutoLoginFilter extends OncePerRequestFilter {
    
    private final AppProperties appProperties;
    private final UserRepository userRepository;
    
    private static volatile boolean devAutoLoginLogged = false;
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        if (!appProperties.isDev()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (SecurityContextHolder.getContext().getAuthentication() != null 
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        User adminUser = userRepository.findByRole(User.Role.ROLE_ADMIN).stream().findFirst().orElse(null);
        
        if (adminUser != null) {
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    adminUser.getUsername(),
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(adminUser.getRole().name()))
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            if (!devAutoLoginLogged) {
                synchronized (DevAutoLoginFilter.class) {
                    if (!devAutoLoginLogged) {
                        log.info("==========================================");
                        log.info("开发环境自动登录已启用");
                        log.info("  用户: {}", adminUser.getUsername());
                        log.info("  角色: {}", adminUser.getRole().name());
                        log.info("==========================================");
                        devAutoLoginLogged = true;
                    }
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
