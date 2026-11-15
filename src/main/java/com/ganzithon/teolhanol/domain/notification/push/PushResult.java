package com.ganzithon.teolhanol.domain.notification.push;

public enum PushResult {
    /** 푸시 서비스가 받았습니다 (2xx) */
    SENT,
    /** 구독이 만료되었거나 사라졌습니다 (404/410). 구독을 지웁니다 */
    EXPIRED,
    /** 일시적인 실패 (그 밖의 응답, 네트워크 오류). 구독은 유지합니다 */
    FAILED,
    /** 웹 푸시가 꺼져 있어 보내지 않았습니다 (VAPID 키 없음) */
    DISABLED
}
