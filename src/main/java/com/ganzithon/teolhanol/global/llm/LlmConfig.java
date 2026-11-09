package com.ganzithon.teolhanol.global.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
@EnableConfigurationProperties({LlmProperties.class, UpstageProperties.class})
public class LlmConfig {

    public enum Provider {
        UPSTAGE, CLAUDE, NONE
    }

    /**
     * 사용할 공급자를 정합니다. provider가 auto(기본)이면 Upstage 키가 있을 때 Upstage, 없고 Anthropic 키가 있으면 Claude,
     * 둘 다 없으면 NONE(LLM 꺼짐). upstage / claude를 직접 지정했는데 키가 없으면 NONE입니다.
     */
    static Provider chooseProvider(String provider, boolean upstageKey, boolean claudeKey) {
        String wanted = provider == null || provider.isBlank() ? "auto" : provider.strip().toLowerCase(Locale.ROOT);
        return switch (wanted) {
            case "upstage" -> upstageKey ? Provider.UPSTAGE : Provider.NONE;
            case "claude" -> claudeKey ? Provider.CLAUDE : Provider.NONE;
            case "auto" -> upstageKey ? Provider.UPSTAGE : claudeKey ? Provider.CLAUDE : Provider.NONE;
            default -> throw new IllegalArgumentException("app.llm.provider는 auto / upstage / claude 중 하나여야 합니다: " + provider);
        };
    }

    @Bean
    public LlmClient llmClient(LlmProperties properties, UpstageProperties upstage, ObjectMapper mapper,
                               @Value("${app.llm.provider:auto}") String provider) {
        Provider chosen = chooseProvider(provider, upstage.enabled(), properties.enabled());
        switch (chosen) {
            case UPSTAGE -> {
                log.info("LLM 공급자: Upstage ({})", upstage.model());
                return new UpstageLlmClient(upstage, properties, mapper);
            }
            case CLAUDE -> {
                log.info("LLM 공급자: Claude ({})", properties.model());
                AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .maxRetries(properties.maxRetries())
                        .timeout(Duration.ofSeconds(properties.chatTimeoutSeconds()));
                if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
                    builder.baseUrl(properties.baseUrl());
                }
                return new ClaudeLlmClient(builder.build(), properties);
            }
            default -> {
                log.warn("UPSTAGE_API_KEY / ANTHROPIC_API_KEY가 없어 LLM 기능이 꺼진 상태로 시작합니다. 분류는 키워드 판정으로 대체됩니다.");
                return new DisabledLlmClient();
            }
        }
    }
}
