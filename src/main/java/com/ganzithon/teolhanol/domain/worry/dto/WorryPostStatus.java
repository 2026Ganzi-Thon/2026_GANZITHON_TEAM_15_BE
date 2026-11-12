package com.ganzithon.teolhanol.domain.worry.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 고민 올리기 결과 (FRM-04) */
@Getter
@RequiredArgsConstructor
public enum WorryPostStatus {

    CREATED("새 양이 태어남"),
    CRISIS("양을 만들지 않고 상담 연결 안내"),
    PII_REJECTED("개인정보가 있어 저장하지 않음");

    private final String label;
}
