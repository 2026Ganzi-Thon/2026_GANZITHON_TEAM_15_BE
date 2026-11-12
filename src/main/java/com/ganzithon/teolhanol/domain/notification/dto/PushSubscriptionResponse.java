package com.ganzithon.teolhanol.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 구독 저장·해지 결과. endpoint와 키는 돌려주지 않습니다. */
public record PushSubscriptionResponse(
        @Schema(description = "이 브라우저가 지금 내 계정으로 구독되어 있는지", example = "true") boolean subscribed,
        @Schema(description = "웹 푸시가 켜져 있는지 (서버에 VAPID 키가 있는지)", example = "true") boolean pushEnabled
) {
}
