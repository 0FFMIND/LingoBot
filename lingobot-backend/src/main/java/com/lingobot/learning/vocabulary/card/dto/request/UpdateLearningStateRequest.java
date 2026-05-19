package com.lingobot.learning.vocabulary.card.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateLearningStateRequest {
    private String status;
    private BigDecimal masteryScore;
    private LocalDateTime nextReviewAt;
    private Boolean neverReview;
}
