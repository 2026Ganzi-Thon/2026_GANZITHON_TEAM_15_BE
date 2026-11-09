package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패 횟수와 잠금 저장. 로그인 실패 응답(예외)과 별개로 커밋되어야 하므로
 * {@link AuthService}와 다른 빈의 트랜잭션으로 둡니다.
 */
@Component
@RequiredArgsConstructor
class LoginAttempts {

    private final UserRepository userRepository;
    private final PolicyProperties policy;

    /** 실패를 기록하고, 이번 실패로 잠금이 걸렸다면 잠금 해제 시각을 돌려줍니다. */
    @Transactional
    public LocalDateTime recordFailure(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        return user.recordLoginFailure(now, policy.auth().maxLoginFailures(), policy.auth().lockMinutes());
    }

    @Transactional
    public void clear(Long userId) {
        userRepository.findById(userId).ifPresent(User::clearLoginFailures);
    }
}
