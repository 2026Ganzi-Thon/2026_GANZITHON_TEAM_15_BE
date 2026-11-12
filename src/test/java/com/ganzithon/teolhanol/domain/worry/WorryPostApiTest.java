package com.ganzithon.teolhanol.domain.worry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.safety.repository.CrisisLogRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class WorryPostApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired CrisisLogRepository crisisLogRepository;
    @MockitoBean LlmClient llmClient;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("worry-post@test.com", "고민 목동");
        llmReturns("safe", "NONE");
    }

    @AfterEach
    void cleanUp() {
        crisisLogRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void llmReturns(String level, String reason) {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenAnswer(inv -> newVerdict(inv.getArgument(1), level, reason));
    }

    @SuppressWarnings("unchecked")
    private static <T> T newVerdict(Class<T> type, String level, String reason) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (T) constructor.newInstance(level, reason);
    }

    private ResultActions postWorry(String farmCode, String content) throws Exception {
        return postWorry(me, farmCode, content);
    }

    private ResultActions postWorry(TestUsers.Login who, String farmCode, String content) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("content", content));
        return mockMvc.perform(post("/farms/" + farmCode + "/sheep").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Farm farm(String code) {
        return farmRepository.findByCode(code).orElseThrow();
    }

    private User reloadMe() {
        return userRepository.findById(me.userId()).orElseThrow();
    }

    private WorrySheep onlySheep() {
        List<WorrySheep> all = worrySheepRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    // ---- safe / caution ----

    @Test
    void 안전한_고민은_털_없는_양_1단계로_등록된다() throws Exception {
        postWorry("CAREER", "  진로를 정하지 못해서 매일 불안해요.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.sheepId").isNumber())
                .andExpect(jsonPath("$.data.farm.code").value("CAREER"))
                .andExpect(jsonPath("$.data.farm.name").isNotEmpty())
                .andExpect(jsonPath("$.data.farm.tabName").value("진로"))
                .andExpect(jsonPath("$.data.stage").value(1))
                .andExpect(jsonPath("$.data.stageName").value("털 깎은 양"))
                .andExpect(jsonPath("$.data.content").value("진로를 정하지 못해서 매일 불안해요."))
                .andExpect(jsonPath("$.data.woolCount").doesNotExist())
                .andExpect(jsonPath("$.data.riskLevel").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.crisisHelp").doesNotExist());

        WorrySheep sheep = onlySheep();
        assertThat(sheep.getContent()).isEqualTo("진로를 정하지 못해서 매일 불안해요.");
        assertThat(sheep.getRiskLevel()).isEqualTo(RiskLevel.SAFE);
        assertThat(sheep.getStage()).isEqualTo(1);
        assertThat(sheep.getWoolCount()).isZero();
        assertThat(sheep.getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
        assertThat(sheep.getFarm().getId()).isEqualTo(farm("CAREER").getId());
        assertThat(sheep.getUser().getId()).isEqualTo(me.userId());
        assertThat(reloadMe().getCrisisBannerUntil()).isNull();
    }

    @Test
    void 주의로_분류되면_보호_양으로_등록되고_응답에는_드러나지_않는다() throws Exception {
        llmReturns("caution", "HOPELESSNESS");

        postWorry("BURNOUT", "요즘은 아무것도 의미 없는 것 같아요.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.stage").value(1))
                .andExpect(jsonPath("$.data.riskLevel").doesNotExist())
                .andExpect(jsonPath("$.data.protectedSheep").doesNotExist());

        assertThat(onlySheep().getRiskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(reloadMe().getCrisisBannerUntil()).isNull();
    }

    // ---- crisis ----

    @Test
    void 위기면_양을_만들지_않고_상담_연결_정보를_주며_배너가_켜진다() throws Exception {
        postWorry("SELF", "요즘 너무 힘들어서 죽고 싶다는 생각이 들어요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CRISIS"))
                .andExpect(jsonPath("$.data.sheepId").doesNotExist())
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.crisisHelp.title").value("전화로 이야기해 볼래요?"))
                .andExpect(jsonPath("$.data.crisisHelp.contacts[0].phone").value("109"))
                .andExpect(jsonPath("$.data.crisisHelp.contacts[1].phone").value("1577-0199"))
                .andExpect(jsonPath("$.data.crisisHelp.emergency.phone").value("119"));

        assertThat(worrySheepRepository.count()).isZero();
        LocalDateTime until = reloadMe().getCrisisBannerUntil();
        assertThat(until).isAfter(LocalDateTime.now().plusHours(23)).isBefore(LocalDateTime.now().plusHours(25));
        assertThat(crisisLogRepository.count()).isEqualTo(1);
    }

    @Test
    void LLM이_위기로_보면_키워드가_없어도_양을_만들지_않는다() throws Exception {
        llmReturns("crisis", "SUICIDAL_IDEATION");

        postWorry("RELATIONSHIP", "모두에게 짐이 되느니 조용히 떠나는 게 낫겠어요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CRISIS"));

        assertThat(worrySheepRepository.count()).isZero();
        assertThat(reloadMe().getCrisisBannerUntil()).isNotNull();
    }

    // ---- 분류 API 실패 ----

    @Test
    void 분류_API가_실패하면_키워드_1차_판정_후_보호_양으로_등록된다() throws Exception {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));

        postWorry("CAREER", "취업 준비가 길어져서 지쳐요.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        assertThat(onlySheep().getRiskLevel()).isEqualTo(RiskLevel.CAUTION);
    }

    @Test
    void 분류_API가_실패해도_위기_키워드가_있으면_양을_만들지_않는다() throws Exception {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.UNAVAILABLE, "down"));

        postWorry("CAREER", "다 끝내고 싶어요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CRISIS"));

        assertThat(worrySheepRepository.count()).isZero();
        assertThat(reloadMe().getCrisisBannerUntil()).isNotNull();
    }

    // ---- 개인정보 ----

    @Test
    void 개인정보가_있으면_저장하지_않고_수정을_부탁한다() throws Exception {
        postWorry("RELATIONSHIP", "같이 이야기해요 010-1234-5678 로 연락 주세요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PII_REJECTED"))
                .andExpect(jsonPath("$.data.message").value(
                        "전화번호나 계정 아이디 같은 연락처는 남기지 않도록 해 주세요. 그 부분만 빼고 다시 적어 주시면 좋아요."))
                .andExpect(jsonPath("$.data.sheepId").doesNotExist());

        assertThat(worrySheepRepository.count()).isZero();
        assertThat(crisisLogRepository.count()).isZero();
        verify(llmClient, never()).completeJson(any(LlmRequest.class), any());
    }

    @Test
    void 이메일도_개인정보로_막는다() throws Exception {
        postWorry("SELF", "메일 주세요 wool@example.com")
                .andExpect(jsonPath("$.data.status").value("PII_REJECTED"));
        assertThat(worrySheepRepository.count()).isZero();
    }

    // ---- 하루 한도 ----

    @Test
    void 하루_세_마리까지만_만들_수_있다() throws Exception {
        postWorry("CAREER", "첫 번째 고민").andExpect(status().isCreated());
        postWorry("BURNOUT", "두 번째 고민").andExpect(status().isCreated());
        postWorry("SELF", "세 번째 고민").andExpect(status().isCreated());

        postWorry("CAREER", "네 번째 고민")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("WORRY_DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value("오늘은 고민을 충분히 들려주셨어요. 내일 다시 들려주세요."));
        assertThat(worrySheepRepository.count()).isEqualTo(3);
    }

    @Test
    void 숨김_졸업한_양도_오늘_만든_수에_들어간다() throws Exception {
        postWorry("CAREER", "첫 번째 고민").andExpect(status().isCreated());
        postWorry("CAREER", "두 번째 고민").andExpect(status().isCreated());
        List<WorrySheep> made = worrySheepRepository.findAll();
        made.get(0).graduate();
        made.get(1).hide();
        worrySheepRepository.saveAllAndFlush(made);

        postWorry("CAREER", "세 번째 고민").andExpect(status().isCreated());
        postWorry("CAREER", "네 번째 고민").andExpect(status().isTooManyRequests());
    }

    @Test
    void 위기나_개인정보로_거절된_시도는_한도에_세지_않는다() throws Exception {
        postWorry("SELF", "죽고 싶어요").andExpect(jsonPath("$.data.status").value("CRISIS"));
        postWorry("SELF", "010-1234-5678").andExpect(jsonPath("$.data.status").value("PII_REJECTED"));

        postWorry("CAREER", "첫 번째 고민").andExpect(status().isCreated());
        postWorry("CAREER", "두 번째 고민").andExpect(status().isCreated());
        postWorry("CAREER", "세 번째 고민").andExpect(status().isCreated());
        assertThat(worrySheepRepository.count()).isEqualTo(3);
    }

    @Test
    void 한도를_넘으면_분류를_부르지_않는다() throws Exception {
        for (int i = 0; i < 3; i++) {
            postWorry("CAREER", "고민 " + i).andExpect(status().isCreated());
        }
        org.mockito.Mockito.clearInvocations(llmClient);

        postWorry("CAREER", "죽고 싶어요").andExpect(status().isTooManyRequests());
        verify(llmClient, never()).completeJson(any(LlmRequest.class), any());
    }

    @Test
    void 다른_사람이_만든_양은_내_한도에_세지_않는다() throws Exception {
        TestUsers.Login other = testUsers.signIn("worry-other@test.com", "다른 목동");
        for (int i = 0; i < 3; i++) {
            postWorry(other, "CAREER", "다른 사람 고민 " + i).andExpect(status().isCreated());
        }

        postWorry("CAREER", "내 고민").andExpect(status().isCreated());
    }

    @Test
    void 동시에_올려도_하루_한도를_넘지_않는다() throws Exception {
        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                String content = "동시에 올리는 고민 " + i;
                Callable<Integer> call = () -> {
                    start.await();
                    return postWorry("CAREER", content).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
            assertThat(statuses).filteredOn(s -> s == 429).hasSize(requests - 3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(worrySheepRepository.count()).isEqualTo(3);
    }

    // ---- 길이 ----

    @Test
    void 비어_있거나_공백뿐이거나_빠지면_400() throws Exception {
        postWorry("CAREER", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"))
                .andExpect(jsonPath("$.error.message").value("고민은 1자에서 200자 사이로 적어 주세요."));
        postWorry("CAREER", "   ").andExpect(status().isBadRequest());
        mockMvc.perform(post("/farms/CAREER/sheep").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(worrySheepRepository.count()).isZero();
    }

    @Test
    void 이백자는_되고_이백일자는_400() throws Exception {
        postWorry("CAREER", "가".repeat(201)).andExpect(status().isBadRequest());
        assertThat(worrySheepRepository.count()).isZero();

        postWorry("CAREER", " " + "가".repeat(200) + " ").andExpect(status().isCreated());
        assertThat(onlySheep().getContent()).hasSize(200);
    }

    // ---- 목장 ----

    @Test
    void 목장_코드는_대소문자를_가리지_않는다() throws Exception {
        postWorry("burnout", "번아웃이 와서 쉬고 싶어요.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.farm.code").value("BURNOUT"));
    }

    @Test
    void 없는_목장이면_404() throws Exception {
        postWorry("HOBBY", "취미 고민이에요.")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FARM_NOT_FOUND"));
        assertThat(worrySheepRepository.count()).isZero();
    }

    @Test
    void 전체_탭으로는_올릴_수_없다() throws Exception {
        postWorry("all", "어디에 올려야 할지 모르겠어요.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("고민을 올릴 목장을 하나 골라 주세요."));
        postWorry("ALL", "어디에 올려야 할지 모르겠어요.").andExpect(status().isBadRequest());
        assertThat(worrySheepRepository.count()).isZero();
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/farms/CAREER/sheep").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고민이에요\"}"))
                .andExpect(status().isUnauthorized());
    }
}
