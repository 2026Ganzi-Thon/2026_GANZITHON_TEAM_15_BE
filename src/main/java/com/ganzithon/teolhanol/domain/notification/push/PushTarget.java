package com.ganzithon.teolhanol.domain.notification.push;

/** 웹 푸시를 받을 브라우저 구독. endpoint와 키는 로그에 남기지 않습니다. */
public record PushTarget(String endpoint, String p256dh, String auth) {

    @Override
    public String toString() {
        return "PushTarget[host=" + PushEndpoints.host(endpoint) + "]";
    }
}
