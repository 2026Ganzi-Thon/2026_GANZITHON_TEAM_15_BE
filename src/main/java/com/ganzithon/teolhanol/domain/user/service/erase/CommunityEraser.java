package com.ganzithon.teolhanol.domain.user.service.erase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 시 고민 목장 데이터를 지웁니다: 내 고민 양(그 양에 달린 다른 사람의 조언과 '고마워요' 포함), 내가 보낸 조언(과 '고마워요'),
 * 내가 한 신고와 내 고민·조언에 대한 신고, 차단 관계(내가 한 것·나를 한 것).
 * 다른 사람 고민 양에 이미 쌓인 털 수는 그대로 둡니다(조언 글만 사라지고 양의 단계는 유지).
 */
@Component
@Order(40)
class CommunityEraser implements UserDataEraser {

    /** 내 고민 양 id 목록 */
    private static final String MY_SHEEP = "(select s.id from WorrySheep s where s.user.id = :uid)";
    /** 내가 보냈거나 내 고민 양에 달린 조언 id 목록 */
    private static final String MY_WOOLS =
            "(select w.id from Wool w where w.sender.id = :uid or w.sheep.id in " + MY_SHEEP + ")";

    @PersistenceContext
    private EntityManager em;

    @Override
    public void eraseAll(Long userId) {
        // 신고는 대상이 지워지기 전에 정리한다
        delete("delete from Report r where r.reporter.id = :uid"
                + " or (r.targetType = com.ganzithon.teolhanol.domain.report.entity.ReportTargetType.WORRY_SHEEP and r.targetId in " + MY_SHEEP + ")"
                + " or (r.targetType = com.ganzithon.teolhanol.domain.report.entity.ReportTargetType.WOOL and r.targetId in " + MY_WOOLS + ")", userId);
        delete("delete from Thanks t where t.wool.id in " + MY_WOOLS, userId);
        delete("delete from Wool w where w.id in " + MY_WOOLS, userId);
        delete("delete from WorrySheep s where s.user.id = :uid", userId);
        delete("delete from Block b where b.blocker.id = :uid or b.blocked.id = :uid", userId);
    }

    private void delete(String jpql, Long userId) {
        em.createQuery(jpql).setParameter("uid", userId).executeUpdate();
    }
}
