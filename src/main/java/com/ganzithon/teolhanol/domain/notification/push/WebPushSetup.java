package com.ganzithon.teolhanol.domain.notification.push;

import com.ganzithon.teolhanol.domain.notification.config.PushProperties;
import java.net.http.HttpClient;
import java.security.Security;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * 웹 푸시 준비 상태 (VAPID 키 확인 결과와 발송기). 키가 없거나 잘못되면 enabled=false이고 웹 푸시만 꺼집니다
 * (서버는 정상 기동, 구독 저장·SSE는 동작). 키 값은 로그에 남기지 않습니다.
 *
 * @param enabled   웹 푸시를 보낼 수 있는지
 * @param publicKey 프론트가 구독할 때 쓰는 공개키 (꺼져 있으면 null)
 * @param sender    키 상태에 맞는 발송기
 */
@Slf4j
public record WebPushSetup(boolean enabled, String publicKey, PushSender sender) {

    public static WebPushSetup from(PushProperties properties) {
        String publicKey = strip(properties.publicKey());
        String privateKey = strip(properties.privateKey());
        if (publicKey == null || privateKey == null) {
            log.warn("VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY가 없어 웹 푸시를 끈 상태로 시작합니다. 구독 저장과 앱 안 알림은 동작합니다.");
            return disabled();
        }
        String subject = strip(properties.subject());
        if (subject == null) {
            log.warn("VAPID_SUBJECT가 비어 있습니다. 일부 브라우저(Safari)로는 웹 푸시가 가지 않을 수 있습니다.");
        }
        try {
            ensureBouncyCastle();
            VapidRequestFactory factory = new VapidRequestFactory(publicKey, privateKey, subject);
            Duration timeout = Duration.ofSeconds(properties.sendTimeoutSeconds());
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            log.info("웹 푸시를 켠 상태로 시작합니다.");
            return new WebPushSetup(true, publicKey,
                    new WebPushSender(factory, httpClient, timeout, properties.ttlSeconds()));
        } catch (Exception e) {
            log.warn("VAPID 키를 읽지 못해 웹 푸시를 끈 상태로 시작합니다. error={}", e.getClass().getSimpleName());
            return disabled();
        }
    }

    private static WebPushSetup disabled() {
        return new WebPushSetup(false, null, new DisabledPushSender());
    }

    private static void ensureBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "WebPushSetup[enabled=" + enabled + "]";
    }
}
