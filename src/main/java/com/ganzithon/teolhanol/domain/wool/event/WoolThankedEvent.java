package com.ganzithon.teolhanol.domain.wool.event;

/**
 * 조언에 고마워요가 눌렸습니다 (WOL-03 → 조언 보낸 사람 알림).
 * 저장 트랜잭션 안에서 발행합니다. 받는 쪽은 커밋 뒤에 처리하도록 {@code @TransactionalEventListener}를 쓰세요.
 * 조언 본문은 담지 않습니다. TODO(#28): 보낸 사람 알림 리스너
 *
 * @param woolId   고마워요를 받은 조언
 * @param sheepId  조언이 달린 고민 양
 * @param senderId 조언을 보낸 사람 (알림 받을 사람)
 */
public record WoolThankedEvent(Long woolId, Long sheepId, Long senderId) {
}
