package com.lingobot.learning.vocabulary.service;

import org.springframework.stereotype.Service;

@Service
public class VocabularyPromptService {

    public String getDisplayFlashcardPrompt(String vocabularyCategory, String vocabularyDifficulty) {
        StringBuilder prompt = new StringBuilder("""
                浣犳槸涓€鍚嶄笓涓氱殑鑻辫璇嶆眹鏁欏笀銆?                褰撳墠浠诲姟锛氱敓鎴愪竴寮犳柊鐨勮嫳鏂囧崟璇嶅崱銆?
                浣犲繀椤昏皟鐢?vocabulary 宸ュ叿锛屽苟涓斿彧璋冪敤 display_flashcard銆?                涓嶈鐢ㄦ櫘閫氭枃鏈洖绛斻€?
                鍗曡瘝鍗¤姹傦細
                - word: 绗﹀悎褰撳墠绫诲埆鍜岄毦搴︾殑鑻辨枃鍗曡瘝
                - phonetic: IPA 闊虫爣
                - partOfSpeech: n., v., adj., adv., prep., conj., pron., interj., 鎴?det.
                - meaning: 鍑嗙‘銆佺畝娲佺殑涓枃閲婁箟
                - example: 浣跨敤璇ュ崟璇嶇殑鑷劧鑻辨枃渚嬪彞锛岄毦搴﹀繀椤诲尮閰?                - exampleTranslation: example 鐨勫噯纭腑鏂囩炕璇?                - synonyms: 3-5 涓嫳鏂囧悓涔夎瘝
                - vocabularyCategory 鍜?vocabularyDifficulty 蹇呴』涓庣敤鎴烽€夋嫨涓€鑷?
                鍙ュ瓙闅惧害鎸囧崡锛?                - A1/A2锛孖ELTS 4.0-5.0锛孴OEFL 60-80锛氱畝鍗曞彞锛?-10 涓崟璇?                - B1锛孖ELTS 5.5-6.5锛孴OEFL 81-100锛氬鍚堝彞锛?0-15 涓崟璇?                - B2/C1锛孖ELTS 7.0-8.0锛孴OEFL 101-110锛氬鏉傚彞锛?5-25 涓崟璇?                - C2锛孖ELTS 8.5-9.0锛孴OEFL 111-120锛氶珮绾у彞锛?5 涓崟璇嶄互涓?                """);

        if (vocabularyCategory != null && vocabularyDifficulty != null) {
            prompt.append("\n").append(buildVocabularyInstruction(vocabularyCategory, vocabularyDifficulty));
        }
        return prompt.toString();
    }

    public String getMeaningCheckPrompt(
            String word,
            String phonetic,
            String partOfSpeech,
            String correctMeaning,
            String example,
            String exampleTranslation,
            String userMeaning) {
        return """
                浣犳槸涓€鍚嶄弗璋ㄧ殑鑻辫璇嶆眹鏁欏笀銆?                褰撳墠浠诲姟锛氭鏌ョ敤鎴风粰鍑虹殑涓枃閲婁箟鏄惁鍑嗙‘銆?
                浣犲繀椤昏皟鐢?vocabulary 宸ュ叿锛屽苟涓斿彧璋冪敤 check_meaning_accuracy銆?                涓嶈鐢ㄦ櫘閫氭枃鏈洖绛斻€?
                褰撳墠姝ｅ湪瀛︿範鐨勫崟璇嶅崱锛?                - word: %s
                - phonetic: %s
                - partOfSpeech: %s
                - correct meaning: %s
                - example: %s
                - exampleTranslation: %s

                鐢ㄦ埛鏈杈撳叆鐨勪腑鏂囬噴涔夛細
                %s

                鍒ゆ柇鏍囧噯锛?                - 濡傛灉鐢ㄦ埛閲婁箟瑕嗙洊浜嗘牳蹇冨惈涔夛紝鍗充娇琛ㄨ揪涓嶅畬鍏ㄤ竴鑷达紝涔熷彲浠ュ垽涓烘纭€?                - 濡傛灉鐢ㄦ埛鍙啓浜嗘棤鍏冲唴瀹广€佽繃瀹芥硾銆佽繃鐙獎鎴栨槑鏄鹃敊璇紝鍒や负閿欒銆?                - check_feedback 鐢ㄤ腑鏂囧啓 1-2 鍙ワ紝鍏堢粰缁撹锛屽啀璇存槑姝ｇ‘鍚箟銆?
                宸ュ叿鍙傛暟瑕佹眰锛?                - action: "check_meaning_accuracy"
                - word: 褰撳墠鍗曡瘝
                - user_meaning: 鐢ㄦ埛杈撳叆鐨勪腑鏂囬噴涔?                - is_correct: true 鎴?false
                - check_feedback: 涓枃鍙嶉
                """.formatted(
                textOrUnknown(word),
                textOrUnknown(phonetic),
                textOrUnknown(partOfSpeech),
                textOrUnknown(correctMeaning),
                textOrUnknown(example),
                textOrUnknown(exampleTranslation),
                textOrUnknown(userMeaning));
    }

    public String getSentenceAnalysisPrompt(
            String word,
            String phonetic,
            String partOfSpeech,
            String meaning,
            String chineseSentence,
            String userEnglishSentence) {
        return """
                浣犳槸涓€鍚嶄笓涓氱殑鑻辫鍐欎綔涓庤瘝姹囨暀甯堛€?                褰撳墠浠诲姟锛氬垎鏋愮敤鎴锋牴鎹腑鏂囦緥鍙ュ啓鍑虹殑鑻辨枃鍙ュ瓙銆?
                浣犲繀椤昏皟鐢?vocabulary 宸ュ叿锛屽苟涓斿彧璋冪敤 analyze_sentence銆?                涓嶈鐢ㄦ櫘閫氭枃鏈洖绛斻€?
                褰撳墠姝ｅ湪瀛︿範鐨勫崟璇嶅崱锛?                - word: %s
                - phonetic: %s
                - partOfSpeech: %s
                - meaning: %s

                涓枃渚嬪彞锛?                %s

                鐢ㄦ埛鏈鍐欏嚭鐨勮嫳鏂囧彞瀛愶細
                %s

                鍒ゆ柇鏍囧噯锛?                - meaning_matches: 鑻辨枃鍙ュ瓙鏄惁琛ㄨ揪浜嗕腑鏂囦緥鍙ョ殑鏍稿績鎰忔€濄€?                - has_new_word: 鑻辨枃鍙ュ瓙鏄惁鑷劧銆佹纭湴鍖呭惈褰撳墠鏂板崟璇嶃€?                - feedback 鐢ㄤ腑鏂囧啓 2-3 鍙ワ紝鎸囧嚭浼樼偣銆侀敊璇拰鍙敼杩涜〃杈俱€?
                宸ュ叿鍙傛暟瑕佹眰锛?                - action: "analyze_sentence"
                - word: 褰撳墠鍗曡瘝
                - meaning_matches: true 鎴?false
                - has_new_word: true 鎴?false
                - feedback: 涓枃鍙嶉
                """.formatted(
                textOrUnknown(word),
                textOrUnknown(phonetic),
                textOrUnknown(partOfSpeech),
                textOrUnknown(meaning),
                textOrUnknown(chineseSentence),
                textOrUnknown(userEnglishSentence));
    }

    private String buildVocabularyInstruction(String category, String difficulty) {
        String normalizedCategory = category.toLowerCase();
        String normalizedDifficulty = difficulty.toLowerCase();
        StringBuilder sb = new StringBuilder();

        sb.append("Selected vocabulary category: ").append(normalizedCategory).append("\n");
        sb.append("Selected vocabulary difficulty: ").append(normalizedDifficulty).append("\n\n");

        switch (normalizedCategory) {
            case "cefr" -> {
                sb.append("Allowed CEFR difficulties: a1, a2, b1, b2, c1, c2.\n");
                sb.append("Generate a word at CEFR ").append(normalizedDifficulty.toUpperCase()).append(" level.\n");
            }
            case "ielts" -> {
                sb.append("Allowed IELTS score bands: 4.0-5.0, 5.5-6.5, 7.0-8.0, 8.5-9.0.\n");
                sb.append("Generate a word appropriate for IELTS band ").append(normalizedDifficulty).append(".\n");
            }
            case "toefl" -> {
                sb.append("Allowed TOEFL score bands: 60-80, 81-100, 101-110, 111-120.\n");
                sb.append("Generate a word appropriate for TOEFL score band ").append(normalizedDifficulty).append(".\n");
            }
            default -> sb.append("Use the selected category and difficulty exactly as provided.\n");
        }

        sb.append("The tool result must set vocabularyCategory and vocabularyDifficulty to these exact selected values.\n");
        return sb.toString();
    }

    private String textOrUnknown(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }
}

