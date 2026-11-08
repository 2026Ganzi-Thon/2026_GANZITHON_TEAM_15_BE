package com.ganzithon.teolhanol.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** AI 위험도 분류 등급 (SAF-01). 키워드 판정과 LLM 판정 중 더 높은 등급을 사용합니다. */
@Getter
@RequiredArgsConstructor
public enum RiskLevel {

    SAFE("안전", 0),
    CAUTION("주의", 1),
    CRISIS("위기", 2);

    private final String label;
    private final int severity;

    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.severity >= b.severity ? a : b;
    }

    public boolean isCrisis() {
        return this == CRISIS;
    }
}
