package com.lingobot.core.user.auth.repository;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口。
 *
 * 提供用户的数据库操作，包括基础 CRUD、按用户名/邮箱查询、
 * 带悲观写锁的查询（用于并发更新场景）等。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 根据用户名查询用户
    Optional<User> findByUsername(String username);

    // 根据用户名查询用户（加悲观写锁，用于并发更新）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    // 根据用户 ID 查询用户（加悲观写锁，用于并发更新）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    // 检查用户名是否已存在
    boolean existsByUsername(String username);
    
    // 根据邮箱查询用户
    Optional<User> findByEmail(String email);
    
    // 检查邮箱是否已存在
    boolean existsByEmail(String email);
    
    // 根据角色查询用户列表
    List<User> findByRole(User.Role role);
}
