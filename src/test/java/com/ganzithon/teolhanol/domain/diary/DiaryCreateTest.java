package com.ganzithon.teolhanol.domain.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class DiaryCreateTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired DiaryRepository diaryRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("diary@test.com", "마음 목동");
    }

    @AfterEach
    void cleanUp() {
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions pick(String emotion) throws Exception {
        return mockMvc.perform(post("/diaries").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"emotion\":\"" + emotion + "\"}"));
    }

    @Test
    void 감정을_고르면_기록이_만들어지고_첫_마디가_온다() throws Exception {
        pick("TIRED").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diaryId").isNumber())
                .andExpect(jsonPath("$.data.firstMessage").value("많이 지쳤구나. 오늘은 어떤 돌봄이 필요해요?"));

        Diary diary = diaryRepository.findAll().get(0);
        assertThat(diary.getEmotion()).isEqualTo(Emotion.TIRED);
        assertThat(diary.getRiskLevel()).isEqualTo(RiskLevel.SAFE);
        assertThat(diary.getCareStatus()).isEqualTo(CareStatus.NONE);
        assertThat(diary.getUser().getId()).isEqualTo(me.userId());
    }

    @ParameterizedTest
    @EnumSource(Emotion.class)
    void 감정_8종_모두_첫_마디가_있고_돌봄을_묻는다(Emotion emotion) throws Exception {
        pick(emotion.name()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firstMessage").value(org.hamcrest.Matchers.endsWith("오늘은 어떤 돌봄이 필요해요?")));
    }

    @Test
    void 없는_감정이거나_비어_있으면_400() throws Exception {
        pick("HAPPY").andExpect(status().isBadRequest());
        mockMvc.perform(post("/diaries").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(diaryRepository.count()).isZero();
    }

    @Test
    void 하루_세_번까지만_시작할_수_있다() throws Exception {
        pick("JOY").andExpect(status().isCreated());
        pick("CALM").andExpect(status().isCreated());
        pick("ANGRY").andExpect(status().isCreated());

        pick("LONELY").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message").value("오늘은 충분히 이야기해 주셨어요. 내일 다시 들려주세요."));
        assertThat(diaryRepository.count()).isEqualTo(3);
    }

    @Test
    void 다른_사람이_시작한_횟수는_세지_않는다() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/diaries").header("Authorization", other.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"emotion\":\"JOY\"}"))
                    .andExpect(status().isCreated());
        }

        pick("JOY").andExpect(status().isCreated());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/diaries").contentType(MediaType.APPLICATION_JSON).content("{\"emotion\":\"JOY\"}"))
                .andExpect(status().isUnauthorized());
    }
}
