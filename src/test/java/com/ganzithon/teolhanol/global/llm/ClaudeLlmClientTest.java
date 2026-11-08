package com.ganzithon.teolhanol.global.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JDK 내장 HttpServer로 Claude API를 흉내 내 SDK 연동을 검증합니다 (실제 API는 호출하지 않음). */
class ClaudeLlmClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastBeta = new AtomicReference<>();
    private volatile Handler handler;

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    record Verdict(String level, String reasonCode) {
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastBeta.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            handler.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ClaudeLlmClient client(boolean serverFallback) {
        LlmProperties props = new LlmProperties("test-key", "http://127.0.0.1:" + server.getAddress().getPort(),
                "claude-opus-5-5", 10, 0, serverFallback);
        return new ClaudeLlmClient(AnthropicOkHttpClient.builder()
                .apiKey(props.apiKey()).baseUrl(props.baseUrl()).maxRetries(0).build(), props);
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String message(String text, String stopReason) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"%s"}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(escaped, stopReason);
    }

    @Test
    void 일반_호출은_텍스트를_돌려주고_모델과_생각_깊이와_거절_대체를_요청에_담는다() {
        handler = ex -> json(ex, message("오늘 많이 지치셨군요.", "end_turn"));

        String result = client(true).complete(
                LlmRequest.chat("시스템", List.of(LlmMessage.user("지쳤어요")), Duration.ofSeconds(5)));

        assertThat(result).isEqualTo("오늘 많이 지치셨군요.");
        assertThat(lastBody.get())
                .contains("\"model\":\"claude-opus-5-5\"")
                .contains("\"effort\":\"medium\"")
                .contains("\"fallbacks\":\"default\"")
                .contains("지쳤어요");
        assertThat(lastBeta.get()).contains("server-side-fallback-2026-07-01");
    }

    @Test
    void 거절_대체를_끄면_요청에_넣지_않는다() {
        handler = ex -> json(ex, message("네", "end_turn"));

        client(false).complete(LlmRequest.classification("시스템", "안녕", Duration.ofSeconds(5)));

        assertThat(lastBody.get()).doesNotContain("fallbacks").contains("\"effort\":\"low\"");
    }

    @Test
    void JSON_응답을_객체로_변환하고_스키마를_요청에_담는다() {
        handler = ex -> json(ex, message("{\"level\":\"CAUTION\",\"reasonCode\":\"HOPELESS\"}", "end_turn"));

        Verdict verdict = client(true).completeJson(
                LlmRequest.classification("분류", "다 그만두고 싶어요", Duration.ofSeconds(5)), Verdict.class);

        assertThat(verdict).isEqualTo(new Verdict("CAUTION", "HOPELESS"));
        assertThat(lastBody.get()).contains("json_schema").contains("reasonCode");
    }

    @Test
    void 스트리밍은_조각을_차례로_넘기고_전체_텍스트를_돌려준다() {
        handler = ex -> {
            String sse = String.join("",
                    event("message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}}"),
                    event("content_block_start", "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"),
                    event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"마음을 \"}}"),
                    event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"들려줘서 고마워요\"}}"),
                    event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}"),
                    event("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":5}}"),
                    event("message_stop", "{\"type\":\"message_stop\"}"));
            byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        };
        List<String> deltas = new ArrayList<>();

        String full = client(true).stream(
                LlmRequest.chat("시스템", List.of(LlmMessage.user("hi")), Duration.ofSeconds(5)), deltas::add);

        assertThat(deltas).containsExactly("마음을 ", "들려줘서 고마워요");
        assertThat(full).isEqualTo("마음을 들려줘서 고마워요");
        assertThat(lastBody.get()).contains("\"stream\":true");
    }

    @Test
    void 타임아웃이_지나면_TIMEOUT으로_실패한다() {
        handler = ex -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            json(ex, message("늦은 응답", "end_turn"));
        };

        assertThatThrownBy(() -> client(true).complete(
                LlmRequest.classification("분류", "내용", Duration.ofMillis(300))))
                .isInstanceOf(LlmException.class)
                .extracting("type").isEqualTo(LlmException.Type.TIMEOUT);
    }

    @Test
    void 모델이_거절하면_REFUSAL로_실패한다() {
        handler = ex -> json(ex, message("", "refusal"));

        assertThatThrownBy(() -> client(true).complete(
                LlmRequest.classification("분류", "내용", Duration.ofSeconds(5))))
                .isInstanceOf(LlmException.class)
                .extracting("type").isEqualTo(LlmException.Type.REFUSAL);
    }

    @Test
    void 서버_오류는_API_ERROR로_실패하고_본문을_메시지에_넣지_않는다() {
        handler = ex -> {
            byte[] bytes = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(529, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        };

        assertThatThrownBy(() -> client(true).complete(
                LlmRequest.classification("분류", "비밀스러운 일기 내용", Duration.ofSeconds(5))))
                .isInstanceOf(LlmException.class)
                .hasMessageNotContaining("비밀스러운")
                .extracting("type").isEqualTo(LlmException.Type.API_ERROR);
    }

    @Test
    void API_키가_없으면_UNAVAILABLE로_실패한다() {
        DisabledLlmClient disabled = new DisabledLlmClient();

        assertThatThrownBy(() -> disabled.complete(LlmRequest.classification("s", "c", Duration.ofSeconds(1))))
                .isInstanceOf(LlmException.class)
                .extracting("type").isEqualTo(LlmException.Type.UNAVAILABLE);
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data + "\n\n";
    }
}
