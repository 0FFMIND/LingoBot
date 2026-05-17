package com.lingobot.learning.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 词汇 Agent 规划请求 DTO。
 *
 * 封装客户端发起记忆规划所需的全部参数：
 * - 会话标识（用于定位历史记忆上下文）
 * - 用户标识（用于获取用户偏好和个人记忆库）
 * - 学习意图（决定记忆抓取策略）
 * - 可选的用户消息和当前单词（用于 LLM 动态调整规划）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanRequest {

    // 会话公开 ID，用于解析出内部 conversationId，为空则不限制会话范围
    private String conversationPublicId;

    // 用户 ID，用于定位用户的词汇记忆库和偏好设置
    private Long userId;

    // 学习意图：new_word（新词）、review（复习）、hybrid（混合），决定启发式规划的默认抓取数量
    private String intent;

    // 用户最新输入消息（可选），提供给 LLM 理解用户当前需求，动态调整规划
    private String userMessage;

    // 当前学习的单词（可选），提供给 LLM 结合上下文调整记忆抓取策略
    private String currentWord;
}
