package com.ganzithon.teolhanol.global.llm;

import java.util.function.Consumer;

/** API 키가 없을 때 쓰는 구현체. 모든 호출이 UNAVAILABLE로 실패해 대체 로직이 동작합니다. */
public class DisabledLlmClient implements LlmClient {

    private static LlmException unavailable() {
        return new LlmException(LlmException.Type.UNAVAILABLE, "LLM API 키가 설정되지 않았습니다.");
    }

    @Override
    public String complete(LlmRequest request) {
        throw unavailable();
    }

    @Override
    public <T> T completeJson(LlmRequest request, Class<T> type) {
        throw unavailable();
    }

    @Override
    public String stream(LlmRequest request, Consumer<String> onDelta) {
        throw unavailable();
    }
}
