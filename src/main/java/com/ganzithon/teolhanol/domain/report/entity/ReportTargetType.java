package com.ganzithon.teolhanol.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 신고 대상 종류 */
@Getter
@RequiredArgsConstructor
public enum ReportTargetType {

    WORRY_SHEEP("고민 양"),
    WOOL("조언");

    private final String label;
}
