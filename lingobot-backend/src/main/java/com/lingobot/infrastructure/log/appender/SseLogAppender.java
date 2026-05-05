package com.lingobot.infrastructure.log.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.infrastructure.log.service.LogPushService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 自定义Logback Appender，将日志通过 SSE 推送到前端
 * 
 * 注意：Logback 在Spring 容器之前初始化，所以使用静态变量+ 延迟初始化来获取 Spring Bean
 */
public class SseLogAppender extends AppenderBase<ILoggingEvent> implements ApplicationContextAware {

    private static volatile LogPushService logPushService;
    private static volatile ApplicationContext applicationContext;
    private static final Object lock = new Object();
    private static boolean initialized = false;

    /**
     * 设置 Spring 应用上下文（供外部调用，因为 Logback Appender 不是 Spring Bean）     */
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

        String userIdentifier = getUserIdentifier();

        logPushService.pushLog(levelStr, loggerName, message, throwable, userIdentifier);
    }

    /**
     * 获取当前用户标识，如果未登录返回 "[SYSTEM]"
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
                    }
                }
            }
        } catch (Exception e) {
        }
        return "[SYSTEM]";
    }
}
