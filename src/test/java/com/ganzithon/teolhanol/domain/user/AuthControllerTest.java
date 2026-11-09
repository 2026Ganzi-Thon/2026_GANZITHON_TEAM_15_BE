package com.ganzithon.teolhanol.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private static String body(String email, String password, String nickname, boolean agreed) {
        return """
                {"email":"%s","password":"%s","nickname":"%s","agreed":%s}
                """.formatted(email, password, nickname, agreed);
    }

    private org.springframework.test.web.servlet.ResultActions signup(String json) throws Exception {
        return mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 가입하면_회원과_나의_양이_함께_만들어진다() throws Exception {
        signup(body("Sheep@Example.com", "warmwool123", " 다정한 목동 ", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("다정한 목동"))
                .andExpect(jsonPath("$.data.sheepStage").value(1))
                .andExpect(jsonPath("$.data.sheepWoolCount").value(0));

        User user = userRepository.findByEmail("sheep@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("warmwool123", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("warmwool123");
        assertThat(mySheepRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_가입한_이메일이면_409() throws Exception {
        signup(body("a@test.com", "warmwool123", "첫째 목동", true)).andExpect(status().isCreated());

        signup(body("A@test.com", "warmwool123", "둘째 목동", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_EMAIL_DUPLICATED"));
    }

    @Test
    void 다른_사람이_쓰는_닉네임이면_409와_이유_문구() throws Exception {
        signup(body("a@test.com", "warmwool123", "Wool Keeper", true)).andExpect(status().isCreated());

        signup(body("b@test.com", "warmwool123", "wool keeper", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NICKNAME_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("이미 다른 목동이 쓰고 있는 이름이에요."));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 비밀번호가_짧거나_동의하지_않거나_이메일_형식이_틀리면_400() throws Exception {
        signup(body("a@test.com", "short", "다정한 목동", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_PASSWORD_TOO_SHORT"));
        signup(body("a@test.com", "warmwool123", "다정한 목동", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("이용약관과 안내에 동의해 주세요."));
        signup(body("not-email", "warmwool123", "다정한 목동", true))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void 닉네임_사용_가능_여부를_로그인_없이_확인한다() throws Exception {
        signup(body("a@test.com", "warmwool123", "다정한 목동", true)).andExpect(status().isCreated());

        mockMvc.perform(get("/auth/nickname/availability").param("nickname", "조용한 목동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(get("/auth/nickname/availability").param("nickname", "다정한 목동"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("DUPLICATE"));
        mockMvc.perform(get("/auth/nickname/availability").param("nickname", "관리자"))
                .andExpect(jsonPath("$.data.reason").value("NOT_ALLOWED"));
        mockMvc.perform(get("/auth/nickname/availability"))
                .andExpect(status().isBadRequest());
    }
}
