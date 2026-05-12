package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeaningCheckStatusDTO {
    private Long cardId;
    private String word;
    private String userMeaningGuess;
    private Boolean meaningCheckCompleted;
    private Boolean meaningIsCorrect;
    private String meaningCheckResult;
    private String chineseSentenceForTranslation;
}
