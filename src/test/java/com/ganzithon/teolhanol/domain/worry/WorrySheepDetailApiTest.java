package com.ganzithon.teolhanol.domain.worry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Thanks;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class WorrySheepDetailApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired JdbcTemplate jdbc;

    TestUsers.Login owner;
    TestUsers.Login other;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = testUsers.signIn("owner22@test.com", "주인목동");
        other = testUsers.signIn("other22@test.com", "지나는목동");
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

    private WorrySheep sheep(RiskLevel risk) {
        var farm = farmRepository.findAll().stream().filter(f -> f.getCode().equals("CAREER")).findFirst().orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", risk));
    }

    private Wool wool(WorrySheep sheep, TestUsers.Login sender, String text) {
        return woolRepository.saveAndFlush(new Wool(sheep, user(sender), text, false));
    }

    private ResultActions view(WorrySheep sheep, TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", who.bearer()));
    }

    @Test
    void 주인은_받은_조언을_최신순으로_보고_고마워요_여부가_나온다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        Wool first = wool(sheep, other, "천천히 정해도 괜찮아요");
        TestUsers.Login third = testUsers.signIn("third22@test.com", "세번째목동");
        Wool second = wool(sheep, third, "충분히 고민하셨어요");
        thanksRepository.saveAndFlush(new Thanks(first));

        view(sheep, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sheepId").value(sheep.getId()))
                .andExpect(jsonPath("$.data.farm.code").value("CAREER"))
                .andExpect(jsonPath("$.data.farm.tabName").value("진로"))
                .andExpect(jsonPath("$.data.content").value("이직을 해야 할지 고민이에요"))
                .andExpect(jsonPath("$.data.stage").value(1))
                .andExpect(jsonPath("$.data.stageName").value("털 깎은 양"))
                .andExpect(jsonPath("$.data.nickname").value("주인목동"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.canSendWool").value(false))
                .andExpect(jsonPath("$.data.alreadySent").value(false))
                .andExpect(jsonPath("$.data.wools.length()").value(2))
                .andExpect(jsonPath("$.data.wools[0].woolId").value(second.getId()))
                .andExpect(jsonPath("$.data.wools[0].senderNickname").value("세번째목동"))
                .andExpect(jsonPath("$.data.wools[0].thanked").value(false))
                .andExpect(jsonPath("$.data.wools[1].woolId").value(first.getId()))
                .andExpect(jsonPath("$.data.wools[1].content").value("천천히 정해도 괜찮아요"))
                .andExpect(jsonPath("$.data.wools[1].thanked").value(true))
                .andExpect(jsonPath("$.data.wools[1].createdAt").isNotEmpty());
    }

    @Test
    void 타인은_고민만_보고_조언_목록은_없으며_보낼_수_있다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        TestUsers.Login third = testUsers.signIn("third22@test.com", "세번째목동");
        wool(sheep, third, "다른 사람의 조언");

        view(sheep, other).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mine").value(false))
                .andExpect(jsonPath("$.data.canSendWool").value(true))
                .andExpect(jsonPath("$.data.alreadySent").value(false))
                .andExpect(jsonPath("$.data.nickname").value("주인목동"))
                .andExpect(jsonPath("$.data.wools").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("다른 사람의 조언"))));
    }

    @Test
    void 이미_조언을_보낸_사람은_canSendWool이_false() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        wool(sheep, other, "보낸 조언");

        view(sheep, other).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySent").value(true))
                .andExpect(jsonPath("$.data.canSendWool").value(false))
                .andExpect(jsonPath("$.data.wools").doesNotExist());
    }

    @Test
    void 응답에_userId_email_woolCount가_없다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        wool(sheep, other, "조언이에요");

        for (TestUsers.Login who : new TestUsers.Login[] {owner, other}) {
            String body = view(sheep, who).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("userId", "email", "woolCount", "@test.com");
        }
    }

    @Test
    void 숨김_양은_주인도_404() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        sheep.hide();
        worrySheepRepository.saveAndFlush(sheep);

        view(sheep, owner).andExpect(status().isNotFound());
        view(sheep, other).andExpect(status().isNotFound());
    }

    @Test
    void 졸업한_양은_주인만_볼_수_있고_canSendWool은_false() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        sheep.graduate();
        worrySheepRepository.saveAndFlush(sheep);

        view(sheep, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADUATED"))
                .andExpect(jsonPath("$.data.canSendWool").value(false));
        view(sheep, other).andExpect(status().isNotFound());
    }

    @Test
    void 차단_관계는_양방향_모두_404() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        Block block = blockRepository.saveAndFlush(new Block(user(other), user(owner)));
        view(sheep, other).andExpect(status().isNotFound());

        blockRepository.delete(block);
        blockRepository.saveAndFlush(new Block(user(owner), user(other)));
        view(sheep, other).andExpect(status().isNotFound());
        view(sheep, owner).andExpect(status().isOk());
    }

    @Test
    void 보호_양은_조언_이력이_부족하면_404_충분하면_200() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.CAUTION);
        view(sheep, other).andExpect(status().isNotFound());
        view(sheep, owner).andExpect(status().isOk());

        jdbc.update("update users set sent_wool_count = 5 where id = ?", other.userId());
        view(sheep, other).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canSendWool").value(true));
    }

    @Test
    void 보호_양에_이미_조언을_보낸_사람은_이력이_부족해도_볼_수_있다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.CAUTION);
        wool(sheep, other, "먼저 보낸 조언");

        view(sheep, other).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySent").value(true));
    }

    @Test
    void 없는_양은_404_로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/worry-sheep/99999999").header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/worry-sheep/1")).andExpect(status().isUnauthorized());
    }
}
