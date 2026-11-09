package com.ganzithon.teolhanol.domain.safety.service;

/**
 * AI 응답 안전 가드 (SAF-03). AI가 만든 문장을 화면에 내보내거나 저장하기 전에 한 번 거칩니다.
 * 금지 패턴(병명 단정, 약 복용·중단 조언, 자해 방법, "저만 믿으세요" 같은 의존 유도 등)이 있으면 준비해 둔 안전 문장으로 바꿉니다.
 */
public interface AiResponseGuard {

    /** 금지 패턴이 들어 있으면 true. 스트리밍 중 문장 단위로 부르므로 가볍게 동작합니다. */
    boolean violates(String text);

    /** 금지 패턴에 걸렸을 때 대신 보여 줄 안전한 대화 문장 */
    String safeReply();

    /** 문제없으면 그대로, 걸리면 {@link #safeReply()}를 돌려줍니다. */
    default String guard(String response) {
        return violates(response) ? safeReply() : response;
    }
}
