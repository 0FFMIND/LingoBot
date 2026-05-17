package com.lingobot.learning.vocabulary.service;

import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.learning.vocabulary.dto.UserVocabularyDTO;
import com.lingobot.learning.vocabulary.dto.UpdateUserVocabularyRequest;
import com.lingobot.learning.vocabulary.dto.UpdateLearningStateRequest;
import com.lingobot.learning.vocabulary.dto.VocabularyStatsDTO;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyEventType;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;

/**
 * 用户词汇服务接口。
 *
 * 提供用户词汇学习进度的管理、统计查询和分页查询功能。
 * 核心包括：学习记录的创建/更新、掌握程度计算、复习时间调度。
 */
public interface UserVocabularyService {

    // 新增或更新用户学习记录（默认事件类型为 NEW_LEARNING）
    UserVocabulary upsertProgress(Long userId, Long vocabularyWordId);

    // 新增或更新用户学习记录（指定事件类型）
    UserVocabulary upsertProgress(Long userId, Long vocabularyWordId, VocabularyEventType eventType);

    // 新增或更新用户学习记录（从词汇卡获取信息，默认事件类型为 NEW_LEARNING）
    UserVocabulary upsertProgress(Long userId, VocabularyCard card);

    // 新增或更新用户学习记录（从词汇卡获取信息，指定事件类型）
    UserVocabulary upsertProgress(Long userId, VocabularyCard card, VocabularyEventType eventType);

    // 仅创建/更新用户词汇记录（只同步展示字段，不增加学习次数，不改变学习状态）
    UserVocabulary upsertRecordOnly(Long userId, VocabularyCard card);

    // 更新用户学习进度（根据测验结果计算掌握程度和复习时间）
    UserVocabulary updateProgress(Long userId, Long vocabularyWordId, boolean isCorrect);

    // 获取用户词汇学习统计数据
    VocabularyStatsDTO getStats(Long userId);

    // 分页查询用户词汇列表（支持状态筛选、类型筛选、排序和搜索）
    PageResponseDTO<UserVocabularyDTO> getUserVocabularies(
            Long userId,
            VocabularyStatus status,
            String filterType,
            String sortBy,
            String search,
            int page,
            int size);

    // 更新用户词汇信息（手动修改单词详情）
    UserVocabularyDTO updateVocabulary(Long userId, Long id, UpdateUserVocabularyRequest request);

    // 更新用户词汇学习状态（手动设置状态、掌握度、复习时间）
    UserVocabularyDTO updateLearningState(Long userId, Long id, UpdateLearningStateRequest request);

    // 删除用户词汇记录
    void deleteVocabulary(Long userId, Long id);
}
