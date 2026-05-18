package com.lingobot.learning.prompt.vocabulary;

import org.springframework.stereotype.Component;

@Component
public class VocabularyAIModifyPromptBuilder {

    private static final String SYSTEM_PROMPT = "You are a professional English vocabulary editor. Return JSON only.";

    private static final String USER_PROMPT_TEMPLATE = """
        请检查这张词汇卡，并填写或完善所有可编辑的显示字段：
        当前卡片：
        - 单词: %s
        - 音标: %s
        - 词性: %s
        - 中文释义: %s
        - 类别: %s
        - 难度: %s

        可选类别：
        - CEFR
        - IELTS
        - TOEFL

        可选难度：
        - CEFR: A1, A2, B1, B2, C1, C2
        - IELTS: 4.0-5.0, 5.5-6.5, 7.0-8.0, 8.5-9.0
        - TOEFL: 60-80, 81-100, 101-110, 111-120

        可选词性：
        n., v., adj., adv., prep., conj., pron., interj., det.

        规则：
        1. 不要修改单词；
        2. 如果某个字段缺失，请填写完整；
        3. 验证类别和难度准确无误：
           - 如果类别和难度不匹配，优先根据已有类别选择最合理的难度；
           - 如果类别缺失但已有难度，则根据难度对应的类别进行填写，然后验证难度是否正确，若不正确则修改为更符合的难度；
           - 如果类别和难度都缺失，请根据单词本身的难度推测类别，再选择合理难度；
        4. 返回所有字段，不仅仅是修改过的字段；
        5. 中文释义必须使用中文。

        返回 JSON 格式如下：
        {
          "phonetic": "...",
          "partOfSpeech": "...",
          "meaning": "...",
          "synonyms": ["...", "..."],
          "category": "...",
          "difficulty": "..."
        }
        """;

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String getUserPrompt(String word, String phonetic, String partOfSpeech, String meaning,
                               String category, String difficulty) {
        return String.format(USER_PROMPT_TEMPLATE, word, phonetic, partOfSpeech, meaning, category, difficulty);
    }
}
