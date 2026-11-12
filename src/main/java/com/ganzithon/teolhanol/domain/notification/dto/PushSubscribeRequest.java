package com.ganzithon.teolhanol.domain.notification.dto;

import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 브라우저 PushSubscription.toJSON() 결과 그대로 (expirationTime 등 다른 필드는 무시) */
public record PushSubscribeRequest(
        @Schema(description = "푸시 서비스 주소", example = "https://fcm.googleapis.com/fcm/send/dXJ...")
        @NotBlank @Size(max = PushSubscription.ENDPOINT_MAX_LENGTH) String endpoint,
        @NotNull @Valid Keys keys
) {

    public record Keys(
            @Schema(description = "브라우저 공개키 (base64url)", example = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM")
            @NotBlank @Size(max = 200) @Pattern(regexp = BASE64URL) String p256dh,
            @Schema(description = "인증 비밀값 (base64url)", example = "tBHItJI5svbpez7KI4CCXg")
            @NotBlank @Size(max = 100) @Pattern(regexp = BASE64URL) String auth
    ) {
    }

    /** base64url(패딩 허용). 일부 브라우저가 표준 base64 문자를 쓰는 경우도 받아 줍니다. */
    static final String BASE64URL = "^[A-Za-z0-9_\\-+/]+={0,2}$";
}
