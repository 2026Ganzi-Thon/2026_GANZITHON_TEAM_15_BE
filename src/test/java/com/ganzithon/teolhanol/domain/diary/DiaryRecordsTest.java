package com.ganzithon.teolhanol.domain.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class DiaryRecordsTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired CareSummaryRepository careSummaryRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("rec@test.com", "기록 목동");
    }

    @AfterEach
    void cleanUp() {
        careSummaryRepository.deleteAll();
        careMessageRepository.deleteAll();
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** 지정한 시각에 만든 기록 */
    private Diary diary(TestUsers.Login owner, Emotion emotion, LocalDateTime at) {
        Diary d = diaryRepository.saveAndFlush(new Diary(userRepository.findById(owner.userId()).orElseThrow(), emotion));
        jdbc.update("update diaries set created_at = ? where id = ?", at, d.getId());
        return d;
    }

    private Diary completed(Diary d, CareType type, String card) {
        d.startCare(type);
        for (int i = 1; i <= 3; i++) {
            careMessageRepository.save(new CareMessage(d, MessageRole.USER, "내 이야기 " + i));
            careMessageRepository.save(new CareMessage(d, MessageRole.AI, "AI 답 " + i));
            d.addUserMessage();
        }
        d.completeCare();
        careSummaryRepository.save(new CareSummary(d, card, LocalDateTime.now()));
        return diaryRepository.saveAndFlush(d);
    }

    private ResultActions list(String month, TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/diaries").queryParam("month", month).header("Authorization", who.bearer()));
    }

    @Test
    void 월_조회는_날짜별_대표_감정과_최신순_목록을_돌려준다() throws Exception {
        diary(me, Emotion.JOY, LocalDateTime.of(2026, 11, 3, 9, 0));
        Diary recent = diary(me, Emotion.TIRED, LocalDateTime.of(2026, 11, 3, 21, 0));
        Diary done = completed(diary(me, Emotion.ANXIOUS, LocalDateTime.of(2026, 11, 5, 12, 0)), CareType.COMFORT,
                "오늘도 애썼어요.\n두 번째 줄");
        diary(me, Emotion.CALM, LocalDateTime.of(2026, 10, 31, 23, 59));
        diary(me, Emotion.ANGRY, LocalDateTime.of(2026, 12, 1, 0, 0));

        list("2026-11", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-11"))
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.data.days[0].emotion").value("TIRED"))
                .andExpect(jsonPath("$.data.days[0].emotionLabel").value("지침"))
                .andExpect(jsonPath("$.data.days[0].diaryId").value(recent.getId()))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-11-05"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].diaryId").value(done.getId()))
                .andExpect(jsonPath("$.data.items[0].summaryLine").value("오늘도 애썼어요."))
                .andExpect(jsonPath("$.data.items[0].careType").value("COMFORT"))
                .andExpect(jsonPath("$.data.items[0].canResume").value(false))
                .andExpect(jsonPath("$.data.items[1].emotion").value("TIRED"))
                .andExpect(jsonPath("$.data.items[1].summaryLine").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].careStatus").value("NONE"))
                .andExpect(jsonPath("$.data.items[1].canResume").value(true))
                .andExpect(jsonPath("$.data.items[2].emotion").value("JOY"));
    }

    @Test
    void 목록의_한_줄은_첫_문장이고_너무_길면_줄인다() throws Exception {
        completed(diary(me, Emotion.JOY, LocalDateTime.of(2026, 11, 3, 9, 0)), CareType.COMFORT, "첫 문장이에요. 두 번째 문장이에요.");
        completed(diary(me, Emotion.CALM, LocalDateTime.of(2026, 11, 4, 9, 0)), CareType.LISTEN, "가".repeat(80));

        list("2026-11", me).andExpect(jsonPath("$.data.items[0].summaryLine").value("가".repeat(60) + "…"))
                .andExpect(jsonPath("$.data.items[1].summaryLine").value("첫 문장이에요."));
    }

    @Test
    void 달을_비우면_이번_달() throws Exception {
        diary(me, Emotion.JOY, LocalDateTime.now());

        mockMvc.perform(get("/diaries").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(java.time.YearMonth.now().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 기록이_없는_달은_빈_목록() throws Exception {
        list("2020-01", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").isEmpty())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void 달_형식이_틀리면_400() throws Exception {
        list("2026-13", me).andExpect(status().isBadRequest());
        list("november", me).andExpect(status().isBadRequest());
    }

    @Test
    void 목록에는_내_기록만_나온다() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        diary(me, Emotion.JOY, LocalDateTime.of(2026, 11, 3, 9, 0));
        diary(other, Emotion.ANGRY, LocalDateTime.of(2026, 11, 3, 10, 0));

        list("2026-11", me).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].emotion").value("JOY"))
                .andExpect(jsonPath("$.data.days[0].emotion").value("JOY"));
    }

    @Test
    void 상세는_감정_카드_첫_이야기_대화_전체를_돌려준다() throws Exception {
        Diary d = completed(diary(me, Emotion.LONELY, LocalDateTime.of(2026, 11, 4, 20, 0)), CareType.LISTEN, "외로운 마음을 털어놓았어요.");

        mockMvc.perform(get("/diaries/" + d.getId()).header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emotion").value("LONELY"))
                .andExpect(jsonPath("$.data.careType").value("LISTEN"))
                .andExpect(jsonPath("$.data.careStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.firstMessage").value("내 이야기 1"))
                .andExpect(jsonPath("$.data.summary.text").value("외로운 마음을 털어놓았어요."))
                .andExpect(jsonPath("$.data.woolEarned").value(true))
                .andExpect(jsonPath("$.data.messages.length()").value(6))
                .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[0].content").value("내 이야기 1"))
                .andExpect(jsonPath("$.data.messages[1].role").value("AI"))
                .andExpect(jsonPath("$.data.messages[5].content").value("AI 답 3"));
    }

    @Test
    void 위기_신호가_있던_기록은_털을_받지_않은_것으로_보인다() throws Exception {
        Diary d = diary(me, Emotion.DEPRESSED, LocalDateTime.of(2026, 11, 4, 20, 0));
        d.applyRisk(RiskLevel.CRISIS);
        d = completed(d, CareType.COMFORT, "곁에 있어요.");

        mockMvc.perform(get("/diaries/" + d.getId()).header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.careStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.woolEarned").value(false));
    }

    @Test
    void 마무리_전_기록은_카드와_첫_이야기가_비어_있다() throws Exception {
        Diary d = diary(me, Emotion.JOY, LocalDateTime.of(2026, 11, 4, 20, 0));

        mockMvc.perform(get("/diaries/" + d.getId()).header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.firstMessage").doesNotExist())
                .andExpect(jsonPath("$.data.woolEarned").value(false))
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void 남의_기록이나_없는_기록은_상세와_삭제_모두_404() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        Diary d = completed(diary(me, Emotion.JOY, LocalDateTime.of(2026, 11, 4, 20, 0)), CareType.LISTEN, "카드");

        mockMvc.perform(get("/diaries/" + d.getId()).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/diaries/" + d.getId()).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(get("/diaries/999999").header("Authorization", me.bearer())).andExpect(status().isNotFound());

        assertThat(diaryRepository.findById(d.getId())).isPresent();
        assertThat(careMessageRepository.count()).isEqualTo(6);
        assertThat(careSummaryRepository.count()).isEqualTo(1);
    }

    @Test
    void 삭제하면_대화와_카드가_함께_지워지고_쌓인_털은_유지된다() throws Exception {
        MySheep sheep = mySheepRepository.findByUserId(me.userId()).orElseThrow();
        sheep.addWool();
        sheep.addWool();
        mySheepRepository.saveAndFlush(sheep);
        Diary mine = completed(diary(me, Emotion.JOY, LocalDateTime.of(2026, 11, 4, 20, 0)), CareType.LISTEN, "카드");
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        Diary theirs = completed(diary(other, Emotion.JOY, LocalDateTime.of(2026, 11, 4, 20, 0)), CareType.LISTEN, "남의 카드");

        mockMvc.perform(delete("/diaries/" + mine.getId()).header("Authorization", me.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        assertThat(diaryRepository.findById(mine.getId())).isEmpty();
        assertThat(careMessageRepository.findByDiaryIdOrderByIdAsc(mine.getId())).isEmpty();
        assertThat(careSummaryRepository.findByDiaryId(mine.getId())).isEmpty();
        assertThat(mySheepRepository.findByUserId(me.userId()).orElseThrow().getWoolCount()).isEqualTo(2);
        // 다른 사람 기록은 그대로
        assertThat(diaryRepository.findById(theirs.getId())).isPresent();
        assertThat(careMessageRepository.findByDiaryIdOrderByIdAsc(theirs.getId())).hasSize(6);
        mockMvc.perform(get("/diaries/" + mine.getId()).header("Authorization", me.bearer())).andExpect(status().isNotFound());
        assertThat(diaryRepository.findById(theirs.getId()).orElseThrow().getCareStatus()).isEqualTo(CareStatus.COMPLETED);
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/diaries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/diaries/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/diaries/1")).andExpect(status().isUnauthorized());
    }
}
