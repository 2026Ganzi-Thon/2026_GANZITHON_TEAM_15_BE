package com.ganzithon.teolhanol.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VapidPublicKeyResponse(
        @Schema(description = "pushManager.subscribe의 applicationServerKey로 쓸 VAPID 공개키(base64url). 꺼져 있으면 null",
                example = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
                nullable = true)
        String publicKey,
        @Schema(description = "웹 푸시가 켜져 있는지. false면 구독 요청 없이 앱 안 알림만 씁니다", example = "true")
        boolean enabled
) {
}
