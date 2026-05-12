package com.lingobot.learning.vocabulary.service;

import com.lingobot.learning.vocabulary.dto.CreateVocabularyCardRequest;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;

import java.util.List;

/**
 * 词汇卡服务接口
 * 提供词汇卡的创建、查询、更新、删除、导航、AI生成等核心功能
 */
public interface VocabularyCardService {

    /**
     * 创建新的词汇卡
     * @param conversationId 对话ID
     * @param request 词汇卡创建请求
     * @return 创建的词汇卡DTO
     */
    VocabularyCardDTO createCard(Long conversationId, CreateVocabularyCardRequest request);

    /**
     * 根据ID获取词汇卡
     * @param cardId 词汇卡ID
     * @return 词汇卡DTO（包含导航信息）
     */
    VocabularyCardDTO getCardById(Long cardId);

    /**
     * 获取对话的所有词汇卡
     * @param conversationId 对话ID
     * @return 词汇卡DTO列表
     */
    List<VocabularyCardDTO> getAllCards(Long conversationId);

    /**
     * 获取下一个词汇卡
     * @param conversationId 对话ID
     * @param currentPosition 当前位置（可选）
     * @param category 词汇类别（可选）
     * @param difficulty 难度级别（可选）
     * @return 下一个词汇卡DTO
     */
    VocabularyCardDTO getNextCard(Long conversationId, Integer currentPosition, String category, String difficulty);

    /**
     * 获取上一个词汇卡
     * @param conversationId 对话ID
     * @param currentPosition 当前位置（可选）
     * @return 上一个词汇卡DTO
     */
    VocabularyCardDTO getPrevCard(Long conversationId, Integer currentPosition);

    /**
     * 获取当前正在学习的词汇卡
     * 优先返回第一个未完成的，否则返回最后一个
     * @param conversationId 对话ID
     * @return 当前词汇卡DTO
     */
    VocabularyCardDTO getCurrentCard(Long conversationId);

    /**
     * 更新用户对单词的释义猜测
     * @param cardId 词汇卡ID
     * @param userMeaning 用户猜测的释义
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO updateUserMeaning(Long cardId, String userMeaning);

    /**
     * 更新用户写的英文句子（根据中文例句翻译）
     * @param cardId 词汇卡ID
     * @param userEnglishSentence 用户写的英文句子
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO updateUserEnglishSentence(Long cardId, String userEnglishSentence);

    /**
     * 触发AI分析用户的英文句子（异步）
     * 分析内容：1）是否与中文例句意思匹配；2）是否包含新单词
     * @param cardId 词汇卡ID
     */
    void analyzeUserSentenceAsync(Long cardId);

    /**
     * 更新AI对句子的分析结果
     * @param cardId 词汇卡ID
     * @param analysis 分析结果（JSON）
     * @param hasNewWord 是否包含新单词
     * @param meaningMatches 意思是否匹配
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO updateSentenceAnalysis(Long cardId, String analysis, Boolean hasNewWord, Boolean meaningMatches);

    /**
     * 标记词汇卡为已完成
     * @param cardId 词汇卡ID
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO markAsCompleted(Long cardId);

    /**
     * 通过AI生成下一个新词汇卡
     * @param conversationId 对话ID
     * @param category 词汇类别（如 cefr, ielts, toefl）
     * @param difficulty 难度级别（如 b2, 5.5-6.5, 81-100 等）
     * @return 生成的词汇卡DTO
     */
    VocabularyCardDTO generateNextCard(Long conversationId, String category, String difficulty);

    /**
     * 重新生成当前词汇卡
     * 删除当前未完成的词汇卡，生成新的替换
     * @param conversationId 对话ID
     * @param category 词汇类别
     * @param difficulty 难度级别
     * @return 重新生成的词汇卡DTO
     */
    VocabularyCardDTO regenerateCard(Long conversationId, String category, String difficulty);

    /**
     * 删除对话的所有词汇卡
     * @param conversationId 对话ID
     */
    void deleteAllCards(Long conversationId);

    /**
     * 获取对话的词汇卡数量
     * @param conversationId 对话ID
     * @return 词汇卡数量
     */
    long getCardCount(Long conversationId);

    /**
     * 直接从数据库读取词汇卡（跳过 Redis 缓存），用于状态轮询接口，
     * 避免事务提交前缓存被污染导致前端长期看到过时状态。
     */
    VocabularyCardDTO getCardByIdFromDb(Long cardId);
}
