package com.ganzithon.teolhanol.domain.care.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 케어 마무리 평가 (CARE-03, 선택 사항) */
@Getter
@RequiredArgsConstructor
public enum CareRating {

    HELPFUL("도움이 됐어요"),
    SO_SO("그저 그랬어요"),
    NOT_HELPFUL("별로였어요");

    private final String label;
}
