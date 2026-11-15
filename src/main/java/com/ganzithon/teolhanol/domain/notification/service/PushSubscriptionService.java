package com.ganzithon.teolhanol.domain.notification.service;

import com.ganzithon.teolhanol.domain.notification.config.PushProperties;
import com.ganzithon.teolhanol.domain.notification.dto.PushSubscribeRequest;
import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import com.ganzithon.teolhanol.domain.notification.push.PushEndpoints;
import com.ganzithon.teolhanol.domain.notification.repository.PushSubscriptionRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹 푸시 구독 저장·해지 (WOL-02). 같은 endpoint(같은 브라우저)는 한 행만 두고, 다시 구독하면 지금 로그인한 사용자로
 * 소유자를 바꿉니다(멱등). endpoint·키는 로그에 남기지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final PushProperties properties;

    /** @param created 새로 저장했으면 true, 이미 있던 구독을 이 사용자로 맞췄으면 false */
    public record Result(boolean created) {
    }

    @Transactional
    public Result subscribe(Long userId, PushSubscribeRequest request) {
        String endpoint = request.endpoint().strip();
        if (!PushEndpoints.isAllowed(endpoint, properties.allowedHosts())) {
            log.info("허용하지 않는 푸시 endpoint userId={} host={}", userId, PushEndpoints.host(endpoint));
            throw new BusinessException(ErrorCode.PUSH_ENDPOINT_NOT_ALLOWED);
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String p256dh = toBase64Url(request.keys().p256dh());
        String auth = toBase64Url(request.keys().auth());

        Optional<PushSubscription> existing = repository.findByEndpoint(endpoint);
        if (existing.isPresent()) {
            existing.get().reassign(user, p256dh, auth);
            return new Result(false);
        }
        try {
            repository.saveAndFlush(new PushSubscription(user, endpoint, p256dh, auth));
        } catch (DataIntegrityViolationException e) {
            // 같은 브라우저가 동시에 두 번 구독함(endpoint UNIQUE). 프론트가 다시 보내면 200으로 맞춰집니다.
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return new Result(true);
    }

    /** 본인 구독이면 지우고 true. 없거나 다른 사람 구독이면 아무것도 하지 않고 false (멱등) */
    @Transactional
    public boolean unsubscribe(Long userId, String endpoint) {
        return repository.deleteByEndpointAndUserId(endpoint.strip(), userId) > 0;
    }

    /** 표준 base64로 온 값도 라이브러리가 읽는 base64url(패딩 없음)로 맞춥니다. */
    static String toBase64Url(String value) {
        return value.strip().replace('+', '-').replace('/', '_').replace("=", "");
    }
}
