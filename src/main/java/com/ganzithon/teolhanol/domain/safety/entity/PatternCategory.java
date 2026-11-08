package com.ganzithon.teolhanol.domain.safety.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 안전 패턴 분류 (SAF-01, SAF-03~05) */
@Getter
@RequiredArgsConstructor
public enum PatternCategory {

    CRISIS_KEYWORD("위기 키워드"),
    BANNED_WORD("금칙어"),
    AI_OUTPUT("AI 응답 금지 패턴"),
    PII("개인정보 패턴");

    private final String label;
}
