package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

/**
 * 认证与用户相关异常，用于注册、登录、账户管理等场景。
 *
 *   throw AuthException.usernameExists();
 *   throw AuthException.userNotFound("用户不存在: " + userId);
 *   throw AuthException.of(ErrorCode.USERNAME_EXISTS, "自定义消息");
 */
public class AuthException extends BaseException {

    // 私有构造，仅由静态工厂方法调用（使用 ErrorCode 默认消息）
    private AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    // 私有构造，仅由静态工厂方法调用（使用自定义消息）
    private AuthException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    // 通用工厂：传入任意 ErrorCode，使用其默认消息
    public static AuthException of(ErrorCode errorCode) {
        return new AuthException(errorCode);
    }

    // 通用工厂：传入任意 ErrorCode，使用自定义消息
    public static AuthException of(ErrorCode errorCode, String customMessage) {
        return new AuthException(errorCode, customMessage);
    }

    // 请求参数错误
    public static AuthException badRequest() {
        return new AuthException(ErrorCode.BAD_REQUEST);
    }

    // 请求参数错误（自定义消息）
    public static AuthException badRequest(String customMessage) {
        return new AuthException(ErrorCode.BAD_REQUEST, customMessage);
    }

    // 请求过于频繁
    public static AuthException tooManyRequests() {
        return new AuthException(ErrorCode.TOO_MANY_REQUESTS);
    }

    // 请求过于频繁（自定义消息）
    public static AuthException tooManyRequests(String customMessage) {
        return new AuthException(ErrorCode.TOO_MANY_REQUESTS, customMessage);
    }

    // 用户名已存在
    public static AuthException usernameExists() {
        return new AuthException(ErrorCode.USERNAME_EXISTS);
    }

    // 用户名已存在（自定义消息）
    public static AuthException usernameExists(String customMessage) {
        return new AuthException(ErrorCode.USERNAME_EXISTS, customMessage);
    }

    // 用户名或密码错误
    public static AuthException usernameOrPasswordError() {
        return new AuthException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
    }

    // 用户名或密码错误（自定义消息）
    public static AuthException usernameOrPasswordError(String customMessage) {
        return new AuthException(ErrorCode.USERNAME_OR_PASSWORD_ERROR, customMessage);
    }

    // 邮箱已被注册
    public static AuthException emailAlreadyRegistered() {
        return new AuthException(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    // 邮箱已被注册（自定义消息）
    public static AuthException emailAlreadyRegistered(String customMessage) {
        return new AuthException(ErrorCode.EMAIL_ALREADY_REGISTERED, customMessage);
    }

    // 无效邮箱地址
    public static AuthException invalidEmail() {
        return new AuthException(ErrorCode.INVALID_EMAIL);
    }

    // 无效邮箱地址（自定义消息）
    public static AuthException invalidEmail(String customMessage) {
        return new AuthException(ErrorCode.INVALID_EMAIL, customMessage);
    }

    // 验证码无效或已过期
    public static AuthException invalidVerificationCode() {
        return new AuthException(ErrorCode.INVALID_VERIFICATION_CODE);
    }

    // 验证码无效或已过期（自定义消息）
    public static AuthException invalidVerificationCode(String customMessage) {
        return new AuthException(ErrorCode.INVALID_VERIFICATION_CODE, customMessage);
    }

    // 账户已被临时锁定
    public static AuthException accountLocked() {
        return new AuthException(ErrorCode.ACCOUNT_LOCKED);
    }

    // 账户已被临时锁定（自定义消息）
    public static AuthException accountLocked(String customMessage) {
        return new AuthException(ErrorCode.ACCOUNT_LOCKED, customMessage);
    }

    // 用户不存在
    public static AuthException userNotFound() {
        return new AuthException(ErrorCode.USER_NOT_FOUND);
    }

    // 用户不存在（自定义消息）
    public static AuthException userNotFound(String customMessage) {
        return new AuthException(ErrorCode.USER_NOT_FOUND, customMessage);
    }

    // 密码错误
    public static AuthException passwordIncorrect() {
        return new AuthException(ErrorCode.PASSWORD_INCORRECT);
    }

    // 密码错误（自定义消息）
    public static AuthException passwordIncorrect(String customMessage) {
        return new AuthException(ErrorCode.PASSWORD_INCORRECT, customMessage);
    }

    // 当前密码错误（修改密码时旧密码验证失败）
    public static AuthException currentPasswordIncorrect() {
        return new AuthException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
    }

    // 当前密码错误（自定义消息）
    public static AuthException currentPasswordIncorrect(String customMessage) {
        return new AuthException(ErrorCode.CURRENT_PASSWORD_INCORRECT, customMessage);
    }

    // 新密码与当前密码相同
    public static AuthException newPasswordSameAsCurrent() {
        return new AuthException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    // 新密码与当前密码相同（自定义消息）
    public static AuthException newPasswordSameAsCurrent(String customMessage) {
        return new AuthException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT, customMessage);
    }

    // 两次输入的密码不一致
    public static AuthException passwordsNotMatch() {
        return new AuthException(ErrorCode.PASSWORDS_NOT_MATCH);
    }

    // 两次输入的密码不一致（自定义消息）
    public static AuthException passwordsNotMatch(String customMessage) {
        return new AuthException(ErrorCode.PASSWORDS_NOT_MATCH, customMessage);
    }
}
