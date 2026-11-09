package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.dto.RiskResult;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisLog;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisSource;
import com.ganzithon.teolhanol.domain.safety.repository.CrisisLogRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 판정 로그 저장과 위기 배너 켜기. LLM 호출 뒤에 짧은 트랜잭션으로 처리합니다. */
@Component
@RequiredArgsConstructor
class CrisisRecorder {

    private final CrisisLogRepository crisisLogRepository;
    private final UserRepository userRepository;
    private final PolicyProperties policy;

    @Transactional
    public void record(Long userId, CrisisSource source, RiskResult result) {
        User user = userRepository.getReferenceById(userId);
        crisisLogRepository.save(new CrisisLog(user, source, result.keywordHit(), result.llmLevel(), result.reasonCode()));
        if (result.isCrisis()) {
            userRepository.findById(userId).ifPresent(u ->
                    u.activateCrisisBanner(LocalDateTime.now().plusHours(policy.safety().crisisBannerHours())));
        }
    }
}
