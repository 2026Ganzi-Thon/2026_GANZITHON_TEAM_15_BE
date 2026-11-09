package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.dto.CareStartRequest;
import com.ganzithon.teolhanol.domain.care.dto.CareStartResponse;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmMessage;
import com.ganzithon.teolhanol.global.llm.LlmProperties;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.global.llm.PromptLoader;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 케어 방식 고르기와 AI 첫 메시지 (CARE-01). 이어지는 대화는 CARE-02에서 다룹니다. */
@Slf4j
@Service
public class CareService {

    private final CareStarter careStarter;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final LlmProperties llmProperties;
    private final AiResponseGuard guard;

    public CareService(CareStarter careStarter, LlmClient llmClient, PromptLoader promptLoader,
                       LlmProperties llmProperties, AiResponseGuard guard) {
        this.careStarter = careStarter;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
        this.guard = guard;
    }

    /**
     * 선택한 방식으로 케어를 시작하고 AI 첫 메시지를 저장해 돌려줍니다.
     * 이미 시작했거나 끝난 기록은 409, 남의 기록은 404입니다.
     * LLM이 실패하면 방식별 고정 문장으로 대신해 대화가 끊기지 않게 합니다.
     */
    public CareStartResponse start(Long userId, Long diaryId, CareStartRequest request) {
        CareType careType = request.careType();
        Emotion emotion = careStarter.begin(userId, diaryId, careType);

        String message = generateOpener(careType, emotion);
        careStarter.saveAiMessage(diaryId, message);
        return new CareStartResponse(diaryId, careType, message);
    }

    private String generateOpener(CareType careType, Emotion emotion) {
        try {
            String system = promptLoader.withSafetyGuide("care-" + careType.name().toLowerCase())
                    + "\n\n" + promptLoader.load("care-first-turn");
            String user = "오늘 고른 감정: %s\n케어 방식: %s".formatted(emotion.getLabel(), careType.getLabel());
            String text = llmClient.complete(LlmRequest.chat(system, List.of(LlmMessage.user(user)),
                    Duration.ofSeconds(llmProperties.chatTimeoutSeconds())));
            if (text != null && !text.isBlank() && !guard.violates(text)) {
                return text.strip();
            }
        } catch (LlmException e) {
            log.warn("케어 첫 메시지 LLM 실패, 고정 문장으로 대체합니다 type={}", e.getType());
        } catch (RuntimeException e) {
            log.warn("케어 첫 메시지 생성 중 예기치 않은 오류, 고정 문장으로 대체합니다 error={}", e.getClass().getSimpleName());
        }
        return CareOpeners.fallback(careType);
    }
}
