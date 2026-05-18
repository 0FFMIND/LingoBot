package com.lingobot.learning.vocabulary.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.entity.Conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 词汇卡实体类
 * 存储用户学习的单词、释义、例句、用户交互记录等信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vocabulary_cards")
public class VocabularyCard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属对话*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Conversation conversation;

    /** 关联的标准化单词ID */
    @Column(name = "vocabulary_word_id")
    private Long vocabularyWordId;

    /** 单词 */
    @Column(nullable = false, length = 100)
    private String word;

    /** 音标 */
    @Column(length = 100)
    private String phonetic;

    /** 词性（如 n., v., adj., adv. 等） */
    @Column(name = "part_of_speech", length = 20)
    private String partOfSpeech;

    /** 中文释义 */
    @Column(columnDefinition = "TEXT")
    private String meaning;

    /** 英文例句 */
    @Column(columnDefinition = "TEXT")
    private String example;

    /** 例句中文翻译 */
    @Column(name = "example_translation", columnDefinition = "TEXT")
    private String exampleTranslation;

    /** 同义词JSON存储 */
    @Column(name = "synonyms_json", columnDefinition = "TEXT")
    private String synonymsJson;

    /** 词汇类别（如 cefr, ielts, toefl） */
    @Column(name = "category", length = 20)
    private String category;

    /** 难度级别（如 a1, b2, 5.5-6.5, 81-100 等） */
    @Column(name = "difficulty", length = 20)
    private String difficulty;

    /** 在对话中的位置顺序*/
    @Column(nullable = false)
    private Integer position;

    /** 用户猜测的释义*/
    @Column(name = "user_meaning_guess", columnDefinition = "TEXT")
    private String userMeaningGuess;

    /** AI对用户释义的检查结果（详细反馈）*/
    @Column(name = "meaning_check_result", columnDefinition = "TEXT")
    private String meaningCheckResult;

    /** 用户输入的释义是否正确*/
    @Column(name = "meaning_is_correct")
    private Boolean meaningIsCorrect;

    /** 释义检查是否已完成 */
    @Column(name = "meaning_check_completed")
    @Builder.Default
    private Boolean meaningCheckCompleted = false;

    /** 中文例句（供用户翻译用） */
    @Column(name = "chinese_sentence_for_translation", columnDefinition = "TEXT")
    private String chineseSentenceForTranslation;

    /** 用户写的英文句子（根据中文例句翻译） */
    @Column(name = "user_english_sentence", columnDefinition = "TEXT")
    private String userEnglishSentence;

    /** AI对用户英文句子的分析结果 */
    @Column(name = "sentence_analysis", columnDefinition = "TEXT")
    private String sentenceAnalysis;

    /** 用户造的句子（简化版本，与userEnglishSentence保持一致） */
    @Column(name = "user_sentence", columnDefinition = "TEXT")
    private String userSentence;

    /** 句子分析是否已完成 */
    @Column(name = "sentence_analysis_completed")
    @Builder.Default
    private Boolean sentenceAnalysisCompleted = false;

    /** 用户句子是否包含新单词 */
    @Column(name = "sentence_has_new_word")
    private Boolean sentenceHasNewWord;

    /** 用户句子的意思是否与中文例句匹配 */
    @Column(name = "sentence_meaning_matches")
    private Boolean sentenceMeaningMatches;

    /** 是否已完成学习*/
    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    /** 是否已被重新生成（true表示已被替换，数据库保留但前端不展示）*/
    @Column(name = "is_regenerated", nullable = false)
    @Builder.Default
    private Boolean isRegenerated = false;

    /** 重新生成次数索引（0=原始，1=第一次重新生成，2=第二次重新生成...）*/
    @Column(name = "regeneration_index", nullable = false)
    @Builder.Default
    private Integer regenerationIndex = 0;

    /** 是否已揭露（用于批量生成后渐进式揭露，true表示用户已解锁可查看）*/
    @Column(name = "is_revealed", nullable = false)
    @Builder.Default
    private Boolean isRevealed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 持久化前自动设置创建时间和默认值*/
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isCompleted == null) {
            isCompleted = false;
        }
        if (isRegenerated == null) {
            isRegenerated = false;
        }
        if (regenerationIndex == null) {
            regenerationIndex = 0;
        }
        if (position == null) {
            position = 0;
        }
        if (isRevealed == null) {
            isRevealed = false;
        }
    }

    /** 更新前自动设置更新时间*/
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 从JSON解析同义词列表*/
    public List<String> getSynonyms() {
        if (synonymsJson == null || synonymsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(synonymsJson, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 将同义词列表序列化为JSON存储 */
    public void setSynonyms(List<String> synonyms) {
        if (synonyms == null || synonyms.isEmpty()) {
            this.synonymsJson = null;
            return;
        }
        try {
            this.synonymsJson = OBJECT_MAPPER.writeValueAsString(synonyms);
        } catch (Exception e) {
            this.synonymsJson = null;
        }
    }
}
