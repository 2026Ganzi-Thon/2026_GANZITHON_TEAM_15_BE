package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.dto.CareCompleteResponse;
import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmMessage;
import com.ganzithon.teolhanol.global.llm.LlmProperties;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.global.llm.PromptLoader;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 털 한 올 쌓고 마무리 (CARE-03, SHP-01). 대화를 바탕으로 케어 방식별 '오늘 받은 말' 카드를 만들고 털을 쌓습니다.
 * LLM 호출은 트랜잭션 밖에서 하고, 저장·완료 표시·털 증가는 {@link CareCompleter}가 한 트랜잭션으로 처리합니다.
 * LLM이 실패해도 방식별 고정 카드로 마무리해 털을 잃지 않게 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareCompleteService {

    private static final Map<CareType, String> FALLBACK_CARDS = new EnumMap<>(CareType.class);

    static {
        FALLBACK_CARDS.put(CareType.LISTEN, "오늘 마음에 있던 이야기를 천천히 꺼내 놓았어요.");
        FALLBACK_CARDS.put(CareType.COMFORT, "오늘도 여기까지 오느라 정말 애썼어요. 그 마음, 충분히 그럴 만해요.");
        FALLBACK_CARDS.put(CareType.ORGANIZE,
                "걸리는 것: 오늘 마음에 남아 있던 일\n느끼는 것: 그 일로 마음이 쓰이고 있다는 것\n바꿀 수 있는 것: 지금 할 수 있는 작은 한 가지부터");
        FALLBACK_CARDS.put(CareType.SOLVE, "1. 잠시 숨을 고르며 따뜻한 물 한 잔 마시기");
    }

    private final CareCompleter completer;
    private final CareMessageRepository careMessageRepository;
    private final AiResponseGuard guard;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final LlmProperties llmProperties;

    /**
     * 본인 기록만, 사용자 메시지가 최소 횟수 이상일 때만 마무리합니다(아니면 409).
     * 이미 마무리한 기록은 저장된 카드를 added=0으로 돌려줍니다.
     */
    public CareCompleteResponse complete(Long userId, Long diaryId) {
        CareCompleter.Check check = completer.check(userId, diaryId);
        if (check.isDone()) {
            return check.result();
        }
        String built = buildCard(diaryId, check.careType(), check.emotionLabel());
        // 금지 패턴이 걸린 카드는 대화용 안전 문장 대신 방식별 고정 카드로 바꿉니다
        String card = guard.guard(guard.violates(built) ? FALLBACK_CARDS.get(check.careType()) : built);
        return completer.complete(userId, diaryId, card);
    }

    private String buildCard(Long diaryId, CareType careType, String emotionLabel) {
        try {
            String system = promptLoader.withSafetyGuide("care-summary") + "\n\n"
                    + promptLoader.load("care-summary-" + careType.name().toLowerCase())
                    + "\n\n오늘 사용자가 고른 감정: " + emotionLabel;
            String text = llmClient.complete(LlmRequest.chat(system,
                    List.of(LlmMessage.user(transcript(diaryId))),
                    Duration.ofSeconds(llmProperties.chatTimeoutSeconds())));
            if (text != null && !text.isBlank()) {
                return tidy(text);
            }
        } catch (LlmException e) {
            log.warn("마무리 카드 LLM 실패, 고정 카드로 대신합니다 type={}", e.getType());
        } catch (RuntimeException e) {
            log.warn("마무리 카드 생성 중 예기치 않은 오류, 고정 카드로 대신합니다 error={}", e.getClass().getSimpleName());
        }
        return FALLBACK_CARDS.get(careType);
    }

    /** 앞뒤 공백과 줄 끝 공백을 정리합니다(모델이 줄 끝에 공백을 두 칸 붙이는 경우가 있음). */
    private static String tidy(String text) {
        return text.strip().lines().map(String::stripTrailing).collect(java.util.stream.Collectors.joining("\n"));
    }

    private String transcript(Long diaryId) {
        StringBuilder sb = new StringBuilder("다음은 오늘 나눈 대화입니다.\n\n");
        for (CareMessage m : careMessageRepository.findByDiaryIdOrderByIdAsc(diaryId)) {
            sb.append(m.getRole() == MessageRole.USER ? "[사용자] " : "[털 한 올] ").append(m.getContent()).append('\n');
        }
        return sb.append("\n위 대화를 바탕으로 카드를 만들어 주세요.").toString();
    }
}
