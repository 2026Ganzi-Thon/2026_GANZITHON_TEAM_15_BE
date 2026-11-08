package com.ganzithon.teolhanol.domain.safety.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 위험도 분류 대상 (crisis_logs.source) */
@Getter
@RequiredArgsConstructor
public enum CrisisSource {

    MESSAGE("케어 대화"),
    WORRY("고민");

    private final String label;
}
