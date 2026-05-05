package com.lingobot.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyCardDTO {

    private Long id;
    private Long conversationId;
    private String word;
    private String phonetic;
    private String meaning;
    private String example;
    private String exampleTranslation;
    private List<String> synonyms;
    private List<String> antonyms;
    private String level;
    private Integer position;
    private String userMeaningGuess;
    private String meaningCheckResult;
    private Boolean meaningIsCorrect;
    private Boolean meaningCheckCompleted;
    private String userSentence;
    private String aiFeedback;
    private Boolean isCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 是否已被重新生成（true表示已被替换，前端不展示�?*/
    private Boolean isRegenerated;
    /** 重新生成次数索引�?=原始�?=�?次重新生�?..�?*/
    private Integer regenerationIndex;
    /** 该位置被重新生成过的历史单词列表（用于展示用户不满意的单词） */
    private List<String> regeneratedWords;

    private Boolean hasPrev;
    private Boolean hasNext;
    private Integer totalCount;
    private Integer currentIndex;
}
