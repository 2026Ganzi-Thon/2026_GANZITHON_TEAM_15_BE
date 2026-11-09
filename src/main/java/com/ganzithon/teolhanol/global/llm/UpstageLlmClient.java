package com.ganzithon.teolhanol.global.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Upstage Solar API 구현체. OpenAI 호환 chat completions(`POST {baseUrl}/chat/completions`)를 JDK HttpClient로 부릅니다.
 * 사용자 본문이 로그에 남지 않도록 로그에는 실패 종류만 기록합니다.
 *
 * <ul>
 *   <li>생각 깊이(effort)는 보내지 않습니다. Solar Pro 4는 기본이 꺼져 있어 분류·첫 마디처럼 빨라야 하는 호출에 알맞습니다.</li>
 *   <li>JSON 응답은 스키마 강제 대신 프롬프트로 요청하고 직접 파싱합니다. 형식이 어긋나면 INVALID_RESPONSE입니다.</li>
 * </ul>
 */
@Slf4j
public class UpstageLlmClient implements LlmClient {

    private static final long MAX_OUTPUT_TOKENS = 4096;
    private static final long RETRY_BACKOFF_MILLIS = 300;

    private final HttpClient http;
    private final UpstageProperties upstage;
    private final LlmProperties llm;
    private final ObjectMapper mapper;

    public UpstageLlmClient(UpstageProperties upstage, LlmProperties llm, ObjectMapper mapper) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.upstage = upstage;
        this.llm = llm;
        this.mapper = mapper;
    }

    @Override
    public String complete(LlmRequest request) {
        return extractText(callForText(request, buildBody(request, request.systemPrompt(), false)));
    }

    @Override
    public <T> T completeJson(LlmRequest request, Class<T> type) {
        String system = (request.systemPrompt() == null ? "" : request.systemPrompt() + "\n\n") + jsonInstruction(type);
        String text = extractText(callForText(request, buildBody(request, system, false)));
        try {
            return mapper.readValue(extractJsonObject(text), type);
        } catch (JacksonException e) {
            log.warn("LLM JSON 응답을 해석하지 못했습니다");
            throw new LlmException(LlmException.Type.INVALID_RESPONSE, "JSON 응답 형식이 맞지 않습니다.", e);
        }
    }

    @Override
    public String stream(LlmRequest request, Consumer<String> onDelta) {
        StringBuilder full = new StringBuilder();
        String body = buildBody(request, request.systemPrompt(), true);
        HttpResponse<Stream<String>> response = post(request, body, HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (!line.startsWith("data:")) {
                    return;
                }
                String payload = line.substring(5).strip();
                if (payload.isEmpty() || payload.equals("[DONE]")) {
                    return;
                }
                JsonNode choice = parse(payload).path("choices").path(0);
                checkFinish(choice.path("finish_reason").asString(""));
                String delta = choice.path("delta").path("content").asString("");
                if (!delta.isEmpty()) {
                    full.append(delta);
                    onDelta.accept(delta);
                }
            });
        } catch (UncheckedIOException e) {
            throw ioFailure(e.getCause());
        }
        return full.toString();
    }

    // ---- 요청 만들기 ----

    private String buildBody(LlmRequest request, String systemPrompt, boolean stream) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", upstage.model());
        root.put("max_tokens", Math.min(request.maxTokens(), MAX_OUTPUT_TOKENS));
        root.put("stream", stream);
        ArrayNode messages = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        for (LlmMessage message : request.messages()) {
            messages.addObject()
                    .put("role", message.role() == LlmMessage.Role.USER ? "user" : "assistant")
                    .put("content", message.content());
        }
        return mapper.writeValueAsString(root);
    }

    /** 응답 JSON의 필드 이름과 형식을 알려 주는 지시문. record 구성요소에서 만듭니다. */
    private static String jsonInstruction(Class<?> type) {
        String fields = Stream.of(type.getRecordComponents())
                .map(UpstageLlmClient::describe)
                .collect(Collectors.joining(", "));
        return "응답은 설명이나 코드 블록 없이 다음 필드를 가진 JSON 객체 하나만 출력합니다: {" + fields + "}";
    }

    private static String describe(RecordComponent c) {
        Class<?> t = c.getType();
        String kind = (t == int.class || t == long.class || t == Integer.class || t == Long.class
                || t == double.class || t == Double.class) ? "number"
                : (t == boolean.class || t == Boolean.class) ? "boolean" : "string";
        return "\"" + c.getName() + "\": " + kind;
    }

    // ---- 호출 ----

    private String callForText(LlmRequest request, String body) {
        HttpResponse<String> response = post(request, body, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private <T> HttpResponse<T> post(LlmRequest request, String body, HttpResponse.BodyHandler<T> handler) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(upstage.baseUrl() + "/chat/completions"))
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + upstage.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        int attempts = 1 + (request.retry() ? Math.max(0, llm.maxRetries()) : 0);
        LlmException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<T> response = http.send(httpRequest, handler);
                int status = response.statusCode();
                if (status == 200) {
                    return response;
                }
                closeQuietly(response.body());
                log.warn("LLM API error: status={}", status);
                last = new LlmException(LlmException.Type.API_ERROR, "LLM API 오류 (" + status + ")");
                if (status != 429 && status < 500) {
                    throw last;
                }
            } catch (HttpTimeoutException e) {
                log.warn("LLM timeout");
                throw new LlmException(LlmException.Type.TIMEOUT, "LLM 응답 시간이 초과됐습니다.", e);
            } catch (IOException e) {
                last = ioFailure(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException(LlmException.Type.UNAVAILABLE, "LLM 호출이 중단됐습니다.", e);
            }
            if (attempt < attempts) {
                sleep(RETRY_BACKOFF_MILLIS * attempt);
            }
        }
        throw last;
    }

    private static LlmException ioFailure(Throwable e) {
        if (e instanceof HttpTimeoutException) {
            log.warn("LLM timeout");
            return new LlmException(LlmException.Type.TIMEOUT, "LLM 응답 시간이 초과됐습니다.", e);
        }
        log.warn("LLM unavailable: {}", e.getClass().getSimpleName());
        return new LlmException(LlmException.Type.UNAVAILABLE, "LLM 서버에 연결하지 못했습니다.", e);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Object body) {
        if (body instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 오류 응답 본문은 쓰지 않으므로 닫기 실패는 무시한다
            }
        }
    }

    // ---- 응답 해석 ----

    private String extractText(String responseBody) {
        JsonNode choice = parse(responseBody).path("choices").path(0);
        checkFinish(choice.path("finish_reason").asString(""));
        String content = choice.path("message").path("content").asString("");
        if (content.isBlank()) {
            throw new LlmException(LlmException.Type.INVALID_RESPONSE, "응답이 비어 있습니다.");
        }
        return content;
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JacksonException e) {
            throw new LlmException(LlmException.Type.INVALID_RESPONSE, "LLM 응답을 처리하지 못했습니다.", e);
        }
    }

    private static void checkFinish(String finishReason) {
        if ("content_filter".equals(finishReason)) {
            throw new LlmException(LlmException.Type.REFUSAL, "모델이 응답을 거절했습니다.");
        }
    }

    /** 코드 블록이나 앞뒤 설명이 붙어도 첫 JSON 객체를 꺼냅니다. */
    static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }
}
