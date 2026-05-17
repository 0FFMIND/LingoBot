package com.lingobot.infrastructure.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 应用全局配置属性类，从配置文件读取 app.* 前缀的配置项。
 * <p>
 * application.yml 中的 app.env 使用 ${APP_ENV:production}，
 * 因此可以通过环境变量 APP_ENV 覆盖默认运行环境。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // 当前运行环境。默认 production；设置环境变量 APP_ENV=dev/development 可启用开发环境特性。
    private String env = "production";

    // 判断当前是否为开发环境，支持 dev 和 development 两种写法（不区分大小写）
    public boolean isDev() {
        String normalizedEnv = env != null ? env.trim() : "";
        return "dev".equalsIgnoreCase(normalizedEnv) || "development".equalsIgnoreCase(normalizedEnv);
    }
}
