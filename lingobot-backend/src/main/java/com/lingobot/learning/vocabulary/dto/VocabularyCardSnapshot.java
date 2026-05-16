package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyCardSnapshot {

    private Long id;
    private Long userId;
    private Long conversationId;
    private Long vocabularyWordId;
    private Long userVocabularyId;

    private String word;
    private String phonetic;
    private String partOfSpeech;
    private String meaning;
    private String example;
    private String exampleTranslation;

    private String userMeaningGuess;
    private Boolean meaningIsCorrect;
    private String meaningCheckResult;
    private String chineseSentenceForTranslation;
    private Boolean meaningCheckCompleted;

    private String userEnglishSentence;
    private Boolean sentenceMeaningMatches;
    private Boolean sentenceHasNewWord;
    private String sentenceAnalysisResult;
    private Boolean sentenceAnalysisCompleted;

    private BigDecimal masteryScore;
    private Boolean isCompleted;

    private Long lastEventId;
    private java.time.LocalDateTime lastEventTime;
}
