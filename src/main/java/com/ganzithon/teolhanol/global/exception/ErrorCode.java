package com.ganzithon.teolhanol.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 모음. 안내 문구는 비난·명령 없는 존댓말로 작성합니다 (기능명세서 6장 어조).
 * 도메인 기능을 구현할 때 이곳에 코드를 추가합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력한 내용을 한 번 더 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "이 기능은 사용할 수 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "찾으시는 내용이 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "지원하지 않는 요청이에요."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "이미 처리된 요청이에요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_429", "오늘은 충분히 이야기해 주셨어요. 내일 다시 들려주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "잠시 문제가 생겼어요. 조금 뒤에 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
