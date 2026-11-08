package com.ganzithon.teolhanol.global.llm;

import java.util.function.Consumer;

/**
 * LLM 호출 공통 인터페이스. 위험도 분류(SAF-01), 케어 대화(CARE-02), 요약(CARE-03), 조언 필터(SAF-04)에서 사용합니다.
 * 실패하면 항상 {@link LlmException}을 던집니다.
 */
public interface LlmClient {

    /** 한 번에 응답 전체를 받습니다. */
    String complete(LlmRequest request);

    /** 응답을 JSON 스키마에 맞춰 받아 객체로 변환합니다. 스키마는 type 클래스(record)에서 만들어집니다. */
    <T> T completeJson(LlmRequest request, Class<T> type);

    /**
     * 응답을 조각(delta)으로 받으면서 onDelta로 넘기고, 끝나면 전체 텍스트를 돌려줍니다.
     * 케어 대화 SSE 스트리밍에 사용합니다.
     */
    String stream(LlmRequest request, Consumer<String> onDelta);
}
