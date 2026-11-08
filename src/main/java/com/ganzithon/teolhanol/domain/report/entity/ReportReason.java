package com.ganzithon.teolhanol.domain.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 신고 사유 (RPT-01) */
@Getter
@RequiredArgsConstructor
public enum ReportReason {

    ABUSIVE("악성"),
    PERSONAL_INFO("개인정보"),
    SPAM("스팸"),
    LOOKS_DANGEROUS("위험해 보여요"),
    ETC("기타");

    private final String label;
}
