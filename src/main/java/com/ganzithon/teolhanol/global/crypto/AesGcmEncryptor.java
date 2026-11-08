package com.ganzithon.teolhanol.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 암호화. 저장 형식은 Base64(IV 12바이트 + 암호문 + 인증 태그)입니다.
 * 매번 새로운 IV를 쓰므로 같은 평문도 다른 암호문이 됩니다.
 */
@Component
@EnableConfigurationProperties(CryptoProperties.class)
public class AesGcmEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(CryptoProperties properties) {
        if (properties.key() == null || properties.key().isBlank()) {
            throw new IllegalStateException("app.crypto.key(APP_CRYPTO_KEY)가 설정되지 않았습니다.");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(properties.key().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.crypto.key는 Base64 문자열이어야 합니다.", e);
        }
        if (raw.length != KEY_LENGTH) {
            throw new IllegalStateException("app.crypto.key는 32바이트(AES-256)여야 합니다. 현재 " + raw.length + "바이트");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(IV_LENGTH + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | java.nio.BufferUnderflowException e) {
            // 원문이 로그에 남지 않도록 예외 메시지에 값을 넣지 않습니다.
            throw new IllegalStateException("복호화에 실패했습니다.", e);
        }
    }
}
