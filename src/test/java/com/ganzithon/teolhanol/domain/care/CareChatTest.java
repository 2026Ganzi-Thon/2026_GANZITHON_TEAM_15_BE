package com.ganzithon.teolhanol.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.safety.repository.CrisisLogRepository;
import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class CareChatTest {

    /** SSE 이벤트 하나 */
    record Sse(String name, String data) {
    }

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired CrisisLogRepository crisisLogRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean LlmClient llmClient;
    @MockitoBean AiResponseGuard guard;

    TestUsers.Login me;
    Diary diary;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        me = testUsers.signIn("chat@test.com", "대화 목동");
        diary = diaryRepository.save(new Diary(userRepository.findById(me.userId()).orElseThrow(), Emotion.TIRED));
        // 분류 LLM은 안전, 가드는 그대로 통과
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> verdict(inv.getArgument(1), "safe", "NONE"));
        when(guard.guard(any())).thenAnswer(inv -> inv.getArgument(0));
        replyWith("많이 힘들었겠어요.", " 더 들려줄래요?");
    }

    @AfterEach
    void cleanUp() {
        careMessageRepository.deleteAll();
        crisisLogRepository.deleteAll();
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    @SuppressWarnings("unchecked")
    private static <T> T verdict(Class<T> type, String level, String reason) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (T) constructor.newInstance(level, reason);
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

    private List<Sse> send(Long diaryId, String content, TestUsers.Login who) throws Exception {
        MvcResult started = mockMvc.perform(post("/diaries/" + diaryId + "/care/messages")
                        .header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        long deadline = System.currentTimeMillis() + 10_000;
        String body;
        do {
            Thread.sleep(20);
            body = started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } while (!(body.contains("event:done") || body.contains("event:error")) && System.currentTimeMillis() < deadline);
        return parse(body);
    }

    private static List<Sse> parse(String body) {
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
        return events;
    }

    private static List<String> names(List<Sse> events) {
        return events.stream().map(Sse::name).toList();
    }

    private Diary reload() {
        return diaryRepository.findById(diary.getId()).orElseThrow();
    }

    private void startCare(CareType type) {
        Diary d = reload();
        d.startCare(type);
        diaryRepository.saveAndFlush(d);
    }

    @Test
    void 응답은_progress_delta_done_순서로_조각을_차례로_내려준다() throws Exception {
        startCare(CareType.COMFORT);

        List<Sse> events = send(diary.getId(), "오늘 너무 지쳤어요", me);

        assertThat(names(events)).containsExactly("progress", "delta", "delta", "done");
        assertThat(events.get(0).data()).contains("\"userMessageCount\":1").contains("\"ready\":false");
        assertThat(events.get(1).data()).contains("많이 힘들었겠어요.");
        assertThat(events.get(2).data()).contains("더 들려줄래요?");

        List<CareMessage> saved = careMessageRepository.findByDiaryIdOrderByIdAsc(diary.getId());
        assertThat(saved).extracting(CareMessage::getRole).containsExactly(MessageRole.USER, MessageRole.AI);
        assertThat(saved.get(0).getContent()).isEqualTo("오늘 너무 지쳤어요");
        assertThat(saved.get(1).getContent()).isEqualTo("많이 힘들었겠어요. 더 들려줄래요?");
        assertThat(reload().getUserMessageCount()).isEqualTo(1);
    }

    @Test
    void 이전_대화와_케어_방식_프롬프트가_LLM에_전달된다() throws Exception {
        startCare(CareType.ORGANIZE);
        careMessageRepository.save(new CareMessage(reload(), MessageRole.AI, "같이 정리해 봐요. 무엇이 걸리나요?"));

        send(diary.getId(), "발표가 걱정돼요", me);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).stream(captor.capture(), any());
        LlmRequest sent = captor.getValue();
        assertThat(sent.systemPrompt()).contains("케어 방식: 생각 정리하기").contains("지침");
        assertThat(sent.messages()).extracting(m -> m.content())
                .containsExactly("안녕하세요.", "같이 정리해 봐요. 무엇이 걸리나요?", "발표가 걱정돼요");
    }

    @Test
    void 세_번째_메시지부터_ready가_true이고_진행_상태가_맞다() throws Exception {
        startCare(CareType.LISTEN);

        String[] ready = new String[3];
        for (int i = 0; i < 3; i++) {
            ready[i] = send(diary.getId(), "이야기 " + i, me).get(0).data();
        }

        assertThat(ready[0]).contains("\"userMessageCount\":1").contains("\"ready\":false");
        assertThat(ready[1]).contains("\"userMessageCount\":2").contains("\"ready\":false");
        assertThat(ready[2]).contains("\"userMessageCount\":3").contains("\"ready\":true");
    }

    @Test
    void 위기_표현이면_AI_응답_대신_전환_이벤트를_내리고_기록한다() throws Exception {
        startCare(CareType.LISTEN);

        List<Sse> events = send(diary.getId(), "더 이상 살기 싫어요", me);

        assertThat(names(events)).containsExactly("progress", "crisis", "done");
        assertThat(events.get(1).data()).contains("잠깐 멈출게요").contains("\"call\"").contains("\"continue\"");
        verify(llmClient, never()).stream(any(), any());

        Diary saved = reload();
        assertThat(saved.isCrisisDetected()).isTrue();
        assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.CRISIS);
        assertThat(userRepository.findById(me.userId()).orElseThrow().getCrisisBannerUntil()).isNotNull();
        assertThat(crisisLogRepository.count()).isEqualTo(1);
        assertThat(careMessageRepository.findFirstByDiaryIdOrderByIdDesc(diary.getId()).orElseThrow().getContent())
                .startsWith("잠깐 멈출게요");
    }

    @Test
    void 위기_뒤_계속_이야기하면_첫_응답이_고마워요로_시작하도록_지시하고_대화는_이어진다() throws Exception {
        startCare(CareType.LISTEN);
        send(diary.getId(), "더 이상 살기 싫어요", me);

        List<Sse> events = send(diary.getId(), "그냥 요즘 많이 지쳤어요", me);

        assertThat(names(events)).containsExactly("progress", "delta", "delta", "done");
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).stream(captor.capture(), any());
        assertThat(captor.getValue().systemPrompt()).contains("말해 줘서 고마워요. 천천히 이어서 말해 줘도 돼요");
        assertThat(reload().isCrisisDetected()).isTrue();

        // 그다음 턴부터는 더 이상 같은 지시를 붙이지 않는다
        send(diary.getId(), "조금 나아졌어요", me);
        verify(llmClient, times(2)).stream(captor.capture(), any());
        assertThat(captor.getValue().systemPrompt()).doesNotContain("말해 줘서 고마워요");
    }

    @Test
    void 사용자_메시지는_열_번까지이고_마지막에는_마무리를_제안하도록_지시한다() throws Exception {
        startCare(CareType.LISTEN);
        for (int i = 1; i <= 10; i++) {
            List<Sse> events = send(diary.getId(), "이야기 " + i, me);
            assertThat(events.get(0).data()).contains("\"userMessageCount\":" + i)
                    .contains(i == 10 ? "\"limitReached\":true" : "\"limitReached\":false");
        }
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, times(10)).stream(captor.capture(), any());
        assertThat(captor.getAllValues().get(8).systemPrompt()).doesNotContain("마지막 대화");
        assertThat(captor.getAllValues().get(9).systemPrompt()).contains("마지막 대화");

        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/messages").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"하나 더\"}"))
                .andExpect(status().isConflict());
        assertThat(reload().getUserMessageCount()).isEqualTo(10);
    }

    @Test
    void 케어_방식을_고르지_않고_바로_말하면_들어주기로_시작한다() throws Exception {
        List<Sse> events = send(diary.getId(), "그냥 말부터 할게요", me);

        assertThat(names(events)).containsExactly("progress", "delta", "delta", "done");
        Diary saved = reload();
        assertThat(saved.getCareType()).isEqualTo(CareType.LISTEN);
        assertThat(saved.getCareStatus()).isEqualTo(CareStatus.IN_PROGRESS);
    }

    @Test
    void 응답이_실패하면_안내_후_다시_시도해_성공하면_이어서_내려준다() throws Exception {
        startCare(CareType.LISTEN);
        int[] calls = {0};
        when(llmClient.stream(any(LlmRequest.class), any())).thenAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(1);
            if (++calls[0] == 1) {
                onDelta.accept("반쯤 오다가");
                throw new LlmException(LlmException.Type.TIMEOUT, "timeout");
            }
            onDelta.accept("다시 말씀드릴게요.");
            return "다시 말씀드릴게요.";
        });

        List<Sse> events = send(diary.getId(), "이야기해요", me);

        assertThat(names(events)).containsExactly("progress", "delta", "reset", "notice", "delta", "done");
        assertThat(events.get(3).data()).contains("잠시 숨을 고르고 있어요");
        assertThat(careMessageRepository.findFirstByDiaryIdOrderByIdDesc(diary.getId()).orElseThrow().getContent())
                .isEqualTo("다시 말씀드릴게요.");
    }

    @Test
    void 세_번_실패하면_error를_내리고_사용자_말은_저장해_둔다() throws Exception {
        startCare(CareType.LISTEN);
        when(llmClient.stream(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.UNAVAILABLE, "down"));

        List<Sse> events = send(diary.getId(), "이야기해요", me);

        assertThat(names(events)).containsExactly("progress", "notice", "notice", "error");
        assertThat(events.get(3).data()).contains("CARE_AI_UNAVAILABLE").contains("잘 저장해 두었어요");
        verify(llmClient, times(3)).stream(any(), any());
        assertThat(careMessageRepository.findByDiaryIdOrderByIdAsc(diary.getId()))
                .extracting(CareMessage::getRole).containsExactly(MessageRole.USER);
        assertThat(reload().getUserMessageCount()).isEqualTo(1);

        // 나중에 다시 보내면 이어서 대화할 수 있다
        replyWith("다시 왔네요. 반가워요.");
        assertThat(names(send(diary.getId(), "다시 왔어요", me))).containsExactly("progress", "delta", "done");
        assertThat(reload().getUserMessageCount()).isEqualTo(2);
    }

    @Test
    void 안전_가드가_응답을_바꾸면_replace를_내리고_바뀐_문장을_저장한다() throws Exception {
        startCare(CareType.LISTEN);
        when(guard.guard(any())).thenReturn("조금 더 천천히 이야기해 볼까요?");

        List<Sse> events = send(diary.getId(), "이야기해요", me);

        assertThat(names(events)).containsExactly("progress", "delta", "delta", "replace", "done");
        assertThat(careMessageRepository.findFirstByDiaryIdOrderByIdDesc(diary.getId()).orElseThrow().getContent())
                .isEqualTo("조금 더 천천히 이야기해 볼까요?");
    }

    @Test
    void 남의_기록은_404_마무리한_기록은_409_빈_내용은_400_토큰_없으면_401() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/messages").header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/messages").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}"))
                .andExpect(status().isUnauthorized());

        Diary d = reload();
        d.startCare(CareType.LISTEN);
        setCompleted(d);
        mockMvc.perform(post("/diaries/" + diary.getId() + "/care/messages").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMON_409"));
        assertThat(careMessageRepository.count()).isZero();
    }

    private void setCompleted(Diary d) throws Exception {
        var f = Diary.class.getDeclaredField("careStatus");
        f.setAccessible(true);
        f.set(d, CareStatus.COMPLETED);
        diaryRepository.saveAndFlush(d);
    }
}
