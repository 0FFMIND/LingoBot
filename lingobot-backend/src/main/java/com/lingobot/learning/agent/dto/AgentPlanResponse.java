package com.lingobot.learning.agent.dto;

import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 词汇 Agent 规划响应 DTO。
 *
 * 封装记忆规划的完整结果，包含三部分：
 * - plan：LLM 生成的记忆抓取计划（各级记忆的抓取数量）
 * - memoryContext：根据计划实际抓取到的记忆上下文数据
 * - conversationId：解析后的内部会话 ID（供下游使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // 记忆抓取计划，包含各级记忆的抓取数量限制和规划理由
    private MemoryRecallPlan plan;

    // 实际检索到的词汇记忆上下文，包含去重词列表和历史记录
    private VocabularyMemoryContext memoryContext;

    // 解析后的内部会话 ID，请求未提供 conversationPublicId 时为 null
    private Long conversationId;
}
