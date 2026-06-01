package com.lingobot.core.user.auth.filter;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JWT 认证过滤器。
 *
 * 继承 OncePerRequestFilter，确保每个请求只执行一次认证逻辑。
 * 从 HTTP 请求中提取 JWT Token，验证有效性后设置到 Spring Security 上下文中。
 *
 * Token 提取优先级：
 * 1. Authorization 请求头（Bearer Token）- 推荐，安全性最高
 * 2. 查询参数 token（仅限 SSE 日志流路径）- 为浏览器 EventSource API 提供便利
 *
 * 安全考虑：
 * - URL 参数中的 Token 存在泄露风险（日志、历史记录、Referer），仅限特定路径使用
 * - Token 验证失败时返回统一的 JSON 错误响应，而不是抛出异常
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    // JWT 服务，用于 Token 的解析和验证
    private final JwtService jwtService;
    // 用户仓库，用于查询用户信息和角色
    private final UserRepository userRepository;
    
    // SSE 日志流路径，允许通过查询参数传递 Token
    private static final String SSE_LOG_STREAM_PATH = "/api/logs/stream";
    // MDC 中存储用户名的键名
    private static final String MDC_USERNAME_KEY = "username";
    
    // 执行认证过滤逻辑：提取 Token → 验证 → 设置 SecurityContext
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        
        try {
            // 优先从 Authorization 请求头获取 Token（推荐方式，安全性最高）
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            } else if (isSseLogStreamRequest(request) && StringUtils.hasText(request.getParameter("token"))) {
                // 仅对 SSE 日志流允许查询参数传递 Token
                // 原因：浏览器 EventSource API 不支持设置自定义请求头
                jwt = request.getParameter("token");
                log.debug("从查询参数获取 Token（SSE 日志流），IP: {}", request.getRemoteAddr());
            } else {
                // 请求中没有 Token，放行由后续安全机制处理
                filterChain.doFilter(request, response);
                return;
            }
            
            // 验证 Token 是否有效
            if (!jwtService.validateToken(jwt)) {
                sendUnauthorizedResponse(response, "Token 无效或已过期");
                return;
            }
            
            // 从 Token 中提取用户名
            username = jwtService.extractUsername(jwt);
            
            // 如果 SecurityContext 中还没有认证信息，则进行认证
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // 创建认证对象，包含用户名、密码（null）和角色权限
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
                    );
                    // 设置请求详情（IP、Session 等）
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // 将认证信息设置到 SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    // 将用户名放入 MDC，供日志线程使用
                    MDC.put(MDC_USERNAME_KEY, username);
                    log.debug("用户已认证:{}, 角色: {}", username, user.getRole().name());
                } else {
                    log.warn("找不到用户:{}", username);
                }
            }
            
            // 继续执行过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束时清理 MDC，避免内存泄漏
            MDC.remove(MDC_USERNAME_KEY);
        }
    }
    
    // 判断是否为 SSE 日志流请求：使用 startsWith 匹配，支持路径后带参数
    private boolean isSseLogStreamRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null && requestUri.startsWith(SSE_LOG_STREAM_PATH);
    }
    
    // 发送未授权响应：设置 401 状态码 + JSON 格式错误信息
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", message);
        
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
