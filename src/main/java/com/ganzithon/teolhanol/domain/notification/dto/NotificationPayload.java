package com.ganzithon.teolhanol.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 앱 안 알림(SSE data)과 웹 푸시 payload에 똑같이 쓰는 알림 내용. 조언 본문·닉네임·다른 사용자의 id는 담지 않습니다.
 * 서비스워커는 type으로 앱 안 토스트와의 중복 표시를 판단합니다.
 */
public record NotificationPayload(
        @Schema(description = "알림 종류", example = "WOOL_ARRIVED") NotificationType type,
        @Schema(description = "알림 제목", example = "털이 풍성해졌어요") String title,
        @Schema(description = "알림 본문 (조언 내용은 담지 않음)", example = "내 고민 양에 새 털 한 올이 도착했어요.") String body,
        @Schema(description = "관련 고민 양 id", example = "12") Long sheepId,
        @Schema(description = "누르면 열 프론트 경로. 고마워요 알림은 null", example = "/farm/my/12", nullable = true) String url
) {

    public static NotificationPayload woolArrived(Long sheepId, String url) {
        return of(NotificationType.WOOL_ARRIVED, sheepId, url);
    }

    /** 조언 보낸 사람은 그 양 상세를 볼 수 없을 수 있어 이동할 경로를 넣지 않습니다. */
    public static NotificationPayload woolThanked(Long sheepId) {
        return of(NotificationType.WOOL_THANKED, sheepId, null);
    }

    private static NotificationPayload of(NotificationType type, Long sheepId, String url) {
        return new NotificationPayload(type, type.getTitle(), type.getBody(), sheepId, url);
    }
}
