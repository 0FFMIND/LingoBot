package com.lingobot.infrastructure.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会话相关配置属性类，从配置文件读取 conversation.* 前缀的配置项。
 * 配置会话列表和消息历史的默认返回数量，避免代码中出现魔法数字。
 */
@Data
@Component
@ConfigurationProperties(prefix = "conversation")
public class ConversationProperties {

    // 会话列表默认返回的最近会话数量
    private int defaultConversationListSize = 20;

    // 消息历史默认返回的最近消息数量
    private int defaultMessageHistorySize = 10;
}
