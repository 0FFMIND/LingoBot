package com.lingobot.infrastructure.log.controller;

import com.lingobot.infrastructure.log.service.LogPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 日志 API 控制器 * 提供日志相关的REST 接口，主要用于SSE 日志流推送 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogPushService logPushService;

    /**
     * 获取实时日志流     * 通过 SSE (Server-Sent Events) 技术实时推送应用日志到前端
     * 连接建立后会先发送历史日志，然后实时推送新产生的日志     * @return SseEmitter 对象，用于与客户端保持长连接
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        return logPushService.createEmitter();
    }
}
