package com.ganzithon.teolhanol.global.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import java.io.InterruptedIOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Claude API 구현체. 사용자 본문이 로그에 남지 않도록 로그에는 실패 종류만 기록합니다.
 */
@Slf4j
public class ClaudeLlmClient implements LlmClient {

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final AnthropicClient noRetryClient;
    private final LlmProperties properties;

    public ClaudeLlmClient(AnthropicClient client, LlmProperties properties) {
        this.client = client;
        this.noRetryClient = client.withOptions(options -> options.maxRetries(0));
        this.properties = properties;
    }

    @Override
    public String complete(LlmRequest request) {
        Message message = call(() -> clientFor(request).messages().create(params(request), options(request)));
        checkStop(message.stopReason());
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .reduce("", String::concat);
    }

    @Override
    public <T> T completeJson(LlmRequest request, Class<T> type) {
        StructuredMessageCreateParams<T> params = params(request).toBuilder()
                .outputConfig(type, JsonSchemaLocalValidation.YES)
                .build();
        StructuredMessage<T> message = call(() -> clientFor(request).messages().create(params, options(request)));
        checkStop(message.stopReason());
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new LlmException(LlmException.Type.INVALID_RESPONSE, "JSON 응답이 비어 있습니다."));
    }

    @Override
    public String stream(LlmRequest request, Consumer<String> onDelta) {
        StringBuilder full = new StringBuilder();
        call(() -> {
            try (StreamResponse<RawMessageStreamEvent> response =
                         clientFor(request).messages().createStreaming(params(request), options(request))) {
                response.stream().forEach(event -> {
                    event.contentBlockDelta()
                            .flatMap(delta -> delta.delta().text())
                            .ifPresent(text -> {
                                full.append(text.text());
                                onDelta.accept(text.text());
                            });
                    event.messageDelta().ifPresent(delta -> checkStop(delta.delta().stopReason()));
                });
            }
            return null;
        });
        return full.toString();
    }

    private MessageCreateParams params(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(request.maxTokens())
                .outputConfig(OutputConfig.builder().effort(effort(request.effort())).build());
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            builder.system(request.systemPrompt());
        }
        for (LlmMessage message : request.messages()) {
            if (message.role() == LlmMessage.Role.USER) {
                builder.addUserMessage(message.content());
            } else {
                builder.addAssistantMessage(message.content());
            }
        }
        if (properties.serverFallback()) {
            // 안전 분류기가 응답을 거절하면 서버가 거절 종류에 맞는 다른 모델로 다시 시도합니다.
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return builder.build();
    }

    private AnthropicClient clientFor(LlmRequest request) {
        return request.retry() ? client : noRetryClient;
    }

    private RequestOptions options(LlmRequest request) {
        return RequestOptions.builder().timeout(request.timeout()).build();
    }

    private static OutputConfig.Effort effort(LlmRequest.Effort effort) {
        return switch (effort) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }

    private static void checkStop(Optional<StopReason> stopReason) {
        if (stopReason.isPresent() && StopReason.REFUSAL.equals(stopReason.get())) {
            throw new LlmException(LlmException.Type.REFUSAL, "모델이 응답을 거절했습니다.");
        }
    }

    private <R> R call(Supplier<R> action) {
        try {
            return action.get();
        } catch (LlmException e) {
            throw e;
        } catch (AnthropicIoException e) {
            if (isTimeout(e)) {
                log.warn("LLM timeout");
                throw new LlmException(LlmException.Type.TIMEOUT, "LLM 응답 시간이 초과됐습니다.", e);
            }
            log.warn("LLM unavailable: {}", e.getClass().getSimpleName());
            throw new LlmException(LlmException.Type.UNAVAILABLE, "LLM 서버에 연결하지 못했습니다.", e);
        } catch (AnthropicServiceException e) {
            log.warn("LLM API error: status={}", e.statusCode());
            throw new LlmException(LlmException.Type.API_ERROR, "LLM API 오류 (" + e.statusCode() + ")", e);
        } catch (RuntimeException e) {
            log.warn("LLM call failed: {}", e.getClass().getSimpleName());
            throw new LlmException(LlmException.Type.INVALID_RESPONSE, "LLM 응답을 처리하지 못했습니다.", e);
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
