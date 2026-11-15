package com.ganzithon.teolhanol.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.notification.config.PushProperties;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import nl.martijndwars.webpush.HttpRequest;
import org.junit.jupiter.api.Test;

/** VAPID 키 확인과 웹 푸시 요청 만들기 (WOL-02). 네트워크는 쓰지 않습니다. */
class WebPushSetupTest {

    private static final List<String> HOSTS = List.of("fcm.googleapis.com", "push.services.mozilla.com");

    private static PushProperties props(String publicKey, String privateKey, String subject) {
        return new PushProperties(publicKey, privateKey, subject, "/farm/my/{sheepId}", HOSTS, 86400, 5, 30, 25, 5);
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return generator.generateKeyPair();
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    /** 비압축 점(0x04 || X || Y)을 base64url로 */
    private static String publicKey(KeyPair pair) {
        ECPublicKey key = (ECPublicKey) pair.getPublic();
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
        return b64(out);
    }

    private static String privateKey(KeyPair pair) {
        return b64(fixed32(((ECPrivateKey) pair.getPrivate()).getS()));
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void 키가_없으면_꺼진_발송기로_시작한다() {
        WebPushSetup setup = WebPushSetup.from(props("", null, null));

        assertThat(setup.enabled()).isFalse();
        assertThat(setup.publicKey()).isNull();
        assertThat(setup.sender()).isInstanceOf(DisabledPushSender.class);
        assertThat(setup.sender().send(new PushTarget("https://fcm.googleapis.com/x", "a", "b"), "{}"))
                .isEqualTo(PushResult.DISABLED);
    }

    @Test
    void 키가_잘못되면_예외_없이_꺼진다() {
        WebPushSetup setup = WebPushSetup.from(props("not-a-key", "also-not-a-key", "mailto:team@example.com"));

        assertThat(setup.enabled()).isFalse();
        assertThat(setup.sender()).isInstanceOf(DisabledPushSender.class);
        assertThat(setup.toString()).doesNotContain("not-a-key");
    }

    @Test
    void 올바른_키면_켜지고_암호화된_요청에_VAPID_헤더가_붙는다() throws Exception {
        KeyPair vapid = p256();
        KeyPair browser = p256();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);

        WebPushSetup setup = WebPushSetup.from(props(publicKey(vapid), privateKey(vapid), "mailto:team@example.com"));
        assertThat(setup.enabled()).isTrue();
        assertThat(setup.publicKey()).isEqualTo(publicKey(vapid));
        assertThat(setup.sender()).isInstanceOf(WebPushSender.class);

        String payload = "{\"type\":\"WOOL_ARRIVED\",\"title\":\"털이 풍성해졌어요\"}";
        HttpRequest request = ((WebPushSender) setup.sender()).prepare(
                new PushTarget("https://updates.push.services.mozilla.com/wpush/v2/abc", publicKey(browser), b64(auth)),
                payload);

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(request.getHeaders());
        assertThat(request.getUrl()).isEqualTo("https://updates.push.services.mozilla.com/wpush/v2/abc");
        assertThat(headers.get("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(headers.get("TTL")).isEqualTo("86400");
        assertThat(headers.get("Authorization")).startsWith("vapid t=").contains("k=" + publicKey(vapid));
        assertThat(request.getBody()).isNotEmpty();
        assertThat(new String(request.getBody(), StandardCharsets.ISO_8859_1)).doesNotContain("WOOL_ARRIVED");
    }

    @Test
    void 응답_코드로_결과를_나눈다() {
        assertThat(WebPushSender.classify(201, "h")).isEqualTo(PushResult.SENT);
        assertThat(WebPushSender.classify(404, "h")).isEqualTo(PushResult.EXPIRED);
        assertThat(WebPushSender.classify(410, "h")).isEqualTo(PushResult.EXPIRED);
        assertThat(WebPushSender.classify(429, "h")).isEqualTo(PushResult.FAILED);
        assertThat(WebPushSender.classify(500, "h")).isEqualTo(PushResult.FAILED);
    }

    @Test
    void endpoint는_https의_허용된_호스트만_받는다() {
        assertThat(PushEndpoints.isAllowed("https://fcm.googleapis.com/fcm/send/x", HOSTS)).isTrue();
        assertThat(PushEndpoints.isAllowed("https://updates.push.services.mozilla.com/wpush/v2/x", HOSTS)).isTrue();
        assertThat(PushEndpoints.isAllowed("http://fcm.googleapis.com/x", HOSTS)).isFalse();
        assertThat(PushEndpoints.isAllowed("https://fcm.googleapis.com.evil.io/x", HOSTS)).isFalse();
        assertThat(PushEndpoints.isAllowed("https://xfcm.googleapis.com/x", HOSTS)).isFalse();
        assertThat(PushEndpoints.isAllowed("https://127.0.0.1/x", HOSTS)).isFalse();
        assertThat(PushEndpoints.isAllowed(null, HOSTS)).isFalse();
        assertThat(PushEndpoints.host("https://fcm.googleapis.com/fcm/send/secret-token")).isEqualTo("fcm.googleapis.com");
        assertThat(new PushTarget("https://fcm.googleapis.com/fcm/send/secret-token", "k", "a").toString())
                .doesNotContain("secret-token");
    }
}
