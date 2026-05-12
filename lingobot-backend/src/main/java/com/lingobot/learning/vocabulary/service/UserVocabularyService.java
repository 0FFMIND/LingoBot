package com.lingobot.learning.vocabulary.service;

import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.learning.vocabulary.dto.UserVocabularyDTO;
import com.lingobot.learning.vocabulary.dto.VocabularyStatsDTO;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
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

    // 更新用户学习进度（根据测验结果计算掌握程度和复习时间）
    UserVocabulary updateProgress(Long userId, Long vocabularyWordId, boolean isCorrect);

    // 获取用户词汇学习统计数据
    VocabularyStatsDTO getStats(Long userId);

    // 分页查询用户词汇列表（支持状态筛选、类型筛选和排序）
    PageResponseDTO<UserVocabularyDTO> getUserVocabularies(
            Long userId,
            VocabularyStatus status,
            String filterType,
            String sortBy,
            int page,
            int size);
}
