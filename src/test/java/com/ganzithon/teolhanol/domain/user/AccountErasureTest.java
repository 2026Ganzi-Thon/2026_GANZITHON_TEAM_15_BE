package com.ganzithon.teolhanol.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.entity.Report;
import com.ganzithon.teolhanol.domain.report.entity.ReportReason;
import com.ganzithon.teolhanol.domain.report.entity.ReportTargetType;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisLog;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisSource;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheepItem;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.wool.entity.Thanks;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.support.TestUsers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class AccountErasureTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired FarmRepository farmRepository;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;

    TestUsers.Login me;
    TestUsers.Login other;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("leave@test.com", "떠날 목동");
        other = testUsers.signIn("stay@test.com", "남을 목동");
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> {
            for (String entity : new String[]{"PushSubscription", "Thanks", "Wool", "Report", "Block", "WorrySheep", "CrisisLog",
                    "CareMessage", "CareSummary", "Diary", "MySheepItem", "MySheep", "User"}) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
        });
    }

    private long count(String jpql, Object... uid) {
        var q = em.createQuery(jpql, Long.class);
        if (uid.length > 0) {
            q.setParameter("uid", uid[0]);
        }
        return q.getSingleResult();
    }

    /** 사용자 한 명 분량의 기록·양·위기 로그 */
    private void records(TestUsers.Login who, int diaries) {
        tx.executeWithoutResult(s -> {
            User user = em.find(User.class, who.userId());
            for (int i = 0; i < diaries; i++) {
                Diary d = new Diary(user, Emotion.TIRED);
                d.startCare(CareType.COMFORT);
                em.persist(d);
                em.persist(new CareMessage(d, MessageRole.USER, "이야기 " + i));
                em.persist(new CareMessage(d, MessageRole.AI, "답 " + i));
                em.persist(new CareSummary(d, "카드 " + i, LocalDateTime.now()));
            }
            em.persist(new CrisisLog(user, CrisisSource.MESSAGE, false, RiskLevel.SAFE, "NONE"));
        });
    }

    private void sheepWithItem(TestUsers.Login who, int wool, int yarn) {
        tx.executeWithoutResult(s -> {
            MySheep sheep = em.createQuery("select s from MySheep s where s.user.id = :uid", MySheep.class)
                    .setParameter("uid", who.userId()).getSingleResult();
            for (int i = 0; i < wool; i++) {
                sheep.addWool();
            }
            em.persist(new MySheepItem(sheep, "SCARF"));
            em.createQuery("update User u set u.yarnCount = :y where u.id = :id")
                    .setParameter("y", yarn).setParameter("id", who.userId()).executeUpdate();
        });
    }

    @Test
    void 기록_전체_삭제는_기록만_지우고_털과_털실과_아이템은_유지한다() throws Exception {
        records(me, 2);
        records(other, 1);
        sheepWithItem(me, 4, 6);

        mockMvc.perform(delete("/me/records").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedDiaries").value(2));

        assertThat(count("select count(d) from Diary d where d.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(m) from CareMessage m where m.diary.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(s) from CareSummary s where s.diary.user.id = :uid", me.userId())).isZero();
        // 유지되는 것
        mockMvc.perform(get("/my-sheep").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.woolCount").value(4))
                .andExpect(jsonPath("$.data.yarnCount").value(6));
        assertThat(count("select count(i) from MySheepItem i where i.mySheep.user.id = :uid", me.userId())).isEqualTo(1);
        assertThat(count("select count(c) from CrisisLog c where c.user.id = :uid", me.userId())).isEqualTo(1);
        // 다른 사람 기록은 그대로
        assertThat(count("select count(d) from Diary d where d.user.id = :uid", other.userId())).isEqualTo(1);
        assertThat(count("select count(m) from CareMessage m where m.diary.user.id = :uid", other.userId())).isEqualTo(2);
    }

    @Test
    void 기록이_없어도_기록_전체_삭제는_성공한다() throws Exception {
        mockMvc.perform(delete("/me/records").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedDiaries").value(0));
    }

    @Test
    void 탈퇴하면_내_데이터가_모두_사라지고_다른_사람_데이터는_남는다() throws Exception {
        Farm farm = farmRepository.findAll().get(0);
        records(me, 2);
        records(other, 1);
        sheepWithItem(me, 4, 6);
        sheepWithItem(other, 2, 3);

        long[] ids = new long[6]; // 0 내 고민, 1 남의 고민, 2 남이 내 고민에 준 조언, 3 내가 남의 고민에 준 조언, 4 남이 남 고민에 준 조언(다른 사람), 5 남이 한 신고
        tx.executeWithoutResult(s -> {
            User meU = em.find(User.class, me.userId());
            User otherU = em.find(User.class, other.userId());
            User third = new User("third@test.com", "hash", "제삼의 목동");
            em.persist(third);
            WorrySheep mine = new WorrySheep(meU, farm, "내 고민", RiskLevel.SAFE);
            WorrySheep theirs = new WorrySheep(otherU, farm, "남의 고민", RiskLevel.SAFE);
            em.persist(mine);
            em.persist(theirs);
            Wool onMine = new Wool(mine, otherU, "남이 내 고민에 준 조언", true);
            Wool byMe = new Wool(theirs, meU, "내가 남의 고민에 준 조언", true);
            Wool thirdOnTheirs = new Wool(theirs, third, "제삼자가 남의 고민에 준 조언", true);
            em.persist(onMine);
            em.persist(byMe);
            em.persist(thirdOnTheirs);
            em.persist(new Thanks(onMine));
            em.persist(new Thanks(byMe));
            em.persist(new Thanks(thirdOnTheirs));
            em.persist(new Report(meU, ReportTargetType.WOOL, thirdOnTheirs.getId(), ReportReason.ETC));       // 내가 한 신고
            em.persist(new Report(otherU, ReportTargetType.WORRY_SHEEP, mine.getId(), ReportReason.ETC));       // 내 고민 신고
            em.persist(new Report(otherU, ReportTargetType.WOOL, byMe.getId(), ReportReason.ETC));              // 내 조언 신고
            Report others = new Report(otherU, ReportTargetType.WOOL, thirdOnTheirs.getId(), ReportReason.ETC); // 나와 무관
            em.persist(others);
            em.persist(new Block(meU, otherU));
            em.persist(new Block(otherU, meU));
            em.persist(new Block(otherU, third));
            em.persist(new PushSubscription(meU, "https://fcm.googleapis.com/fcm/send/leave", "p256dh", "auth"));
            em.persist(new PushSubscription(otherU, "https://fcm.googleapis.com/fcm/send/stay", "p256dh", "auth"));
            ids[0] = mine.getId();
            ids[1] = theirs.getId();
            ids[2] = onMine.getId();
            ids[3] = byMe.getId();
            ids[4] = thirdOnTheirs.getId();
            ids[5] = others.getId();
        });

        mockMvc.perform(delete("/me").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 내 데이터는 어디에도 없다
        assertThat(count("select count(u) from User u where u.id = :uid", me.userId())).isZero();
        assertThat(count("select count(d) from Diary d where d.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(s) from MySheep s where s.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(i) from MySheepItem i where i.mySheep.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(c) from CrisisLog c where c.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(s) from WorrySheep s where s.user.id = :uid", me.userId())).isZero();
        assertThat(count("select count(w) from Wool w where w.sender.id = :uid", me.userId())).isZero();
        assertThat(count("select count(r) from Report r where r.reporter.id = :uid", me.userId())).isZero();
        assertThat(count("select count(b) from Block b where b.blocker.id = :uid or b.blocked.id = :uid", me.userId())).isZero();
        assertThat(count("select count(p) from PushSubscription p where p.user.id = :uid", me.userId())).isZero();
        // 내 고민에 달린 조언, 내 조언, 그에 대한 신고도 사라진다
        assertThat(count("select count(w) from Wool w where w.id in (" + ids[2] + "," + ids[3] + ")")).isZero();
        assertThat(count("select count(t) from Thanks t where t.wool.id in (" + ids[2] + "," + ids[3] + ")")).isZero();
        assertThat(count("select count(r) from Report r where r.targetId in (" + ids[0] + "," + ids[2] + "," + ids[3] + ")")).isZero();

        // 다른 사람 데이터는 그대로
        assertThat(count("select count(u) from User u where u.id = :uid", other.userId())).isEqualTo(1);
        assertThat(count("select count(s) from WorrySheep s where s.id = " + ids[1])).isEqualTo(1);
        assertThat(count("select count(w) from Wool w where w.id = " + ids[4])).isEqualTo(1);
        assertThat(count("select count(t) from Thanks t where t.wool.id = " + ids[4])).isEqualTo(1);
        assertThat(count("select count(r) from Report r where r.id = " + ids[5])).isEqualTo(1);
        assertThat(count("select count(b) from Block b where b.blocker.id = :uid", other.userId())).isEqualTo(1);
        assertThat(count("select count(p) from PushSubscription p where p.user.id = :uid", other.userId())).isEqualTo(1);
        assertThat(count("select count(d) from Diary d where d.user.id = :uid", other.userId())).isEqualTo(1);
        assertThat(count("select count(s) from MySheep s where s.user.id = :uid", other.userId())).isEqualTo(1);
    }

    @Test
    void 탈퇴한_계정으로는_로그인할_수_없고_같은_이메일로_새로_가입할_수_있다() throws Exception {
        mockMvc.perform(delete("/me").header("Authorization", me.bearer())).andExpect(status().isOk());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@test.com\",\"password\":\"" + TestUsers.PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@test.com\",\"password\":\"warmwool123\",\"nickname\":\"떠날 목동\",\"agreed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sheepWoolCount").value(0));
    }

    @Test
    void 이미_탈퇴한_계정의_토큰으로_다시_탈퇴하면_401() throws Exception {
        mockMvc.perform(delete("/me").header("Authorization", me.bearer())).andExpect(status().isOk());

        mockMvc.perform(delete("/me").header("Authorization", me.bearer())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/me/records").header("Authorization", me.bearer())).andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(delete("/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/me/records")).andExpect(status().isUnauthorized());
    }
}
