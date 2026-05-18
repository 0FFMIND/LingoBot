package com.lingobot.learning.prompt.vocabulary;

import org.springframework.stereotype.Component;

@Component
public class VocabularyCompactPromptBuilder {

    public String buildCompactSystemPrompt() {
        return """
                你是 LingoBot 的词汇学习记忆压缩器。你的任务是把较长的词汇学习 context history 压缩成后续生成词卡时可直接使用的记忆摘要。

                压缩目标：
                - 大幅减少 token。
                - 保留会影响后续生成、避重和复习决策的信息。
                - 不编造原始 context history 里没有的信息。
                - 如果输入里包含旧摘要，请把新历史合并进去，输出一份完整的新摘要，而不是只总结新增部分。

                必须保留：
                - 单词、词性、音标、核心中文释义。
                - 用户是否答对、答错、重新生成、不满意。
                - 用户明显薄弱的点，例如释义误解、造句语法问题、搭配不自然。
                - 应避免短期重复生成的词。

                可以压缩或省略：
                - 例句全文，除非用户错误必须依赖该句才能理解。
                - 冗长 AI 反馈，改写成一句短的错误/掌握情况。
                - 重复的说明性文字。

                输出要求：
                - 只输出摘要正文，不要解释你的压缩过程。
                - 使用中文。
                - 使用 Markdown。
                - 没有内容的分区可以省略。

                推荐结构：
                ## 词汇学习历史摘要
                ### 已掌握/较熟悉
                - word [phonetic] (pos.)：中文释义；

                ### 薄弱/需要复习
                - word [phonetic] (pos.)：中文释义；

                ### 重新生成/不满意信号
                - word [phonetic] (pos.)：中文释义；

                """;
    }

    public String buildCompactUserPrompt(String vocabularyHistory, String existingSummary) {
        StringBuilder sb = new StringBuilder();

        if (existingSummary != null && !existingSummary.trim().isEmpty()) {
            sb.append("## 已有压缩摘要\n");
            sb.append(existingSummary);
            sb.append("\n\n");
        }

        sb.append("## 本次需要压缩的 context history\n");
        sb.append(vocabularyHistory);
        sb.append("\n\n");

        sb.append("请根据上面的 context history 输出新的完整压缩摘要。");
        if (existingSummary != null && !existingSummary.trim().isEmpty()) {
            sb.append("注意：必须把已有摘要和本次新历史合并，不能丢失旧摘要中的有效记忆。");
        } else {
            sb.append("请生成一份新的压缩摘要。");
        }
        sb.append("只输出摘要正文。");

        return sb.toString();
    }
}
