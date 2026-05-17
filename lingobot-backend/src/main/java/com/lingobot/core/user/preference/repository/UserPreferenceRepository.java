package com.lingobot.core.user.preference.repository;

import com.lingobot.core.user.preference.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户偏好设置仓库接口。
 *
 * 提供用户偏好设置的数据库操作，包括根据用户 ID 查询等。
 */
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    
    // 根据用户 ID 查询偏好设置
    Optional<UserPreference> findByUserId(Long userId);
    
    // 检查指定用户是否已有偏好设置
    boolean existsByUserId(Long userId);
}
