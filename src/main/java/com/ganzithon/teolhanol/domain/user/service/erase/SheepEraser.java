package com.ganzithon.teolhanol.domain.user.service.erase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 탈퇴 시 나의 양(털·단계)과 꾸미기 아이템, 위기 판정 로그를 지웁니다. 기록만 지울 때는 유지합니다. */
@Component
@Order(20)
class SheepEraser implements UserDataEraser {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void eraseAll(Long userId) {
        em.createQuery("delete from MySheepItem i where i.mySheep.id in (select s.id from MySheep s where s.user.id = :uid)")
                .setParameter("uid", userId).executeUpdate();
        em.createQuery("delete from MySheep s where s.user.id = :uid").setParameter("uid", userId).executeUpdate();
        em.createQuery("delete from CrisisLog c where c.user.id = :uid").setParameter("uid", userId).executeUpdate();
    }
}
