package com.lingobot.core.user.auth.service;

import com.lingobot.core.user.auth.dto.AuthResponse;
import com.lingobot.core.user.auth.dto.ChangePasswordRequest;
import com.lingobot.core.user.auth.dto.LoginWithCodeRequest;
import com.lingobot.core.user.auth.dto.RegisterWithCodeRequest;
import com.lingobot.core.user.auth.dto.SendLoginCodeRequest;
import com.lingobot.core.user.auth.dto.UserDTO;

/**
 * 认证服务接口。
 *
 * 定义用户认证相关的核心操作：注册、登录、验证码发送、账户管理等。
 * 支持邮箱验证码注册、邮箱密码验证码登录，包含登录失败次数限制、IP 封锁等安全机制。
 */
public interface AuthService {
    // 用户注册（邮箱 + 验证码方式）
    AuthResponse registerWithCode(RegisterWithCodeRequest request, String clientIp);
    // 发送登录验证码（需先验证邮箱密码）
    void sendLoginVerificationCode(SendLoginCodeRequest request, String clientIp);
    // 用户登录（邮箱 + 密码 + 验证码方式）
    AuthResponse loginWithCode(LoginWithCodeRequest request, String clientIp);
    // 获取当前登录用户的完整信息
    UserDTO getCurrentUser();
    // 获取当前登录用户的 ID（未登录时返回 null）
    Long getCurrentUserId();
    // 用户退出登录，清除安全上下文
    void logout();
    // 注销当前用户账户，删除用户数据
    void deactivateAccount();
    // 修改当前用户的密码
    void changePassword(ChangePasswordRequest request);
    // 更新当前用户的头像
    void updateAvatar(String avatarBase64);
    // 更新当前用户的昵称，返回新的认证响应（包含新 Token）
    AuthResponse updateUsername(String username);
}
