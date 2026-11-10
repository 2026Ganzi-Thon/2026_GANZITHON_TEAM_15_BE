package com.ganzithon.teolhanol.domain.sheep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepItemRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.support.TestUsers;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class SheepItemsTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired MySheepItemRepository mySheepItemRepository;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("item@test.com", "꾸미기 목동");
    }

    @AfterEach
    void cleanUp() {
        mySheepItemRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** 엔티티에 털실을 더하는 메서드가 없어서(털실은 목장 기능에서 쌓임) 테스트에서만 필드를 채운다 */
    private void giveYarn(Long userId, int yarn) throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        var f = User.class.getDeclaredField("yarnCount");
        f.setAccessible(true);
        f.set(user, yarn);
        userRepository.saveAndFlush(user);
    }

    private int yarnOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getYarnCount();
    }

    private ResultActions save(String json, TestUsers.Login who) throws Exception {
        return mockMvc.perform(put("/my-sheep/items").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 처음에는_네_가지_아이템이_모두_못_가진_상태로_보인다() throws Exception {
        giveYarn(me.userId(), 5);

        mockMvc.perform(get("/my-sheep/items").header("Authorization", me.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yarnCount").value(5))
                .andExpect(jsonPath("$.data.spentYarn").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].code").value("SCARF"))
                .andExpect(jsonPath("$.data.items[0].name").value("목도리"))
                .andExpect(jsonPath("$.data.items[0].yarnCost").value(3))
                .andExpect(jsonPath("$.data.items[0].owned").value(false))
                .andExpect(jsonPath("$.data.items[3].code").value("RIBBON"));
    }

    @Test
    void 털실로_아이템을_만들면_털실이_차감되고_가진_것으로_표시된다() throws Exception {
        giveYarn(me.userId(), 10);

        save("{\"craft\":[\"SCARF\",\"HAT\"]}", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spentYarn").value(8))
                .andExpect(jsonPath("$.data.yarnCount").value(2))
                .andExpect(jsonPath("$.data.items[0].owned").value(true))
                .andExpect(jsonPath("$.data.items[0].equipped").value(false))
                .andExpect(jsonPath("$.data.items[1].owned").value(true))
                .andExpect(jsonPath("$.data.items[2].owned").value(false));

        assertThat(yarnOf(me.userId())).isEqualTo(2);
        assertThat(mySheepItemRepository.count()).isEqualTo(2);
    }

    @Test
    void 털실이_모자라면_409이고_아무것도_바뀌지_않는다() throws Exception {
        giveYarn(me.userId(), 5);

        // 목도리(3) + 모자(5) = 8 > 5. 목도리만으로는 되지만 함께 요청하면 전부 거절
        save("{\"craft\":[\"SCARF\",\"HAT\"],\"equipped\":[\"SCARF\"]}", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SHEEP_YARN_NOT_ENOUGH"));

        assertThat(yarnOf(me.userId())).isEqualTo(5);
        assertThat(mySheepItemRepository.count()).isZero();
    }

    @Test
    void 이미_가진_아이템을_다시_만들어도_털실을_쓰지_않는다() throws Exception {
        giveYarn(me.userId(), 10);
        save("{\"craft\":[\"BELL\"]}", me).andExpect(jsonPath("$.data.yarnCount").value(2));

        save("{\"craft\":[\"BELL\",\"BELL\"]}", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spentYarn").value(0))
                .andExpect(jsonPath("$.data.yarnCount").value(2));
        assertThat(mySheepItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 착용을_저장하면_나의_양_조회에_반영되고_해제도_된다() throws Exception {
        giveYarn(me.userId(), 20);
        save("{\"craft\":[\"SCARF\",\"HAT\",\"RIBBON\"],\"equipped\":[\"SCARF\",\"RIBBON\"]}", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].equipped").value(true))
                .andExpect(jsonPath("$.data.items[1].equipped").value(false))
                .andExpect(jsonPath("$.data.items[3].equipped").value(true));

        mockMvc.perform(get("/my-sheep").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.equippedItems.length()").value(2))
                .andExpect(jsonPath("$.data.equippedItems[0]").value("SCARF"))
                .andExpect(jsonPath("$.data.equippedItems[1]").value("RIBBON"));

        // 착용 목록만 바꾸면 만든 아이템은 그대로, 털실도 그대로
        save("{\"equipped\":[\"HAT\"]}", me).andExpect(jsonPath("$.data.spentYarn").value(0))
                .andExpect(jsonPath("$.data.items[0].equipped").value(false))
                .andExpect(jsonPath("$.data.items[1].equipped").value(true));
        // 빈 목록이면 모두 해제, 생략(null)이면 그대로
        save("{\"equipped\":[]}", me).andExpect(jsonPath("$.data.items[1].equipped").value(false));
        save("{\"equipped\":[\"HAT\"]}", me).andExpect(status().isOk());
        save("{}", me).andExpect(jsonPath("$.data.items[1].equipped").value(true));
        mockMvc.perform(get("/my-sheep").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.equippedItems.length()").value(1));
    }

    @Test
    void 만들지_않은_아이템은_착용할_수_없다() throws Exception {
        giveYarn(me.userId(), 10);

        save("{\"equipped\":[\"SCARF\"]}", me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SHEEP_ITEM_NOT_OWNED"));
        // 같은 요청에서 만들며 착용하는 것은 가능
        save("{\"craft\":[\"SCARF\"],\"equipped\":[\"SCARF\"]}", me).andExpect(status().isOk());
    }

    @Test
    void 알_수_없는_아이템_코드는_400이고_털실은_그대로() throws Exception {
        giveYarn(me.userId(), 10);

        save("{\"craft\":[\"CROWN\"]}", me).andExpect(status().isBadRequest());
        save("{\"craft\":[\"BELL\"],\"equipped\":[\"crown\"]}", me).andExpect(status().isBadRequest());

        assertThat(yarnOf(me.userId())).isEqualTo(10);
        assertThat(mySheepItemRepository.count()).isZero();
    }

    @Test
    void 꾸미기는_털_단계에_영향을_주지_않는다() throws Exception {
        giveYarn(me.userId(), 10);
        MySheep sheep = mySheepRepository.findByUserId(me.userId()).orElseThrow();
        for (int i = 0; i < 3; i++) {
            sheep.addWool();
        }
        mySheepRepository.saveAndFlush(sheep);

        save("{\"craft\":[\"SCARF\"],\"equipped\":[\"SCARF\"]}", me).andExpect(status().isOk());

        mockMvc.perform(get("/my-sheep").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.stage").value(2))
                .andExpect(jsonPath("$.data.woolCount").value(3))
                .andExpect(jsonPath("$.data.yarnCount").value(7));
    }

    @Test
    void 다른_사용자의_아이템과_털실은_섞이지_않는다() throws Exception {
        TestUsers.Login other = testUsers.signIn("other@test.com", "다른 목동");
        giveYarn(me.userId(), 10);
        giveYarn(other.userId(), 10);
        save("{\"craft\":[\"HAT\"],\"equipped\":[\"HAT\"]}", me).andExpect(status().isOk());

        mockMvc.perform(get("/my-sheep/items").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.yarnCount").value(10))
                .andExpect(jsonPath("$.data.items[1].owned").value(false));
        assertThat(yarnOf(other.userId())).isEqualTo(10);
    }

    @Test
    void 동시에_같은_아이템을_두_번_만들어도_털실은_한_번만_쓴다() throws Exception {
        giveYarn(me.userId(), 10);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> save("{\"craft\":[\"HAT\"]}", me).andReturn().getResponse().getStatus();
            Future<Integer> a = pool.submit(call);
            Future<Integer> b = pool.submit(call);
            assertThat(List.of(a.get(), b.get())).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(yarnOf(me.userId())).isEqualTo(5);
        assertThat(mySheepItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/my-sheep/items")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/my-sheep/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
