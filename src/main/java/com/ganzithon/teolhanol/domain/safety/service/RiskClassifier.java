package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.dto.RiskResult;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisSource;
import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.global.llm.PromptLoader;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.Duration;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 위험도 분류 (SAF-01). 케어 대화(#13)와 고민 올리기(#23)에서 공통으로 부릅니다.
 *
 * <pre>{@code RiskResult result = riskClassifier.classify(text, CrisisSource.MESSAGE, user);}</pre>
 *
 * 1차 위기 키워드 매칭, 2차 LLM 판정 중 더 높은 등급을 씁니다.
 * LLM이 실패하거나 시간이 넘으면 키워드 결과를 쓰되 최소 '주의'로 둡니다.
 * 결과는 crisis_logs에 남기고(본문 제외), 위기면 users.crisis_banner_until을 켭니다.
 * 이 메서드는 예외를 던지지 않으므로 호출하는 쪽에서 별도 대체 로직이 필요 없습니다.
 */
@Slf4j
@Service
public class RiskClassifier {

    static final String REASON_KEYWORD = "KEYWORD";
    static final String REASON_LLM_FAILED = "LLM_FAILED";
    static final String REASON_UNSPECIFIED = "UNSPECIFIED";
    private static final int MAX_REASON_LENGTH = 50;

    /** LLM이 돌려주는 JSON. level: safe / caution / crisis */
    record LlmVerdict(String level, String reasonCode) {
    }

    private final SafetyPatternRepository safetyPatternRepository;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final PolicyProperties policy;
    private final CrisisRecorder crisisRecorder;

    public RiskClassifier(SafetyPatternRepository safetyPatternRepository, LlmClient llmClient,
                          PromptLoader promptLoader, PolicyProperties policy, CrisisRecorder crisisRecorder) {
        this.safetyPatternRepository = safetyPatternRepository;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.policy = policy;
        this.crisisRecorder = crisisRecorder;
    }

    public RiskResult classify(String text, CrisisSource source, User user) {
        RiskResult result = judge(text);
        crisisRecorder.record(user.getId(), source, result);
        log.info("위험도 분류 source={} level={} keywordHit={} llmLevel={} reason={}",
                source, result.level(), result.keywordHit(), result.llmLevel(), result.reasonCode());
        return result;
    }

    /** 저장 없이 판정만 합니다. */
    RiskResult judge(String text) {
        boolean keywordHit = matchesCrisisKeyword(text);

        RiskLevel llmLevel = null;
        String llmReason = null;
        try {
            LlmVerdict verdict = llmClient.completeJson(LlmRequest.classification(
                    promptLoader.load("risk-classification"), text,
                    Duration.ofSeconds(policy.safety().llmTimeoutSeconds())), LlmVerdict.class);
            llmLevel = parseLevel(verdict == null ? null : verdict.level());
            llmReason = verdict == null ? null : verdict.reasonCode();
        } catch (LlmException e) {
            log.warn("위험도 LLM 분류 실패, 키워드 결과로 대체합니다 type={}", e.getType());
        } catch (RuntimeException e) {
            log.warn("위험도 LLM 분류 중 예기치 않은 오류, 키워드 결과로 대체합니다 error={}", e.getClass().getSimpleName());
        }

        RiskLevel keywordLevel = keywordHit ? RiskLevel.CRISIS : RiskLevel.SAFE;
        RiskLevel level = RiskLevel.max(keywordLevel, llmLevel == null ? RiskLevel.CAUTION : llmLevel);
        return new RiskResult(level, keywordHit, llmLevel, reasonCode(keywordHit, llmLevel, llmReason));
    }

    private boolean matchesCrisisKeyword(String text) {
        String normalized = normalize(text);
        for (SafetyPattern p : safetyPatternRepository.findAllByCategory(PatternCategory.CRISIS_KEYWORD)) {
            if (normalized.contains(normalize(p.getPattern()))) {
                return true;
            }
        }
        return false;
    }

    /** 띄어쓰기·대소문자 차이로 놓치지 않도록 공백을 제거합니다. */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static RiskLevel parseLevel(String level) {
        if (level == null) {
            return null;
        }
        return switch (level.strip().toLowerCase(Locale.ROOT)) {
            case "safe" -> RiskLevel.SAFE;
            case "caution" -> RiskLevel.CAUTION;
            case "crisis" -> RiskLevel.CRISIS;
            default -> null;
        };
    }

    private static String reasonCode(boolean keywordHit, RiskLevel llmLevel, String llmReason) {
        if (llmLevel == null) {
            return keywordHit ? REASON_KEYWORD : REASON_LLM_FAILED;
        }
        String cleaned = llmReason == null ? "" : llmReason.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z_]", "");
        if (cleaned.length() > MAX_REASON_LENGTH) {
            cleaned = cleaned.substring(0, MAX_REASON_LENGTH);
        }
        if (cleaned.isEmpty()) {
            cleaned = REASON_UNSPECIFIED;
        }
        // LLM은 안전이라 했지만 키워드가 걸린 경우, 사유는 키워드로 남긴다
        if (keywordHit && (llmLevel == RiskLevel.SAFE || "NONE".equals(cleaned))) {
            return REASON_KEYWORD;
        }
        return cleaned;
    }
}
