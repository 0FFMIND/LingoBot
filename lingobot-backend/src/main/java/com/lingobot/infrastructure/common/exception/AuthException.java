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

    private AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    private AuthException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static AuthException of(ErrorCode errorCode) {
        return new AuthException(errorCode);
    }

    public static AuthException of(ErrorCode errorCode, String customMessage) {
        return new AuthException(errorCode, customMessage);
    }

    public static AuthException badRequest() {
        return new AuthException(ErrorCode.BAD_REQUEST);
    }

    public static AuthException badRequest(String customMessage) {
        return new AuthException(ErrorCode.BAD_REQUEST, customMessage);
    }

    public static AuthException tooManyRequests() {
        return new AuthException(ErrorCode.TOO_MANY_REQUESTS);
    }

    public static AuthException tooManyRequests(String customMessage) {
        return new AuthException(ErrorCode.TOO_MANY_REQUESTS, customMessage);
    }

    public static AuthException usernameExists() {
        return new AuthException(ErrorCode.USERNAME_EXISTS);
    }

    public static AuthException usernameExists(String customMessage) {
        return new AuthException(ErrorCode.USERNAME_EXISTS, customMessage);
    }

    public static AuthException usernameOrPasswordError() {
        return new AuthException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
    }

    public static AuthException usernameOrPasswordError(String customMessage) {
        return new AuthException(ErrorCode.USERNAME_OR_PASSWORD_ERROR, customMessage);
    }

    public static AuthException emailAlreadyRegistered() {
        return new AuthException(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    public static AuthException emailAlreadyRegistered(String customMessage) {
        return new AuthException(ErrorCode.EMAIL_ALREADY_REGISTERED, customMessage);
    }

    public static AuthException invalidEmail() {
        return new AuthException(ErrorCode.INVALID_EMAIL);
    }

    public static AuthException invalidEmail(String customMessage) {
        return new AuthException(ErrorCode.INVALID_EMAIL, customMessage);
    }

    public static AuthException invalidVerificationCode() {
        return new AuthException(ErrorCode.INVALID_VERIFICATION_CODE);
    }

    public static AuthException invalidVerificationCode(String customMessage) {
        return new AuthException(ErrorCode.INVALID_VERIFICATION_CODE, customMessage);
    }

    public static AuthException accountLocked() {
        return new AuthException(ErrorCode.ACCOUNT_LOCKED);
    }

    public static AuthException accountLocked(String customMessage) {
        return new AuthException(ErrorCode.ACCOUNT_LOCKED, customMessage);
    }

    public static AuthException userNotFound() {
        return new AuthException(ErrorCode.USER_NOT_FOUND);
    }

    public static AuthException userNotFound(String customMessage) {
        return new AuthException(ErrorCode.USER_NOT_FOUND, customMessage);
    }

    public static AuthException passwordIncorrect() {
        return new AuthException(ErrorCode.PASSWORD_INCORRECT);
    }

    public static AuthException passwordIncorrect(String customMessage) {
        return new AuthException(ErrorCode.PASSWORD_INCORRECT, customMessage);
    }

    public static AuthException currentPasswordIncorrect() {
        return new AuthException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
    }

    public static AuthException currentPasswordIncorrect(String customMessage) {
        return new AuthException(ErrorCode.CURRENT_PASSWORD_INCORRECT, customMessage);
    }

    public static AuthException newPasswordSameAsCurrent() {
        return new AuthException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    public static AuthException newPasswordSameAsCurrent(String customMessage) {
        return new AuthException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT, customMessage);
    }

    public static AuthException passwordsNotMatch() {
        return new AuthException(ErrorCode.PASSWORDS_NOT_MATCH);
    }

    public static AuthException passwordsNotMatch(String customMessage) {
        return new AuthException(ErrorCode.PASSWORDS_NOT_MATCH, customMessage);
    }
}
