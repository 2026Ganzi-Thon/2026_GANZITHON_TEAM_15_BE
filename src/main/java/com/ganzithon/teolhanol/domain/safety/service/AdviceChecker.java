package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.dto.AdviceCategory;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckResult;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.global.llm.PromptLoader;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 조언 다시 확인 (SAF-04, 와이어프레임 FARM-04b). 조언 보내기(#27)가 저장 전에 부릅니다.
 *
 * <pre>{@code AdviceCheckResult result = adviceChecker.check(content);}</pre>
 *
 * <ol>
 *   <li>1차 금칙어(욕설·혐오·성적 표현, 자해 조장) → BLOCKED. LLM은 부르지 않습니다.</li>
 *   <li>2차 LLM 어조 분류(비난·다그침·비교·조롱·섣부른 진단·종교/상품 권유) → SUGGEST + 바꿀 문장</li>
 * </ol>
 *
 * LLM이 실패하거나 시간이 넘거나 형식이 틀리면 금칙어 판정만으로 PASS입니다. 예외를 던지지 않습니다.
 * 바꿀 문장이 조언 최대 길이를 넘거나 금칙어가 들어 있으면 문장 없이 분류만 돌려줍니다(SUGGEST 유지).
 * 개인정보 패턴(SAF-05)은 PiiDetector(#34)가 따로 검사합니다. 조언 본문과 바꿀 문장은 로그에 남기지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdviceChecker {

    static final String PROMPT = "advice-check";
    private static final String QUOTES = "\"'“”‘’「」『』";

    /** LLM이 돌려주는 JSON. category: NONE / BLAME / LECTURE / COMPARE / MOCK / DIAGNOSIS / SOLICIT */
    record LlmVerdict(String category, String suggestion) {
    }

    private final BannedWordFilter bannedWordFilter;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final PolicyProperties policy;

    public AdviceCheckResult check(String content) {
        if (content == null || content.isBlank()) {
            return AdviceCheckResult.pass();
        }
        if (bannedWordFilter.contains(content)) {
            log.info("조언 확인 status=BLOCKED category={}", AdviceCategory.BANNED_WORD);
            return AdviceCheckResult.blocked(AdviceCategory.BANNED_WORD);
        }
        AdviceCheckResult result = classifyTone(content);
        log.info("조언 확인 status={} category={} hasSuggestion={}",
                result.status(), result.category(), result.suggestion() != null);
        return result;
    }

    private AdviceCheckResult classifyTone(String content) {
        LlmVerdict verdict;
        try {
            verdict = llmClient.completeJson(LlmRequest.classification(systemPrompt(), content,
                    Duration.ofSeconds(policy.safety().llmTimeoutSeconds())), LlmVerdict.class);
        } catch (LlmException e) {
            log.warn("조언 어조 분류 실패, 금칙어 판정으로 대체합니다 type={}", e.getType());
            return AdviceCheckResult.pass();
        } catch (RuntimeException e) {
            log.warn("조언 어조 분류 중 예기치 않은 오류, 금칙어 판정으로 대체합니다 error={}", e.getClass().getSimpleName());
            return AdviceCheckResult.pass();
        }
        if (verdict == null || verdict.category() == null) {
            log.warn("조언 어조 분류 응답이 비어 있어 금칙어 판정으로 대체합니다");
            return AdviceCheckResult.pass();
        }
        if ("NONE".equalsIgnoreCase(verdict.category().strip())) {
            return AdviceCheckResult.pass();
        }
        Optional<AdviceCategory> category = AdviceCategory.ofTone(verdict.category());
        if (category.isEmpty()) {
            log.warn("조언 어조 분류 응답 형식이 달라 금칙어 판정으로 대체합니다");
            return AdviceCheckResult.pass();
        }
        return AdviceCheckResult.suggest(category.get(), usableSuggestion(verdict.suggestion(), content));
    }

    /** 그대로 보여 줄 수 있는 바꿀 문장. 비었거나, 길거나, 금칙어가 있거나, 원문과 같으면 null */
    private String usableSuggestion(String raw, String content) {
        if (raw == null) {
            return null;
        }
        String suggestion = stripQuotes(raw.strip());
        if (suggestion.isEmpty() || suggestion.equals(content.strip())) {
            return null;
        }
        if (suggestion.codePointCount(0, suggestion.length()) > policy.wool().maxLength()) {
            log.info("바꿀 문장이 조언 최대 길이를 넘어 분류만 돌려줍니다");
            return null;
        }
        if (bannedWordFilter.contains(suggestion)) {
            log.warn("바꿀 문장에 금칙어가 있어 분류만 돌려줍니다");
            return null;
        }
        return suggestion;
    }

    /** 모델이 문장 전체를 따옴표로 감싸 돌려준 경우만 벗깁니다. */
    private static String stripQuotes(String s) {
        String result = s;
        while (result.length() >= 2 && QUOTES.indexOf(result.charAt(0)) >= 0
                && QUOTES.indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(1, result.length() - 1).strip();
        }
        return result;
    }

    private String systemPrompt() {
        return promptLoader.load(PROMPT).replace("{maxLength}", String.valueOf(policy.wool().maxLength()));
    }
}
