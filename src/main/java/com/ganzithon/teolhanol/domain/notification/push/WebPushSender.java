package com.ganzithon.teolhanol.domain.notification.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Notification;

/**
 * VAPID 웹 푸시 발송기 (WOL-02). 요청 스레드가 아닌 알림 전용 스레드에서 불립니다.
 * 실패는 로그만 남기고(endpoint 전체·키·본문은 남기지 않음), 404/410이면 {@link PushResult#EXPIRED}로 알려 구독을 지우게 합니다.
 */
@Slf4j
public class WebPushSender implements PushSender {

    /** JDK HttpClient가 직접 정하는 헤더 */
    private static final Set<String> RESTRICTED_HEADERS = Set.of("content-length", "host", "connection");

    private final VapidRequestFactory factory;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final int ttlSeconds;

    WebPushSender(VapidRequestFactory factory, HttpClient httpClient, Duration timeout, int ttlSeconds) {
        this.factory = factory;
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public PushResult send(PushTarget target, String payload) {
        String host = PushEndpoints.host(target.endpoint());
        try {
            HttpResponse<Void> response = httpClient.send(toHttpRequest(prepare(target, payload)),
                    HttpResponse.BodyHandlers.discarding());
            return classify(response.statusCode(), host);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("웹 푸시 발송 중단 host={}", host);
            return PushResult.FAILED;
        } catch (Exception e) {
            log.warn("웹 푸시 발송 실패 host={} error={}", host, e.getClass().getSimpleName());
            return PushResult.FAILED;
        }
    }

    /** 암호화된 본문과 VAPID 헤더를 만듭니다. 네트워크는 쓰지 않습니다. */
    HttpRequest prepare(PushTarget target, String payload) throws Exception {
        Notification notification = Notification.builder()
                .endpoint(target.endpoint())
                .userPublicKey(target.p256dh())
                .userAuth(target.auth())
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .ttl(ttlSeconds)
                .build();
        return factory.request(notification);
    }

    private java.net.http.HttpRequest toHttpRequest(HttpRequest prepared) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create(prepared.getUrl()))
                .timeout(timeout)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(
                        prepared.getBody() == null ? new byte[0] : prepared.getBody()));
        for (Map.Entry<String, String> header : prepared.getHeaders().entrySet()) {
            if (!RESTRICTED_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return builder.build();
    }

    static PushResult classify(int status, String host) {
        if (status >= 200 && status < 300) {
            return PushResult.SENT;
        }
        if (status == 404 || status == 410) {
            log.info("만료된 웹 푸시 구독 host={} status={}", host, status);
            return PushResult.EXPIRED;
        }
        log.warn("웹 푸시 발송 실패 host={} status={}", host, status);
        return PushResult.FAILED;
    }
}
