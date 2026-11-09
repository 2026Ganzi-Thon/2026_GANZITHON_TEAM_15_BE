package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.dto.CareEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 전송 도우미. 사용자가 연결을 끊으면 이후 전송을 조용히 건너뜁니다
 * (이미 시작한 응답은 끝까지 만들어 저장하므로 다시 들어오면 이어서 볼 수 있습니다).
 */
final class CareEvents {

    private final SseEmitter emitter;
    private final ObjectMapper mapper;
    private boolean closed;

    CareEvents(SseEmitter emitter, ObjectMapper mapper) {
        this.emitter = emitter;
        this.mapper = mapper;
    }

    void delta(String text) {
        send("delta", new CareEvent.Delta(text));
    }

    void progress(CareEvent.Progress progress) {
        send("progress", progress);
    }

    void crisis(String message) {
        send("crisis", new CareEvent.Crisis(message, List.of("call", "continue")));
    }

    void notice(String message) {
        send("notice", new CareEvent.Notice(message));
    }

    /** 지금까지 보낸 AI 응답 조각을 지우고 처음부터 다시 받는다는 신호 */
    void reset() {
        send("reset", Map.of());
    }

    void replace(String text) {
        send("replace", new CareEvent.Replace(text));
    }

    void error(String code, String message) {
        send("error", new CareEvent.Error(code, message));
    }

    void done() {
        send("done", Map.of());
    }

    void complete() {
        if (!closed) {
            closed = true;
            emitter.complete();
        }
    }

    private void send(String name, Object data) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(mapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            closed = true;
        }
    }
}
