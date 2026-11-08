package com.ganzithon.teolhanol.global.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    public LlmClient llmClient(LlmProperties properties) {
        if (!properties.enabled()) {
            log.warn("ANTHROPIC_API_KEY가 없어 LLM 기능이 꺼진 상태로 시작합니다. 분류는 키워드 판정으로 대체됩니다.");
            return new DisabledLlmClient();
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .maxRetries(properties.maxRetries())
                .timeout(Duration.ofSeconds(properties.chatTimeoutSeconds()));
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        return new ClaudeLlmClient(builder.build(), properties);
    }
}
