package com.ganzithon.teolhanol.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 신고 처리 상태 */
@Getter
@RequiredArgsConstructor
public enum ReportStatus {

    PENDING("대기"),
    RESOLVED("처리됨"),
    REJECTED("반려");

    private final String label;
}
