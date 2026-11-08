package com.ganzithon.teolhanol.global.llm;

import java.time.Duration;
import java.util.List;

/**
 * LLM 호출 요청.
 *
 * @param systemPrompt 시스템 프롬프트 ({@link PromptLoader}로 불러온 값)
 * @param messages     대화 내용 (마지막은 사용자 메시지)
 * @param maxTokens    최대 출력 토큰 (생각 토큰 포함)
 * @param effort       생각 깊이. 분류처럼 빨라야 하는 호출은 LOW
 * @param timeout      이 호출의 전체 타임아웃
 * @param retry        실패 시 재시도할지 여부
 */
public record LlmRequest(
        String systemPrompt,
        List<LlmMessage> messages,
        long maxTokens,
        Effort effort,
        Duration timeout,
        boolean retry
) {

    public enum Effort {
        LOW, MEDIUM, HIGH
    }

    public LlmRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages가 비어 있습니다.");
        }
        messages = List.copyOf(messages);
    }

    /** 위험도 분류, 조언 필터처럼 빠르게 판정해야 하는 호출 (재시도 없음) */
    public static LlmRequest classification(String systemPrompt, String content, Duration timeout) {
        return new LlmRequest(systemPrompt, List.of(LlmMessage.user(content)), 2048, Effort.LOW, timeout, false);
    }

    /** 케어 대화·요약처럼 사용자가 기다리는 생성 호출 */
    public static LlmRequest chat(String systemPrompt, List<LlmMessage> messages, Duration timeout) {
        return new LlmRequest(systemPrompt, messages, 8192, Effort.MEDIUM, timeout, true);
    }
}
