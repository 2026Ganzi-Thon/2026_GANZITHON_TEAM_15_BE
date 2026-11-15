package com.ganzithon.teolhanol.domain.report.repository;

import com.ganzithon.teolhanol.domain.report.entity.Report;
import com.ganzithon.teolhanol.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    /** 대상에 쌓인 신고 수. (신고자, 대상) 유니크라 서로 다른 신고자 수와 같습니다. */
    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);
}
