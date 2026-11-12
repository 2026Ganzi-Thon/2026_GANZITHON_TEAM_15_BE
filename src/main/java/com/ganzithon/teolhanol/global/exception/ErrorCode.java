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
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "잠시 쉬어 갈게요. 잠시 뒤에 다시 로그인해 주세요."),

    // 나의 양 꾸미기 (A)
    YARN_NOT_ENOUGH(HttpStatus.CONFLICT, "SHEEP_YARN_NOT_ENOUGH", "털실이 조금 모자라요. 다른 목동에게 조언을 보내면 털실을 모을 수 있어요."),
    ITEM_NOT_OWNED(HttpStatus.CONFLICT, "SHEEP_ITEM_NOT_OWNED", "아직 만들지 않은 아이템이에요. 먼저 털실로 만들어 주세요."),

    // 고민 목장·안전·신고 (B)
    PII_DETECTED(HttpStatus.BAD_REQUEST, "SAFETY_PII_DETECTED",
            "전화번호나 계정 아이디 같은 연락처는 남기지 않도록 해 주세요. 그 부분만 빼고 다시 적어 주시면 좋아요."),
    FARM_NOT_FOUND(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "찾으시는 목장이 없어요. 목장 목록에서 다시 골라 주세요."),
    WORRY_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "WORRY_DAILY_LIMIT_EXCEEDED",
            "오늘은 고민을 충분히 들려주셨어요. 내일 다시 들려주세요."),
    BLOCK_SELF(HttpStatus.BAD_REQUEST, "BLOCK_SELF", "나 자신은 차단할 수 없어요."),
    WORRY_SHEEP_GRADUATED(HttpStatus.CONFLICT, "WORRY_SHEEP_GRADUATED", "이 양은 방금 목장을 떠났어요."),
    WOOL_OWN_SHEEP(HttpStatus.FORBIDDEN, "WOOL_OWN_SHEEP",
            "내 고민 양에는 털을 보낼 수 없어요. 다른 목동의 양을 둘러봐 주세요."),
    WOOL_ALREADY_SENT(HttpStatus.CONFLICT, "WOOL_ALREADY_SENT", "이 양에게는 이미 털 한 올을 보냈어요."),
    THANKS_ALREADY_SENT(HttpStatus.CONFLICT, "THANKS_ALREADY_SENT", "이 조언에는 이미 고마운 마음을 전했어요."),
    PUSH_ENDPOINT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PUSH_ENDPOINT_NOT_ALLOWED",
            "이 브라우저에서는 알림을 받을 수 없어요. 앱 안 알림으로 알려 드릴게요."),
    REPORT_OWN_CONTENT(HttpStatus.BAD_REQUEST, "REPORT_OWN_CONTENT", "내가 쓴 글은 신고할 수 없어요."),
    REPORT_DUPLICATED(HttpStatus.CONFLICT, "REPORT_DUPLICATED", "이미 신고해 주신 내용이에요. 살펴볼게요, 고마워요."),
    THANKS_ALREADY_SENT(HttpStatus.CONFLICT, "THANKS_ALREADY_SENT", "이 조언에는 이미 고마운 마음을 전했어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
