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
     * @return 下一个词汇卡DTO
     */
    VocabularyCardDTO getNextCard(Long conversationId, Integer currentPosition);

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
     * 更新用户用单词造的句子
     * @param cardId 词汇卡ID
     * @param userSentence 用户造的句子
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO updateUserSentence(Long cardId, String userSentence);

    /**
     * 更新AI对用户造句的反馈
     * @param cardId 词汇卡ID
     * @param feedback AI反馈内容
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO updateAIFeedback(Long cardId, String feedback);

    /**
     * 标记词汇卡为已完成
     * @param cardId 词汇卡ID
     * @return 更新后的词汇卡DTO
     */
    VocabularyCardDTO markAsCompleted(Long cardId);

    /**
     * 通过AI生成下一个新词汇卡
     * @param conversationId 对话ID
     * @param level 难度级别（如A1, B2等）
     * @return 生成的词汇卡DTO
     */
    VocabularyCardDTO generateNextCard(Long conversationId, String level);

    /**
     * 重新生成当前词汇卡
     * 删除当前未完成的词汇卡，生成新的替换
     * @param conversationId 对话ID
     * @param level 难度级别
     * @return 重新生成的词汇卡DTO
     */
    VocabularyCardDTO regenerateCard(Long conversationId, String level);

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
}
