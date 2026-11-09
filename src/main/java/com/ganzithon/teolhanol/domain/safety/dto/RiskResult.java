package com.ganzithon.teolhanol.domain.safety.dto;

import com.ganzithon.teolhanol.global.enums.RiskLevel;

/**
 * 위험도 분류 결과 (SAF-01).
 *
 * @param level       최종 등급. 키워드와 LLM 중 더 높은 등급
 * @param keywordHit  위기 키워드가 걸렸는지
 * @param llmLevel    LLM 판정. 호출이 실패하면 null
 * @param reasonCode  사유 코드 (본문은 담지 않음)
 */
public record RiskResult(RiskLevel level, boolean keywordHit, RiskLevel llmLevel, String reasonCode) {

    public boolean isCrisis() {
        return level.isCrisis();
    }

    public boolean llmFailed() {
        return llmLevel == null;
    }
}
