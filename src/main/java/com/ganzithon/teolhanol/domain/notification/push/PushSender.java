package com.ganzithon.teolhanol.domain.notification.push;

/**
 * 웹 푸시 한 건을 보냅니다 (WOL-02). 운영에서는 {@link WebPushSender}, VAPID 키가 없으면 {@link DisabledPushSender}입니다.
 * 테스트에서는 {@code @MockitoBean}으로 바꿔 외부 호출 없이 검증합니다. 예외를 던지지 않고 결과로 알려 줍니다.
 */
public interface PushSender {

    /**
     * @param target  받을 브라우저 구독
     * @param payload 서비스워커가 받을 JSON 문자열 (조언 본문·닉네임은 담지 않음)
     */
    PushResult send(PushTarget target, String payload);
}
