package com.ganzithon.teolhanol.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class LoginTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired TestUsers testUsers;

    @BeforeEach
    void setUp() {
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private String tokenOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.data.accessToken");
    }

    @Test
    void 로그인하면_토큰을_받고_보호된_API를_호출할_수_있다() throws Exception {
        testUsers.signIn("a@test.com", "다정한 목동");

        ResultActions result = login("A@test.com", TestUsers.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("다정한 목동"));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + tokenOf(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("다정한 목동"));
    }

    @Test
    void 가입_응답에도_토큰이_들어_있다() throws Exception {
        ResultActions result = mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"new@test.com","password":"warmwool123","nickname":"새 목동","agreed":true}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + tokenOf(result)))
                .andExpect(status().isOk());
    }

    @Test
    void 토큰_없이_보호된_API를_부르면_401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    void 위조된_토큰이면_401() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 픽스처로_만든_사용자의_토큰이_통한다() throws Exception {
        TestUsers.Login me = testUsers.signIn("fx@test.com", "픽스처 목동");

        mockMvc.perform(get("/users/me").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(me.userId()));
    }

    @Test
    void 비밀번호가_틀려도_없는_이메일과_같은_응답() throws Exception {
        testUsers.signIn("a@test.com", "다정한 목동");

        login("a@test.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOGIN_FAILED"));
        login("none@test.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOGIN_FAILED"));
    }

    @Test
    void 다섯_번_실패하면_잠기고_맞는_비밀번호도_거절하며_남은_시간을_안내한다() throws Exception {
        testUsers.signIn("a@test.com", "다정한 목동");

        for (int i = 0; i < 4; i++) {
            login("a@test.com", "wrong").andExpect(status().isUnauthorized());
        }
        login("a@test.com", "wrong")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("5분")));

        login("a@test.com", TestUsers.PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void 잠금_시간이_지나면_다시_로그인할_수_있다() throws Exception {
        testUsers.signIn("a@test.com", "다정한 목동");
        for (int i = 0; i < 5; i++) {
            login("a@test.com", "wrong");
        }
        User locked = userRepository.findByEmail("a@test.com").orElseThrow();
        assertThat(locked.isLocked(LocalDateTime.now())).isTrue();
        assertThat(locked.isLocked(LocalDateTime.now().plusMinutes(5).plusSeconds(1))).isFalse();

        // 5분이 지난 상황: 잠금 해제 시각을 과거로 돌린다
        java.lang.reflect.Field f = User.class.getDeclaredField("lockedUntil");
        f.setAccessible(true);
        f.set(locked, LocalDateTime.now().minusSeconds(1));
        userRepository.saveAndFlush(locked);

        login("a@test.com", TestUsers.PASSWORD).andExpect(status().isOk());
        User after = userRepository.findByEmail("a@test.com").orElseThrow();
        assertThat(after.getLoginFailCount()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void 성공하면_실패_횟수가_0으로_돌아간다() throws Exception {
        testUsers.signIn("a@test.com", "다정한 목동");
        for (int i = 0; i < 3; i++) {
            login("a@test.com", "wrong");
        }
        assertThat(userRepository.findByEmail("a@test.com").orElseThrow().getLoginFailCount()).isEqualTo(3);

        login("a@test.com", TestUsers.PASSWORD).andExpect(status().isOk());

        assertThat(userRepository.findByEmail("a@test.com").orElseThrow().getLoginFailCount()).isZero();
    }

    @Test
    void 로그아웃은_인증_없이도_성공한다() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isOk());
    }
}
