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
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "잠시 문제가 생겼어요. 조금 뒤에 다시 시도해 주세요."),

    // 계정 (A)
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "ACCOUNT_EMAIL_DUPLICATED", "이미 가입한 이메일이에요. 로그인해 주세요."),
    NICKNAME_UNAVAILABLE(HttpStatus.CONFLICT, "ACCOUNT_NICKNAME_UNAVAILABLE", "이 이름은 쓸 수 없어요. 다른 이름을 골라 주세요."),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "ACCOUNT_PASSWORD_TOO_SHORT", "비밀번호는 8자 이상으로 정해 주세요."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOGIN_FAILED", "이메일이나 비밀번호를 한 번 더 확인해 주세요."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "잠시 쉬어 갈게요. 잠시 뒤에 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
