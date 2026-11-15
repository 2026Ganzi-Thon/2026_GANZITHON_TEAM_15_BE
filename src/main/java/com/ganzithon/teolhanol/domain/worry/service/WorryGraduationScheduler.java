package com.ganzithon.teolhanol.domain.worry.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 기간 경과 자동 졸업 스케줄 (FRM-06). 테스트 프로필에서는 app.scheduling.enabled=false 로 꺼 둡니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class WorryGraduationScheduler {

    private final WorryGraduationService graduationService;

    @Scheduled(cron = "${app.policy.worry.auto-graduate-cron:0 0 * * * *}", zone = "Asia/Seoul")
    void graduateExpired() {
        graduationService.graduateExpired(LocalDateTime.now());
    }
}
