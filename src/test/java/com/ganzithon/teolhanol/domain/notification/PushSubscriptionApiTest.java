package com.ganzithon.teolhanol.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import com.ganzithon.teolhanol.domain.notification.push.DisabledPushSender;
import com.ganzithon.teolhanol.domain.notification.push.PushSender;
import com.ganzithon.teolhanol.domain.notification.repository.PushSubscriptionRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.support.TestUsers;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 웹 푸시 구독 저장·해지와 VAPID 공개키 (WOL-02). 테스트 설정에는 VAPID 키가 없어 웹 푸시가 꺼진 채로 뜹니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class PushSubscriptionApiTest {

    static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/sub-test-endpoint";
    static final String P256DH = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
    static final String AUTH = "tBHItJI5svbpez7KI4CCXg";

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired PushSubscriptionRepository subscriptionRepository;
    @Autowired PushSender pushSender;

    TestUsers.Login me;
    TestUsers.Login other;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("push-me@test.com", "알림목동");
        other = testUsers.signIn("push-other@test.com", "옆집목동");
    }

    @AfterEach
    void cleanUp() {
        subscriptionRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions subscribe(TestUsers.Login who, String endpoint, String p256dh, String auth) throws Exception {
        String body = """
                {"endpoint":"%s","expirationTime":null,"keys":{"p256dh":"%s","auth":"%s"}}
                """.formatted(endpoint, p256dh, auth);
        return mockMvc.perform(post("/push/subscriptions").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions unsubscribe(TestUsers.Login who, String endpoint) throws Exception {
        return mockMvc.perform(delete("/push/subscriptions").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"));
    }

    private List<PushSubscription> subscriptions() {
        return subscriptionRepository.findAll();
    }

    @Test
    void 키가_없으면_웹_푸시는_꺼진_채로_기동하고_공개키는_null() throws Exception {
        mockMvc.perform(get("/push/vapid-public-key").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.publicKey").value(nullValue()));
        assertThat(pushSender).isInstanceOf(DisabledPushSender.class);
    }

    @Test
    void 새_구독은_201로_저장되고_웹_푸시가_꺼져_있어도_저장된다() throws Exception {
        subscribe(me, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.pushEnabled").value(false));

        assertThat(subscriptions()).singleElement().satisfies(s -> {
            assertThat(s.getEndpoint()).isEqualTo(ENDPOINT);
            assertThat(s.getP256dh()).isEqualTo(P256DH);
            assertThat(s.getAuth()).isEqualTo(AUTH);
            assertThat(s.getCreatedAt()).isNotNull();
        });
        assertThat(subscriptionRepository.findAllByUserId(me.userId())).hasSize(1);
    }

    @Test
    void 같은_endpoint를_다시_보내면_200이고_한_행만_남는다() throws Exception {
        subscribe(me, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());
        subscribe(me, ENDPOINT, P256DH, AUTH).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true));

        assertThat(subscriptions()).hasSize(1);
    }

    @Test
    void 같은_브라우저에서_다른_계정이_구독하면_소유자가_바뀐다() throws Exception {
        subscribe(me, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());
        subscribe(other, ENDPOINT, P256DH, "bmV3LWF1dGgtc2VjcmV0IQ").andExpect(status().isOk());

        assertThat(subscriptionRepository.findAllByUserId(me.userId())).isEmpty();
        assertThat(subscriptionRepository.findAllByUserId(other.userId())).singleElement()
                .satisfies(s -> assertThat(s.getAuth()).isEqualTo("bmV3LWF1dGgtc2VjcmV0IQ"));
    }

    @Test
    void 표준_base64_키는_base64url로_맞춰_저장한다() throws Exception {
        subscribe(me, ENDPOINT, "BNc+dre/ALRF==", "tBH+tJ/5sv==").andExpect(status().isCreated());

        assertThat(subscriptions()).singleElement().satisfies(s -> {
            assertThat(s.getP256dh()).isEqualTo("BNc-dre_ALRF");
            assertThat(s.getAuth()).isEqualTo("tBH-tJ_5sv");
        });
    }

    @Test
    void 알려진_푸시_서비스의_https_주소가_아니면_400() throws Exception {
        for (String endpoint : List.of("http://fcm.googleapis.com/fcm/send/x", "https://localhost:8080/x",
                "https://169.254.169.254/latest", "https://evilfcm.googleapis.com.attacker.io/x",
                "https://user@fcm.googleapis.com/x", "not a url")) {
            subscribe(me, endpoint, P256DH, AUTH)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PUSH_ENDPOINT_NOT_ALLOWED"));
        }
        // 하위 도메인은 받습니다
        subscribe(me, "https://updates.push.services.mozilla.com/wpush/v2/abc", P256DH, AUTH)
                .andExpect(status().isCreated());
        subscribe(me, "https://web.push.apple.com/abc", P256DH, AUTH).andExpect(status().isCreated());
        assertThat(subscriptions()).hasSize(2);
    }

    @Test
    void 키가_없거나_형식이_틀리면_400() throws Exception {
        mockMvc.perform(post("/push/subscriptions").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        subscribe(me, ENDPOINT, "", AUTH).andExpect(status().isBadRequest());
        subscribe(me, ENDPOINT, P256DH, "not base64!").andExpect(status().isBadRequest());
        subscribe(me, "https://fcm.googleapis.com/" + "a".repeat(500), P256DH, AUTH).andExpect(status().isBadRequest());
        assertThat(subscriptions()).isEmpty();
    }

    @Test
    void 해지하면_본인_구독만_지워진다() throws Exception {
        subscribe(me, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());

        unsubscribe(other, ENDPOINT).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false));
        assertThat(subscriptions()).hasSize(1);

        unsubscribe(me, ENDPOINT).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false));
        assertThat(subscriptions()).isEmpty();

        // 이미 없어도 200 (멱등)
        unsubscribe(me, ENDPOINT).andExpect(status().isOk());
    }

    @Test
    void 탈퇴하면_내_구독이_지워지고_다른_사람_구독은_남는다() throws Exception {
        subscribe(me, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());
        subscribe(other, ENDPOINT + "-other", P256DH, AUTH).andExpect(status().isCreated());

        mockMvc.perform(delete("/me").header("Authorization", me.bearer())).andExpect(status().isOk());

        assertThat(subscriptions()).singleElement()
                .satisfies(s -> assertThat(s.getEndpoint()).isEqualTo(ENDPOINT + "-other"));
    }

    @Test
    void 기록만_지울_때는_구독이_남는다() throws Exception {
        subscribe(me, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());

        mockMvc.perform(delete("/me/records").header("Authorization", me.bearer())).andExpect(status().isOk());

        assertThat(subscriptions()).hasSize(1);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/push/vapid-public-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + ENDPOINT + "\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH + "\"}}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/push/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/stream")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        assertThat(subscriptions()).isEmpty();
    }
}
