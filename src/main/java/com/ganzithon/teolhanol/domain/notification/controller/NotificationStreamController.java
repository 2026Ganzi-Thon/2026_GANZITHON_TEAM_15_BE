package com.ganzithon.teolhanol.domain.notification.controller;

import com.ganzithon.teolhanol.domain.notification.service.NotificationStreams;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "털 도착 알림 (웹 푸시 + 앱 안)")
@RestController
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreams streams;

    @Operation(summary = "앱 안 알림 스트림 (WOL-02, NOTI-01)",
            description = """
                    text/event-stream으로 앱 안 토스트용 알림을 받습니다. EventSource가 아니라 Authorization 헤더를 보낼 수 있는 \
                    fetch 스트림으로 읽습니다(케어 대화 SSE와 같은 방식). 탭마다 연결할 수 있고, 연결이 끝나면(약 30분) 다시 연결합니다.
                    - wool-arrived {type: WOOL_ARRIVED, title, body, sheepId, url}: 내 고민 양에 털이 도착함. url은 내 고민 양 상세 경로
                    - wool-thanked {type: WOOL_THANKED, title, body, sheepId, url: null}: 내가 보낸 조언에 고마워요가 눌림
                    - 주석(:connected, :ping): 연결 확인·유지용이며 무시합니다
                    알림에는 조언 내용과 닉네임이 들어 있지 않습니다. 웹 푸시 구독이 있으면 같은 알림이 웹 푸시로도 갑니다.
                    """)
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user, HttpServletResponse response) {
        // 프록시(nginx 등)가 스트림을 모아 두지 않도록 합니다.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return streams.connect(user.id());
    }
}
