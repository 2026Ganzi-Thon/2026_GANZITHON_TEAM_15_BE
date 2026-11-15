package com.ganzithon.teolhanol.domain.notification.service;

import com.ganzithon.teolhanol.domain.notification.config.NotificationConfig;
import com.ganzithon.teolhanol.domain.notification.dto.NotificationPayload;
import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import com.ganzithon.teolhanol.domain.notification.push.PushResult;
import com.ganzithon.teolhanol.domain.notification.push.PushSender;
import com.ganzithon.teolhanol.domain.notification.push.PushTarget;
import com.ganzithon.teolhanol.domain.notification.repository.PushSubscriptionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 한 사용자에게 알림을 보냅니다 (WOL-02). 열린 앱 탭에는 SSE 이벤트로, 웹 푸시 구독이 있으면 웹 푸시로도 보냅니다
 * (앱이 열려 있을 때의 중복 표시는 서비스워커가 payload의 type으로 판단). 모든 발송은 알림 전용 스레드에서 하므로
 * 요청 스레드를 막지 않고, 실패해도 원래 요청(조언 저장 등)에는 영향이 없습니다. 404/410을 받은 구독은 지웁니다.
 */
@Slf4j
@Service
public class NotificationDispatcher {

    private final NotificationStreams streams;
    private final PushSender pushSender;
    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper mapper;
    private final Executor executor;

    public NotificationDispatcher(NotificationStreams streams, PushSender pushSender,
                                  PushSubscriptionRepository subscriptionRepository, ObjectMapper mapper,
                                  @Qualifier(NotificationConfig.EXECUTOR) Executor executor) {
        this.streams = streams;
        this.pushSender = pushSender;
        this.subscriptionRepository = subscriptionRepository;
        this.mapper = mapper;
        this.executor = executor;
    }

    public void notify(Long userId, NotificationPayload payload) {
        try {
            executor.execute(() -> deliver(userId, payload));
        } catch (RejectedExecutionException e) {
            log.warn("알림 대기열이 가득 차 알림을 건너뜁니다 userId={} type={}", userId, payload.type());
        }
    }

    void deliver(Long userId, NotificationPayload payload) {
        try {
            streams.send(userId, payload);
        } catch (RuntimeException e) {
            log.warn("앱 안 알림 실패 userId={} type={} error={}", userId, payload.type(), e.getClass().getSimpleName());
        }
        try {
            push(userId, payload);
        } catch (RuntimeException e) {
            log.warn("웹 푸시 처리 실패 userId={} type={} error={}", userId, payload.type(), e.getClass().getSimpleName());
        }
    }

    private void push(Long userId, NotificationPayload payload) {
        List<PushSubscription> subscriptions = subscriptionRepository.findAllByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String json = mapper.writeValueAsString(payload);
        List<Long> expired = new ArrayList<>();
        for (PushSubscription subscription : subscriptions) {
            PushTarget target = new PushTarget(subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth());
            if (pushSender.send(target, json) == PushResult.EXPIRED) {
                expired.add(subscription.getId());
            }
        }
        if (!expired.isEmpty()) {
            subscriptionRepository.deleteAllByIdInBatch(expired);
            log.info("만료된 웹 푸시 구독 정리 userId={} count={}", userId, expired.size());
        }
    }
}
