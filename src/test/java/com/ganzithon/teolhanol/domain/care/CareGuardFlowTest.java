package com.ganzithon.teolhanol.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.safety.repository.CrisisLogRepository;
import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 실제 안전 가드(패턴 사전)를 거치는 케어 흐름: 스트리밍 응답, 첫 마디, 마무리 카드 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class CareGuardFlowTest {

    record Sse(String name, String data) {
    }

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired AiResponseGuard guard;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired CareSummaryRepository careSummaryRepository;
    @Autowired CrisisLogRepository crisisLogRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean LlmClient llmClient;

    TestUsers.Login me;
    Diary diary;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        me = testUsers.signIn("guard@test.com", "가드 목동");
        diary = new Diary(userRepository.findById(me.userId()).orElseThrow(), Emotion.TIRED);
        diary.startCare(CareType.COMFORT);
        diary = diaryRepository.save(diary);
        // 위험도 분류 LLM은 안전으로 답한다
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> {
            var c = ((Class<?>) inv.getArgument(1)).getDeclaredConstructors()[0];
            c.setAccessible(true);
            return c.newInstance("safe", "NONE");
        });
    }

    @AfterEach
    void cleanUp() {
        careSummaryRepository.deleteAll();
        careMessageRepository.deleteAll();
        crisisLogRepository.deleteAll();
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void replyWith(String... chunks) {
        when(llmClient.stream(any(LlmRequest.class), any())).thenAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(1);
            StringBuilder all = new StringBuilder();
            for (String chunk : chunks) {
                onDelta.accept(chunk);
                all.append(chunk);
            }
            return all.toString();
        });
    }

    private record Chat(List<Sse> events, String body) {
    }

    private Chat chat(String content) throws Exception {
        MvcResult started = mockMvc.perform(post("/diaries/" + diary.getId() + "/care/messages")
                        .header("Authorization", me.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        long deadline = System.currentTimeMillis() + 10_000;
        String body;
        do {
            Thread.sleep(20);
            body = started.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } while (!(body.contains("event:done") || body.contains("event:error")) && System.currentTimeMillis() < deadline);
        List<Sse> events = new ArrayList<>();
        for (String block : body.split("\n\n")) {
            String name = null;
            String data = "";
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    data = line.substring(5).strip();
                }
            }
            if (name != null) {
                events.add(new Sse(name, data));
            }
        }
        return new Chat(events, body);
    }

    private String lastAiMessage() {
        return careMessageRepository.findByDiaryIdOrderByIdAsc(diary.getId()).stream()
                .filter(m -> m.getRole() == MessageRole.AI).reduce((a, b) -> b).orElseThrow().getContent();
    }

    @Test
    void 정상_응답은_문장_단위로_내려간다() throws Exception {
        replyWith("많이 힘들었겠어요.", " 어떤 일이 있었는지", " 들려줄래요?");

        Chat chat = chat("오늘 지쳤어요");

        assertThat(chat.events().stream().map(Sse::name)).containsExactly("progress", "delta", "delta", "done");
        assertThat(chat.events().get(1).data()).contains("많이 힘들었겠어요.");
        assertThat(chat.events().get(2).data()).contains("어떤 일이 있었는지 들려줄래요?");
    }

    @Test
    void 중간_문장에서_금지_패턴이_걸리면_그_문장은_내보내지_않고_안전_문장으로_바꾼다() throws Exception {
        replyWith("많이 힘들었겠어요.", " 아무래도 우울증이에요.", " 푹 쉬세요.");

        Chat chat = chat("오늘 지쳤어요");

        assertThat(chat.events().stream().map(Sse::name)).containsExactly("progress", "delta", "replace", "done");
        assertThat(chat.body()).doesNotContain("우울증");
        assertThat(chat.events().get(2).data()).contains("의사나 전문 상담사");
        assertThat(lastAiMessage()).isEqualTo(guard.safeReply());
    }

    @Test
    void 첫_문장부터_걸리면_안전_문장을_바로_내려보낸다() throws Exception {
        replyWith("약을 끊어 보세요.", " 그러면 나아져요.");

        Chat chat = chat("약이 힘들어요");

        assertThat(chat.events().stream().map(Sse::name)).containsExactly("progress", "delta", "done");
        assertThat(chat.events().get(1).data()).contains("의사나 전문 상담사");
        assertThat(chat.body()).doesNotContain("끊어");
        assertThat(lastAiMessage()).isEqualTo(guard.safeReply());
    }

    @Test
    void 패턴이_토큰_단위로_쪼개져_와도_걸린다() throws Exception {
        replyWith("그건 ", "우울", "증이", "에요", ".");

        Chat chat = chat("요즘 기운이 없어요");

        assertThat(chat.body()).doesNotContain("우울");
        assertThat(lastAiMessage()).isEqualTo(guard.safeReply());
    }

    @Test
    void 줄바꿈으로_갈라진_패턴도_걸린다() throws Exception {
        replyWith("약을\n", "끊어 보세요.");

        Chat chat = chat("약이 힘들어요");

        assertThat(chat.body()).doesNotContain("끊어");
        assertThat(lastAiMessage()).isEqualTo(guard.safeReply());
    }

    @Test
    void 케어_첫_마디에_금지_패턴이_있으면_방식별_고정_문장으로_바꾼다() throws Exception {
        Diary fresh = diaryRepository.save(new Diary(userRepository.findById(me.userId()).orElseThrow(), Emotion.ANXIOUS));
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("불안장애입니다. 걱정 마세요.");

        mockMvc.perform(post("/diaries/" + fresh.getId() + "/care").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"careType\":\"COMFORT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("애썼어요")));
    }

    @Test
    void 마무리_카드에_금지_패턴이_있으면_방식별_고정_카드로_바꾸고_털은_그대로_쌓인다() throws Exception {
        for (int i = 1; i <= 3; i++) {
            careMessageRepository.save(new CareMessage(diary, MessageRole.USER, "이야기 " + i));
            diary.addUserMessage();
        }
        diaryRepository.saveAndFlush(diary);
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("당신은 우울증이에요. 약을 끊어 보세요.");

        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/complete").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.text").value(org.hamcrest.Matchers.containsString("애썼어요")))
                .andExpect(jsonPath("$.data.wool.added").value(1));
        assertThat(careSummaryRepository.findByDiaryId(diary.getId()).orElseThrow().getSummary()).doesNotContain("우울증");
    }
}
