package com.ganzithon.teolhanol.domain.sheep;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheepItem;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepItemRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class MySheepApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired MySheepItemRepository mySheepItemRepository;

    TestUsers.Login me;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = testUsers.signIn("sheep@test.com", "양 목동");
    }

    @AfterEach
    void cleanUp() {
        mySheepItemRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void giveWool(int count) {
        MySheep sheep = mySheepRepository.findByUserId(me.userId()).orElseThrow();
        for (int i = 0; i < count; i++) {
            sheep.addWool();
        }
        mySheepRepository.saveAndFlush(sheep);
    }

    private ResultActions getSheep() throws Exception {
        return mockMvc.perform(get("/my-sheep").header("Authorization", me.bearer()));
    }

    @Test
    void 가입_직후는_1단계_털_0() throws Exception {
        getSheep().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value(1))
                .andExpect(jsonPath("$.data.stageName").value("털 깎은 양"))
                .andExpect(jsonPath("$.data.woolCount").value(0))
                .andExpect(jsonPath("$.data.stageStartAt").value(0))
                .andExpect(jsonPath("$.data.nextStageAt").value(3))
                .andExpect(jsonPath("$.data.woolToNextStage").value(3))
                .andExpect(jsonPath("$.data.yarnCount").value(0))
                .andExpect(jsonPath("$.data.equippedItems").isEmpty());
    }

    @Test
    void 털_2올은_아직_1단계이고_3올에서_2단계() throws Exception {
        giveWool(2);
        getSheep().andExpect(jsonPath("$.data.stage").value(1)).andExpect(jsonPath("$.data.woolToNextStage").value(1));

        giveWool(1);
        getSheep().andExpect(jsonPath("$.data.stage").value(2))
                .andExpect(jsonPath("$.data.stageName").value("보송한 양"))
                .andExpect(jsonPath("$.data.stageStartAt").value(3))
                .andExpect(jsonPath("$.data.nextStageAt").value(6))
                .andExpect(jsonPath("$.data.woolToNextStage").value(3));
    }

    @Test
    void 털_6올_9올_12올에서_단계가_오른다() throws Exception {
        giveWool(6);
        getSheep().andExpect(jsonPath("$.data.stage").value(3)).andExpect(jsonPath("$.data.stageName").value("포근한 양"));
        giveWool(3);
        getSheep().andExpect(jsonPath("$.data.stage").value(4)).andExpect(jsonPath("$.data.stageName").value("몽글몽글 양"));
        giveWool(3);
        getSheep().andExpect(jsonPath("$.data.stage").value(5)).andExpect(jsonPath("$.data.stageName").value("구름 양"));
    }

    @Test
    void 마지막_단계는_다음_단계가_없고_털이_더_쌓여도_5단계() throws Exception {
        giveWool(15);
        getSheep().andExpect(jsonPath("$.data.stage").value(5))
                .andExpect(jsonPath("$.data.woolCount").value(15))
                .andExpect(jsonPath("$.data.nextStageAt").doesNotExist())
                .andExpect(jsonPath("$.data.woolToNextStage").value(0));
    }

    @Test
    void 털실과_착용_아이템_코드를_돌려준다() throws Exception {
        var user = userRepository.findById(me.userId()).orElseThrow();
        ReflectionYarn.set(user, 7);
        userRepository.saveAndFlush(user);
        MySheep sheep = mySheepRepository.findByUserId(me.userId()).orElseThrow();
        MySheepItem worn = new MySheepItem(sheep, "SCARF");
        ReflectionYarn.equip(worn);
        mySheepItemRepository.saveAndFlush(worn);
        mySheepItemRepository.saveAndFlush(new MySheepItem(sheep, "HAT"));

        getSheep().andExpect(jsonPath("$.data.yarnCount").value(7))
                .andExpect(jsonPath("$.data.equippedItems[0]").value("SCARF"))
                .andExpect(jsonPath("$.data.equippedItems.length()").value(1));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/my-sheep")).andExpect(status().isUnauthorized());
    }

    /** 엔티티에 setter가 없어서 테스트에서만 필드를 직접 채운다 */
    static class ReflectionYarn {
        static void set(Object user, int yarn) {
            setField(user, "yarnCount", yarn);
        }

        static void equip(Object item) {
            setField(item, "equipped", true);
        }

        private static void setField(Object target, String name, Object value) {
            try {
                var f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
