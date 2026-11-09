package com.ganzithon.teolhanol.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class CareStartTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean LlmClient llmClient;

    TestUsers.Login me;
    Diary diary;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("care@test.com", "케어 목동");
        diary = diaryRepository.save(new Diary(userRepository.findById(me.userId()).orElseThrow(), Emotion.TIRED));
    }

    @AfterEach
    void cleanUp() {
        careMessageRepository.deleteAll();
        diaryRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions start(Long diaryId, String type, TestUsers.Login who) throws Exception {
        return mockMvc.perform(post("/diaries/" + diaryId + "/care").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"careType\":\"" + type + "\"}"));
    }

    @ParameterizedTest
    @EnumSource(CareType.class)
    void 네_가지_방식_모두_방식별_프롬프트로_첫_메시지를_만든다(CareType type) throws Exception {
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("  많이 지쳤군요. 어떤 일이 있었어요?  ");

        start(diary.getId(), type.name(), me).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.careType").value(type.name()))
                .andExpect(jsonPath("$.data.message").value("많이 지쳤군요. 어떤 일이 있었어요?"));

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        Mockito.verify(llmClient).complete(captor.capture());
        assertThat(captor.getValue().systemPrompt()).contains("케어 방식: " + type.getLabel());
        assertThat(captor.getValue().messages().get(0).content()).contains("지침").contains(type.getLabel());
    }

    @Test
    void 시작하면_방식과_진행_상태가_저장되고_AI_첫_메시지가_대화에_남는다() throws Exception {
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("편하게 말해 주세요.");

        start(diary.getId(), "LISTEN", me).andExpect(status().isCreated());

        Diary saved = diaryRepository.findById(diary.getId()).orElseThrow();
        assertThat(saved.getCareType()).isEqualTo(CareType.LISTEN);
        assertThat(saved.getCareStatus()).isEqualTo(CareStatus.IN_PROGRESS);
        CareMessage message = careMessageRepository.findAll().get(0);
        assertThat(message.getRole()).isEqualTo(MessageRole.AI);
        assertThat(message.getContent()).isEqualTo("편하게 말해 주세요.");
    }

    @Test
    void 이미_시작한_기록은_409이고_메시지가_늘지_않는다() throws Exception {
        when(llmClient.complete(any(LlmRequest.class))).thenReturn("첫 메시지");
        start(diary.getId(), "LISTEN", me).andExpect(status().isCreated());

        start(diary.getId(), "COMFORT", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMON_409"));

        assertThat(careMessageRepository.count()).isEqualTo(1);
        assertThat(diaryRepository.findById(diary.getId()).orElseThrow().getCareType()).isEqualTo(CareType.LISTEN);
    }

    @Test
    void 끝난_기록도_409() throws Exception {
        var done = diaryRepository.findById(diary.getId()).orElseThrow();
        done.startCare(CareType.SOLVE);
        diaryRepository.saveAndFlush(done);

        start(diary.getId(), "LISTEN", me).andExpect(status().isConflict());
    }

    @Test
    void 남의_기록이나_없는_기록은_404() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");

        start(diary.getId(), "LISTEN", other).andExpect(status().isNotFound());
        start(999999L, "LISTEN", me).andExpect(status().isNotFound());
        assertThat(diaryRepository.findById(diary.getId()).orElseThrow().getCareStatus()).isEqualTo(CareStatus.NONE);
    }

    @Test
    void LLM이_실패하면_방식별_고정_문장으로_시작한다() throws Exception {
        when(llmClient.complete(any(LlmRequest.class)))
                .thenThrow(new LlmException(LlmException.Type.UNAVAILABLE, "no key"));

        start(diary.getId(), "COMFORT", me).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("애썼어요")));

        assertThat(careMessageRepository.count()).isEqualTo(1);
        assertThat(diaryRepository.findById(diary.getId()).orElseThrow().getCareStatus()).isEqualTo(CareStatus.IN_PROGRESS);
    }

    @Test
    void 케어_방식이_없거나_잘못되면_400() throws Exception {
        start(diary.getId(), "CHEER", me).andExpect(status().isBadRequest());
        mockMvc.perform(post("/diaries/" + diary.getId() + "/care").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/diaries/1/care").contentType(MediaType.APPLICATION_JSON).content("{\"careType\":\"LISTEN\"}"))
                .andExpect(status().isUnauthorized());
    }
}
