package com.lingobot.infrastructure.common.config;

import com.lingobot.infrastructure.log.appender.SseLogAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
@RequiredArgsConstructor
public class SseLogAppenderConfig {

    private final ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        SseLogAppender.setApplicationContextStatic(applicationContext);
    }
}
