package com.ganzithon.teolhanol.domain.report.dto;

/** 차단 대상은 user_id가 아니라 양 또는 조언 id로 지정합니다(익명성 유지). */
public record BlockCreateRequest(BlockTargetType targetType, Long targetId) {
}
