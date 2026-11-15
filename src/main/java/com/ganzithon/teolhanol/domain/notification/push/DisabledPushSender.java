package com.ganzithon.teolhanol.domain.notification.push;

/** VAPID 키가 없을 때 쓰는 발송기. 아무것도 보내지 않습니다. */
public class DisabledPushSender implements PushSender {

    @Override
    public PushResult send(PushTarget target, String payload) {
        return PushResult.DISABLED;
    }
}
