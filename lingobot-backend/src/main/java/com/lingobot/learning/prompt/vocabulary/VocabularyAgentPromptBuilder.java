package com.lingobot.learning.prompt.vocabulary;

import com.lingobot.learning.agent.dto.MemoryRecallPlan;
import com.lingobot.learning.vocabulary.common.dto.ConversationOverviewDTO;
import com.lingobot.learning.vocabulary.progress.dto.response.VocabularyStatsDTO;
import org.springframework.stereotype.Component;

@Component
public class VocabularyAgentPromptBuilder {

    public String buildNormalSystemPrompt() {
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
              "recommendedIntent": "new_word|review|hybrid",
              "l1RecentLimit": 数量,
              "l1WrongLimit": 数量,
              "l1RegeneratedLimit": 数量,
              "l2MasteredLimit": 数量,
              "l2ReviewingLimit": 数量,
              "l2LearningLimit": 数量,
              "l2WeakLimit": 数量,
              "reasoning": "简短说明为什么这样分配"
            }
            
            规则说明：
            1. 用户意图已经明确指定了学习模式（new_word/review/hybrid），recommendedIntent 返回与输入相同的值
            2. 根据学习模式调整各级记忆的抓取数量：
               - new_word：优先抓取 L1 去重相关层级，L2 已掌握词和复习中词少抓
               - review：优先抓取 L1_WRONG、L2_REVIEWING、L2_WEAK
               - hybrid：均衡分配
            3. 每个数量范围 0-50，0表示不需要。
            """;
    }

    public String buildSmartRecommendSystemPrompt() {
        return """
            你是一个词汇学习记忆规划助手。根据用户的学习状态，先判断最合适的学习模式，再决定应该从各级记忆中抓取多少条记录。
            
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
              "recommendedIntent": "new_word|review|hybrid",
              "l1RecentLimit": 数量,
              "l1WrongLimit": 数量,
              "l1RegeneratedLimit": 数量,
              "l2MasteredLimit": 数量,
              "l2ReviewingLimit": 数量,
              "l2LearningLimit": 数量,
              "l2WeakLimit": 数量,
              "reasoning": "简短说明为什么选择这个模式和这样分配"
            }
            
            规则说明：
            1. 先根据用户学习状态判断最合适的学习模式：
               - 待复习词多或薄弱词多或新词多 → review（优先复习）
               - 已掌握词多且待复习词少 → new_word（优先学新词）
               - 两者均衡 → hybrid（混合模式）
               然后在 recommendedIntent 字段返回你选择的模式（new_word/review/hybrid）
            2. 根据你选择的学习模式调整各级记忆的抓取数量：
               - new_word：优先抓取 L1 去重相关层级，L2 已掌握词和复习中词少抓
               - review：优先抓取 L1_WRONG、L2_REVIEWING、L2_WEAK
               - hybrid：均衡分配
            3. 每个数量范围 0-50，0表示不需要。
            """;
    }

    public String buildUserPrompt(String intent, String currentWord, String userMessage, MemoryRecallPlan heuristicPlan) {
        return buildUserPrompt(intent, currentWord, userMessage, heuristicPlan, null, null);
    }

    public String buildUserPrompt(String intent, String currentWord, String userMessage,
                                  MemoryRecallPlan heuristicPlan,
                                  VocabularyStatsDTO learningOverview,
                                  ConversationOverviewDTO conversationOverview) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户学习意图: ").append(intent != null ? intent : "next_word").append("\n\n");

        if (learningOverview != null) {
            sb.append("用户学习概览:\n");
            sb.append("- 总词汇量: ").append(learningOverview.getTotalCount()).append("\n");
            sb.append("- 新词: ").append(learningOverview.getNewCount()).append("\n");
            sb.append("- 学习中: ").append(learningOverview.getLearningCount()).append("\n");
            sb.append("- 复习中: ").append(learningOverview.getReviewingCount()).append("\n");
            sb.append("- 已掌握: ").append(learningOverview.getMasteredCount()).append("\n");
            sb.append("- 待复习: ").append(learningOverview.getToReviewCount()).append("\n");
            sb.append("- 困难词: ").append(learningOverview.getDifficultCount()).append("\n\n");
        }

        if (conversationOverview != null) {
            sb.append("当前对话概览:\n");
            sb.append("- 有效卡片数: ").append(conversationOverview.getActiveCount()).append("\n");
            sb.append("- 已揭示: ").append(conversationOverview.getRevealedCount()).append("\n");
            sb.append("- 未揭示: ").append(conversationOverview.getHiddenCount()).append("\n");
            sb.append("- 已完成: ").append(conversationOverview.getCompletedCount()).append("\n\n");
        }

        if (currentWord != null && !currentWord.isBlank()) {
            sb.append("当前学习的单词: ").append(currentWord).append("\n\n");
        }

        if (userMessage != null && !userMessage.isBlank()) {
            sb.append("用户最新消息: ").append(userMessage).append("\n\n");
        }

        sb.append("如果无法根据学习状态判断某个层级的抓取数量，该层级默认使用 10。\n");

        return sb.toString();
    }
}
