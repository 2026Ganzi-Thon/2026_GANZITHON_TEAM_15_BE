package com.ganzithon.teolhanol.domain.care.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 케어 대화 메시지 작성 주체 */
@Getter
@RequiredArgsConstructor
public enum MessageRole {

    USER("사용자"),
    AI("AI");

    private final String label;
}
