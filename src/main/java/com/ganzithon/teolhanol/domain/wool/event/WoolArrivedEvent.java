package com.ganzithon.teolhanol.domain.wool.event;

/**
 * 고민 양에 털(조언)이 도착했습니다 (WOL-01 → WOL-02, NOTI-01).
 * 저장 트랜잭션 안에서 발행합니다. 받는 쪽은 커밋 뒤에 처리하도록 {@code @TransactionalEventListener}를 쓰세요.
 * 조언 본문은 담지 않습니다. TODO(#28): 주인 알림 리스너
 *
 * @param sheepId 털을 받은 고민 양
 * @param ownerId 고민 양 주인 (알림 받을 사람)
 * @param woolId  저장된 조언
 */
public record WoolArrivedEvent(Long sheepId, Long ownerId, Long woolId) {
}
