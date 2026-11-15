package com.ganzithon.teolhanol.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(
        @Schema(description = "해지할 구독의 푸시 서비스 주소", example = "https://fcm.googleapis.com/fcm/send/dXJ...")
        @NotBlank String endpoint
) {
}
