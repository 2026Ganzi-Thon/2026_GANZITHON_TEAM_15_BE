package com.ganzithon.teolhanol.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
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

/** 닉네임 바꾸기 (ACC-05, 와이어프레임 v3 ME-02)와 닉네임 2~10자 규칙 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class NicknameChangeTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("nick@test.com", "다정한 목동");
    }

    @AfterEach
    void cleanUp() {
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions change(String nickname, TestUsers.Login who) throws Exception {
        return mockMvc.perform(put("/users/me/nickname").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}"));
    }

    private String nicknameOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getNickname();
    }

    @Test
    void 규칙에_맞는_새_닉네임으로_바꾼다() throws Exception {
        change("포근한 목동", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(me.userId()))
                .andExpect(jsonPath("$.data.nickname").value("포근한 목동"));

        assertThat(nicknameOf(me.userId())).isEqualTo("포근한 목동");
        mockMvc.perform(get("/users/me").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.nickname").value("포근한 목동"));
    }

    @Test
    void 앞뒤_공백은_지우고_저장한다() throws Exception {
        change("  양털 목동  ", me).andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("양털 목동"));
        assertThat(nicknameOf(me.userId())).isEqualTo("양털 목동");
    }

    @Test
    void 지금_닉네임과_같으면_그대로_성공한다() throws Exception {
        change("다정한 목동", me).andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("다정한 목동"));
        assertThat(nicknameOf(me.userId())).isEqualTo("다정한 목동");
    }

    @Test
    void 내_이름에서_대소문자만_바꾸는_것은_허용한다() throws Exception {
        TestUsers.Login eng = testUsers.signIn("eng@test.com", "Wool Pal");

        change("wool pal", eng).andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("wool pal"));
        assertThat(nicknameOf(eng.userId())).isEqualTo("wool pal");
    }

    @Test
    void 다른_사람이_쓰는_닉네임이면_409와_이유_문구() throws Exception {
        testUsers.signIn("other@test.com", "Sleepy Ram");

        change("sleepy ram", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NICKNAME_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("이미 다른 목동이 쓰고 있는 이름이에요."));
        assertThat(nicknameOf(me.userId())).isEqualTo("다정한 목동");
    }

    @Test
    void 규칙에_어긋나면_409이고_닉네임은_그대로() throws Exception {
        change("양", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("닉네임은 2~10자로 정해 주세요."));
        change("가나다라마바사아자차카", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("닉네임은 2~10자로 정해 주세요."));
        change("목동!", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("한글, 영문, 숫자와 띄어쓰기 한 칸만 쓸 수 있어요."));
        change("운영자 목동", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("이 이름은 쓸 수 없어요. 다른 이름을 골라 주세요."));

        assertThat(nicknameOf(me.userId())).isEqualTo("다정한 목동");
    }

    @Test
    void 열_글자까지는_되고_열한_글자는_안_된다() throws Exception {
        change("가나다라마바사아자차", me).andExpect(status().isOk());
        change("가나다라마바사아자차카", me).andExpect(status().isConflict());
    }

    @Test
    void 열한_글자_닉네임으로는_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"long@test.com\",\"password\":\"warmwool123\",\"nickname\":\"가나다라마바사아자차카\",\"agreed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("닉네임은 2~10자로 정해 주세요."));
        mockMvc.perform(get("/auth/nickname/availability").queryParam("nickname", "가나다라마바사아자차카"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("LENGTH"));
    }

    @Test
    void 닉네임을_비우거나_보내지_않으면_400이다() throws Exception {
        mockMvc.perform(put("/users/me/nickname").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        change("   ", me).andExpect(status().isConflict());
        assertThat(nicknameOf(me.userId())).isEqualTo("다정한 목동");
    }

    @Test
    void 바꾼_뒤에는_이전_닉네임을_다른_사람이_쓸_수_있다() throws Exception {
        change("포근한 목동", me).andExpect(status().isOk());

        TestUsers.Login other = testUsers.signIn("other@test.com", "임시 목동");
        change("다정한 목동", other).andExpect(status().isOk());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(put("/users/me/nickname").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"포근한 목동\"}"))
                .andExpect(status().isUnauthorized());
    }
}
