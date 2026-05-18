package com.lingobot.learning.prompt.vocabulary;

import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import org.springframework.stereotype.Component;

import static com.lingobot.learning.prompt.vocabulary.VocabularyPreferencePromptBuilder.buildVocabularyInstruction;
import static com.lingobot.learning.util.PromptStringUtils.textOrUnknown;

@Component
public class VocabularyCardGenerationPromptBuilder {

    public String getBatchFlashcardPrompt(String vocabularyCategory, String vocabularyDifficulty, int cardCount) {
        return getBatchFlashcardPrompt(vocabularyCategory, vocabularyDifficulty, cardCount, VocabularyGenerationIntent.NEW_WORD);
    }

    public String getBatchFlashcardPrompt(String vocabularyCategory, String vocabularyDifficulty, int cardCount, VocabularyGenerationIntent intent) {
        StringBuilder prompt = new StringBuilder();
        
        String learningModeDescription = switch (intent) {
            case NEW_WORD -> """
                    你是一名专业的英语词汇教师。
                    当前任务：生成 %d 张新的英文单词卡。
                    学习模式：学习新单词
                    重点：优先生成用户从未学习过的全新单词，帮助用户扩展词汇量。
                    """.formatted(cardCount);
            case REVIEW -> """
                    你是一名专业的英语词汇教师。
                    当前任务：生成 %d 张英文单词卡。
                    学习模式：复习单词
                    重点：优先从用户已学习过但需要复习的单词中选择。
                    如果用户需要复习的单词不足以生成 %d 张，可以补充一些新单词。
                    """.formatted(cardCount, cardCount);
            case HYBRID -> """
                    你是一名专业的英语词汇教师。
                    当前任务：生成 %d 张英文单词卡。
                    学习模式：混合模式
                    重点：50%% 是需要复习的单词，50%% 是新单词。
                    如果复习单词不足，可以用新单词补充。
                    """.formatted(cardCount);
            default -> """
                    你是一名专业的英语词汇教师。
                    当前任务：生成 %d 张新的英文单词卡。
                    学习模式：学习新单词
                    重点：优先生成用户从未学习过的全新单词，帮助用户扩展词汇量。
                    """.formatted(cardCount);
        };

        prompt.append(learningModeDescription);
        
        prompt.append("""
                你必须调用 vocabulary 工具，并且只调用 display_flashcard_batch。
                不要用普通文本回答。

                每张单词卡要求：
                - word: 符合当前类别和难度的英文单词
                - phonetic: IPA 音标
                - partOfSpeech: n., v., adj., adv., prep., conj., pron., interj. 或 det.
                - meaning: 准确、简洁的中文释义
                - example: 使用该单词的自然英文例句，难度必须匹配
                - exampleTranslation: example 的准确中文翻译
                - synonyms: 3-5 个英文同义词
                - vocabularyCategory 和 vocabularyDifficulty 必须与用户选择一致

                句子难度指南：
                - A1/A2，IELTS 4.0-5.0，TOEFL 60-80：简单句，5-10 个单词
                - B1，IELTS 5.5-6.5，TOEFL 81-100：复合句，10-15 个单词
                - B2/C1，IELTS 7.0-8.0，TOEFL 101-110：复杂句，15-25 个单词
                - C2，IELTS 8.5-9.0，TOEFL 111-120：高级句，25 个单词以上

                重要要求：
                1. 生成的 %d 个单词必须互不相同
                2. 所有单词必须符合指定的类别和难度
                3. 返回格式必须是一个包含 cards 数组的 JSON，每个元素是单词卡对象
                """.formatted(cardCount));

        if (vocabularyCategory != null && vocabularyDifficulty != null) {
            prompt.append("\n").append(buildVocabularyInstruction(vocabularyCategory, vocabularyDifficulty));
        }
        return prompt.toString();
    }

    public String getRegenerateFlashcardPrompt(
            String vocabularyCategory,
            String vocabularyDifficulty,
            int position,
            String oldWord,
            String oldPartOfSpeech,
            String oldMeaning) {
        StringBuilder prompt = new StringBuilder("""
                你是一名专业的英语词汇教师。
                当前任务：重新生成一张英文单词卡。
                上下文：用户对【位置 %d】的单词卡不满意，需要生成一张全新的、不同的单词卡来替换。

                被替换的旧单词卡信息：
                - 位置：%d
                - 单词：%s
                - 词性：%s
                - 释义：%s

                要求：
                1. 生成的新单词必须与旧单词不同
                2. 新单词必须符合指定的类别和难度
                3. 不要生成用户当前会话已经学习过或重新生成过的单词（请查看下方记忆上下文）

                你必须调用 vocabulary 工具，并且只调用 display_flashcard。
                不要用普通文本回答。

                单词卡要求：
                - word: 符合当前类别和难度的英文单词
                - phonetic: IPA 音标
                - partOfSpeech: n., v., adj., adv., prep., conj., pron., interj. 或 det.
                - meaning: 准确、简洁的中文释义
                - example: 使用该单词的自然英文例句，难度必须匹配
                - exampleTranslation: example 的准确中文翻译
                - synonyms: 3-5 个英文同义词
                - vocabularyCategory 和 vocabularyDifficulty 必须与用户选择一致

                句子难度指南：
                - A1/A2，IELTS 4.0-5.0，TOEFL 60-80：简单句，5-10 个单词
                - B1，IELTS 5.5-6.5，TOEFL 81-100：复合句，10-15 个单词
                - B2/C1，IELTS 7.0-8.0，TOEFL 101-110：复杂句，15-25 个单词
                - C2，IELTS 8.5-9.0，TOEFL 111-120：高级句，25 个单词以上
                """.formatted(
                        position, position,
                        textOrUnknown(oldWord),
                        textOrUnknown(oldPartOfSpeech),
                        textOrUnknown(oldMeaning)));

        if (vocabularyCategory != null && vocabularyDifficulty != null) {
            prompt.append("\n").append(buildVocabularyInstruction(vocabularyCategory, vocabularyDifficulty));
        }
        return prompt.toString();
    }
}
