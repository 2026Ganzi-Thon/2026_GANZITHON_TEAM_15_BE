package com.ganzithon.teolhanol.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조언 다시 확인 결과 (SAF-04).
 *
 * @param status     PASS / SUGGEST / BLOCKED
 * @param category   걸린 이유. PASS면 null
 * @param suggestion 바꿀 문장 (SUGGEST일 때만, 조언 최대 길이 이내). 알맞은 문장을 만들지 못했으면 SUGGEST여도 null
 */
public record AdviceCheckResult(
        @Schema(description = "PASS / SUGGEST / BLOCKED", example = "SUGGEST") AdviceCheckStatus status,
        @Schema(description = "BANNED_WORD / BLAME / LECTURE / COMPARE / MOCK / DIAGNOSIS / SOLICIT", example = "LECTURE")
        AdviceCategory category,
        @Schema(example = "지금은 쉬어 가도 괜찮아요. 조금 기운이 나면 한 걸음씩 해 봐요.") String suggestion
) {

    public static AdviceCheckResult pass() {
        return new AdviceCheckResult(AdviceCheckStatus.PASS, null, null);
    }

    public static AdviceCheckResult blocked(AdviceCategory category) {
        return new AdviceCheckResult(AdviceCheckStatus.BLOCKED, category, null);
    }

    public static AdviceCheckResult suggest(AdviceCategory category, String suggestion) {
        return new AdviceCheckResult(AdviceCheckStatus.SUGGEST, category, suggestion);
    }
}
