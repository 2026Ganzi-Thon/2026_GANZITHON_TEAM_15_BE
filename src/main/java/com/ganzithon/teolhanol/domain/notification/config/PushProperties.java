package com.ganzithon.teolhanol.domain.notification.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 설정 (WOL-02, app.push). VAPID 키가 비어 있거나 잘못되면 웹 푸시만 꺼지고 구독 저장과 앱 안 알림(SSE)은 동작합니다.
 *
 * @param publicKey           VAPID 공개키 (base64url, 비압축 P-256 점 65바이트)
 * @param privateKey          VAPID 비밀키 (base64url, 32바이트)
 * @param subject             VAPID subject (mailto: 또는 https: 주소). 일부 푸시 서비스(Apple)는 필수
 * @param clickUrlPattern     털 도착 알림을 눌렀을 때 열 프론트 경로. {sheepId}를 양 id로 바꿉니다
 * @param allowedHosts        구독 endpoint로 받아 줄 푸시 서비스 호스트(뒤쪽 일치). 서버가 임의 주소로 요청하지 않게 막습니다
 * @param ttlSeconds          푸시 서비스가 기기에 전달하지 못했을 때 보관할 시간
 * @param sendTimeoutSeconds  푸시 서비스 요청 타임아웃
 * @param sseTimeoutMinutes   앱 안 알림 스트림 연결 유지 시간. 끝나면 프론트가 다시 연결합니다
 * @param sseHeartbeatSeconds 연결 유지용 주석(heartbeat) 간격
 * @param sseMaxPerUser       한 사용자가 동시에 열 수 있는 스트림 수(탭 수). 넘으면 가장 오래된 연결을 닫습니다
 */
@ConfigurationProperties("app.push")
public record PushProperties(
        String publicKey,
        String privateKey,
        String subject,
        String clickUrlPattern,
        List<String> allowedHosts,
        int ttlSeconds,
        int sendTimeoutSeconds,
        long sseTimeoutMinutes,
        int sseHeartbeatSeconds,
        int sseMaxPerUser
) {

    public PushProperties {
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
    }

    /** 털 도착 알림을 눌렀을 때 열 경로 */
    public String clickUrl(Long sheepId) {
        return clickUrlPattern.replace("{sheepId}", String.valueOf(sheepId));
    }
}
