package com.ganzithon.teolhanol.domain.farm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
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
class FarmListApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("farm@test.com", "목장 목동");
    }

    @AfterEach
    void cleanUp() {
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Farm farm(String code) {
        return farmRepository.findAll().stream().filter(f -> f.getCode().equals(code)).findFirst().orElseThrow();
    }

    private WorrySheep sheep(String farmCode) {
        User user = userRepository.findById(me.userId()).orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user, farm(farmCode), "고민이에요", RiskLevel.SAFE));
    }

    private ResultActions list() throws Exception {
        return mockMvc.perform(get("/farms").header("Authorization", me.bearer()));
    }

    @Test
    void 목장_4곳이_순서대로_나오고_양이_없으면_0() throws Exception {
        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].code").value("CAREER"))
                .andExpect(jsonPath("$.data[0].tabName").value("진로"))
                .andExpect(jsonPath("$.data[1].code").value("RELATIONSHIP"))
                .andExpect(jsonPath("$.data[2].code").value("BURNOUT"))
                .andExpect(jsonPath("$.data[3].code").value("SELF"))
                .andExpect(jsonPath("$.data[3].tabName").value("나"))
                .andExpect(jsonPath("$.data[0].description").isNotEmpty())
                .andExpect(jsonPath("$.data[0].waitingCount").value(0))
                .andExpect(jsonPath("$.data[3].waitingCount").value(0));
    }

    @Test
    void 목장별_ACTIVE_양만_센다() throws Exception {
        sheep("CAREER");
        sheep("CAREER");
        sheep("BURNOUT");
        WorrySheep graduated = sheep("CAREER");
        graduated.graduate();
        worrySheepRepository.saveAndFlush(graduated);
        WorrySheep hidden = sheep("BURNOUT");
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);

        list().andExpect(jsonPath("$.data[0].waitingCount").value(2))
                .andExpect(jsonPath("$.data[1].waitingCount").value(0))
                .andExpect(jsonPath("$.data[2].waitingCount").value(1))
                .andExpect(jsonPath("$.data[3].waitingCount").value(0));
    }

    @Test
    void 인기나_조회수_같은_값과_사용자_정보는_없다() throws Exception {
        sheep("SELF");
        list().andExpect(jsonPath("$.data[3].waitingCount").value(1))
                .andExpect(jsonPath("$.data[3].viewCount").doesNotExist())
                .andExpect(jsonPath("$.data[3].userId").doesNotExist());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/farms")).andExpect(status().isUnauthorized());
    }
}
