package com.ganzithon.teolhanol.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class CareCompleteTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired CareSummaryRepository careSummaryRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean LlmClient llmClient;
    @MockitoBean AiResponseGuard guard;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("done@test.com", "마무리 목동");
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("  오늘도 애썼어요.  ");
        when(guard.guard(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void cleanUp() {
        careSummaryRepository.deleteAll();
        careMessageRepository.deleteAll();
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** 사용자 메시지 count개를 주고받은 케어 중 기록 */
    private Diary diaryWith(int userMessages, CareType type) {
        Diary diary = new Diary(userRepository.findById(me.userId()).orElseThrow(), Emotion.ANXIOUS);
        if (type != null) {
            diary.startCare(type);
        }
        diary = diaryRepository.save(diary);
        for (int i = 1; i <= userMessages; i++) {
            careMessageRepository.save(new CareMessage(diary, MessageRole.USER, "사용자 이야기 " + i));
            careMessageRepository.save(new CareMessage(diary, MessageRole.AI, "AI 답 " + i));
            diary.addUserMessage();
        }
        return diaryRepository.saveAndFlush(diary);
    }

    private void giveWool(int count) {
        MySheep sheep = mySheepRepository.findByUserId(me.userId()).orElseThrow();
        for (int i = 0; i < count; i++) {
            sheep.addWool();
        }
        mySheepRepository.saveAndFlush(sheep);
    }

    private ResultActions complete(Long diaryId, TestUsers.Login who) throws Exception {
        return mockMvc.perform(post("/diaries/" + diaryId + "/care/complete").header("Authorization", who.bearer()));
    }

    private int woolOf() {
        return mySheepRepository.findByUserId(me.userId()).orElseThrow().getWoolCount();
    }

    @Test
    void 사용자_메시지_3회면_카드가_저장되고_털_한_올이_쌓인다() throws Exception {
        Diary diary = diaryWith(3, CareType.COMFORT);

        complete(diary.getId(), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.careType").value("COMFORT"))
                .andExpect(jsonPath("$.data.summary.text").value("오늘도 애썼어요."))
                .andExpect(jsonPath("$.data.wool.added").value(1))
                .andExpect(jsonPath("$.data.wool.total").value(1))
                .andExpect(jsonPath("$.data.wool.stage").value(1))
                .andExpect(jsonPath("$.data.wool.stageUp").value(false));

        assertThat(diaryRepository.findById(diary.getId()).orElseThrow().getCareStatus()).isEqualTo(CareStatus.COMPLETED);
        assertThat(careSummaryRepository.findByDiaryId(diary.getId()).orElseThrow().getSummary()).isEqualTo("오늘도 애썼어요.");
        assertThat(woolOf()).isEqualTo(1);
    }

    @Test
    void 털이_3올째가_되면_단계가_올라_stageUp이_true() throws Exception {
        giveWool(2);
        Diary diary = diaryWith(3, CareType.LISTEN);

        complete(diary.getId(), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wool.total").value(3))
                .andExpect(jsonPath("$.data.wool.stage").value(2))
                .andExpect(jsonPath("$.data.wool.stageUp").value(true));
    }

    @Test
    void 위기_신호가_나온_대화는_카드만_남기고_털은_쌓지_않는다() throws Exception {
        Diary diary = diaryWith(4, CareType.LISTEN);
        diary.applyRisk(RiskLevel.CRISIS);
        diaryRepository.saveAndFlush(diary);

        complete(diary.getId(), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.text").value("오늘도 애썼어요."))
                .andExpect(jsonPath("$.data.wool.added").value(0))
                .andExpect(jsonPath("$.data.wool.total").value(0))
                .andExpect(jsonPath("$.data.wool.stageUp").value(false));

        assertThat(diaryRepository.findById(diary.getId()).orElseThrow().getCareStatus()).isEqualTo(CareStatus.COMPLETED);
        assertThat(careSummaryRepository.count()).isEqualTo(1);
        assertThat(woolOf()).isZero();
    }

    @Test
    void 사용자_메시지가_3회_미만이거나_케어를_시작하지_않았으면_409() throws Exception {
        Diary few = diaryWith(2, CareType.LISTEN);
        Diary none = diaryWith(0, null);

        complete(few.getId(), me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMON_409"));
        complete(none.getId(), me).andExpect(status().isConflict());

        verify(llmClient, never()).complete(any());
        assertThat(careSummaryRepository.count()).isZero();
        assertThat(woolOf()).isZero();
        assertThat(diaryRepository.findById(few.getId()).orElseThrow().getCareStatus()).isEqualTo(CareStatus.IN_PROGRESS);
    }

    @Test
    void 같은_기록을_다시_마무리해도_털은_한_번만_오르고_저장된_카드를_돌려준다() throws Exception {
        Diary diary = diaryWith(3, CareType.COMFORT);
        complete(diary.getId(), me).andExpect(jsonPath("$.data.wool.added").value(1));
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("다른 카드");

        complete(diary.getId(), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.text").value("오늘도 애썼어요."))
                .andExpect(jsonPath("$.data.wool.added").value(0))
                .andExpect(jsonPath("$.data.wool.total").value(1));

        assertThat(woolOf()).isEqualTo(1);
        assertThat(careSummaryRepository.count()).isEqualTo(1);
    }

    @Test
    void 동시에_두_번_보내도_털은_한_번만_오른다() throws Exception {
        Diary diary = diaryWith(3, CareType.LISTEN);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> complete(diary.getId(), me).andReturn().getResponse().getStatus();
            Future<Integer> a = pool.submit(call);
            Future<Integer> b = pool.submit(call);
            assertThat(List.of(a.get(), b.get())).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(woolOf()).isEqualTo(1);
        assertThat(careSummaryRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(CareType.class)
    void 케어_방식별_카드_프롬프트와_대화_원문이_LLM에_전달된다(CareType type) throws Exception {
        Diary diary = diaryWith(3, type);

        complete(diary.getId(), me).andExpect(status().isOk());

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(captor.capture());
        assertThat(captor.getValue().systemPrompt()).contains("카드 종류:").contains("불안");
        String expected = switch (type) {
            case LISTEN -> "털어놓은 마음";
            case COMFORT -> "위로의 말";
            case ORGANIZE -> "정리된 생각 세 줄";
            case SOLVE -> "해볼 행동";
        };
        assertThat(captor.getValue().systemPrompt()).contains(expected);
        assertThat(captor.getValue().messages().get(0).content())
                .contains("[사용자] 사용자 이야기 1").contains("[털 한 올] AI 답 3");
    }

    @Test
    void 카드의_줄_끝_공백은_정리해서_저장한다() throws Exception {
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("1. 첫째  \n2. 둘째 \n");
        Diary diary = diaryWith(3, CareType.SOLVE);

        complete(diary.getId(), me).andExpect(jsonPath("$.data.summary.text").value("1. 첫째\n2. 둘째"));
    }

    @Test
    void LLM이_실패해도_고정_카드로_마무리하고_털은_쌓인다() throws Exception {
        when(llmClient.complete(any(LlmRequest.class)))
                .thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));
        Diary diary = diaryWith(3, CareType.ORGANIZE);

        complete(diary.getId(), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.text").value(org.hamcrest.Matchers.containsString("걸리는 것:")))
                .andExpect(jsonPath("$.data.wool.added").value(1));
        assertThat(woolOf()).isEqualTo(1);
    }

    @Test
    void 안전_가드가_카드를_바꾸면_바뀐_문장이_저장된다() throws Exception {
        when(guard.guard(any())).thenReturn("가드를 거친 카드");
        Diary diary = diaryWith(3, CareType.COMFORT);

        complete(diary.getId(), me).andExpect(jsonPath("$.data.summary.text").value("가드를 거친 카드"));
        assertThat(careSummaryRepository.findByDiaryId(diary.getId()).orElseThrow().getSummary()).isEqualTo("가드를 거친 카드");
    }

    @Test
    void 남의_기록은_404_토큰이_없으면_401() throws Exception {
        Diary diary = diaryWith(3, CareType.LISTEN);
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");

        complete(diary.getId(), other).andExpect(status().isNotFound());
        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/complete")).andExpect(status().isUnauthorized());
        assertThat(woolOf()).isZero();
    }
}
