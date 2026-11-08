package com.ganzithon.teolhanol.global.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 민감정보 암호화 키 설정.
 *
 * @param key Base64로 인코딩한 32바이트(AES-256) 키. 운영에서는 환경변수 APP_CRYPTO_KEY로만 주입합니다.
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(String key) {
}
