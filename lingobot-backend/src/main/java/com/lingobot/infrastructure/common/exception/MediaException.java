package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

/**
 * 媒体相关业务异常，用于 TTS、音频处理、图片处理等场景。
 *
 *   throw MediaException.ttsFailed("获取发音失败");
 *   throw MediaException.audioConversionFailed("音频转换失败");
 *   throw MediaException.of(ErrorCode.SOME_CODE, "自定义消息");
 */
public class MediaException extends BaseException {

    private MediaException(ErrorCode errorCode) {
        super(errorCode);
    }

    private MediaException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static MediaException of(ErrorCode errorCode) {
        return new MediaException(errorCode);
    }

    public static MediaException of(ErrorCode errorCode, String customMessage) {
        return new MediaException(errorCode, customMessage);
    }

    public static MediaException badRequest() {
        return new MediaException(ErrorCode.BAD_REQUEST);
    }

    public static MediaException badRequest(String customMessage) {
        return new MediaException(ErrorCode.BAD_REQUEST, customMessage);
    }

    public static MediaException ttsFailed() {
        return new MediaException(ErrorCode.TTS_FAILED);
    }

    public static MediaException ttsFailed(String customMessage) {
        return new MediaException(ErrorCode.TTS_FAILED, customMessage);
    }

    public static MediaException audioConversionFailed() {
        return new MediaException(ErrorCode.AUDIO_CONVERSION_FAILED);
    }

    public static MediaException audioConversionFailed(String customMessage) {
        return new MediaException(ErrorCode.AUDIO_CONVERSION_FAILED, customMessage);
    }

    public static MediaException audioNotFound() {
        return new MediaException(ErrorCode.AUDIO_NOT_FOUND);
    }

    public static MediaException audioNotFound(String customMessage) {
        return new MediaException(ErrorCode.AUDIO_NOT_FOUND, customMessage);
    }

    public static MediaException audioDataInvalid() {
        return new MediaException(ErrorCode.AUDIO_DATA_INVALID);
    }

    public static MediaException audioDataInvalid(String customMessage) {
        return new MediaException(ErrorCode.AUDIO_DATA_INVALID, customMessage);
    }
}
