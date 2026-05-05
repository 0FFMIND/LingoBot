package com.lingobot;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;

/**
 * LingoBot 后端应用主类
 * 负责启动 Spring Boot 应用并加载环境配置
 */
@Slf4j
@EnableAsync
@SpringBootApplication
public class LingoBotApplication {
    
    /**
     * 应用入口方法
     * 先加载 .env 环境配置，再启动 Spring Boot 应用
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(LingoBotApplication.class, args);
    }
    
    /**
     * 加载 .env 环境配置文件
     * 优先从当前工作目录查找，若不存在则查找父目录
     * 已存在的系统属性或环境变量不会被覆盖
     */
    private static void loadDotenv() {
        String userDir = System.getProperty("user.dir");
        File dotenvFile = new File(userDir, ".env");

        // 若当前目录不存在 .env 文件，则尝试从父目录查找
        if (!dotenvFile.exists()) {
            File parentDir = new File(userDir).getParentFile();
            if (parentDir != null) {
                dotenvFile = new File(parentDir, ".env");
            }
        }

        // 加载 .env 文件中的配置项到系统属性
        if (dotenvFile.exists()) {
            log.info("加载 .env 文件: {}", dotenvFile.getAbsolutePath());

            Dotenv dotenv = Dotenv.configure()
                    .directory(dotenvFile.getParent())
                    .filename(".env")
                    .load();

            long[] count = {0};
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                // 仅加载尚未存在的系统属性和环境变量
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, entry.getValue());
                    count[0]++;
                }
            });
            log.info("已加载 {} 个配置项", count[0]);
        } else {
            log.warn("未找到 .env 文件，将依赖环境变量或默认值");
        }
    }
}
