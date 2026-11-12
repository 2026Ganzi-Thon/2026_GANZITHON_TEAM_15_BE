package com.ganzithon.teolhanol.domain.notification.service;

import com.ganzithon.teolhanol.domain.notification.config.PushProperties;
import com.ganzithon.teolhanol.domain.notification.dto.NotificationPayload;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * 앱 안 알림 SSE 연결 모음 (WOL-02, 앱 안 토스트). 한 사용자가 여러 탭을 열 수 있고, 끊기거나 시간이 다 된 연결은 바로 뺍니다.
 * 이 서버 인스턴스에 연결된 탭에만 보냅니다(단일 인스턴스 배포 기준).
 */
@Slf4j
@Component
public class NotificationStreams {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final PushProperties properties;
    private final ObjectMapper mapper;

    public NotificationStreams(PushProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    /** 새 연결을 엽니다. 탭 수가 상한을 넘으면 가장 오래된 연결을 닫습니다. */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(properties.sseTimeoutMinutes()).toMillis());
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));

        List<SseEmitter> evicted = new ArrayList<>();
        emitters.compute(userId, (id, list) -> {
            List<SseEmitter> current = list == null ? new CopyOnWriteArrayList<>() : list;
            current.add(emitter);
            while (current.size() > Math.max(1, properties.sseMaxPerUser())) {
                evicted.add(current.remove(0));
            }
            return current;
        });
        evicted.forEach(SseEmitter::complete);

        // 첫 바이트를 보내 응답 헤더를 바로 내려보냅니다(프론트 fetch가 연결됨을 알 수 있게).
        sendOrDrop(userId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** 이 사용자의 열린 탭 모두에 알림을 보냅니다. 연결이 없으면 아무것도 하지 않습니다. */
    public void send(Long userId, NotificationPayload payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String data = mapper.writeValueAsString(payload);
        for (SseEmitter emitter : list) {
            sendOrDrop(userId, emitter,
                    SseEmitter.event().name(payload.type().getEventName()).data(data, MediaType.APPLICATION_JSON));
        }
    }

    /** 프록시·브라우저가 쉬는 연결을 끊지 않도록 주기적으로 주석을 보냅니다. 끊긴 연결은 이때 정리됩니다. */
    @Scheduled(fixedDelayString = "${app.push.sse-heartbeat-seconds:25}", initialDelayString = "${app.push.sse-heartbeat-seconds:25}",
            timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        emitters.forEach((userId, list) -> list.forEach(e -> sendOrDrop(userId, e, SseEmitter.event().comment("ping"))));
    }

    /** 이 사용자의 열린 연결 수 */
    public int connectionCount(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list == null ? 0 : list.size();
    }

    private void sendOrDrop(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // 클라이언트가 떠났거나 이미 끝난 연결
            remove(userId, emitter);
            log.debug("알림 스트림 정리 userId={} error={}", userId, e.getClass().getSimpleName());
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
