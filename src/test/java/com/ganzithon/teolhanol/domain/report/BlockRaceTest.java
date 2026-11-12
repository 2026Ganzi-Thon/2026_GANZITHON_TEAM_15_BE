package com.ganzithon.teolhanol.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.report.repository.ReportRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 차단이 동시에 들어와 UNIQUE(blocker_id, blocked_id) 위반이 나는 경우 (RPT-01 '차단 함께', RPT-02).
 * '기존 차단 확인'과 '저장' 사이에 다른 요청이 같은 차단을 먼저 커밋한 상황을 결정적으로 만듭니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class BlockRaceTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean BlockRepository blockRepository;

    TestUsers.Login me;
    TestUsers.Login writer;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("me-race@test.com", "경쟁목동");
        writer = testUsers.signIn("writer-race@test.com", "글쓴경쟁목동");
    }

    @AfterEach
    void cleanUp() {
        thanksRepository.deleteAll();
        woolRepository.deleteAll();
        reportRepository.deleteAll();
        blockRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(TestUsers.Login login) {
        return userRepository.findById(login.userId()).orElseThrow();
    }

    private WorrySheep sheep(TestUsers.Login owner) {
        var farm = farmRepository.findByCode("CAREER").orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", RiskLevel.SAFE));
    }

    /**
     * 첫 '기존 차단 확인'은 아직 없다고 답하면서, 그 사이 다른 트랜잭션이 같은 차단을 커밋하게 합니다.
     * 그 뒤의 확인은 실제 데이터를 돌려줍니다.
     */
    private void competingBlockCommitsBetweenCheckAndInsert() {
        TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(inv -> {
            Long blockerId = inv.getArgument(0);
            Long blockedId = inv.getArgument(1);
            if (first.getAndSet(false)) {
                otherRequest.executeWithoutResult(s -> blockRepository.saveAndFlush(new Block(
                        userRepository.getReferenceById(blockerId), userRepository.getReferenceById(blockedId))));
                return Optional.empty();
            }
            return blockRepository.findAllByBlockerWithBlocked(blockerId).stream()
                    .filter(b -> b.getBlocked().getId().equals(blockedId))
                    .findFirst();
        }).when(blockRepository).findByBlockerIdAndBlockedId(anyLong(), anyLong());
    }

    private ResultActions report(TestUsers.Login who, Long sheepId, String reason) throws Exception {
        String body = "{\"targetType\":\"WORRY_SHEEP\",\"targetId\":" + sheepId + ",\"reason\":\"" + reason
                + "\",\"blockUser\":true}";
        return mockMvc.perform(post("/reports").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 신고의_차단이_동시_차단과_겹쳐도_신고는_저장되고_blocked는_true() throws Exception {
        WorrySheep sheep = sheep(writer);
        competingBlockCommitsBetweenCheckAndInsert();

        report(me, sheep.getId(), "ABUSIVE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(true));

        assertThat(reportRepository.count()).isEqualTo(1);
        assertThat(blockRepository.count()).isEqualTo(1);
        assertThat(blockRepository.existsByBlockerIdAndBlockedId(me.userId(), writer.userId())).isTrue();
    }

    @Test
    void 위험해_보여요와_차단을_함께_해도_동시_차단과_겹치면_배너와_신고가_모두_남는다() throws Exception {
        WorrySheep sheep = sheep(writer);
        competingBlockCommitsBetweenCheckAndInsert();

        report(me, sheep.getId(), "LOOKS_DANGEROUS").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blocked").value(true));

        assertThat(reportRepository.count()).isEqualTo(1);
        assertThat(blockRepository.count()).isEqualTo(1);
        assertThat(user(writer).getCrisisBannerUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void 위험해_보여요와_차단을_함께_하면_배너와_차단이_모두_남는다() throws Exception {
        WorrySheep sheep = sheep(writer);

        report(me, sheep.getId(), "LOOKS_DANGEROUS").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blocked").value(true));

        assertThat(blockRepository.existsByBlockerIdAndBlockedId(me.userId(), writer.userId())).isTrue();
        assertThat(user(writer).getCrisisBannerUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void 차단_API도_동시_차단과_겹치면_먼저_저장된_차단을_200으로_돌려준다() throws Exception {
        WorrySheep sheep = sheep(writer);
        competingBlockCommitsBetweenCheckAndInsert();

        String body = "{\"targetType\":\"WORRY_SHEEP\",\"targetId\":" + sheep.getId() + "}";
        mockMvc.perform(post("/blocks").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("글쓴경쟁목동"))
                .andExpect(jsonPath("$.data.blockId").isNumber());

        assertThat(blockRepository.count()).isEqualTo(1);
    }
}
