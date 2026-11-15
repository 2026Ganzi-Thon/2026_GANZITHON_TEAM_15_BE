package com.ganzithon.teolhanol.domain.wool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.event.WoolArrivedEvent;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** 조언(털) 보내기 (WOL-01, SAF-04, SAF-05). 안전 패턴은 시드 데이터를 그대로 씁니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class WoolSendApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired ApplicationEvents events;
    @MockitoSpyBean WoolRepository woolRepository;
    @MockitoBean LlmClient llmClient;

    TestUsers.Login owner;
    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = testUsers.signIn("wool-owner@test.com", "고민주인");
        me = testUsers.signIn("wool-me@test.com", "조언목동");
        llmReturns("NONE", null);
    }

    @AfterEach
    void cleanUp() {
        thanksRepository.deleteAll();
        woolRepository.deleteAll();
        blockRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---- 도우미 ----

    private void llmReturns(String category, String suggestion) {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenAnswer(inv -> newVerdict(inv.getArgument(1), category, suggestion));
    }

    @SuppressWarnings("unchecked")
    private static <T> T newVerdict(Class<T> type, String category, String suggestion) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (T) constructor.newInstance(category, suggestion);
    }

    private User user(TestUsers.Login login) {
        return userRepository.findById(login.userId()).orElseThrow();
    }

    private WorrySheep sheep(RiskLevel risk) {
        var farm = farmRepository.findByCode("CAREER").orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", risk));
    }

    /** 다른 목동들이 보낸 털 n개를 가진 양. 털 수는 Hibernate 경로로 맞춥니다. */
    private WorrySheep sheepWithWools(int n) {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        for (int i = 0; i < n; i++) {
            TestUsers.Login helper = testUsers.signIn("wool-helper" + i + "@test.com", "도우미" + i);
            woolRepository.saveAndFlush(new Wool(sheep, user(helper), "응원해요 " + i, false));
            sheep.addWool(10);
        }
        return worrySheepRepository.saveAndFlush(sheep);
    }

    private WorrySheep reload(WorrySheep sheep) {
        return worrySheepRepository.findById(sheep.getId()).orElseThrow();
    }

    private ResultActions send(TestUsers.Login who, WorrySheep sheep, String content, Boolean sendAsIs)
            throws Exception {
        return send(who, sheep.getId(), content, sendAsIs);
    }

    private ResultActions send(TestUsers.Login who, Long sheepId, String content, Boolean sendAsIs)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        if (sendAsIs != null) {
            body.put("sendAsIs", sendAsIs);
        }
        return mockMvc.perform(post("/worry-sheep/" + sheepId + "/wools").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private List<Wool> woolsOf(WorrySheep sheep) {
        return woolRepository.findAll().stream().filter(w -> w.getSheep().getId().equals(sheep.getId())).toList();
    }

    // ---- 보내기 성공 ----

    @Test
    void 통과한_조언은_저장되고_양_털과_보낸_사람_털실이_오른다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        String body = send(me, sheep, "  천천히 정해도 괜찮아요  ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.woolId").isNumber())
                .andExpect(jsonPath("$.data.sheepStage").value(2))
                .andExpect(jsonPath("$.data.sheepStageName").value("보송한 양"))
                .andExpect(jsonPath("$.data.stageUp").value(true))
                .andExpect(jsonPath("$.data.graduated").value(false))
                .andExpect(jsonPath("$.data.yarnAdded").value(1))
                .andExpect(jsonPath("$.data.yarnCount").value(1))
                .andExpect(jsonPath("$.data.category").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("woolCount", "userId", "email", "@test.com");

        List<Wool> wools = woolsOf(sheep);
        assertThat(wools).hasSize(1);
        assertThat(wools.get(0).getContent()).isEqualTo("천천히 정해도 괜찮아요");
        assertThat(wools.get(0).isSentAsIs()).isFalse();

        WorrySheep after = reload(sheep);
        assertThat(after.getWoolCount()).isEqualTo(1);
        assertThat(after.getStage()).isEqualTo(2);
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);

        User sender = user(me);
        assertThat(sender.getYarnCount()).isEqualTo(1);
        assertThat(sender.getSentWoolCount()).isEqualTo(1);
        assertThat(user(owner).getYarnCount()).isZero();
    }

    @Test
    void 단계가_그대로면_stageUp은_false() throws Exception {
        WorrySheep sheep = sheepWithWools(1);

        send(me, sheep, "응원할게요", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sheepStage").value(2))
                .andExpect(jsonPath("$.data.stageUp").value(false));
        assertThat(reload(sheep).getWoolCount()).isEqualTo(2);
    }

    @Test
    void 털이_상한에_닿으면_같은_요청에서_졸업한다() throws Exception {
        WorrySheep sheep = sheepWithWools(9);

        send(me, sheep, "혼자가 아니에요", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sheepStage").value(5))
                .andExpect(jsonPath("$.data.sheepStageName").value("구름 양"))
                .andExpect(jsonPath("$.data.stageUp").value(true))
                .andExpect(jsonPath("$.data.graduated").value(true));

        WorrySheep after = reload(sheep);
        assertThat(after.getWoolCount()).isEqualTo(10);
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(after.getGraduatedAt()).isNotNull();
    }

    @Test
    void 저장하면_주인에게_털_도착_이벤트를_발행한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        String body = send(me, sheep, "오늘 정말 고생 많았어요", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long woolId = objectMapper.readTree(body).path("data").path("woolId").asLong();

        assertThat(events.stream(WoolArrivedEvent.class))
                .containsExactly(new WoolArrivedEvent(sheep.getId(), owner.userId(), woolId));
    }

    @Test
    void 조언_본문은_로그에_남지_않는다(CapturedOutput output) throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        send(me, sheep, "로그비밀조언문장", null).andExpect(status().isCreated());

        assertThat(output.getAll()).doesNotContain("로그비밀조언문장");
    }

    @Test
    void 보내기_로그에는_보낸_사람_id가_없다(CapturedOutput output) throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        send(me, sheep, "010-1234-5678로 연락 주세요", null).andExpect(jsonPath("$.data.status").value("BLOCKED"));
        send(me, sheep, "씨발 그냥 참아요", null).andExpect(jsonPath("$.data.status").value("BLOCKED"));
        send(me, sheep, "응원할게요", null).andExpect(status().isCreated());

        String logs = output.getAll();
        assertThat(logs).contains("조언 보내기 개인정보 패턴으로 저장하지 않음 sheepId=" + sheep.getId())
                .contains("조언 보내기 막음 sheepId=" + sheep.getId())
                .contains("조언 보냄 sheepId=" + sheep.getId());
        assertThat(logs).doesNotContain("senderId");
    }

    // ---- 권한 ----

    @Test
    void 내_양에는_보낼_수_없다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        send(owner, sheep, "스스로 응원해요", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WOOL_OWN_SHEEP"));
        assertThat(woolsOf(sheep)).isEmpty();
        verify(llmClient, never()).completeJson(any(), any());
    }

    @Test
    void 이미_보낸_양에_다시_보내면_409이고_털실은_한_번만_오른다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        send(me, sheep, "응원할게요", null).andExpect(status().isCreated());

        send(me, sheep, "한 번 더 응원해요", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WOOL_ALREADY_SENT"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.WOOL_ALREADY_SENT.getMessage()));

        assertThat(woolsOf(sheep)).hasSize(1);
        assertThat(reload(sheep).getWoolCount()).isEqualTo(1);
        assertThat(user(me).getYarnCount()).isEqualTo(1);
        assertThat(user(me).getSentWoolCount()).isEqualTo(1);
    }

    @Test
    void 졸업한_양에는_방금_떠났다고_안내한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        sheep.graduate();
        worrySheepRepository.saveAndFlush(sheep);

        send(me, sheep, "응원할게요", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORRY_SHEEP_GRADUATED"))
                .andExpect(jsonPath("$.error.message").value("이 양은 방금 목장을 떠났어요."));
        assertThat(woolsOf(sheep)).isEmpty();
        assertThat(user(me).getYarnCount()).isZero();
    }

    @Test
    void 확인하는_사이_졸업하면_저장하지_않고_안내한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> {
            WorrySheep current = reload(sheep);
            current.graduate();
            worrySheepRepository.saveAndFlush(current);
            return newVerdict(inv.getArgument(1), "NONE", null);
        });

        send(me, sheep, "응원할게요", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORRY_SHEEP_GRADUATED"));
        assertThat(woolsOf(sheep)).isEmpty();
        assertThat(user(me).getYarnCount()).isZero();
    }

    @Test
    void 확인하는_사이_같은_사람이_먼저_보냈으면_409() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> {
            woolRepository.saveAndFlush(new Wool(reload(sheep), user(me), "먼저 보낸 조언", false));
            return newVerdict(inv.getArgument(1), "NONE", null);
        });

        send(me, sheep, "응원할게요", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WOOL_ALREADY_SENT"));
        assertThat(woolsOf(sheep)).hasSize(1);
        assertThat(reload(sheep).getWoolCount()).isZero();
    }

    @Test
    void 중복_검사를_지나쳐도_UNIQUE_위반을_409로_바꾼다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        woolRepository.saveAndFlush(new Wool(sheep, user(me), "먼저 보낸 조언", false));
        // 미리 확인과 잠금 뒤 확인을 모두 지나쳤다고 가정합니다. 저장 실패 뒤 확인에서는 이미 있다고 답합니다.
        doReturn(false).doReturn(false).doReturn(true)
                .when(woolRepository).existsBySheepIdAndSenderId(anyLong(), anyLong());

        send(me, sheep, "응원할게요", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WOOL_ALREADY_SENT"));

        assertThat(woolsOf(sheep)).hasSize(1);
        assertThat(reload(sheep).getWoolCount()).isZero();
        assertThat(user(me).getYarnCount()).isZero();
        assertThat(user(me).getSentWoolCount()).isZero();
    }

    @Test
    void 숨김_차단_보호_양_없는_양은_404() throws Exception {
        WorrySheep hidden = sheep(RiskLevel.SAFE);
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);
        send(me, hidden, "응원할게요", null).andExpect(status().isNotFound());

        WorrySheep blocked = sheep(RiskLevel.SAFE);
        Block block = blockRepository.saveAndFlush(new Block(user(owner), user(me)));
        send(me, blocked, "응원할게요", null).andExpect(status().isNotFound());
        blockRepository.delete(block);
        blockRepository.saveAndFlush(new Block(user(me), user(owner)));
        send(me, blocked, "응원할게요", null).andExpect(status().isNotFound());

        send(me, 99_999_999L, "응원할게요", null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_404"));

        assertThat(woolRepository.count()).isZero();
        verify(llmClient, never()).completeJson(any(), any());
    }

    @Test
    void 보호_양은_조언_이력이_충분해야_보낼_수_있다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.CAUTION);
        send(me, sheep, "응원할게요", null).andExpect(status().isNotFound());

        // 조언 이력 5회를 Hibernate 경로로 쌓습니다.
        User sender = user(me);
        for (int i = 0; i < 5; i++) {
            sender.addSentWool();
        }
        userRepository.saveAndFlush(sender);

        send(me, sheep, "응원할게요", null).andExpect(status().isCreated());
        assertThat(user(me).getSentWoolCount()).isEqualTo(6);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        mockMvc.perform(post("/worry-sheep/" + sheep.getId() + "/wools")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"응원해요\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 길이 ----

    @Test
    void 비었거나_100자를_넘으면_400() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        send(me, sheep, "   ", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"))
                .andExpect(jsonPath("$.error.message").value("조언은 1자에서 100자 사이로 적어 주세요."));
        send(me, sheep, null, null).andExpect(status().isBadRequest());
        send(me, sheep, "가".repeat(101), null).andExpect(status().isBadRequest());
        assertThat(woolRepository.count()).isZero();

        send(me, sheep, "  " + "가".repeat(100) + "  ", null).andExpect(status().isCreated());
    }

    @Test
    void 이모지도_한_글자로_세어_백자_경계를_지킨다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        // 코드 포인트 101개(이모지 1개 포함)는 400
        send(me, sheep, "가".repeat(100) + "🐑", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("조언은 1자에서 100자 사이로 적어 주세요."));
        assertThat(woolRepository.count()).isZero();

        // 코드 포인트 100개는 UTF-16 길이가 100을 넘어도 길이 검사를 통과한다.
        // (H2 의 VARCHAR 는 UTF-16 단위로 길이를 재서 저장까지는 확인할 수 없으므로, 길이 검사 다음 단계인
        //  어조 제안 응답으로 통과를 확인합니다. 운영 MySQL utf8mb4 의 varchar(100)은 문자(코드 포인트) 100개까지 담습니다.)
        String boundary = "가".repeat(99) + "🐑";
        assertThat(boundary.length()).isEqualTo(101);
        llmReturns("LECTURE", "지금은 쉬어 가도 괜찮아요.");
        send(me, sheep, boundary, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUGGEST"));
        String allEmoji = "🐑".repeat(100);
        send(me, sheep, allEmoji, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUGGEST"));
        assertThat(woolRepository.count()).isZero();

        // 이모지가 든 조언은 그대로 저장된다(H2 에도 들어가는 길이)
        llmReturns("NONE", null);
        String withEmoji = "가".repeat(98) + "🐑";
        send(me, sheep, withEmoji, null).andExpect(status().isCreated());
        assertThat(woolsOf(sheep)).extracting(Wool::getContent).containsExactly(withEmoji);
    }

    // ---- 다시 확인 (SAF-04, SAF-05) ----

    @Test
    void 금칙어는_그대로_보내기를_골라도_막는다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        for (Boolean asIs : new Boolean[] {false, true}) {
            send(me, sheep, "씨발 그냥 참아요", asIs)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                    .andExpect(jsonPath("$.data.category").value("BANNED_WORD"))
                    .andExpect(jsonPath("$.data.message").isNotEmpty())
                    .andExpect(jsonPath("$.data.woolId").doesNotExist())
                    .andExpect(jsonPath("$.data.suggestion").doesNotExist());
        }
        assertThat(woolsOf(sheep)).isEmpty();
        assertThat(user(me).getYarnCount()).isZero();
        verify(llmClient, never()).completeJson(any(), any());
    }

    @Test
    void 개인정보가_있으면_PII로_막는다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        send(me, sheep, "010-1234-5678로 연락 주세요", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.category").value("PII"))
                .andExpect(jsonPath("$.data.message").value(ErrorCode.PII_DETECTED.getMessage()));
        assertThat(woolsOf(sheep)).isEmpty();
        verify(llmClient, never()).completeJson(any(), any());
    }

    @Test
    void 어조_주의는_저장하지_않고_바꿀_문장을_제안한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        llmReturns("LECTURE", "지금은 쉬어 가도 괜찮아요.");

        send(me, sheep, "그만 징징대고 일어나요", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUGGEST"))
                .andExpect(jsonPath("$.data.category").value("LECTURE"))
                .andExpect(jsonPath("$.data.categoryLabel").value("다그침·훈계"))
                .andExpect(jsonPath("$.data.message").value("다그치는 말처럼 들릴 수 있어요."))
                .andExpect(jsonPath("$.data.suggestion").value("지금은 쉬어 가도 괜찮아요."))
                .andExpect(jsonPath("$.data.woolId").doesNotExist());

        assertThat(woolsOf(sheep)).isEmpty();
        assertThat(reload(sheep).getWoolCount()).isZero();
        assertThat(user(me).getYarnCount()).isZero();
    }

    @Test
    void 바꿀_문장을_만들지_못했으면_suggestion_없이_제안한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        llmReturns("COMPARE", null);

        send(me, sheep, "너만 힘든 거 아니에요", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUGGEST"))
                .andExpect(jsonPath("$.data.category").value("COMPARE"))
                .andExpect(jsonPath("$.data.suggestion").doesNotExist());
        assertThat(woolsOf(sheep)).isEmpty();
    }

    @Test
    void 어조_주의에도_그대로_보내기를_고르면_sent_as_is로_저장한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        llmReturns("LECTURE", "지금은 쉬어 가도 괜찮아요.");

        send(me, sheep, "그만 징징대고 일어나요", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.yarnCount").value(1));

        List<Wool> wools = woolsOf(sheep);
        assertThat(wools).hasSize(1);
        assertThat(wools.get(0).isSentAsIs()).isTrue();
        assertThat(wools.get(0).getContent()).isEqualTo("그만 징징대고 일어나요");
    }

    @Test
    void 통과한_조언은_sendAsIs가_true여도_sent_as_is가_아니다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);

        send(me, sheep, "응원할게요", true).andExpect(status().isCreated());
        assertThat(woolsOf(sheep).get(0).isSentAsIs()).isFalse();
    }

    @Test
    void LLM이_실패하면_금칙어_판정만으로_보낸다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));

        send(me, sheep, "응원할게요", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"));
        assertThat(woolsOf(sheep)).hasSize(1);
        assertThat(woolsOf(sheep).get(0).isSentAsIs()).isFalse();
    }

    // ---- 동시성 ----

    @Test
    void 털_9개인_양에_5명이_동시에_보내면_1명만_성공하고_10개에서_졸업한다() throws Exception {
        WorrySheep sheep = sheepWithWools(9);
        List<TestUsers.Login> senders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            senders.add(testUsers.signIn("wool-racer" + i + "@test.com", "동시목동" + i));
        }

        List<Integer> statuses = concurrently(senders.stream()
                .<Callable<Integer>>map(s -> () -> send(s, sheep, "응원할게요", null)
                        .andReturn().getResponse().getStatus())
                .toList());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(4);

        WorrySheep after = reload(sheep);
        assertThat(after.getWoolCount()).isEqualTo(10);
        assertThat(after.getStatus()).isEqualTo(WorrySheepStatus.GRADUATED);
        assertThat(woolsOf(sheep)).hasSize(10);
        int yarnSum = senders.stream().mapToInt(s -> user(s).getYarnCount()).sum();
        int sentSum = senders.stream().mapToInt(s -> user(s).getSentWoolCount()).sum();
        assertThat(yarnSum).isEqualTo(1);
        assertThat(sentSum).isEqualTo(1);
    }

    @Test
    void 같은_사람이_동시에_두_번_보내면_한_번만_성공한다() throws Exception {
        WorrySheep sheep = sheep(RiskLevel.SAFE);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String content = "동시에 보내는 응원 " + i;
            calls.add(() -> send(me, sheep, content, null).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = concurrently(calls);

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(woolsOf(sheep)).hasSize(1);
        assertThat(reload(sheep).getWoolCount()).isEqualTo(1);
        assertThat(user(me).getYarnCount()).isEqualTo(1);
        assertThat(user(me).getSentWoolCount()).isEqualTo(1);
    }

    private static List<Integer> concurrently(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
