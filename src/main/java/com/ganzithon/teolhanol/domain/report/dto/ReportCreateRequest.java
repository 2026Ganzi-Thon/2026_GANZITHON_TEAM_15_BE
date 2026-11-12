package com.ganzithon.teolhanol.domain.report.dto;

import com.ganzithon.teolhanol.domain.report.entity.ReportReason;
import com.ganzithon.teolhanol.domain.report.entity.ReportTargetType;

/**
 * 신고 요청 (RPT-01). 대상은 user_id가 아니라 양 또는 조언 id로 지정합니다(익명성 유지).
 *
 * @param blockUser 신고 완료 화면의 '이 목동 차단하기'. 비우면 false
 */
public record ReportCreateRequest(ReportTargetType targetType, Long targetId, ReportReason reason, Boolean blockUser) {

    public boolean blockUserOrFalse() {
        return Boolean.TRUE.equals(blockUser);
    }
}
