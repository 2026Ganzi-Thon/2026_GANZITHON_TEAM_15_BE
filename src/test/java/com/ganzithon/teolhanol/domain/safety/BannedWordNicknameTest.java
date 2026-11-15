package com.ganzithon.teolhanol.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.safety.service.BannedWordFilter;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.user.service.NicknameReason;
import com.ganzithon.teolhanol.domain.user.service.NicknameValidator;
import com.ganzithon.teolhanol.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 닉네임 검사에서도 조언과 같은 금칙어 검사기를 씁니다 (SAF-04, 기능명세서 5장). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class BannedWordNicknameTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired NicknameValidator validator;
    @Autowired BannedWordFilter bannedWordFilter;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;

    @AfterEach
    void cleanUp() {
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    @ParameterizedTest(name = "\"{0}\" → NOT_ALLOWED")
    @ValueSource(strings = {"씨발목동", "병신 목동", "병 신", "존나 귀여운 양", "죽어버려"})
    void 금칙어가_들어간_닉네임은_쓸_수_없다(String nickname) {
        assertThat(validator.check(nickname)).contains(NicknameReason.NOT_ALLOWED);
    }

    @Test
    void 평범한_닉네임은_금칙어_검사를_통과한다() {
        assertThat(validator.check("다정한 목동")).isEmpty();
        assertThat(bannedWordFilter.contains("포근한 양털")).isFalse();
    }

    @Test
    void 사용_가능_확인_API에서도_금칙어를_알려준다() throws Exception {
        mockMvc.perform(get("/auth/nickname/availability").param("nickname", "병신 목동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_ALLOWED"));
    }

    @Test
    void 금칙어로_닉네임을_바꿀_수_없다() throws Exception {
        TestUsers.Login me = testUsers.signIn("banned-nick@test.com", "다정한 목동");

        mockMvc.perform(put("/users/me/nickname").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"지랄 목동\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NICKNAME_UNAVAILABLE"));

        assertThat(userRepository.findById(me.userId()).orElseThrow().getNickname()).isEqualTo("다정한 목동");
    }
}
