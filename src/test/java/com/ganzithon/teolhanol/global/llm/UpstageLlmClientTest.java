package com.ganzithon.teolhanol.global.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** JDK 내장 HttpServer로 Upstage API를 흉내 내 연동을 검증합니다 (실제 API는 호출하지 않음). */
class UpstageLlmClientTest {

    record Verdict(String level, String reasonCode) {
    }

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile Handler handler;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            handler.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private UpstageLlmClient client(int maxRetries) {
        UpstageProperties upstage = new UpstageProperties("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "");
        LlmProperties llm = new LlmProperties("", "", "claude-opus-5-5", 10, maxRetries, false);
        return new UpstageLlmClient(upstage, llm, JsonMapper.builder().build());
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String completion(String content, String finishReason) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped
                + "\"},\"finish_reason\":\"" + finishReason + "\"}]}";
    }

    private static LlmRequest chat(boolean retry) {
        return new LlmRequest("너는 다정한 상대야.",
                List.of(LlmMessage.user("안녕"), LlmMessage.assistant("어서 와요."), LlmMessage.user("피곤해요")),
                8192, LlmRequest.Effort.MEDIUM, Duration.ofSeconds(5), retry);
    }

    @Test
    void 요청은_OpenAI_호환_형식으로_가고_응답_텍스트를_돌려준다() {
        handler = ex -> respond(ex, 200, "application/json", completion("많이 지쳤군요.", "stop"));

        String text = client(0).complete(chat(false));

        assertThat(text).isEqualTo("많이 지쳤군요.");
        assertThat(lastPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(lastBody.get())
                .contains("\"model\":\"solar-pro4\"")
                .contains("\"stream\":false")
                .contains("\"max_tokens\":4096")
                .contains("\"role\":\"system\"").contains("너는 다정한 상대야.")
                .contains("\"role\":\"assistant\"")
                .doesNotContain("reasoning");
    }

    @Test
    void JSON_응답은_코드_블록이_붙어도_해석한다() {
        handler = ex -> respond(ex, 200, "application/json",
                completion("```json\n{\"level\":\"caution\",\"reasonCode\":\"HOPELESSNESS\"}\n```", "stop"));

        Verdict verdict = client(0).completeJson(LlmRequest.classification("분류해", "글", Duration.ofSeconds(5)), Verdict.class);

        assertThat(verdict).isEqualTo(new Verdict("caution", "HOPELESSNESS"));
        assertThat(lastBody.get()).contains("\\\"level\\\": string").contains("\\\"reasonCode\\\": string");
    }

    @Test
    void JSON이_아니면_INVALID_RESPONSE() {
        handler = ex -> respond(ex, 200, "application/json", completion("잘 모르겠어요", "stop"));

        assertThatThrownBy(() -> client(0).completeJson(
                LlmRequest.classification("분류해", "글", Duration.ofSeconds(5)), Verdict.class))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.INVALID_RESPONSE));
    }

    @Test
    void 콘텐츠_필터에_걸리면_REFUSAL() {
        handler = ex -> respond(ex, 200, "application/json", completion("", "content_filter"));

        assertThatThrownBy(() -> client(0).complete(chat(false)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.REFUSAL));
    }

    @Test
    void 인증_오류는_재시도하지_않고_API_ERROR() {
        handler = ex -> respond(ex, 401, "application/json", "{\"error\":\"unauthorized\"}");

        assertThatThrownBy(() -> client(2).complete(chat(true)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.API_ERROR));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 한도_초과는_재시도_허용_호출만_다시_시도한다() {
        AtomicInteger n = new AtomicInteger();
        handler = ex -> {
            if (n.incrementAndGet() < 3) {
                respond(ex, 429, "application/json", "{}");
            } else {
                respond(ex, 200, "application/json", completion("괜찮아요.", "stop"));
            }
        };
        assertThat(client(2).complete(chat(true))).isEqualTo("괜찮아요.");
        assertThat(calls.get()).isEqualTo(3);

        calls.set(0);
        n.set(0);
        assertThatThrownBy(() -> client(2).complete(chat(false)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.API_ERROR));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 응답이_느리면_TIMEOUT() {
        handler = ex -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, "application/json", completion("늦은 답", "stop"));
        };
        LlmRequest slow = new LlmRequest("s", List.of(LlmMessage.user("u")), 100, LlmRequest.Effort.LOW,
                Duration.ofMillis(300), false);

        assertThatThrownBy(() -> client(0).complete(slow))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.TIMEOUT));
    }

    @Test
    void 서버에_연결할_수_없으면_UNAVAILABLE() {
        UpstageLlmClient dead = new UpstageLlmClient(new UpstageProperties("k", "http://127.0.0.1:1/v1", ""),
                new LlmProperties("", "", "m", 10, 0, false), JsonMapper.builder().build());

        assertThatThrownBy(() -> dead.complete(chat(false)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.UNAVAILABLE));
    }

    @Test
    void 스트리밍은_조각을_차례로_넘기고_전체를_돌려준다() {
        handler = ex -> respond(ex, 200, "text/event-stream",
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"많이 \"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"지쳤군요.\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n");
        List<String> deltas = new ArrayList<>();

        String full = client(0).stream(chat(false), deltas::add);

        assertThat(deltas).containsExactly("많이 ", "지쳤군요.");
        assertThat(full).isEqualTo("많이 지쳤군요.");
        assertThat(lastBody.get()).contains("\"stream\":true");
    }

    @Test
    void 스트리밍_중_오류_상태면_API_ERROR() {
        handler = ex -> respond(ex, 500, "application/json", "{}");

        assertThatThrownBy(() -> client(0).stream(chat(false), d -> { }))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getType()).isEqualTo(LlmException.Type.API_ERROR));
    }
}
