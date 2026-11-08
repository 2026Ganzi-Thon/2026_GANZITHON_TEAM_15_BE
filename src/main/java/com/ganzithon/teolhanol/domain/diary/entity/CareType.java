package com.ganzithon.teolhanol.domain.diary.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 케어 방식 4종과 각 방식의 우리 (CARE-01, SHP-02, 기능명세서 5장 우리 목록) */
@Getter
@RequiredArgsConstructor
public enum CareType {

    LISTEN("이야기 들어주기", "들어주는 우리"),
    COMFORT("위로받기", "위로의 우리"),
    ORGANIZE("생각 정리하기", "정리의 우리"),
    SOLVE("해결 방법 찾기", "해결의 우리");

    private final String label;
    private final String penName;
}
