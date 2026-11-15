package com.ganzithon.teolhanol.domain.wool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.event.WoolThankedEvent;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 고마워요 (WOL-03). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
@RecordApplicationEvents
class ThanksApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired ApplicationEvents events;

    TestUsers.Login owner;
    TestUsers.Login helper;
    TestUsers.Login stranger;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = testUsers.signIn("thanks-owner@test.com", "고민주인");
        helper = testUsers.signIn("thanks-helper@test.com", "조언목동");
        stranger = testUsers.signIn("thanks-stranger@test.com", "지나가는목동");
    }

    @AfterEach
    void cleanUp() {
        thanksRepository.deleteAll();
        woolRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(TestUsers.Login login) {
        return userRepository.findById(login.userId()).orElseThrow();
    }

    private WorrySheep sheep() {
        var farm = farmRepository.findByCode("CAREER").orElseThrow();
        return worrySheepRepository.saveAndFlush(
                new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", RiskLevel.SAFE));
    }

    private Wool wool(WorrySheep sheep) {
        Wool wool = woolRepository.saveAndFlush(new Wool(sheep, user(helper), "천천히 정해도 괜찮아요", false));
        sheep.addWool(10);
        worrySheepRepository.saveAndFlush(sheep);
        return wool;
    }

    private ResultActions thank(TestUsers.Login who, Long woolId) throws Exception {
        return mockMvc.perform(post("/wools/" + woolId + "/thanks").header("Authorization", who.bearer()));
    }

    @Test
    void 주인이_누르면_201이고_상세의_thanked가_true가_된다() throws Exception {
        WorrySheep sheep = sheep();
        Wool wool = wool(sheep);
        int woolCount = worrySheepRepository.findById(sheep.getId()).orElseThrow().getWoolCount();

        thank(owner, wool.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.woolId").value(wool.getId()))
                .andExpect(jsonPath("$.data.thanked").value(true));

        assertThat(thanksRepository.existsByWoolId(wool.getId())).isTrue();
        mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wools[0].woolId").value(wool.getId()))
                .andExpect(jsonPath("$.data.wools[0].thanked").value(true));

        // 보상 없음: 털실·털 수는 그대로
        assertThat(user(helper).getYarnCount()).isZero();
        assertThat(user(owner).getYarnCount()).isZero();
        assertThat(worrySheepRepository.findById(sheep.getId()).orElseThrow().getWoolCount()).isEqualTo(woolCount);
    }

    @Test
    void 고마워요가_눌리면_보낸_사람에게_갈_이벤트가_발행된다() throws Exception {
        WorrySheep sheep = sheep();
        Wool wool = wool(sheep);

        thank(owner, wool.getId()).andExpect(status().isCreated());

        List<WoolThankedEvent> published = events.stream(WoolThankedEvent.class).toList();
        assertThat(published).containsExactly(new WoolThankedEvent(wool.getId(), sheep.getId(), helper.userId()));
    }

    @Test
    void 주인이_아니면_404이고_저장하지_않으며_이벤트도_없다() throws Exception {
        Wool wool = wool(sheep());

        thank(stranger, wool.getId()).andExpect(status().isNotFound());
        thank(helper, wool.getId()).andExpect(status().isNotFound());

        assertThat(thanksRepository.existsByWoolId(wool.getId())).isFalse();
        assertThat(events.stream(WoolThankedEvent.class).count()).isZero();
    }

    @Test
    void 없는_조언은_404() throws Exception {
        thank(owner, 99999999L).andExpect(status().isNotFound());
    }

    @Test
    void 이미_누른_조언은_409_THANKS_ALREADY_SENT() throws Exception {
        Wool wool = wool(sheep());

        thank(owner, wool.getId()).andExpect(status().isCreated());
        thank(owner, wool.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("THANKS_ALREADY_SENT"));

        assertThat(thanksRepository.count()).isEqualTo(1);
        assertThat(events.stream(WoolThankedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void 숨긴_양의_조언은_404() throws Exception {
        WorrySheep sheep = sheep();
        Wool wool = wool(sheep);
        sheep.hide();
        worrySheepRepository.saveAndFlush(sheep);

        thank(owner, wool.getId()).andExpect(status().isNotFound());
        assertThat(thanksRepository.existsByWoolId(wool.getId())).isFalse();
    }

    @Test
    void 졸업한_양의_조언에도_누를_수_있다() throws Exception {
        WorrySheep sheep = sheep();
        Wool wool = wool(sheep);
        sheep.graduate();
        worrySheepRepository.saveAndFlush(sheep);

        thank(owner, wool.getId()).andExpect(status().isCreated());
        assertThat(thanksRepository.existsByWoolId(wool.getId())).isTrue();
    }

    @Test
    void 동시에_눌러도_한_번만_성공한다() throws Exception {
        Wool wool = wool(sheep());
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return thank(owner, wool.getId()).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(threads - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(thanksRepository.count()).isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/wools/1/thanks")).andExpect(status().isUnauthorized());
    }
}
