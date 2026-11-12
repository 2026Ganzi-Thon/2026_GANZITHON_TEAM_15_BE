package com.ganzithon.teolhanol.domain.safety;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class CrisisHelpApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;

    @AfterEach
    void cleanUp() {
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 비회원도_상담_연결_정보를_본다() throws Exception {
        mockMvc.perform(get("/help/crisis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contacts[0].phone").value("109"))
                .andExpect(jsonPath("$.data.contacts[1].phone").value("1577-0199"))
                .andExpect(jsonPath("$.data.contacts[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.emergency.phone").value("119"))
                .andExpect(jsonPath("$.data.emergency.message").value("지금 위험하다면 119에 바로 연락해 주세요."))
                .andExpect(jsonPath("$.data.hoursNote").value("두 곳 모두 24시간"));
    }

    @Test
    void 배너_상태는_토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/help/crisis/banner")).andExpect(status().isUnauthorized());
    }

    @Test
    void 위기_판정이_없으면_배너가_꺼져_있다() throws Exception {
        TestUsers.Login me = testUsers.signIn("saf2-a@test.com", "배너꺼짐");
        mockMvc.perform(get("/help/crisis/banner").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.crisisBannerUntil").doesNotExist());
    }

    @Test
    void 위기_판정_뒤에는_배너가_켜지고_만료_시각이_내려간다() throws Exception {
        TestUsers.Login me = testUsers.signIn("saf2-b@test.com", "배너켜짐");
        User user = userRepository.findById(me.userId()).orElseThrow();
        user.activateCrisisBanner(LocalDateTime.now().plusHours(24));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/help/crisis/banner").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.crisisBannerUntil").isNotEmpty());
    }

    @Test
    void 만료된_배너는_꺼진_것으로_본다() throws Exception {
        TestUsers.Login me = testUsers.signIn("saf2-c@test.com", "배너만료");
        User user = userRepository.findById(me.userId()).orElseThrow();
        user.activateCrisisBanner(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/help/crisis/banner").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.crisisBannerUntil").doesNotExist());
    }
}
