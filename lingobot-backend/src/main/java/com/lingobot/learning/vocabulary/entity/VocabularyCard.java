package com.lingobot.learning.vocabulary.entity;

import com.lingobot.core.conversation.entity.Conversation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属对话*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** 单词 */
    @Column(nullable = false, length = 100)
    private String word;

    /** 音标 */
    @Column(length = 100)
    private String phonetic;

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

    /** 反义词JSON存储 */
    @Column(name = "antonyms_json", columnDefinition = "TEXT")
    private String antonymsJson;

    /** 难度级别（如A1, B2, C1等） */
    @Column(length = 10)
    private String level;

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

    /** 用户造的句子 */
    @Column(name = "user_sentence", columnDefinition = "TEXT")
    private String userSentence;

    /** AI对用户造句的反馈*/
    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(synonymsJson, List.class);
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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.synonymsJson = mapper.writeValueAsString(synonyms);
        } catch (Exception e) {
            this.synonymsJson = null;
        }
    }

    /** 从JSON解析反义词列�?*/
    public List<String> getAntonyms() {
        if (antonymsJson == null || antonymsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(antonymsJson, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 将反义词列表序列化为JSON存储 */
    public void setAntonyms(List<String> antonyms) {
        if (antonyms == null || antonyms.isEmpty()) {
            this.antonymsJson = null;
            return;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.antonymsJson = mapper.writeValueAsString(antonyms);
        } catch (Exception e) {
            this.antonymsJson = null;
        }
    }
}
