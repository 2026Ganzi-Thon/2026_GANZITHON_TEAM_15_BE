package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.dto.CareEvent;
import com.ganzithon.teolhanol.domain.care.dto.CareMessageRequest;
import com.ganzithon.teolhanol.domain.care.service.CareChatStore.Turn;
import com.ganzithon.teolhanol.domain.safety.dto.RiskResult;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisSource;
import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import com.ganzithon.teolhanol.domain.safety.service.RiskClassifier;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmMessage;
import com.ganzithon.teolhanol.global.llm.LlmProperties;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.global.llm.PromptLoader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 케어 대화 (CARE-02, SAF-01, SAF-02).
 *
 * <p>사용자 메시지를 저장하고 나면 SSE로 이벤트를 내려줍니다: progress → (crisis | delta… [notice/reset] [replace]) → done.
 * 검증 오류(404/409/400)는 SSE를 열기 전에 일반 JSON 오류로 응답합니다. 본문은 로그에 남기지 않습니다.
 */
@Slf4j
@Service
public class CareChatService {

    static final String CRISIS_MESSAGE = "잠깐 멈출게요. 지금은 전화로 이야기할 수 있는 곳을 알려드리고 싶어요.";
    static final String BREATHING_NOTICE = "잠시 숨을 고르고 있어요.";
    static final String AI_UNAVAILABLE_MESSAGE =
            "지금은 이어서 이야기하기 어려워요. 하신 이야기는 잘 저장해 두었어요. 잠시 뒤에 다시 이어서 말해 줘도 돼요.";
    static final String ERROR_CODE_AI_UNAVAILABLE = "CARE_AI_UNAVAILABLE";
    static final int MAX_ATTEMPTS = 3;
    private static final long SSE_TIMEOUT_MILLIS = 120_000L;

    private static final String RESUME_INSTRUCTION = """
            사용자가 조금 전 상담 안내를 보고도 대화를 계속하기로 했습니다. 이번 응답은 반드시 다음 문장으로 시작합니다: \
            "말해 줘서 고마워요. 천천히 이어서 말해 줘도 돼요. 위에 있는 '상담'은 언제든 누를 수 있어요." \
            그다음 사용자의 말에 이어서 답합니다.""";
    private static final String CLOSING_INSTRUCTION = """
            이번이 오늘 나눌 수 있는 마지막 대화입니다. 사용자의 말에 답한 뒤, 오늘 이야기를 자연스럽게 마무리하자고 \
            부드럽게 제안합니다. 새로운 질문은 하지 않습니다.""";

    private final CareChatStore store;
    private final RiskClassifier riskClassifier;
    private final AiResponseGuard guard;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final LlmProperties llmProperties;
    private final UserRepository userRepository;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public CareChatService(CareChatStore store, RiskClassifier riskClassifier, AiResponseGuard guard,
                           LlmClient llmClient, PromptLoader promptLoader, LlmProperties llmProperties,
                           UserRepository userRepository, ObjectMapper mapper,
                           @Qualifier("careStreamExecutor") ExecutorService executor) {
        this.store = store;
        this.riskClassifier = riskClassifier;
        this.guard = guard;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.executor = executor;
    }

    public SseEmitter send(Long userId, Long diaryId, CareMessageRequest request) {
        String content = request.content().strip();
        Turn turn = store.begin(userId, diaryId, content);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CareEvents events = new CareEvents(emitter, mapper);
        executor.execute(() -> run(userId, turn, content, events));
        return emitter;
    }

    private void run(Long userId, Turn turn, String content, CareEvents events) {
        try {
            events.progress(new CareEvent.Progress(turn.userMessageCount(), turn.ready(),
                    turn.maxUserMessages(), turn.limitReached()));

            RiskResult risk = riskClassifier.classify(content, CrisisSource.MESSAGE, userRepository.getReferenceById(userId));
            store.applyRisk(turn.diaryId(), risk.level());
            if (risk.isCrisis()) {
                store.saveAiMessage(turn.diaryId(), CRISIS_MESSAGE);
                events.crisis(CRISIS_MESSAGE);
                events.done();
                return;
            }

            String reply = streamReply(turn, events);
            if (reply == null) {
                events.error(ERROR_CODE_AI_UNAVAILABLE, AI_UNAVAILABLE_MESSAGE);
                return;
            }
            String guarded = guard.guard(reply);
            if (!guarded.equals(reply)) {
                events.replace(guarded);
            }
            store.saveAiMessage(turn.diaryId(), guarded);
            events.done();
        } catch (RuntimeException e) {
            log.warn("케어 대화 처리 중 오류 diaryId={} error={}", turn.diaryId(), e.getClass().getSimpleName());
            events.error(ERROR_CODE_AI_UNAVAILABLE, AI_UNAVAILABLE_MESSAGE);
        } finally {
            events.complete();
        }
    }

    /** 응답이 실패하면 '잠시 숨을 고르고 있어요' 안내 후 다시 시도하고, 3번 실패하면 null입니다. */
    private String streamReply(Turn turn, CareEvents events) {
        LlmRequest request = LlmRequest.chat(systemPrompt(turn), contextMessages(turn),
                Duration.ofSeconds(llmProperties.chatTimeoutSeconds()));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean[] emitted = {false};
            try {
                String full = llmClient.stream(request, delta -> {
                    emitted[0] = true;
                    events.delta(delta);
                });
                if (full != null && !full.isBlank()) {
                    return full.strip();
                }
                throw new LlmException(LlmException.Type.INVALID_RESPONSE, "빈 응답");
            } catch (LlmException e) {
                log.warn("케어 응답 실패 attempt={} type={}", attempt, e.getType());
                if (emitted[0]) {
                    events.reset();
                }
                if (attempt < MAX_ATTEMPTS) {
                    events.notice(BREATHING_NOTICE);
                }
            }
        }
        return null;
    }

    private String systemPrompt(Turn turn) {
        StringBuilder system = new StringBuilder(promptLoader.withSafetyGuide("care-" + turn.careType().name().toLowerCase()))
                .append("\n\n").append(promptLoader.load("care-chat"))
                .append("\n\n오늘 사용자가 고른 감정: ").append(turn.emotion().getLabel());
        if (turn.afterCrisis()) {
            system.append("\n\n").append(RESUME_INSTRUCTION);
        }
        if (turn.limitReached()) {
            system.append("\n\n").append(CLOSING_INSTRUCTION);
        }
        return system.toString();
    }

    /** 지금까지의 대화. 대화가 AI 첫 메시지로 시작하면 모델이 받아들이도록 앞에 짧은 사용자 인사를 붙입니다. */
    private List<LlmMessage> contextMessages(Turn turn) {
        List<LlmMessage> history = store.history(turn.diaryId());
        if (!history.isEmpty() && history.get(0).role() == LlmMessage.Role.ASSISTANT) {
            List<LlmMessage> withGreeting = new ArrayList<>();
            withGreeting.add(LlmMessage.user("안녕하세요."));
            withGreeting.addAll(history);
            return withGreeting;
        }
        return history;
    }
}
