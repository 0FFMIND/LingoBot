package com.lingobot.infrastructure.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.user.auth.filter.DevAutoLoginFilter;
import com.lingobot.core.user.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 安全配置类。
 * 配置 JWT 认证、权限控制、跨域策略和密码编码器，
 * 采用无状态（Stateless）的 REST API 安全模型。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    // JWT 认证过滤器，用于从请求头中提取和验证 JWT Token
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    // 开发环境自动登录过滤器
    private final DevAutoLoginFilter devAutoLoginFilter;
    
    // 应用配置属性
    private final AppProperties appProperties;
    
    // 配置安全过滤链：禁用 CSRF、配置 CORS、设置无状态会话、配置白名单和认证入口
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF，因为我们使用 JWT Token 而非 Cookie 进行认证
            .csrf(AbstractHttpConfigurer::disable)
            // 配置 CORS，使用自定义的 corsConfigurationSource
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 使用无状态会话策略，不创建或使用 HttpSession
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> {
                // 认证相关接口：注册、登录等
                auth.requestMatchers("/api/auth/**").permitAll();
                // H2 控制台：仅开发环境使用
                auth.requestMatchers("/h2-console/**").permitAll();
                // 错误页面
                auth.requestMatchers("/error").permitAll();
                // 静态资源
                auth.requestMatchers("/static/**").permitAll();
                // TTS 语音合成接口
                auth.requestMatchers("/api/tts/**").permitAll();
                // 日志环境检查接口：任何环境下都可访问
                auth.requestMatchers("/api/logs/dev-check").permitAll();
                // 管理员环境检查接口：任何环境下都可访问
                auth.requestMatchers("/api/admin/dev-check").permitAll();

                // 其他所有请求需要认证
                auth.anyRequest().authenticated();
            })
            // 配置未认证时的处理逻辑，返回 JSON 格式的错误信息
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    
                    Map<String, Object> body = new HashMap<>();
                    body.put("timestamp", LocalDateTime.now().toString());
                    body.put("status", HttpStatus.UNAUTHORIZED.value());
                    body.put("error", "Unauthorized");
                    body.put("message", "请先登录");
                    
                    ObjectMapper objectMapper = new ObjectMapper();
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            )
            // 禁用 X-Frame-Options 以允许 H2 控制台在 iframe 中显示
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            // 在 UsernamePasswordAuthenticationFilter 之前添加 JWT 认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 在 JWT 认证过滤器之后添加开发环境自动登录过滤器
            // 执行顺序：先尝试 JWT 认证，如果失败且是开发环境，则自动登录为 admin
            .addFilterAfter(devAutoLoginFilter, JwtAuthenticationFilter.class);
        
        return http.build();
    }
    
    // 配置 CORS 策略，允许本地开发环境的前端访问后端 API
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许的来源：本地开发环境的所有端口
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    // 配置密码编码器，使用 BCrypt 算法进行密码加密和验证
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
