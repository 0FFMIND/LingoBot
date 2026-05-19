package com.lingobot.infrastructure.log.service;

import com.lingobot.infrastructure.common.config.LogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 日志推送服务。
 * 通过 SSE (Server-Sent Events) 技术将应用日志实时推送到前端，
 * 同时维护日志历史记录，供新连接的客户端获取历史日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogPushService {

    private final LogProperties logProperties;

    // 存储所有活跃的 SSE 连接，使用 CopyOnWriteArrayList 保证线程安全
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    // 存储日志历史记录，用于新连接时发送历史日志
    private final List<String> logHistory = new ArrayList<>();

    /**
     * 创建一个新的 SSE 连接。
     * 当客户端连接时，会先发送历史日志，然后实时推送新日志。
     * 
     * @return SseEmitter 对象，用于与客户端保持长连接
     */
    public SseEmitter createEmitter() {
        // 创建一个 5 分钟超时的 SSE 连接
        SseEmitter emitter = new SseEmitter(300000L);

        // 将新连接添加到活跃连接列表
        emitters.add(emitter);

        // 设置连接完成时的回调
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE emitter completed, remaining: {}", emitters.size());
        });

        // 设置连接超时的回调
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("SSE emitter timed out, remaining: {}", emitters.size());
        });

        // 设置连接错误的回调
        emitter.onError((e) -> {
            emitters.remove(emitter);
            log.info("SSE emitter error, remaining: {}", emitters.size());
        });

        // 向新连接的客户端发送历史日志
        synchronized (logHistory) {
            for (String logEntry : logHistory) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("log")
                            .data(logEntry));
                } catch (IOException e) {
                    // 发送失败时忽略，继续发送下一条
                }
            }
        }
        
        return emitter;
    }

    /**
     * 推送日志到所有活跃的 SSE 连接。
     * 同时将日志添加到历史记录中。
     * 
     * @param level 日志级别 (INFO, DEBUG, WARN, ERROR 等)
     * @param logger 日志记录器名称
     * @param message 日志消息内容
     */
    public void pushLog(String level, String logger, String message) {
        pushLog(level, logger, message, null, null);
    }

    /**
     * 推送带有异常堆栈信息的日志。
     * 
     * @param level 日志级别
     * @param logger 日志记录器名称
     * @param message 日志消息内容
     * @param throwable 异常对象，用于提取堆栈信息
     */
    public void pushLog(String level, String logger, String message, Throwable throwable) {
        pushLog(level, logger, message, throwable, null);
    }

    /**
     * 推送日志到所有活跃的 SSE 连接（带用户标识）。
     * 同时将日志添加到历史记录中。
     * 
     * @param level 日志级别 (INFO, DEBUG, WARN, ERROR 等)
     * @param logger 日志记录器名称
     * @param message 日志消息内容
     * @param userIdentifier 用户标识，格式为 "[USER 邮箱]" 或 "[SYSTEM]"
     */
    public void pushLog(String level, String logger, String message, String userIdentifier) {
        pushLog(level, logger, message, null, userIdentifier);
    }

    /**
     * 核心推送方法：推送带有异常堆栈和用户标识的日志。
     * 同时将日志添加到历史记录中。
     * 
     * @param level 日志级别 (INFO, DEBUG, WARN, ERROR 等)
     * @param logger 日志记录器名称
     * @param message 日志消息内容
     * @param throwable 异常对象，用于提取堆栈信息（可为 null）
     * @param userIdentifier 用户标识，格式为 "[USER 邮箱]" 或 "[SYSTEM]"（可为 null）
     */
    public void pushLog(String level, String logger, String message, Throwable throwable, String userIdentifier) {
        // 构建异常堆栈信息字符串
        String fullMessage = message;
        if (throwable != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(message).append("\n");
            sb.append(throwable.getMessage()).append("\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("    at ").append(element.toString()).append("\n");
            }
            fullMessage = sb.toString();
        }

        String timestamp = java.time.LocalTime.now().toString();
        String userPart;
        if (userIdentifier != null && !userIdentifier.isEmpty() && userIdentifier.startsWith("[USER ")) {
            String username = userIdentifier.substring(6, userIdentifier.length() - 1);
            userPart = "[" + username + "]";
        } else {
            userPart = "[SYSTEM]";
        }
        String logEntry = String.format("[%s] [%s] %s %s - %s", timestamp, level, userPart, logger, fullMessage);
        
        // 将日志添加到历史记录，超出最大数量时移除最旧的
        synchronized (logHistory) {
            logHistory.add(logEntry);
            if (logHistory.size() > logProperties.getMaxHistorySize()) {
                logHistory.remove(0);
            }
        }
        
        // 推送日志到所有活跃的 SSE 连接
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(logEntry));
            } catch (IOException e) {
                // 记录发送失败的连接，稍后移除
                deadEmitters.add(emitter);
            }
        }

        // 移除所有已失效的连接
        emitters.removeAll(deadEmitters);
    }
}
