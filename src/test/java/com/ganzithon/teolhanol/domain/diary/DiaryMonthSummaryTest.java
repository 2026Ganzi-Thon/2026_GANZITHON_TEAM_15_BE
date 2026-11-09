package com.ganzithon.teolhanol.domain.diary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class DiaryMonthSummaryTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired CareSummaryRepository careSummaryRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;
    @PersistenceContext EntityManager em;
    @Autowired TransactionTemplate tx;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("rev@test.com", "돌아보기 목동");
    }

    @AfterEach
    void cleanUp() {
        careSummaryRepository.deleteAll();
        careMessageRepository.deleteAll();
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Diary made(TestUsers.Login owner, Emotion emotion, CareType type, boolean completed, boolean crisis, LocalDateTime at) {
        Diary d = new Diary(userRepository.findById(owner.userId()).orElseThrow(), emotion);
        if (type != null) {
            d.startCare(type);
        }
        if (crisis) {
            d.applyRisk(RiskLevel.CRISIS);
        }
        if (completed) {
            d.completeCare();
        }
        Diary saved = diaryRepository.saveAndFlush(d);
        tx.executeWithoutResult(s -> em.createNativeQuery("update diaries set created_at = :at where id = :id")
                .setParameter("at", at).setParameter("id", saved.getId()).executeUpdate());
        return saved;
    }

    private ResultActions summary(String month, TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/diaries/summary").queryParam("month", month).header("Authorization", who.bearer()));
    }

    private static LocalDateTime nov(int day) {
        return LocalDateTime.of(2026, 11, day, 12, 0);
    }

    @Test
    void 가장_많이_고른_감정과_케어_방식_조합을_한_문장으로_만든다() throws Exception {
        made(me, Emotion.ANXIOUS, CareType.COMFORT, true, false, nov(1));
        made(me, Emotion.ANXIOUS, CareType.COMFORT, true, false, nov(2));
        made(me, Emotion.TIRED, CareType.LISTEN, true, false, nov(3));
        made(me, Emotion.ANXIOUS, CareType.SOLVE, true, false, nov(4));

        summary("2026-11", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"))
                .andExpect(jsonPath("$.data.woolCount").value(4))
                .andExpect(jsonPath("$.data.sentence").value("불안한 날엔 위로받기를 자주 골랐어요"));
    }

    @Test
    void 기록이_3개_미만이면_문장_없이_털_수만() throws Exception {
        made(me, Emotion.JOY, CareType.LISTEN, true, false, nov(1));
        made(me, Emotion.JOY, CareType.LISTEN, true, false, nov(2));

        summary("2026-11", me).andExpect(jsonPath("$.data.woolCount").value(2))
                .andExpect(jsonPath("$.data.sentence").doesNotExist());
    }

    @Test
    void 횟수가_같으면_더_최근에_고른_조합() throws Exception {
        made(me, Emotion.LONELY, CareType.LISTEN, true, false, nov(1));
        made(me, Emotion.LONELY, CareType.LISTEN, true, false, nov(2));
        made(me, Emotion.ANGRY, CareType.ORGANIZE, true, false, nov(8));
        made(me, Emotion.ANGRY, CareType.ORGANIZE, true, false, nov(9));

        summary("2026-11", me).andExpect(jsonPath("$.data.sentence").value("화가 난 날엔 생각 정리하기를 자주 골랐어요"));
    }

    @Test
    void 마무리하지_않았거나_위기_신호가_있던_기록은_세지_않는다() throws Exception {
        made(me, Emotion.JOY, CareType.COMFORT, true, false, nov(1));
        made(me, Emotion.JOY, CareType.COMFORT, false, false, nov(2));
        made(me, Emotion.JOY, null, false, false, nov(3));
        made(me, Emotion.DEPRESSED, CareType.COMFORT, true, true, nov(4));

        summary("2026-11", me).andExpect(jsonPath("$.data.woolCount").value(1))
                .andExpect(jsonPath("$.data.sentence").doesNotExist());
    }

    @Test
    void 다른_달과_다른_사용자의_기록은_세지_않는다() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        made(me, Emotion.JOY, CareType.LISTEN, true, false, nov(1));
        made(me, Emotion.JOY, CareType.LISTEN, true, false, LocalDateTime.of(2026, 10, 31, 23, 0));
        made(me, Emotion.JOY, CareType.LISTEN, true, false, LocalDateTime.of(2026, 12, 1, 0, 0));
        made(other, Emotion.JOY, CareType.LISTEN, true, false, nov(2));

        summary("2026-11", me).andExpect(jsonPath("$.data.woolCount").value(1));
    }

    @Test
    void 감정_8종과_케어_방식_4종_문장이_모두_자연스럽다() throws Exception {
        String[] days = {"기쁜 날엔", "평온한 날엔", "지친 날엔", "불안한 날엔", "우울한 날엔", "외로운 날엔", "화가 난 날엔", "마음이 복잡한 날엔"};
        String[] cares = {"이야기 들어주기를", "위로받기를", "생각 정리하기를", "해결 방법 찾기를"};
        Emotion[] emotions = Emotion.values();
        CareType[] types = CareType.values();
        for (int i = 0; i < emotions.length; i++) {
            TestUsers.Login who = testUsers.signIn("e" + i + "@test.com", "감정목동" + i);
            CareType type = types[i % types.length];
            for (int n = 1; n <= 3; n++) {
                made(who, emotions[i], type, true, false, nov(n));
            }
            summary("2026-11", who).andExpect(jsonPath("$.data.sentence")
                    .value(days[i] + " " + cares[i % cares.length] + " 자주 골랐어요"));
        }
    }

    @Test
    void 달을_비우면_이번_달_형식이_틀리면_400_빈_달은_0() throws Exception {
        made(me, Emotion.JOY, CareType.LISTEN, true, false, LocalDateTime.now());

        mockMvc.perform(get("/diaries/summary").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(java.time.YearMonth.now().toString()))
                .andExpect(jsonPath("$.data.woolCount").value(1));
        summary("abc", me).andExpect(status().isBadRequest());
        summary("2020-01", me).andExpect(status().isOk()).andExpect(jsonPath("$.data.woolCount").value(0));
    }

    @Test
    void 기록_상세_경로와_겹치지_않고_토큰이_없으면_401() throws Exception {
        Diary d = made(me, Emotion.JOY, CareType.LISTEN, true, false, nov(1));

        mockMvc.perform(get("/diaries/" + d.getId()).header("Authorization", me.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/diaries/summary")).andExpect(status().isUnauthorized());
    }
}
