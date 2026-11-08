package com.ganzithon.teolhanol.global.llm;

import lombok.Getter;

/**
 * LLM 호출 실패. 호출하는 쪽은 type에 따라 대체 로직으로 넘어갑니다.
 * (예: 위험도 분류 실패 → 키워드 판정 + 주의 등급, 조언 필터 실패 → 금칙어 판정)
 */
@Getter
public class LlmException extends RuntimeException {

    public enum Type {
        /** API 키가 없거나 서버에 연결할 수 없음 */
        UNAVAILABLE,
        /** 타임아웃 */
        TIMEOUT,
        /** 모델이 응답을 거절함 */
        REFUSAL,
        /** 응답 형식이 기대와 다름 */
        INVALID_RESPONSE,
        /** 요청 한도 초과, 서버 오류 등 */
        API_ERROR
    }

    private final Type type;

    public LlmException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public LlmException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }
}
