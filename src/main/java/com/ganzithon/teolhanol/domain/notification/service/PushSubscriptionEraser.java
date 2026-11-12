package com.ganzithon.teolhanol.domain.notification.service;

import com.ganzithon.teolhanol.domain.user.service.erase.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 탈퇴 시 이 사용자의 웹 푸시 구독을 지웁니다 (ACC-03, WOL-02). 기록만 지울 때는 유지합니다. */
@Component
@Order(50)
class PushSubscriptionEraser implements UserDataEraser {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void eraseAll(Long userId) {
        em.createQuery("delete from PushSubscription p where p.user.id = :uid")
                .setParameter("uid", userId).executeUpdate();
    }
}
