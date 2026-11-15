package com.ganzithon.teolhanol.domain.farm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.farm.service.FarmRecommender;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class FarmRecommendationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired DiaryRepository diaryRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired FarmRecommender farmRecommender;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("rec@test.com", "추천 목동");
    }

    @AfterEach
    void cleanUp() {
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Diary diary(Long userId, Emotion emotion, CareType careType) {
        Diary d = new Diary(userRepository.findById(userId).orElseThrow(), emotion);
        if (careType != null) {
            d.startCare(careType);
        }
        return diaryRepository.saveAndFlush(d);
    }

    private ResultActions recommend(Long diaryId) throws Exception {
        return mockMvc.perform(get("/diaries/" + diaryId + "/farm-recommendation").header("Authorization", me.bearer()));
    }

    @Test
    void 지침은_번아웃_목장을_추천한다() throws Exception {
        recommend(diary(me.userId(), Emotion.TIRED, CareType.LISTEN).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farmCode").value("BURNOUT"))
                .andExpect(jsonPath("$.data.name").value("번아웃"))
                .andExpect(jsonPath("$.data.tabName").value("번아웃"));
    }

    @Test
    void 불안과_해결_방법은_진로_케어_방식이_없으면_나() throws Exception {
        recommend(diary(me.userId(), Emotion.ANXIOUS, CareType.SOLVE).getId())
                .andExpect(jsonPath("$.data.farmCode").value("CAREER"));
        recommend(diary(me.userId(), Emotion.ANXIOUS, null).getId())
                .andExpect(jsonPath("$.data.farmCode").value("SELF"));
    }

    @Test
    void 외로움과_화남은_관계() throws Exception {
        recommend(diary(me.userId(), Emotion.LONELY, CareType.COMFORT).getId())
                .andExpect(jsonPath("$.data.farmCode").value("RELATIONSHIP"));
        recommend(diary(me.userId(), Emotion.ANGRY, null).getId())
                .andExpect(jsonPath("$.data.farmCode").value("RELATIONSHIP"));
    }

    @Test
    void 감정과_케어_방식_모든_조합이_실제_목장을_돌려준다() {
        CareType[] types = new CareType[] {
                null, CareType.LISTEN, CareType.COMFORT, CareType.ORGANIZE, CareType.SOLVE };
        for (Emotion emotion : Emotion.values()) {
            for (CareType type : types) {
                String code = farmRecommender.recommend(emotion, type);
                assertThat(farmRepository.existsByCode(code)).as("%s x %s -> %s", emotion, type, code).isTrue();
            }
        }
    }

    @Test
    void 남의_기록과_없는_기록은_404() throws Exception {
        TestUsers.Login other = testUsers.signIn("rec-other@test.com", "다른 목동");
        Long othersDiary = diary(other.userId(), Emotion.TIRED, null).getId();
        recommend(othersDiary).andExpect(status().isNotFound());
        recommend(999999L).andExpect(status().isNotFound());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/diaries/1/farm-recommendation")).andExpect(status().isUnauthorized());
    }
}
