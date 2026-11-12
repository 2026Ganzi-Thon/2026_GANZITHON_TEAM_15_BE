package com.ganzithon.teolhanol.domain.notification.service;

import com.ganzithon.teolhanol.domain.notification.config.PushProperties;
import com.ganzithon.teolhanol.domain.notification.dto.NotificationPayload;
import com.ganzithon.teolhanol.domain.wool.event.WoolArrivedEvent;
import com.ganzithon.teolhanol.domain.wool.event.WoolThankedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 조언·고마워요 저장이 커밋된 뒤에만 알립니다 (WOL-02, WOL-03). 롤백된 저장은 알리지 않습니다.
 * 이벤트에는 조언 본문이 없고, 알림에도 조언 내용·닉네임을 넣지 않습니다.
 */
@Component
@RequiredArgsConstructor
class WoolNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final PushProperties properties;

    /** 털 도착 → 양 주인. 누르면 내 고민 양 상세(FARM-05)로 갑니다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWoolArrived(WoolArrivedEvent event) {
        dispatcher.notify(event.ownerId(),
                NotificationPayload.woolArrived(event.sheepId(), properties.clickUrl(event.sheepId())));
    }

    /** 고마워요 → 조언 보낸 사람 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWoolThanked(WoolThankedEvent event) {
        dispatcher.notify(event.senderId(), NotificationPayload.woolThanked(event.sheepId()));
    }
}
