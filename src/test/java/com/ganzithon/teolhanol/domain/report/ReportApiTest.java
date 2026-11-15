package com.ganzithon.teolhanol.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.report.entity.ReportTargetType;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.report.repository.ReportRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hamcrest.Matchers;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class ReportApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;
    @Autowired ThanksRepository thanksRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired ReportRepository reportRepository;

    TestUsers.Login me;
    TestUsers.Login writer;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("me35@test.com", "신고목동");
        writer = testUsers.signIn("writer35@test.com", "글쓴목동");
    }

    @AfterEach
    void cleanUp() {
        thanksRepository.deleteAll();
        woolRepository.deleteAll();
        reportRepository.deleteAll();
        blockRepository.deleteAll();
        worrySheepRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---- 픽스처 ----

    private User user(TestUsers.Login login) {
        return userRepository.findById(login.userId()).orElseThrow();
    }

    private WorrySheep sheep(TestUsers.Login owner, RiskLevel risk) {
        var farm = farmRepository.findAll().stream().filter(f -> f.getCode().equals("CAREER")).findFirst().orElseThrow();
        return worrySheepRepository.saveAndFlush(new WorrySheep(user(owner), farm, "이직을 해야 할지 고민이에요", risk));
    }

    private WorrySheep sheep(TestUsers.Login owner) {
        return sheep(owner, RiskLevel.SAFE);
    }

    private Wool wool(WorrySheep sheep, TestUsers.Login sender, String text) {
        return woolRepository.saveAndFlush(new Wool(sheep, user(sender), text, false));
    }

    private List<TestUsers.Login> others(int n) {
        List<TestUsers.Login> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(testUsers.signIn("reporter35-" + i + "@test.com", "신고자" + i));
        }
        return list;
    }

    private ResultActions report(TestUsers.Login who, String type, Long id, String reason, Boolean blockUser)
            throws Exception {
        String body = "{\"targetType\":\"" + type + "\",\"targetId\":" + id + ",\"reason\":\"" + reason + "\""
                + (blockUser == null ? "" : ",\"blockUser\":" + blockUser) + "}";
        return mockMvc.perform(post("/reports").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions report(TestUsers.Login who, String type, Long id, String reason) throws Exception {
        return report(who, type, id, reason, null);
    }

    private ResultActions detail(WorrySheep sheep, TestUsers.Login who) throws Exception {
        return mockMvc.perform(get("/worry-sheep/" + sheep.getId()).header("Authorization", who.bearer()));
    }

    private WorrySheep reload(WorrySheep sheep) {
        return worrySheepRepository.findById(sheep.getId()).orElseThrow();
    }

    private Wool reload(Wool wool) {
        return woolRepository.findById(wool.getId()).orElseThrow();
    }

    // ---- 기본 ----

    @Test
    void 양을_신고하면_201이고_글쓴이_정보는_응답에_없다() throws Exception {
        WorrySheep sheep = sheep(writer);

        String body = report(me, "WORRY_SHEEP", sheep.getId(), "ABUSIVE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").isNumber())
                .andExpect(jsonPath("$.data.hidden").value(false))
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("userId", "글쓴목동", "writer35", "이직을");
        assertThat(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.WORRY_SHEEP, sheep.getId()))
                .isEqualTo(1);
        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
        assertThat(user(writer).getCrisisBannerUntil()).isNull();
    }

    @Test
    void 같은_대상을_다시_신고하면_409() throws Exception {
        WorrySheep sheep = sheep(writer);
        report(me, "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(status().isCreated());

        report(me, "WORRY_SHEEP", sheep.getId(), "ETC").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_DUPLICATED"));
        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_사람이_같은_대상을_동시에_신고해도_한_번만_저장된다() throws Exception {
        WorrySheep sheep = sheep(writer);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(() -> report(me, "WORRY_SHEEP", sheep.getId(), "SPAM").andReturn().getResponse().getStatus());
        }

        assertThat(concurrently(calls)).containsExactlyInAnyOrder(201, 409, 409);
        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        mockMvc.perform(post("/reports").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        WorrySheep sheep = sheep(writer);
        mockMvc.perform(post("/reports").header("Authorization", me.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"WORRY_SHEEP\",\"targetId\":" + sheep.getId() + "}"))
                .andExpect(status().isBadRequest());
        report(me, "USER", sheep.getId(), "SPAM").andExpect(status().isBadRequest());
        report(me, "WORRY_SHEEP", sheep.getId(), "BORING").andExpect(status().isBadRequest());
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"WORRY_SHEEP\",\"targetId\":1,\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 자기 글 / 볼 수 없는 대상 ----

    @Test
    void 내_글은_신고할_수_없다() throws Exception {
        WorrySheep mine = sheep(me);
        report(me, "WORRY_SHEEP", mine.getId(), "SPAM").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_OWN_CONTENT"));

        Wool sentByMe = wool(sheep(writer), me, "천천히 정해도 괜찮아요");
        report(me, "WOOL", sentByMe.getId(), "SPAM").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_OWN_CONTENT"));
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    void 볼_수_없는_양은_404() throws Exception {
        report(me, "WORRY_SHEEP", 99999999L, "SPAM").andExpect(status().isNotFound());

        WorrySheep hidden = sheep(writer);
        hidden.hide();
        worrySheepRepository.saveAndFlush(hidden);
        report(me, "WORRY_SHEEP", hidden.getId(), "SPAM").andExpect(status().isNotFound());

        WorrySheep graduated = sheep(writer);
        graduated.graduate();
        worrySheepRepository.saveAndFlush(graduated);
        report(me, "WORRY_SHEEP", graduated.getId(), "SPAM").andExpect(status().isNotFound());

        // 조언 이력이 모자라면 보호 양은 보이지 않습니다.
        WorrySheep protectedSheep = sheep(writer, RiskLevel.CAUTION);
        report(me, "WORRY_SHEEP", protectedSheep.getId(), "SPAM").andExpect(status().isNotFound());

        // 차단 관계(양방향)
        TestUsers.Login blocker = testUsers.signIn("blocker35@test.com", "차단한목동");
        WorrySheep blockersSheep = sheep(blocker);
        blockRepository.saveAndFlush(new com.ganzithon.teolhanol.domain.report.entity.Block(user(blocker), user(me)));
        report(me, "WORRY_SHEEP", blockersSheep.getId(), "SPAM").andExpect(status().isNotFound());

        assertThat(reportRepository.count()).isZero();
    }

    @Test
    void 이미_조언을_보낸_보호_양은_신고할_수_있다() throws Exception {
        WorrySheep protectedSheep = sheep(writer, RiskLevel.CAUTION);
        wool(protectedSheep, me, "응원할게요");

        report(me, "WORRY_SHEEP", protectedSheep.getId(), "ETC").andExpect(status().isCreated());
    }

    @Test
    void 내_양에_달린_조언이_아니면_404() throws Exception {
        TestUsers.Login third = testUsers.signIn("third35@test.com", "세번째목동");
        Wool othersWool = wool(sheep(writer), third, "다른 사람에게 온 조언");

        report(me, "WOOL", othersWool.getId(), "ABUSIVE").andExpect(status().isNotFound());
        report(me, "WOOL", 99999999L, "ABUSIVE").andExpect(status().isNotFound());

        // 숨김 양에 달린 조언도 볼 수 없습니다.
        WorrySheep mine = sheep(me);
        Wool received = wool(mine, writer, "받은 조언");
        mine.hide();
        worrySheepRepository.saveAndFlush(mine);
        report(me, "WOOL", received.getId(), "ABUSIVE").andExpect(status().isNotFound());
        assertThat(reportRepository.count()).isZero();
    }

    // ---- 3회 누적 숨김 ----

    @Test
    void 서로_다른_세_명이_양을_신고하면_세_번째에_자동_숨김된다() throws Exception {
        WorrySheep sheep = sheep(writer);
        List<TestUsers.Login> reporters = others(3);

        report(reporters.get(0), "WORRY_SHEEP", sheep.getId(), "ABUSIVE").andExpect(jsonPath("$.data.hidden").value(false));
        report(reporters.get(1), "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(jsonPath("$.data.hidden").value(false));
        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);

        report(reporters.get(2), "WORRY_SHEEP", sheep.getId(), "ETC").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hidden").value(true));

        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.HIDDEN);
        // 숨긴 양은 목장·상세에서 사라지고, 더 신고할 수도 없습니다.
        mockMvc.perform(get("/farms/all/sheep").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.sheep.length()").value(0));
        detail(sheep, me).andExpect(status().isNotFound());
        report(me, "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(status().isNotFound());
    }

    @Test
    void 같은_사람의_중복_신고는_누적에_들어가지_않는다() throws Exception {
        WorrySheep sheep = sheep(writer);
        List<TestUsers.Login> reporters = others(2);
        report(reporters.get(0), "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(status().isCreated());
        report(reporters.get(0), "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(status().isConflict());
        report(reporters.get(1), "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(jsonPath("$.data.hidden").value(false));

        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
    }

    @Test
    void 받은_조언이_세_번_신고되면_주인_상세에서_빠지고_털_수는_그대로다() throws Exception {
        // 조언은 양 주인만 API로 신고할 수 있어, 앞선 두 건은 저장소로 직접 쌓습니다(서로 다른 신고자).
        WorrySheep mine = sheep(me);
        Wool bad = wool(mine, writer, "거친 조언");
        TestUsers.Login kind = testUsers.signIn("kind35@test.com", "다정한목동");
        Wool good = wool(mine, kind, "다정한 조언");
        mine.addWool(10);
        mine.addWool(10);
        worrySheepRepository.saveAndFlush(mine);
        List<TestUsers.Login> earlier = others(2);
        for (TestUsers.Login r : earlier) {
            reportRepository.saveAndFlush(new com.ganzithon.teolhanol.domain.report.entity.Report(
                    user(r), ReportTargetType.WOOL, bad.getId(),
                    com.ganzithon.teolhanol.domain.report.entity.ReportReason.ABUSIVE));
        }

        report(me, "WOOL", bad.getId(), "ABUSIVE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hidden").value(true));

        assertThat(reload(bad).isHidden()).isTrue();
        assertThat(reload(good).isHidden()).isFalse();
        detail(mine, me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wools.length()").value(1))
                .andExpect(jsonPath("$.data.wools[0].woolId").value(good.getId()))
                .andExpect(jsonPath("$.data.stage").value(reload(mine).getStage()));
        assertThat(reload(mine).getWoolCount()).isEqualTo(2);
        // 숨긴 조언은 다시 신고·차단할 수 없습니다.
        report(me, "WOOL", bad.getId(), "SPAM").andExpect(status().isNotFound());
        mockMvc.perform(post("/blocks").header("Authorization", me.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"WOOL\",\"targetId\":" + bad.getId() + "}")).andExpect(status().isNotFound());
    }

    @Test
    void 동시에_다섯_명이_신고해도_정확히_한_번만_숨김된다() throws Exception {
        WorrySheep sheep = sheep(writer);
        List<TestUsers.Login> reporters = others(5);

        List<String> results = concurrentlyRaw(reporters.stream()
                .<Callable<String>>map(r -> () -> {
                    var res = report(r, "WORRY_SHEEP", sheep.getId(), "SPAM").andReturn().getResponse();
                    return res.getStatus() + ":" + res.getContentAsString();
                })
                .toList());

        // 3명까지는 저장(그중 1명이 숨김), 숨겨진 뒤 온 신고는 볼 수 없는 양이라 404 입니다.
        assertThat(results).filteredOn(r -> r.startsWith("201")).hasSize(3);
        assertThat(results).filteredOn(r -> r.startsWith("404")).hasSize(2);
        assertThat(results).filteredOn(r -> r.contains("\"hidden\":true")).hasSize(1);
        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.HIDDEN);
        assertThat(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.WORRY_SHEEP, sheep.getId()))
                .isEqualTo(3);
    }

    // ---- 위험해 보여요 배너 ----

    @Test
    void 위험해_보여요_한_번이면_양_주인에게_상담_배너가_켜진다() throws Exception {
        WorrySheep sheep = sheep(writer);
        mockMvc.perform(get("/help/crisis/banner").header("Authorization", writer.bearer()))
                .andExpect(jsonPath("$.data.active").value(false));

        LocalDateTime before = LocalDateTime.now();
        report(me, "WORRY_SHEEP", sheep.getId(), "LOOKS_DANGEROUS").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hidden").value(false));

        LocalDateTime until = user(writer).getCrisisBannerUntil();
        assertThat(until).isAfterOrEqualTo(before.plusHours(24).minusMinutes(1))
                .isBeforeOrEqualTo(LocalDateTime.now().plusHours(24).plusMinutes(1));
        mockMvc.perform(get("/help/crisis/banner").header("Authorization", writer.bearer()))
                .andExpect(jsonPath("$.data.active").value(true));
        // 신고한 사람에게는 켜지지 않습니다.
        assertThat(user(me).getCrisisBannerUntil()).isNull();
        // 양은 숨기지 않습니다(1회).
        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
    }

    @Test
    void 조언이_위험해_보이면_조언_보낸_사람에게_배너가_켜진다() throws Exception {
        WorrySheep mine = sheep(me);
        Wool wool = wool(mine, writer, "요즘 저도 많이 힘들어요");

        report(me, "WOOL", wool.getId(), "LOOKS_DANGEROUS").andExpect(status().isCreated());

        assertThat(user(writer).getCrisisBannerUntil()).isAfter(LocalDateTime.now());
        assertThat(user(me).getCrisisBannerUntil()).isNull();
        assertThat(reload(wool).isHidden()).isFalse();
    }

    @Test
    void 위험해_보여요도_누적_숨김에_한_번으로_센다() throws Exception {
        WorrySheep sheep = sheep(writer);
        List<TestUsers.Login> reporters = others(3);
        report(reporters.get(0), "WORRY_SHEEP", sheep.getId(), "LOOKS_DANGEROUS").andExpect(status().isCreated());
        report(reporters.get(1), "WORRY_SHEEP", sheep.getId(), "SPAM").andExpect(jsonPath("$.data.hidden").value(false));
        report(reporters.get(2), "WORRY_SHEEP", sheep.getId(), "LOOKS_DANGEROUS")
                .andExpect(jsonPath("$.data.hidden").value(true));
        assertThat(reload(sheep).getStatus()).isEqualTo(WorrySheepStatus.HIDDEN);
    }

    // ---- 차단 연계 ----

    @Test
    void blockUser가_켜져_있으면_글쓴이를_차단하고_목장에서_사라진다() throws Exception {
        WorrySheep reported = sheep(writer);
        WorrySheep another = sheep(writer);
        mockMvc.perform(get("/farms/all/sheep").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.sheep.length()").value(2));

        report(me, "WORRY_SHEEP", reported.getId(), "ABUSIVE", true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.hidden").value(false));

        assertThat(blockRepository.findByBlockerIdAndBlockedId(me.userId(), writer.userId())).isPresent();
        mockMvc.perform(get("/farms/all/sheep").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.sheep.length()").value(0));
        mockMvc.perform(get("/blocks").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data[*].nickname").value(Matchers.contains("글쓴목동")));
        detail(another, me).andExpect(status().isNotFound());
    }

    @Test
    void 이미_차단한_조언_보낸_사람을_다시_차단해도_멱등이다() throws Exception {
        WorrySheep mine = sheep(me);
        Wool first = wool(mine, writer, "첫 조언");
        WorrySheep mine2 = sheep(me);
        Wool second = wool(mine2, writer, "두 번째 조언");

        report(me, "WOOL", first.getId(), "SPAM", true).andExpect(jsonPath("$.data.blocked").value(true));
        report(me, "WOOL", second.getId(), "SPAM", true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blocked").value(true));

        assertThat(blockRepository.count()).isEqualTo(1);
        assertThat(reportRepository.count()).isEqualTo(2);
    }

    @Test
    void blockUser가_꺼져_있으면_차단하지_않는다() throws Exception {
        WorrySheep sheep = sheep(writer);
        report(me, "WORRY_SHEEP", sheep.getId(), "SPAM", false).andExpect(jsonPath("$.data.blocked").value(false));
        assertThat(blockRepository.count()).isZero();
    }

    // ---- 동시 실행 도우미 ----

    private static List<Integer> concurrently(List<Callable<Integer>> calls) throws Exception {
        return concurrentlyRaw(calls);
    }

    private static <T> List<T> concurrentlyRaw(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
