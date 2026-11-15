package com.ganzithon.teolhanol.global.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    private static final Logger log = LoggerFactory.getLogger(CorsProperties.class);

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /** 공백·빈 항목을 버리고, 모든 출처를 여는 '*' 단독 값은 실수 방지를 위해 무시합니다. */
    public List<String> originPatterns() {
        return allowedOrigins.stream()
                .filter(o -> o != null)
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .filter(o -> {
                    if (o.equals("*")) {
                        log.warn("CORS_ALLOWED_ORIGINS 의 '*' (모든 출처 허용)는 무시합니다.");
                        return false;
                    }
                    return true;
                })
                .toList();
    }
}
