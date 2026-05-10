package com.lingobot.infrastructure.log.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.lingobot.infrastructure.log.service.LogPushService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 自定义 Logback Appender，将日志通过 SSE 推送到前端。
 * 
 * 注意：Logback 在 Spring 容器之前初始化，所以使用静态变量加延迟初始化的方式来获取 Spring Bean，
 * 由 SseLogAppenderConfig 在应用启动时注入 ApplicationContext。
 */
public class SseLogAppender extends AppenderBase<ILoggingEvent> implements ApplicationContextAware {

    // 日志推送服务，用于将日志推送到前端
    private static volatile LogPushService logPushService;
    // Spring 应用上下文，用于在非 Spring 管理的类中获取 Bean
    private static volatile ApplicationContext applicationContext;
    // 同步锁对象
    private static final Object lock = new Object();
    // 是否已初始化标志
    private static boolean initialized = false;

    /**
     * 设置 Spring 应用上下文（供外部调用，因为 Logback Appender 不是 Spring Bean）。
     * 在应用启动时由 SseLogAppenderConfig 调用。
     */
    public static void setApplicationContextStatic(ApplicationContext context) {
        synchronized (lock) {
            if (applicationContext == null && context != null) {
                applicationContext = context;
                if (logPushService == null) {
                    try {
                        logPushService = context.getBean(LogPushService.class);
                        initialized = true;
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    // 设置 Spring 应用上下文，ApplicationContextAware 接口方法
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        setApplicationContextStatic(context);
    }

    // 追加日志事件，将日志推送到所有活跃的 SSE 连接
    @Override
    protected void append(ILoggingEvent event) {
        // 延迟初始化 logPushService
        if (!initialized) {
            synchronized (lock) {
                if (!initialized && applicationContext != null) {
                    try {
                        logPushService = applicationContext.getBean(LogPushService.class);
                        initialized = true;
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        }
        
        // 如果 logPushService 还未初始化，直接返回
        if (logPushService == null) {
            return;
        }

        // 过滤掉 LogPushService 自身的日志，避免循环推送
        String loggerName = event.getLoggerName();
        if (loggerName.equals(LogPushService.class.getName())) {
            return;
        }

        // 从 Logback 事件中提取日志级别和格式化后的消息（参数已由 Logback 填充完毕）
        Level level = event.getLevel();
        String levelStr = level.toString();
        String message = event.getFormattedMessage();
        
        // 提取异常对象
        Throwable throwable = null;
        if (event.getThrowableProxy() != null && event.getThrowableProxy() instanceof ThrowableProxy) {
            throwable = ((ThrowableProxy) event.getThrowableProxy()).getThrowable();
        }

        // 获取当前用户标识
        String userIdentifier = getUserIdentifier();

        // 推送日志
        logPushService.pushLog(levelStr, loggerName, message, throwable, userIdentifier);
    }

    /**
     * 获取当前用户标识。
     * 如果用户已登录，返回格式为 "[USER 邮箱]"；
     * 如果未登录或获取失败，返回 "[SYSTEM]"。
     */
    private String getUserIdentifier() {
        try {
            // 从 SecurityContext 中获取认证信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return "[SYSTEM]";
            }
            
            String username = authentication.getName();
            if (username == null || username.isBlank()) {
                return "[SYSTEM]";
            }

            return "[USER " + username + "]";
        } catch (Exception e) {
            return "[SYSTEM]";
        }
    }
}
