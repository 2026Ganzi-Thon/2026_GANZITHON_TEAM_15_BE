package com.ganzithon.teolhanol.domain.user.service.erase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 마음 돌보기 기록: 오늘의 마음(diaries), 대화(care_messages), 마무리 카드(care_summaries). 털·털실·아이템은 건드리지 않습니다. */
@Component
@Order(10)
class RecordsEraser implements UserDataEraser {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void eraseRecords(Long userId) {
        delete("delete from CareMessage m where m.diary.id in (select d.id from Diary d where d.user.id = :uid)", userId);
        delete("delete from CareSummary s where s.diary.id in (select d.id from Diary d where d.user.id = :uid)", userId);
        delete("delete from Diary d where d.user.id = :uid", userId);
    }

    @Override
    public void eraseAll(Long userId) {
        eraseRecords(userId);
    }

    private void delete(String jpql, Long userId) {
        em.createQuery(jpql).setParameter("uid", userId).executeUpdate();
    }
}
