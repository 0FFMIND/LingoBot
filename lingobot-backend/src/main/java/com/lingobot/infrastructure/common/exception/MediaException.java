package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

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

    public static MediaException ttsPronunciationFailed() {
        return new MediaException(ErrorCode.TTS_PRONUNCIATION_FAILED);
    }

    public static MediaException ttsPronunciationFailed(String customMessage) {
        return new MediaException(ErrorCode.TTS_PRONUNCIATION_FAILED, customMessage);
    }

    public static MediaException audioConversionFailed() {
        return new MediaException(ErrorCode.AUDIO_CONVERSION_FAILED);
    }

    public static MediaException audioConversionFailed(String customMessage) {
        return new MediaException(ErrorCode.AUDIO_CONVERSION_FAILED, customMessage);
    }

    public static MediaException audioFileNotFound() {
        return new MediaException(ErrorCode.AUDIO_FILE_NOT_FOUND);
    }

    public static MediaException audioFileNotFound(String customMessage) {
        return new MediaException(ErrorCode.AUDIO_FILE_NOT_FOUND, customMessage);
    }
}
