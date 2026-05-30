package com.lingobot.infrastructure.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置类。
 * 启用 Spring 的异步方法支持，并配置线程池，
 * 用于执行词义检查等耗时操作，提升系统响应速度。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    // 配置词义检查专用线程池，核心线程数 2，最大线程数 5，队列容量 20
    @Bean(name = "sseExecutor")
    public ThreadPoolTaskExecutor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Async executor 'sseExecutor' initialized with corePoolSize=4, maxPoolSize=10");
        return executor;
    }

    @Bean(name = "meaningCheckExecutor")
    public ThreadPoolTaskExecutor meaningCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数，即使空闲也保持存活
        executor.setCorePoolSize(2);
        // 最大线程数，当队列满时创建新线程
        executor.setMaxPoolSize(5);
        // 任务队列容量，超过后创建新线程
        executor.setQueueCapacity(20);
        // 线程名前缀，便于日志识别
        executor.setThreadNamePrefix("meaning-check-");
        // 拒绝策略：当线程池满时，由调用线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待任务完成的最大时间，单位秒
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Async executor 'meaningCheckExecutor' initialized with corePoolSize=2, maxPoolSize=5");
        return executor;
    }

    // 获取默认异步执行器，供 @Async 注解使用
    @Override
    public Executor getAsyncExecutor() {
        return meaningCheckExecutor();
    }
}
