package com.ganzithon.teolhanol.domain.worry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
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
import com.ganzithon.teolhanol.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class MyWorryListApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;

    TestUsers.Login me;
    TestUsers.Login other;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("me24@test.com", "내목동");
        other = testUsers.signIn("other24@test.com", "남의목동");
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

    private WorrySheep sheep(TestUsers.Login owner, String farmCode, String content) {
        var farm = farmRepository.findAll().stream().filter(f -> f.getCode().equals(farmCode)).findFirst().orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, content, RiskLevel.SAFE));
    }

    private void wool(WorrySheep sheep, TestUsers.Login sender) {
        woolRepository.saveAndFlush(new Wool(sheep, user(sender), "조언이에요", false));
    }

    private ResultActions list(TestUsers.Login who, String query) throws Exception {
        return mockMvc.perform(get("/worry-sheep/me" + query).header("Authorization", who.bearer()));
    }

    private void pause() throws InterruptedException {
        Thread.sleep(30);
    }

    @Test
    void 본인_양만_최신순으로_나오고_항목_형식이_맞다() throws Exception {
        WorrySheep older = sheep(me, "CAREER", "이직을 해야 할지 고민이에요");
        pause();
        WorrySheep newer = sheep(me, "CAREER", "가".repeat(45));
        sheep(other, "CAREER", "남의 고민");

        String body = list(me, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sheep.length()").value(2))
                .andExpect(jsonPath("$.data.sheep[0].sheepId").value(newer.getId()))
                .andExpect(jsonPath("$.data.sheep[0].preview").value("가".repeat(40) + "…"))
                .andExpect(jsonPath("$.data.sheep[1].sheepId").value(older.getId()))
                .andExpect(jsonPath("$.data.sheep[1].preview").value("이직을 해야 할지 고민이에요"))
                .andExpect(jsonPath("$.data.sheep[1].farm.code").value("CAREER"))
                .andExpect(jsonPath("$.data.sheep[1].farm.tabName").value("진로"))
                .andExpect(jsonPath("$.data.sheep[1].stage").value(1))
                .andExpect(jsonPath("$.data.sheep[1].stageName").value("털 깎은 양"))
                .andExpect(jsonPath("$.data.sheep[1].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sheep[1].newWoolCount").value(0))
                .andExpect(jsonPath("$.data.sheep[1].hidden").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("남의 고민", "userId", "email", "woolCount", "@test.com");

        list(other, "").andExpect(jsonPath("$.data.sheep.length()").value(1))
                .andExpect(jsonPath("$.data.sheep[0].preview").value("남의 고민"));
    }

    @Test
    void 진행중과_보관함_탭이_나뉘고_숨김_양은_진행중에_표시된다() throws Exception {
        WorrySheep active = sheep(me, "CAREER", "진행 중 고민");
        WorrySheep graduated = sheep(me, "CAREER", "졸업한 고민");
        graduated.graduate();
        worrySheepRepository.saveAndFlush(graduated);
        WorrySheep hidden = sheep(me, "CAREER", "숨겨진 고민");
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);

        list(me, "?status=active").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sheep.length()").value(2))
                .andExpect(jsonPath("$.data.sheep[0].sheepId").value(hidden.getId()))
                .andExpect(jsonPath("$.data.sheep[0].hidden").value(true))
                .andExpect(jsonPath("$.data.sheep[1].sheepId").value(active.getId()))
                .andExpect(jsonPath("$.data.sheep[1].hidden").value(false));
        list(me, "?status=Graduated").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADUATED"))
                .andExpect(jsonPath("$.data.sheep.length()").value(1))
                .andExpect(jsonPath("$.data.sheep[0].sheepId").value(graduated.getId()))
                .andExpect(jsonPath("$.data.sheep[0].graduatedAt").isNotEmpty());
    }

    @Test
    void 알_수_없는_status는_400() throws Exception {
        list(me, "?status=all").andExpect(status().isBadRequest());
    }

    @Test
    void me는_상세_경로의_id로_잡히지_않는다() throws Exception {
        list(me, "").andExpect(status().isOk());
        mockMvc.perform(get("/worry-sheep/abc").header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 새_털_수는_확인_전_전부_상세_조회_후_0_그_뒤_새_털만_센다() throws Exception {
        WorrySheep sheep = sheep(me, "CAREER", "새 털 고민");
        WorrySheep untouched = sheep(me, "CAREER", "털 없는 고민");
        TestUsers.Login third = testUsers.signIn("third24@test.com", "세번째목동");
        wool(sheep, other);
        wool(sheep, third);

        // 확인 전: 받은 털 전부
        list(me, "").andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + sheep.getId() + ")].newWoolCount").value(2))
                .andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + untouched.getId() + ")].newWoolCount").value(0));

        // 남이 상세를 봐도 확인 시각은 바뀌지 않는다
        pause();
        mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", other.bearer()))
                .andExpect(status().isOk());
        list(me, "").andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + sheep.getId() + ")].newWoolCount").value(2));

        // 주인이 상세를 보면 0
        pause();
        mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", me.bearer()))
                .andExpect(status().isOk());
        list(me, "").andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + sheep.getId() + ")].newWoolCount").value(0));

        // 그 뒤 새로 온 털만 센다
        pause();
        TestUsers.Login fourth = testUsers.signIn("fourth24@test.com", "네번째목동");
        wool(sheep, fourth);
        list(me, "").andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + sheep.getId() + ")].newWoolCount").value(1));
    }

    @Test
    void 신고로_숨겨진_조언은_새_털_수에_세지_않는다() throws Exception {
        WorrySheep sheep = sheep(me, "CAREER", "숨긴 조언이 있는 고민");
        TestUsers.Login third = testUsers.signIn("third24@test.com", "세번째목동");
        wool(sheep, other);
        Wool hiddenWool = woolRepository.saveAndFlush(new Wool(sheep, user(third), "숨겨질 조언", false));
        hiddenWool.hide();
        woolRepository.saveAndFlush(hiddenWool);

        list(me, "").andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + sheep.getId() + ")].newWoolCount").value(1));

        // 확인한 뒤 새로 온 조언이 숨겨져도 세지 않는다
        pause();
        mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", me.bearer()))
                .andExpect(status().isOk());
        pause();
        TestUsers.Login fourth = testUsers.signIn("fourth24@test.com", "네번째목동");
        Wool lateHidden = woolRepository.saveAndFlush(new Wool(sheep, user(fourth), "늦게 온 숨김 조언", false));
        lateHidden.hide();
        woolRepository.saveAndFlush(lateHidden);
        list(me, "").andExpect(jsonPath("$.data.sheep[?(@.sheepId==" + sheep.getId() + ")].newWoolCount").value(0));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/worry-sheep/me")).andExpect(status().isUnauthorized());
    }
}
