package com.lingobot.infrastructure.log.controller;

import com.lingobot.infrastructure.common.config.AppProperties;
import com.lingobot.infrastructure.log.service.LogPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogPushService logPushService;
    private final AppProperties appProperties;

    @PreAuthorize("@appProperties.isDev() or hasRole('ADMIN')")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        return logPushService.createEmitter();
    }

    @GetMapping("/dev-check")
    public boolean isDev() {
        return appProperties.isDev();
    }
}
