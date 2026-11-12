package com.ganzithon.teolhanol.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import com.ganzithon.teolhanol.domain.notification.push.PushResult;
import com.ganzithon.teolhanol.domain.notification.push.PushSender;
import com.ganzithon.teolhanol.domain.notification.push.PushTarget;
import com.ganzithon.teolhanol.domain.notification.repository.PushSubscriptionRepository;
import com.ganzithon.teolhanol.domain.notification.service.NotificationStreams;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.event.WoolArrivedEvent;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 털 도착·고마워요 알림 (WOL-02, NOTI-01). 웹 푸시 발송기는 목으로 바꿔 외부 호출 없이 확인합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
@ExtendWith(OutputCaptureExtension.class)
class WoolNotificationTest {

    static final String ADVICE = "천천히 정해도 괜찮아요";
    static final String OWNER_ENDPOINT = "https://fcm.googleapis.com/fcm/send/owner-browser";
    static final String HELPER_ENDPOINT = "https://fcm.googleapis.com/fcm/send/helper-browser";
    static final long WAIT_MILLIS = 5_000;

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired PushSubscriptionRepository subscriptionRepository;
    @Autowired NotificationStreams streams;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired TransactionTemplate tx;
    @MockitoBean PushSender pushSender;
    @MockitoBean LlmClient llmClient;

    TestUsers.Login owner;
    TestUsers.Login helper;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        owner = testUsers.signIn("noti-owner@test.com", "고민주인");
        helper = testUsers.signIn("noti-helper@test.com", "조언목동");
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenAnswer(inv -> verdict(inv.getArgument(1)));
        when(pushSender.send(any(), anyString())).thenReturn(PushResult.SENT);
    }

    @AfterEach
    void cleanUp() {
        subscriptionRepository.deleteAll();
        thanksRepository.deleteAll();
        woolRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---- 도우미 ----

    /** 어조 확인 결과 '문제 없음' */
    @SuppressWarnings("unchecked")
    private static <T> T verdict(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (T) constructor.newInstance("NONE", null);
    }

    private User user(TestUsers.Login login) {
        return userRepository.findById(login.userId()).orElseThrow();
    }

    private WorrySheep sheep() {
        var farm = farmRepository.findByCode("CAREER").orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", RiskLevel.SAFE));
    }

    private PushSubscription subscribe(TestUsers.Login who, String endpoint) {
        return subscriptionRepository.saveAndFlush(new PushSubscription(user(who), endpoint, "p256dh-" + who.userId(), "auth"));
    }

    private Long sendWool(TestUsers.Login who, WorrySheep sheep) throws Exception {
        String body = mockMvc.perform(post("/worry-sheep/" + sheep.getId() + "/wools").header("Authorization", who.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + ADVICE + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("woolId").asLong();
    }

    /** 이벤트 없이 저장한 조언 (고마워요 테스트용) */
    private Long woolFromHelper(WorrySheep sheep) {
        Wool wool = woolRepository.saveAndFlush(new Wool(sheep, user(helper), ADVICE, false));
        sheep.addWool(10);
        worrySheepRepository.saveAndFlush(sheep);
        return wool.getId();
    }

    /** 이 endpoint로 보낸 웹 푸시 payload */
    private JsonNode pushedTo(String endpoint) {
        ArgumentCaptor<PushTarget> target = ArgumentCaptor.forClass(PushTarget.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushSender, timeout(WAIT_MILLIS)).send(target.capture(), payload.capture());
        assertThat(target.getValue().endpoint()).isEqualTo(endpoint);
        return objectMapper.readTree(payload.getValue());
    }

    private MvcResult openStream(TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/notifications/stream").header("Authorization", who.bearer())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /** 스트림에 이 이벤트가 올 때까지 기다렸다가 data를 돌려줍니다. */
    private JsonNode awaitEvent(MvcResult stream, String eventName) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            String body = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
            for (String block : body.split("\n\n")) {
                if (block.contains("event:" + eventName)) {
                    for (String line : block.split("\n")) {
                        if (line.startsWith("data:")) {
                            return objectMapper.readTree(line.substring(5));
                        }
                    }
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError(eventName + " 이벤트가 오지 않았습니다: "
                + stream.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---- 웹 푸시 ----

    @Test
    void 다른_계정이_조언을_보내면_주인_구독으로_웹_푸시가_간다() throws Exception {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);
        subscribe(helper, HELPER_ENDPOINT);

        sendWool(helper, sheep);

        JsonNode payload = pushedTo(OWNER_ENDPOINT);
        assertThat(payload.path("type").asString()).isEqualTo("WOOL_ARRIVED");
        assertThat(payload.path("title").asString()).isEqualTo("털이 풍성해졌어요");
        assertThat(payload.path("sheepId").asLong()).isEqualTo(sheep.getId());
        assertThat(payload.path("url").asString()).isEqualTo("/farm/my/" + sheep.getId());
        // 본문에 조언 내용·닉네임·다른 사용자 정보가 없다
        String raw = payload.toString();
        assertThat(raw).doesNotContain(ADVICE, "조언목동", "고민주인", "noti-", "userId", "woolId");
        // 보낸 사람 자신의 구독으로는 가지 않는다 (한 번만 불림)
        verify(pushSender, after(300).times(1)).send(any(), anyString());
    }

    @Test
    void 구독이_여러_개면_모두에게_보낸다() throws Exception {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);
        subscribe(owner, OWNER_ENDPOINT + "-phone");

        sendWool(helper, sheep);

        verify(pushSender, timeout(WAIT_MILLIS).times(2)).send(any(), anyString());
    }

    @Test
    void 고마워요를_누르면_조언_보낸_사람에게_url_없이_알린다() throws Exception {
        Long woolId = woolFromHelper(sheep());
        subscribe(helper, HELPER_ENDPOINT);
        subscribe(owner, OWNER_ENDPOINT);

        mockMvc.perform(post("/wools/" + woolId + "/thanks").header("Authorization", owner.bearer()))
                .andExpect(status().isCreated());

        JsonNode payload = pushedTo(HELPER_ENDPOINT);
        assertThat(payload.path("type").asString()).isEqualTo("WOOL_THANKED");
        assertThat(payload.path("title").asString()).isEqualTo("고마워요가 도착했어요");
        assertThat(payload.path("url").isNull()).isTrue();
        assertThat(payload.toString()).doesNotContain(ADVICE, "고민주인", "조언목동");
        // 양 주인(누른 사람)에게는 가지 않는다
        verify(pushSender, after(300).times(1)).send(any(), anyString());
    }

    @Test
    void 롤백된_전송은_알리지_않는다() {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);

        tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new WoolArrivedEvent(sheep.getId(), owner.userId(), 1L));
            status.setRollbackOnly();
        });

        verify(pushSender, after(500).never()).send(any(), anyString());
    }

    @Test
    void 트랜잭션_밖에서_발행된_이벤트는_알리지_않는다() {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);

        eventPublisher.publishEvent(new WoolArrivedEvent(sheep.getId(), owner.userId(), 1L));

        verify(pushSender, after(300).never()).send(any(), anyString());
    }

    @Test
    void 커밋된_이벤트만_알린다() {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);

        tx.executeWithoutResult(status ->
                eventPublisher.publishEvent(new WoolArrivedEvent(sheep.getId(), owner.userId(), 1L)));

        verify(pushSender, timeout(WAIT_MILLIS)).send(any(), anyString());
    }

    @Test
    void 만료_응답을_받은_구독은_지우고_다른_구독은_남긴다() throws Exception {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);
        subscribe(owner, OWNER_ENDPOINT + "-gone");
        when(pushSender.send(any(), anyString())).thenAnswer(inv -> {
            PushTarget target = inv.getArgument(0);
            return target.endpoint().endsWith("-gone") ? PushResult.EXPIRED : PushResult.FAILED;
        });

        sendWool(helper, sheep);

        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (subscriptionRepository.findAllByUserId(owner.userId()).size() > 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        // 일시 실패(FAILED)는 유지, 만료(EXPIRED)만 정리
        assertThat(subscriptionRepository.findAllByUserId(owner.userId())).singleElement()
                .satisfies(s -> assertThat(s.getEndpoint()).isEqualTo(OWNER_ENDPOINT));
    }

    @Test
    void 발송이_실패해도_조언_저장은_성공한다() throws Exception {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);
        when(pushSender.send(any(), anyString())).thenThrow(new IllegalStateException("boom"));

        sendWool(helper, sheep);

        verify(pushSender, timeout(WAIT_MILLIS)).send(any(), anyString());
        assertThat(woolRepository.count()).isEqualTo(1);
    }

    @Test
    void 조언_본문과_endpoint는_로그에_남지_않는다(CapturedOutput output) throws Exception {
        WorrySheep sheep = sheep();
        subscribe(owner, OWNER_ENDPOINT);
        subscribe(owner, OWNER_ENDPOINT + "-gone");
        when(pushSender.send(any(), anyString())).thenReturn(PushResult.EXPIRED);

        sendWool(helper, sheep);
        verify(pushSender, timeout(WAIT_MILLIS).times(2)).send(any(), anyString());
        Thread.sleep(200);

        assertThat(output.getAll()).doesNotContain(ADVICE, "owner-browser");
    }

    // ---- 앱 안 알림 (SSE) ----

    @Test
    void 스트림이_열려_있으면_wool_arrived_이벤트를_받는다() throws Exception {
        WorrySheep sheep = sheep();
        MvcResult stream = openStream(owner);
        assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);

        sendWool(helper, sheep);

        JsonNode data = awaitEvent(stream, "wool-arrived");
        assertThat(data.path("type").asString()).isEqualTo("WOOL_ARRIVED");
        assertThat(data.path("sheepId").asLong()).isEqualTo(sheep.getId());
        assertThat(data.path("url").asString()).isEqualTo("/farm/my/" + sheep.getId());
        assertThat(data.toString()).doesNotContain(ADVICE, "조언목동");
        // 구독이 없으면 웹 푸시는 부르지 않는다
        verify(pushSender, after(200).never()).send(any(), anyString());
    }

    @Test
    void 여러_탭이_열려_있으면_모두_받고_다른_사람_스트림에는_가지_않는다() throws Exception {
        WorrySheep sheep = sheep();
        MvcResult tab1 = openStream(owner);
        MvcResult tab2 = openStream(owner);
        MvcResult helperTab = openStream(helper);
        assertThat(streams.connectionCount(owner.userId())).isEqualTo(2);

        sendWool(helper, sheep);

        awaitEvent(tab1, "wool-arrived");
        awaitEvent(tab2, "wool-arrived");
        Thread.sleep(200);
        assertThat(helperTab.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("wool-arrived");
    }

    @Test
    void 고마워요는_조언_보낸_사람의_스트림에_wool_thanked로_간다() throws Exception {
        Long woolId = woolFromHelper(sheep());
        MvcResult stream = openStream(helper);

        mockMvc.perform(post("/wools/" + woolId + "/thanks").header("Authorization", owner.bearer()))
                .andExpect(status().isCreated());

        JsonNode data = awaitEvent(stream, "wool-thanked");
        assertThat(data.path("type").asString()).isEqualTo("WOOL_THANKED");
        assertThat(data.path("url").isNull()).isTrue();
    }

    @Test
    void 끊긴_연결은_정리되고_탭_수가_상한을_넘으면_오래된_연결을_닫는다() throws Exception {
        Long userId = owner.userId();
        List<SseEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            emitters.add(streams.connect(userId));
        }
        assertThat(streams.connectionCount(userId)).isEqualTo(5);

        // 끝난 연결은 다음 전송·heartbeat 때 빠진다
        emitters.get(5).complete();
        emitters.get(4).complete();
        streams.heartbeat();
        assertThat(streams.connectionCount(userId)).isEqualTo(3);

        emitters.subList(1, 4).forEach(SseEmitter::complete);
        streams.heartbeat();
        assertThat(streams.connectionCount(userId)).isZero();
    }

    @Test
    void 서버가_닫은_스트림은_정상적으로_끝난다() throws Exception {
        MvcResult stream = openStream(owner);
        // 탭 수 상한을 넘겨 가장 오래된(위의) 연결을 닫게 합니다.
        for (int i = 0; i < 5; i++) {
            streams.connect(owner.userId());
        }

        mockMvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(":connected");
        assertThat(streams.connectionCount(owner.userId())).isEqualTo(5);
    }

    @Test
    void 로그인하지_않으면_스트림은_401() throws Exception {
        mockMvc.perform(get("/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
