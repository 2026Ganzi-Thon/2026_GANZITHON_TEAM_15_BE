package com.ganzithon.teolhanol.domain.worry;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.support.TestUsers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class FarmSheepListApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired PolicyProperties policy;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;

    TestUsers.Login me;
    TestUsers.Login author;
    LocalDateTime base;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("grass-me@test.com", "풀밭 목동");
        author = testUsers.signIn("grass-author@test.com", "고민 목동");
        base = LocalDateTime.now().minusDays(1).withNano(0);
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

    private User user(TestUsers.Login login) {
        return userRepository.findById(login.userId()).orElseThrow();
    }

    private Farm farm(String code) {
        return farmRepository.findByCode(code).orElseThrow();
    }

    /** 털 woolCount개를 받은, minutesAfterBase 분에 만든 고민 양 */
    private WorrySheep sheep(TestUsers.Login owner, String farmCode, RiskLevel risk, int woolCount,
                             long minutesAfterBase) {
        WorrySheep s = new WorrySheep(user(owner), farm(farmCode), "고민이에요 " + minutesAfterBase, risk);
        for (int i = 0; i < woolCount; i++) {
            s.addWool(policy.worry().woolCap());
        }
        WorrySheep saved = worrySheepRepository.saveAndFlush(s);
        // JDBC로 직접 넣으면 JVM 시간대(CI는 UTC)와 앱의 Asia/Seoul 설정이 어긋나므로 Hibernate 경로로 넣는다
        LocalDateTime at = base.plusMinutes(minutesAfterBase);
        tx.executeWithoutResult(st -> em.createNativeQuery("update worry_sheep set created_at = :at where id = :id")
                .setParameter("at", at).setParameter("id", saved.getId()).executeUpdate());
        return saved;
    }

    private WorrySheep sheep(String farmCode, int woolCount, long minutesAfterBase) {
        return sheep(author, farmCode, RiskLevel.SAFE, woolCount, minutesAfterBase);
    }

    private WorrySheep sheepWithContent(String content) {
        return worrySheepRepository.saveAndFlush(
                new WorrySheep(user(author), farm("SELF"), content, RiskLevel.SAFE));
    }

    /** 내가 이 양에 조언을 보낸 것으로 기록 (털 수도 함께 늘림) */
    private void sendWool(TestUsers.Login sender, WorrySheep target) {
        WorrySheep s = worrySheepRepository.findById(target.getId()).orElseThrow();
        s.addWool(policy.worry().woolCap());
        worrySheepRepository.saveAndFlush(s);
        woolRepository.saveAndFlush(new Wool(s, user(sender), "천천히 해도 괜찮아요", false));
    }

    private void setSentWools(TestUsers.Login who, int count) {
        User u = user(who);
        for (int i = 0; i < count; i++) {
            u.addSentWool();
        }
        userRepository.saveAndFlush(u);
    }

    private void block(TestUsers.Login blocker, TestUsers.Login blocked) {
        blockRepository.saveAndFlush(new Block(user(blocker), user(blocked)));
    }

    private ResultActions list(String farmCode, int page) throws Exception {
        return mockMvc.perform(get("/farms/{farmCode}/sheep", farmCode).queryParam("page", String.valueOf(page))
                .header("Authorization", me.bearer()));
    }

    private static int id(WorrySheep s) {
        return s.getId().intValue();
    }

    @Test
    void 털_적은_순_다음_오래된_순이고_이미_보낸_양은_뒤로() throws Exception {
        WorrySheep twoWools = sheep("CAREER", 2, 10);
        WorrySheep zeroNewer = sheep("CAREER", 0, 30);
        WorrySheep zeroOlder = sheep("CAREER", 0, 20);
        WorrySheep oneWool = sheep("CAREER", 1, 40);
        // 가장 오래됐고 털도 적지만 내가 이미 보냈으므로 맨 뒤
        WorrySheep sent = sheep("CAREER", 0, 0);
        sendWool(me, sent);

        list("CAREER", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sheep[*].sheepId")
                        .value(contains(id(zeroOlder), id(zeroNewer), id(oneWool), id(twoWools), id(sent))))
                .andExpect(jsonPath("$.data.sheep[*].alreadySent").value(contains(false, false, false, false, true)));
    }

    @Test
    void 다른_사람이_보낸_조언은_alreadySent에_영향이_없다() throws Exception {
        TestUsers.Login other = testUsers.signIn("grass-other@test.com", "다른 목동");
        WorrySheep s = sheep("BURNOUT", 0, 0);
        sendWool(other, s);

        list("BURNOUT", 0)
                .andExpect(jsonPath("$.data.sheep.length()").value(1))
                .andExpect(jsonPath("$.data.sheep[0].alreadySent").value(false));
    }

    @Test
    void 졸업하거나_숨김된_양은_빠진다() throws Exception {
        WorrySheep visible = sheep("CAREER", 0, 0);
        WorrySheep graduated = sheep("CAREER", 0, 1);
        graduated.graduate();
        worrySheepRepository.saveAndFlush(graduated);
        WorrySheep hidden = sheep("CAREER", 0, 2);
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);

        list("CAREER", 0).andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(visible))));
    }

    @Test
    void 내_양은_빠진다() throws Exception {
        WorrySheep others = sheep("SELF", 0, 0);
        sheep(me, "SELF", RiskLevel.SAFE, 0, 1);

        list("SELF", 0).andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(others))));
    }

    @Test
    void 내가_차단한_사용자의_양은_빠진다() throws Exception {
        TestUsers.Login blocked = testUsers.signIn("grass-blocked@test.com", "차단된 목동");
        WorrySheep visible = sheep("CAREER", 0, 0);
        sheep(blocked, "CAREER", RiskLevel.SAFE, 0, 1);
        block(me, blocked);

        list("CAREER", 0).andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(visible))));
    }

    @Test
    void 나를_차단한_사용자의_양은_빠진다() throws Exception {
        TestUsers.Login blocker = testUsers.signIn("grass-blocker@test.com", "차단한 목동");
        WorrySheep visible = sheep("CAREER", 0, 0);
        sheep(blocker, "CAREER", RiskLevel.SAFE, 0, 1);
        block(blocker, me);

        list("CAREER", 0).andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(visible))));
    }

    @Test
    void 다른_사람끼리의_차단은_내_목록에_영향이_없다() throws Exception {
        TestUsers.Login other = testUsers.signIn("grass-other@test.com", "다른 목동");
        WorrySheep s = sheep("CAREER", 0, 0);
        block(other, author);

        list("CAREER", 0).andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(s))));
    }

    @Test
    void 보호_양은_조언을_4번_보낸_사용자에게는_보이지_않는다() throws Exception {
        int min = policy.worry().protectedViewMinSentWools();
        setSentWools(me, min - 1);
        WorrySheep safe = sheep("RELATIONSHIP", 0, 0);
        sheep(author, "RELATIONSHIP", RiskLevel.CAUTION, 0, 1);

        list("RELATIONSHIP", 0).andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(safe))));
    }

    @Test
    void 보호_양은_조언을_5번_보낸_사용자에게_보인다() throws Exception {
        int min = policy.worry().protectedViewMinSentWools();
        setSentWools(me, min);
        WorrySheep safe = sheep("RELATIONSHIP", 0, 0);
        WorrySheep caution = sheep(author, "RELATIONSHIP", RiskLevel.CAUTION, 0, 1);
        // 위기 고민은 목장에 올라오지 않지만, 혹시 있어도 누구에게도 보이지 않는다
        sheep(author, "RELATIONSHIP", RiskLevel.CRISIS, 0, 2);

        list("RELATIONSHIP", 0)
                .andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(safe), id(caution))));
    }

    @Test
    void 전체_탭은_모든_목장의_양을_같은_기준으로_정렬한다() throws Exception {
        WorrySheep career = sheep("CAREER", 1, 0);
        WorrySheep burnout = sheep("BURNOUT", 0, 10);
        WorrySheep self = sheep("SELF", 0, 5);

        list("all", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farmCode").value("ALL"))
                .andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(self), id(burnout), id(career))))
                .andExpect(jsonPath("$.data.sheep[*].farmCode").value(contains("SELF", "BURNOUT", "CAREER")));

        list("ALL", 0).andExpect(jsonPath("$.data.sheep.length()").value(3));
    }

    @Test
    void 목장_탭은_그_목장의_양만_나오고_코드는_대소문자를_가리지_않는다() throws Exception {
        WorrySheep career = sheep("CAREER", 0, 0);
        sheep("BURNOUT", 0, 1);

        list("career", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farmCode").value("CAREER"))
                .andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(career))));
    }

    @Test
    void 한_페이지는_20마리이고_hasNext로_다음_페이지를_알린다() throws Exception {
        int size = policy.worry().pageSize();
        for (int i = 0; i < size; i++) {
            sheep("SELF", 0, i);
        }
        list("SELF", 0).andExpect(jsonPath("$.data.size").value(size))
                .andExpect(jsonPath("$.data.sheep.length()").value(size))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        WorrySheep last = sheep("SELF", 0, size + 100);
        list("SELF", 0).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.sheep.length()").value(size))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        list("SELF", 1).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(last))))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        list("SELF", 2).andExpect(jsonPath("$.data.sheep.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void page를_생략하면_첫_페이지() throws Exception {
        WorrySheep s = sheep("SELF", 0, 0);
        mockMvc.perform(get("/farms/self/sheep").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.sheep[*].sheepId").value(contains(id(s))));
    }

    @Test
    void 카드_정보는_단계_앞부분_닉네임_작성시각이고_작성자_식별정보와_털_개수는_없다() throws Exception {
        sheep("CAREER", 3, 0);

        list("CAREER", 0)
                .andExpect(jsonPath("$.data.sheep[0].stage").value(3))
                .andExpect(jsonPath("$.data.sheep[0].stageName").value("포근한 양"))
                .andExpect(jsonPath("$.data.sheep[0].preview").value("고민이에요 0"))
                .andExpect(jsonPath("$.data.sheep[0].nickname").value("고민 목동"))
                .andExpect(jsonPath("$.data.sheep[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sheep[0].alreadySent").value(false))
                .andExpect(jsonPath("$.data.sheep[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.sheep[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.data.sheep[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.sheep[0].woolCount").doesNotExist())
                .andExpect(jsonPath("$.data.sheep[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.sheep[0].riskLevel").doesNotExist())
                .andExpect(jsonPath("$.data.sheep[0].viewCount").doesNotExist());
    }

    @Test
    void 고민_앞부분은_40자까지이고_넘으면_말줄임표를_붙인다() throws Exception {
        String exactly40 = "가".repeat(40);
        String over40 = "나".repeat(41);
        sheepWithContent(exactly40);
        sheepWithContent(over40);

        list("SELF", 0).andExpect(jsonPath("$.data.sheep[*].preview")
                .value(containsInAnyOrder(exactly40, "나".repeat(40) + "…")));
    }

    @Test
    void 없는_목장이면_404() throws Exception {
        list("UNKNOWN", 0).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FARM_NOT_FOUND"));
    }

    @Test
    void 음수_페이지는_400() throws Exception {
        list("all", -1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/farms/all/sheep")).andExpect(status().isUnauthorized());
    }
}
