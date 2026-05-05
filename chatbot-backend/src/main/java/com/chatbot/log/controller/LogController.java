package com.lingobot.log.controller;

import com.lingobot.log.service.LogPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 日志 API 控制�? * 提供日志相关�?REST 接口，主要用�?SSE 日志流推�? */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogPushService logPushService;

    /**
     * 获取实时日志�?     * 通过 SSE (Server-Sent Events) 技术实时推送应用日志到前端
     * 连接建立后会先发送历史日志，然后实时推送新产生的日�?     * @return SseEmitter 对象，用于与客户端保持长连接
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        return logPushService.createEmitter();
    }
}
