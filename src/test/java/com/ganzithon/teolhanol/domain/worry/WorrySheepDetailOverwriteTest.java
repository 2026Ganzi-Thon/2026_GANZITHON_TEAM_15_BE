package com.ganzithon.teolhanol.domain.worry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주인이 상세를 여는 사이(양을 잠금 없이 읽은 뒤 확인 시각을 남기기 전) 다른 트랜잭션이 양의 상태·털 수를 바꿔 커밋해도,
 * 상세 조회가 읽어 둔 옛 값으로 덮어쓰지 않는지 확인합니다 (FRM-05).
 * 상세 조회 중 '고마워요 목록' 조회 시점에 끼어들어 순서를 결정적으로 만듭니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class WorrySheepDetailOverwriteTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean ThanksRepository thanksRepository;

    TestUsers.Login owner;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = testUsers.signIn("owner-overwrite@test.com", "덮어쓰기주인");
    }

    @AfterEach
    void cleanUp() {
        thanksRepository.deleteAll();
        woolRepository.deleteAll();
        blockRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private WorrySheep sheep() {
        var farm = farmRepository.findByCode("CAREER").orElseThrow();
        var user = userRepository.findById(owner.userId()).orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user, farm, "이직을 해야 할지 고민이에요", RiskLevel.SAFE));
    }

    /** 상세 조회가 양을 읽은 뒤 끼어들어, 별도 트랜잭션에서 양을 바꾸고 커밋합니다. */
    private void whileOwnerViews(Long sheepId, Consumer<WorrySheep> change) {
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(inv -> {
            newTx.executeWithoutResult(s -> change.accept(worrySheepRepository.findById(sheepId).orElseThrow()));
            return List.of();
        }).when(thanksRepository).findThankedWoolIdsBySheepId(anyLong());
    }

    private void viewAsOwner(WorrySheep sheep) throws Exception {
        mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", owner.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void 상세를_보는_사이_털이_늘고_숨겨져도_되돌리지_않고_확인_시각만_남긴다() throws Exception {
        WorrySheep sheep = sheep();
        whileOwnerViews(sheep.getId(), s -> {
            s.addWool(10);
            s.hide();
        });
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        viewAsOwner(sheep);

        WorrySheep after = worrySheepRepository.findById(sheep.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.HIDDEN);
        assertThat(after.getWoolCount()).isEqualTo(1);
        assertThat(after.getStage()).isEqualTo(2);
        assertThat(after.getOwnerCheckedAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    void 상세를_보는_사이_졸업해도_다시_진행중이_되지_않는다() throws Exception {
        WorrySheep sheep = sheep();
        whileOwnerViews(sheep.getId(), s -> {
            for (int i = 0; i < 10; i++) {
                s.addWool(10);
            }
            s.graduate();
        });

        viewAsOwner(sheep);

        WorrySheep after = worrySheepRepository.findById(sheep.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(after.getGraduatedAt()).isNotNull();
        assertThat(after.getWoolCount()).isEqualTo(10);
        assertThat(after.getOwnerCheckedAt()).isNotNull();
    }
}
