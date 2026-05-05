package com.lingobot.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.lingobot.auth.entity.User;
import com.lingobot.auth.repository.UserRepository;
import com.lingobot.log.service.LogPushService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SseLogAppender extends AppenderBase<ILoggingEvent> implements ApplicationContextAware {

    private static volatile LogPushService logPushService;
    private static volatile ApplicationContext applicationContext;
    private static final Object lock = new Object();

    private static boolean initialized = false;

    public static void setApplicationContextStatic(ApplicationContext context) {
        synchronized (lock) {
            if (applicationContext == null && context != null) {
                applicationContext = context;
                if (logPushService == null) {
                    try {
                        logPushService = context.getBean(LogPushService.class);
                        initialized = true;
                    } catch (Exception e) {
                        // Ignore if bean not available yet
                    }
                }
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        setApplicationContextStatic(context);
    }

    @Override
    protected void append(ILoggingEvent event) {
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
        
        if (logPushService == null) {
            return;
        }

        String loggerName = event.getLoggerName();
        if (!loggerName.startsWith("com.lingobot")) {
            return;
        }
        
        // 排除 LogPushService 的日志，避免无限递归
        if (loggerName.equals("com.lingobot.log.service.LogPushService")) {
            return;
        }

        Level level = event.getLevel();
        String levelStr = level.toString();
        String message = event.getFormattedMessage();
        
        Throwable throwable = null;
        if (event.getThrowableProxy() != null && event.getThrowableProxy() instanceof ThrowableProxy) {
            throwable = ((ThrowableProxy) event.getThrowableProxy()).getThrowable();
        }

        // 获取用户标识，格式为 [USER 邮箱] �?[SYSTEM]
        String userIdentifier = getUserIdentifier();

        logPushService.pushLog(levelStr, loggerName, message, throwable, userIdentifier);
    }

    /**
     * 获取当前用户标识
     * 如果有登录用户，返回 [USER 邮箱] 格式
     * 如果没有登录用户，返�?[SYSTEM]
     */
    private String getUserIdentifier() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() 
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                if (username != null && applicationContext != null) {
                    try {
                        UserRepository userRepository = applicationContext.getBean(UserRepository.class);
                        User user = userRepository.findByUsername(username).orElse(null);
                        if (user != null && user.getEmail() != null) {
                            return "[USER " + user.getEmail() + "]";
                        }
                    } catch (Exception e) {
                        // 获取用户信息失败时，使用 SYSTEM 标识
                    }
                }
            }
        } catch (Exception e) {
            // SecurityContextHolder 可能在非请求线程中不可用，忽略异�?        }
        return "[SYSTEM]";
    }
}
