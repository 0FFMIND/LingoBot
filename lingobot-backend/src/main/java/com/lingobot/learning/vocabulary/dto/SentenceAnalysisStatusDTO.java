package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentenceAnalysisStatusDTO {
    private Long cardId;
    private String word;
    private String chineseSentenceForTranslation;
    private String userEnglishSentence;
    private Boolean sentenceAnalysisCompleted;
    private Boolean sentenceHasNewWord;
    private Boolean sentenceMeaningMatches;
    private String sentenceAnalysis;
}
