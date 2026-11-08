package com.ganzithon.teolhanol.domain.diary.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 오늘의 마음(diaries)의 케어 진행 상태 */
@Getter
@RequiredArgsConstructor
public enum CareStatus {

    NONE("케어 전"),
    IN_PROGRESS("케어 중"),
    COMPLETED("케어 완료");

    private final String label;
}
