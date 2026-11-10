package com.ganzithon.teolhanol.domain.user.service.erase;

/**
 * 사용자 데이터 삭제 확장점 (ACC-03). 사용자와 연결된 데이터를 가진 기능은 이 인터페이스를 구현한 빈을 하나 추가해
 * 탈퇴할 때 함께 지워지게 합니다. 실행 순서는 {@code @Order}로 정하며, 사용자 행은 모든 eraser가 끝난 뒤 지워집니다.
 *
 * <p>예) 푸시 구독(WOL-02)을 만들 때는 {@code PushSubscriptionEraser}를 만들어 {@code eraseAll}에서 구독을 지웁니다.
 * 새 엔티티가 사용자를 참조하면 {@code UserDataErasureCoverageTest}가 실패해 빠뜨리지 않게 알려 줍니다.
 */
public interface UserDataEraser {

    /** '기록 전체 삭제'(탈퇴하지 않고 마음 돌보기 기록만 지우기)에서 지울 데이터. 기본은 지우는 것이 없음 */
    default void eraseRecords(Long userId) {
    }

    /** 탈퇴에서 지울 데이터. 이 사용자가 만들었거나 받은 모든 것을 지웁니다. */
    void eraseAll(Long userId);
}
