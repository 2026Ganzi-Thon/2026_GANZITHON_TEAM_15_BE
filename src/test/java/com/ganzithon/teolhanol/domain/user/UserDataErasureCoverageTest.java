package com.ganzithon.teolhanol.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.entity.Report;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisLog;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheepItem;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.wool.entity.Thanks;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 탈퇴 시 지우는 데이터를 빠뜨리지 않게 지키는 검사 (ACC-03).
 * 사용자를 직접 참조하는 엔티티, 또는 그 엔티티의 자식 엔티티가 새로 생기면 실패합니다.
 * 실패하면: 1) {@code UserDataEraser}를 구현한 빈으로 그 데이터를 지우는 코드를 추가하고
 * 2) 아래 기대 목록에 엔티티 이름을 더한 뒤 3) {@code AccountErasureTest}에 확인을 추가하세요.
 */
@SpringBootTest
class UserDataErasureCoverageTest {

    /** User를 직접 참조하는 엔티티 (사용자 삭제 전에 지워야 함) */
    private static final Set<Class<?>> USER_OWNED = Set.of(
            Diary.class, MySheep.class, CrisisLog.class, WorrySheep.class, Wool.class, Report.class, Block.class);
    /**
     * 위 엔티티 중 하나를 참조하는 자식 엔티티 (부모보다 먼저 지워야 함).
     * Wool은 사용자(보낸 사람)와 고민 양을 모두 참조하므로 두 목록에 다 들어갑니다.
     */
    private static final Set<Class<?>> CHILDREN = Set.of(
            CareMessage.class, CareSummary.class, MySheepItem.class, Thanks.class, Wool.class);

    @PersistenceContext EntityManager em;

    private Set<String> entitiesReferencing(Set<Class<?>> targets) {
        return em.getMetamodel().getEntities().stream()
                .filter(e -> e.getSingularAttributes().stream().anyMatch(a -> targets.contains(a.getJavaType())))
                .map(EntityType::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> names(Set<Class<?>> classes) {
        return classes.stream().map(Class::getSimpleName).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void 사용자를_직접_참조하는_엔티티는_모두_탈퇴_삭제_대상으로_등록되어_있다() {
        Set<String> actual = entitiesReferencing(Set.of(User.class));

        assertThat(actual)
                .as("User를 참조하는 엔티티가 바뀌었습니다. 탈퇴 시 삭제 로직(UserDataEraser)을 추가하고 이 테스트의 USER_OWNED에 더해 주세요.")
                .isEqualTo(names(USER_OWNED));
    }

    @Test
    void 사용자_소유_엔티티를_참조하는_자식_엔티티도_모두_등록되어_있다() {
        Set<String> actual = entitiesReferencing(USER_OWNED);

        assertThat(actual)
                .as("사용자 데이터에 딸린 엔티티가 바뀌었습니다. 탈퇴 시 먼저 지우도록 UserDataEraser를 추가하고 이 테스트의 CHILDREN에 더해 주세요.")
                .isEqualTo(names(CHILDREN));
    }
}
