package com.ganzithon.teolhanol.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 설정.
 *
 * @param secret            서명 키 (HS256, 32바이트 이상 문자열). 운영에서는 APP_JWT_SECRET 환경변수로 주입합니다.
 * @param expirationMinutes 토큰 유효 시간(분)
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @Positive long expirationMinutes) {

    public JwtProperties {
        if (secret != null && secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret은 32바이트 이상이어야 합니다.");
        }
    }
}
