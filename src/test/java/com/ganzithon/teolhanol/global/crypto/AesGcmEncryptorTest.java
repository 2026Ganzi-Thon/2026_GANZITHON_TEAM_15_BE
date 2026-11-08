package com.ganzithon.teolhanol.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmEncryptorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final AesGcmEncryptor encryptor = new AesGcmEncryptor(new CryptoProperties(KEY));

    @Test
    void 암호화한_값을_복호화하면_원문이_나온다() {
        String plain = "오늘은 조금 지쳤어요. 😮‍💨";
        String cipher = encryptor.encrypt(plain);

        assertThat(cipher).doesNotContain("지쳤어요");
        assertThat(encryptor.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    void 같은_원문도_매번_다른_암호문이_된다() {
        assertThat(encryptor.encrypt("같은 문장")).isNotEqualTo(encryptor.encrypt("같은 문장"));
    }

    @Test
    void null은_그대로_null이다() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void 다른_키로는_복호화할_수_없다() {
        String cipher = encryptor.encrypt("비밀");
        byte[] other = new byte[32];
        other[0] = 1;
        AesGcmEncryptor otherEncryptor = new AesGcmEncryptor(
                new CryptoProperties(Base64.getEncoder().encodeToString(other)));

        assertThatThrownBy(() -> otherEncryptor.decrypt(cipher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("비밀");
    }

    @Test
    void 키가_없거나_길이가_틀리면_시작하지_않는다() {
        assertThatThrownBy(() -> new AesGcmEncryptor(new CryptoProperties(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmEncryptor(
                new CryptoProperties(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
    }
}
