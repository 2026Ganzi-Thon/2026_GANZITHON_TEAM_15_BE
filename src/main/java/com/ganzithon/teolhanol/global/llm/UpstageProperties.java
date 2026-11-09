package com.ganzithon.teolhanol.global.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstage Solar API 설정 (OpenAI 호환 chat completions).
 *
 * @param apiKey  Upstage API 키. 비어 있으면 Upstage는 쓰지 않습니다.
 * @param baseUrl API 주소. 비우면 https://api.upstage.ai/v1
 * @param model   모델. 비우면 solar-pro4
 */
@ConfigurationProperties(prefix = "app.llm.upstage")
public record UpstageProperties(String apiKey, String baseUrl, String model) {

    public static final String DEFAULT_BASE_URL = "https://api.upstage.ai/v1";
    public static final String DEFAULT_MODEL = "solar-pro4";

    public UpstageProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip().replaceAll("/+$", "");
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
