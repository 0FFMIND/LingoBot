package com.lingobot.infrastructure.common.config;

import com.lingobot.infrastructure.log.appender.SseLogAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * SSE 日志追加器配置类。
 * 应用启动时将 ApplicationContext 注入到 SseLogAppender 中，
 * 使日志追加器能够获取 Spring 管理的 Bean 来推送日志到前端。
 */
@Configuration
@RequiredArgsConstructor
public class SseLogAppenderConfig {

    // Spring 应用上下文，用于在非 Spring 管理的类中获取 Bean
    private final ApplicationContext applicationContext;

    // 应用启动完成后执行，将 ApplicationContext 设置到 SseLogAppender 的静态字段
    @PostConstruct
    public void init() {
        SseLogAppender.setApplicationContextStatic(applicationContext);
    }
}
