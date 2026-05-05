package com.lingobot.core.user.auth.service.impl;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.BalanceService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.infrastructure.common.exception.InsufficientBalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 余额服务实现类
 * 用于管理用户余额的查询、扣费和充值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {
    
    private final UserRepository userRepository;
    
    @Override
    public int getCurrentUserBalance() {
        User user = getCurrentUser();
        return user.getBalance() != null ? user.getBalance() : 0;
    }
    
    @Override
    public boolean hasSufficientBalance(double cost) {
        int currentBalance = getCurrentUserBalance();
        return currentBalance >= cost;
    }
    
    @Override
    @Transactional
    public int deductBalance(double cost) {
        User user = getCurrentUser();
        int currentBalance = user.getBalance() != null ? user.getBalance() : 0;
        
        if (currentBalance < cost) {
            log.warn("用户余额不足。用户: {}, 当前余额: {}, 需要: {}", 
                    user.getUsername(), currentBalance, cost);
            throw new InsufficientBalanceException("你的余额不足", currentBalance, cost);
        }
        
        int newBalance = (int) (currentBalance - cost);
        user.setBalance(newBalance);
        userRepository.save(user);
        
        log.info("用户扣费成功。用户: {}, 扣除: {}, 剩余: {}", 
                user.getUsername(), cost, newBalance);
        
        return newBalance;
    }
    
    @Override
    @Transactional
    public int addBalance(Long userId, int amount) {
        if (amount <= 0) {
            throw new ChatException("充值金额必须大于0");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatException("用户不存在"));
        
        int currentBalance = user.getBalance() != null ? user.getBalance() : 0;
        int newBalance = currentBalance + amount;
        user.setBalance(newBalance);
        userRepository.save(user);
        
        log.info("用户充值成功。用户: {}, 充值: {}, 新余额: {}", 
                user.getUsername(), amount, newBalance);
        
        return newBalance;
    }
    
    @Override
    @Transactional
    public int addCurrentUserBalance(int amount) {
        User user = getCurrentUser();
        return addBalance(user.getId(), amount);
    }
    
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
    }
}
