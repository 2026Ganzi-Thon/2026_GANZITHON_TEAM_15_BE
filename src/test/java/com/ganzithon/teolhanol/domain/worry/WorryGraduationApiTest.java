package com.ganzithon.teolhanol.domain.worry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.domain.worry.service.WorryGraduationService;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 고민 양 졸업 (FRM-06): 직접 졸업, 털 상한 도달, 기간 경과 자동 졸업. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class WorryGraduationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired WorryGraduationService graduationService;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;
    @MockitoBean LlmClient llmClient;

    TestUsers.Login owner;
    TestUsers.Login other;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        owner = testUsers.signIn("grad-owner@test.com", "고민주인");
        other = testUsers.signIn("grad-other@test.com", "지나가는목동");
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> {
            var constructor = inv.getArgument(1, Class.class).getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance("NONE", null);
        });
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

    private WorrySheep sheep() {
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner),
                farmRepository.findByCode("CAREER").orElseThrow(), "이직을 해야 할지 고민이에요", RiskLevel.SAFE));
    }

    /** created_at 을 Hibernate 경로로 바꿉니다. (JDBC 직접 입력은 CI(UTC)와 앱(Asia/Seoul) 시간대가 어긋남) */
    private void createdAt(WorrySheep sheep, LocalDateTime at) {
        tx.executeWithoutResult(st -> em.createNativeQuery("update worry_sheep set created_at = :at where id = :id")
                .setParameter("at", at).setParameter("id", sheep.getId()).executeUpdate());
    }

    private WorrySheep reload(WorrySheep sheep) {
        return worrySheepRepository.findById(sheep.getId()).orElseThrow();
    }

    private ResultActions graduate(TestUsers.Login who, Long sheepId) throws Exception {
        return mockMvc.perform(post("/worry-sheep/" + sheepId + "/graduate").header("Authorization", who.bearer()));
    }

    // ---- 직접 졸업 ----

    @Test
    void 주인이_직접_졸업시키면_GRADUATED와_시각이_남는다() throws Exception {
        WorrySheep sheep = sheep();

        graduate(owner, sheep.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sheepId").value(sheep.getId()))
                .andExpect(jsonPath("$.data.status").value("GRADUATED"))
                .andExpect(jsonPath("$.data.graduatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.stage").value(1))
                .andExpect(jsonPath("$.data.stageName").value("털 깎은 양"))
                .andExpect(jsonPath("$.data.woolCount").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        WorrySheep after = reload(sheep);
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(after.getGraduatedAt()).isNotNull();
    }

    @Test
    void 주인이_아니면_404이고_상태는_그대로() throws Exception {
        WorrySheep sheep = sheep();

        graduate(other, sheep.getId()).andExpect(status().isNotFound());
        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
    }

    @Test
    void 없는_양과_숨김_양은_404() throws Exception {
        graduate(owner, 999_999L).andExpect(status().isNotFound());

        WorrySheep hidden = sheep();
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);
        graduate(owner, hidden.getId()).andExpect(status().isNotFound());
        assertThat(reload(hidden).getStatus()).isEqualTo(WorrySheepStatus.HIDDEN);
    }

    @Test
    void 이미_졸업한_양은_오류_없이_현재_상태를_돌려준다() throws Exception {
        WorrySheep sheep = sheep();
        graduate(owner, sheep.getId()).andExpect(status().isOk());
        LocalDateTime first = reload(sheep).getGraduatedAt();

        graduate(owner, sheep.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADUATED"))
                .andExpect(jsonPath("$.data.graduatedAt").isNotEmpty());
        assertThat(reload(sheep).getGraduatedAt()).isEqualTo(first);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/worry-sheep/1/graduate")).andExpect(status().isUnauthorized());
    }

    // ---- 털 상한 도달 ----

    @Test
    void 열_번째_털이_도착하면_조언_보내기와_같은_요청에서_졸업한다() throws Exception {
        WorrySheep sheep = sheep();
        for (int i = 0; i < 9; i++) {
            TestUsers.Login helper = testUsers.signIn("grad-helper" + i + "@test.com", "도우미" + i);
            woolRepository.saveAndFlush(new Wool(sheep, user(helper), "응원해요 " + i, false));
            sheep.addWool(10);
        }
        worrySheepRepository.saveAndFlush(sheep);

        mockMvc.perform(post("/worry-sheep/" + sheep.getId() + "/wools").header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "혼자가 아니에요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.graduated").value(true));

        WorrySheep after = reload(sheep);
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(after.getGraduatedAt()).isNotNull();
    }

    // ---- 기간 경과 자동 졸업 ----

    @Test
    void 정확히_7일_된_양은_졸업하고_직전은_그대로() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        WorrySheep exactly = sheep();
        createdAt(exactly, now.minusDays(7));
        WorrySheep justBefore = sheep();
        createdAt(justBefore, now.minusDays(7).plusSeconds(1));
        WorrySheep old = sheep();
        createdAt(old, now.minusDays(30));
        WorrySheep fresh = sheep();

        int count = graduationService.graduateExpired(now);

        assertThat(count).isEqualTo(2);
        assertThat(reload(exactly).getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(reload(exactly).getGraduatedAt()).isEqualTo(now);
        assertThat(reload(old).getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(reload(justBefore).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
        assertThat(reload(justBefore).getGraduatedAt()).isNull();
        assertThat(reload(fresh).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
    }

    @Test
    void 자동_졸업은_숨김_양과_이미_졸업한_양을_건드리지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        WorrySheep hidden = sheep();
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);
        createdAt(hidden, now.minusDays(10));
        WorrySheep done = sheep();
        done.graduate();
        worrySheepRepository.saveAndFlush(done);
        createdAt(done, now.minusDays(10));
        LocalDateTime graduatedAt = reload(done).getGraduatedAt();

        assertThat(graduationService.graduateExpired(now)).isZero();

        assertThat(reload(hidden).getStatus()).isEqualTo(WorrySheepStatus.HIDDEN);
        assertThat(reload(hidden).getGraduatedAt()).isNull();
        assertThat(reload(done).getGraduatedAt()).isEqualTo(graduatedAt);
    }

    // ---- 졸업 후 노출 ----

    @Test
    void 졸업한_양은_목장_목록과_대기_수에서_빠지고_내_고민_졸업_탭에_나온다() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        WorrySheep expired = sheep();
        createdAt(expired, now.minusDays(8));
        WorrySheep stay = sheep();

        String list = "/farms/CAREER/sheep";
        mockMvc.perform(get(list).header("Authorization", other.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sheep.length()").value(2));

        graduationService.graduateExpired(now);
        graduate(owner, stay.getId()).andExpect(status().isOk());

        mockMvc.perform(get(list).header("Authorization", other.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sheep.length()").value(0));
        mockMvc.perform(get("/farms").header("Authorization", other.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].waitingCount").value(0));
        mockMvc.perform(get("/worry-sheep/me?status=graduated").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sheep.length()").value(2))
                .andExpect(jsonPath("$.data.sheep[0].graduatedAt").isNotEmpty());
        mockMvc.perform(get("/worry-sheep/me?status=active").header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.data.sheep.length()").value(0));
    }
}
