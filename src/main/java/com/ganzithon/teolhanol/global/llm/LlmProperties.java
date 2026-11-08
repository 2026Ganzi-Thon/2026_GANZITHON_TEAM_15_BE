package com.ganzithon.teolhanol.global.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM(Claude API) 설정.
 *
 * @param apiKey             Anthropic API 키. 비어 있으면 LLM 호출이 모두 UNAVAILABLE로 실패하고, 호출하는 쪽의 대체 로직이 동작합니다.
 * @param baseUrl            API 주소. 비우면 기본값 (테스트에서 가짜 서버를 가리킬 때 사용)
 * @param model              사용할 모델
 * @param chatTimeoutSeconds 케어 대화·요약처럼 사용자가 기다리는 생성 호출의 타임아웃
 * @param maxRetries         일반 호출의 재시도 횟수 (분류 호출은 타임아웃 예산을 지키려고 재시도하지 않음)
 * @param serverFallback     거절(refusal) 시 서버가 다른 모델로 자동 재시도하도록 할지 여부
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String apiKey,
        String baseUrl,
        String model,
        int chatTimeoutSeconds,
        int maxRetries,
        boolean serverFallback
) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
