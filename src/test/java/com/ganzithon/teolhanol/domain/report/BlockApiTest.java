package com.ganzithon.teolhanol.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.report.service.BlockService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class BlockApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired BlockService blockService;

    TestUsers.Login me;
    TestUsers.Login shepherd;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("me36@test.com", "나목동");
        shepherd = testUsers.signIn("shepherd36@test.com", "상대목동");
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

    private WorrySheep sheep(TestUsers.Login owner) {
        var farm = farmRepository.findAll().stream().filter(f -> f.getCode().equals("CAREER")).findFirst().orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", RiskLevel.SAFE));
    }

    private Wool wool(WorrySheep sheep, TestUsers.Login sender) {
        return woolRepository.saveAndFlush(new Wool(sheep, user(sender), "천천히 정해도 괜찮아요", false));
    }

    private ResultActions block(TestUsers.Login who, String type, Long id) throws Exception {
        String body = "{\"targetType\":\"" + type + "\",\"targetId\":" + id + "}";
        return mockMvc.perform(post("/blocks").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/blocks").header("Authorization", who.bearer()));
    }

    private ResultActions farmSheep(TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/farms/all/sheep").header("Authorization", who.bearer()));
    }

    private long blockId(ResultActions result) throws Exception {
        JsonNode node = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("data").get("blockId").asLong();
    }

    @Test
    void 양으로_차단하면_201과_닉네임이_나오고_user_id는_없다() throws Exception {
        WorrySheep sheep = sheep(shepherd);

        ResultActions result = block(me, "WORRY_SHEEP", sheep.getId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blockId").isNumber())
                .andExpect(jsonPath("$.data.nickname").value("상대목동"))
                .andExpect(jsonPath("$.data.blockedAt").isNotEmpty());
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("userId", "blockedId", "email", "@test.com");
    }

    @Test
    void 받은_조언으로_차단할_수_있다() throws Exception {
        WorrySheep mine = sheep(me);
        Wool wool = wool(mine, shepherd);

        block(me, "WOOL", wool.getId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value("상대목동"));
    }

    @Test
    void 내_양에_달린_조언이_아니면_조언으로_차단할_수_없다() throws Exception {
        WorrySheep others = sheep(shepherd);
        TestUsers.Login third = testUsers.signIn("third36@test.com", "세번째목동");
        Wool wool = wool(others, third);

        block(me, "WOOL", wool.getId()).andExpect(status().isNotFound());
        // 내가 보낸 조언도 마찬가지(받은 조언만 대상)
        Wool mineSent = wool(sheep(third), me);
        block(me, "WOOL", mineSent.getId()).andExpect(status().isNotFound());
        block(me, "WOOL", 99999999L).andExpect(status().isNotFound());
    }

    @Test
    void 없는_양_숨김_양은_404() throws Exception {
        block(me, "WORRY_SHEEP", 99999999L).andExpect(status().isNotFound());
        WorrySheep hidden = sheep(shepherd);
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);
        block(me, "WORRY_SHEEP", hidden.getId()).andExpect(status().isNotFound());
    }

    @Test
    void 볼_수_없는_보호_양으로는_차단할_수_없고_닉네임도_나오지_않는다() throws Exception {
        var farm = farmRepository.findAll().stream().filter(f -> f.getCode().equals("CAREER")).findFirst().orElseThrow();
        WorrySheep protectedSheep = worrySheepRepository.saveAndFlush(
                new WorrySheep(user(shepherd), farm, "요즘 많이 지쳐요", RiskLevel.CAUTION));

        String body = block(me, "WORRY_SHEEP", protectedSheep.getId()).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("상대목동");
        assertThat(blockRepository.count()).isZero();

        // 이미 조언을 보낸 보호 양이면 볼 수 있으므로 차단할 수 있습니다.
        wool(protectedSheep, me);
        block(me, "WORRY_SHEEP", protectedSheep.getId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value("상대목동"));
    }

    @Test
    void 자기_자신은_차단할_수_없다() throws Exception {
        WorrySheep mine = sheep(me);
        block(me, "WORRY_SHEEP", mine.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BLOCK_SELF"));
        assertThat(blockRepository.count()).isZero();
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        mockMvc.perform(post("/blocks").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/blocks").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"USER\",\"targetId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미_차단한_목동을_다시_차단하면_기존_차단을_200으로_돌려준다() throws Exception {
        WorrySheep first = sheep(shepherd);
        WorrySheep second = sheep(shepherd);
        long id = blockId(block(me, "WORRY_SHEEP", first.getId()).andExpect(status().isCreated()));

        block(me, "WORRY_SHEEP", second.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockId").value(id));
        assertThat(blockRepository.count()).isEqualTo(1);
    }

    @Test
    void blockUser는_멱등이다() {
        var first = blockService.blockUser(me.userId(), shepherd.userId());
        var again = blockService.blockUser(me.userId(), shepherd.userId());
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(blockRepository.count()).isEqualTo(1);
    }

    @Test
    void 차단_목록은_최신순이고_내_차단만_보인다() throws Exception {
        TestUsers.Login third = testUsers.signIn("third36@test.com", "세번째목동");
        block(me, "WORRY_SHEEP", sheep(shepherd).getId()).andExpect(status().isCreated());
        block(me, "WORRY_SHEEP", sheep(third).getId()).andExpect(status().isCreated());
        block(shepherd, "WORRY_SHEEP", sheep(third).getId()).andExpect(status().isCreated());

        list(me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].nickname").value("세번째목동"))
                .andExpect(jsonPath("$.data[1].nickname").value("상대목동"))
                .andExpect(jsonPath("$.data[0].blockedAt").isNotEmpty());
        list(third).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 차단하면_서로의_양이_목장에서_사라지고_해제하면_다시_보인다() throws Exception {
        WorrySheep theirs = sheep(shepherd);
        WorrySheep mine = sheep(me);
        farmSheep(me).andExpect(jsonPath("$.data.sheep[*].sheepId").value(org.hamcrest.Matchers.contains(
                theirs.getId().intValue())));
        farmSheep(shepherd).andExpect(jsonPath("$.data.sheep[*].sheepId").value(org.hamcrest.Matchers.contains(
                mine.getId().intValue())));

        long id = blockId(block(me, "WORRY_SHEEP", theirs.getId()));
        list(me).andExpect(jsonPath("$.data.length()").value(1));
        farmSheep(me).andExpect(jsonPath("$.data.sheep.length()").value(0));
        farmSheep(shepherd).andExpect(jsonPath("$.data.sheep.length()").value(0));
        mockMvc.perform(get("/worry-sheep/" + theirs.getId()).header("Authorization", me.bearer()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/blocks/" + id).header("Authorization", me.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        list(me).andExpect(jsonPath("$.data.length()").value(0));
        farmSheep(me).andExpect(jsonPath("$.data.sheep.length()").value(1));
        farmSheep(shepherd).andExpect(jsonPath("$.data.sheep.length()").value(1));
    }

    @Test
    void 남의_차단이나_없는_차단_해제는_404() throws Exception {
        long id = blockId(block(me, "WORRY_SHEEP", sheep(shepherd).getId()));

        mockMvc.perform(delete("/blocks/" + id).header("Authorization", shepherd.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/blocks/99999999").header("Authorization", me.bearer()))
                .andExpect(status().isNotFound());
        assertThat(blockRepository.count()).isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/blocks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/blocks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/blocks/1")).andExpect(status().isUnauthorized());
    }
}
