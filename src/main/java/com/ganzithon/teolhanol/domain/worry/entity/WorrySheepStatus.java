package com.ganzithon.teolhanol.domain.worry.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 고민 양 상태 */
@Getter
@RequiredArgsConstructor
public enum WorrySheepStatus {

    ACTIVE("목장에 있음"),
    GRADUATED("졸업"),
    HIDDEN("숨김");

    private final String label;
}
