package com.ganzithon.teolhanol.global.exception;

import lombok.Getter;

/** 서비스 로직에서 던지는 예외. ErrorCode의 상태 코드와 문구로 응답합니다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 문구 대신 상황에 맞는 문구를 내려줄 때 사용합니다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
