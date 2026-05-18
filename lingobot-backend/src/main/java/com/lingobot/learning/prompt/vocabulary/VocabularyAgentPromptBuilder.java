package com.lingobot.learning.prompt.vocabulary;

import com.lingobot.learning.agent.dto.MemoryRecallPlan;
import org.springframework.stereotype.Component;

@Component
public class VocabularyAgentPromptBuilder {

    public String buildSystemPrompt() {
        return """
            你是一个词汇学习记忆规划助手。根据用户的学习意图和当前状态，决定应该从各级记忆中抓取多少条记录。
            
            记忆层级说明：
            - L1_RECENT: 最近7天接触过的词（短期去重）
            - L1_WRONG: 最近14天答错的词（复习优先）
            - L1_REGENERATED: 最近14天重新生成过的词（用户不满意信号）
            - L2_MASTERED: 已掌握词（掌握度>=80%）
            - L2_REVIEWING: 复习中词
            - L2_LEARNING: 学习中词
            - L2_WEAK: 薄弱词（掌握度<40%，错误>=1）
            
            输出格式要求（严格JSON，不要其他内容）：
            {
              "l1RecentLimit": 数量,
              "l1WrongLimit": 数量,
              "l1RegeneratedLimit": 数量,
              "l2MasteredLimit": 数量,
              "l2ReviewingLimit": 数量,
              "l2LearningLimit": 数量,
              "l2WeakLimit": 数量,
              "reasoning": "简短说明为什么这样分配"
            }
            
            限制：每个数量范围 0-50，0表示不需要。
            """;
    }

    public String buildUserPrompt(String intent, String currentWord, String userMessage, MemoryRecallPlan heuristicPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户学习意图: ").append(intent != null ? intent : "next_word").append("\n\n");

        if (currentWord != null && !currentWord.isBlank()) {
            sb.append("当前学习的单词: ").append(currentWord).append("\n\n");
        }

        if (userMessage != null && !userMessage.isBlank()) {
            sb.append("用户最新消息: ").append(userMessage).append("\n\n");
        }

        sb.append("启发式建议的抓取数量:\n");
        sb.append("- L1_RECENT: ").append(heuristicPlan.getL1RecentLimit()).append("\n");
        sb.append("- L1_WRONG: ").append(heuristicPlan.getL1WrongLimit()).append("\n");
        sb.append("- L1_REGENERATED: ").append(heuristicPlan.getL1RegeneratedLimit()).append("\n");
        sb.append("- L2_MASTERED: ").append(heuristicPlan.getL2MasteredLimit()).append("\n");
        sb.append("- L2_REVIEWING: ").append(heuristicPlan.getL2ReviewingLimit()).append("\n");
        sb.append("- L2_LEARNING: ").append(heuristicPlan.getL2LearningLimit()).append("\n");
        sb.append("- L2_WEAK: ").append(heuristicPlan.getL2WeakLimit()).append("\n\n");
        sb.append("启发式理由: ").append(heuristicPlan.getReasoning()).append("\n\n");
        sb.append("请根据用户意图调整抓取数量，输出JSON格式的计划。");

        return sb.toString();
    }
}
