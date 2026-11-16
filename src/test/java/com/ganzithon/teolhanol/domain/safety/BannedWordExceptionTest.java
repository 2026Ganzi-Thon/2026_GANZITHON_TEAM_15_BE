package com.ganzithon.teolhanol.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.domain.safety.dto.AdviceCategory;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckResult;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckStatus;
import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import com.ganzithon.teolhanol.domain.safety.service.AdviceChecker;
import com.ganzithon.teolhanol.domain.safety.service.BannedWordExceptions;
import com.ganzithon.teolhanol.domain.safety.service.BannedWordFilter;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.user.service.NicknameReason;
import com.ganzithon.teolhanol.domain.user.service.NicknameValidator;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 금칙어 오탐 예외 (#93). 금칙어가 걸린 자리를 예외 표현이 감싸면 통과하고, 감싸지 못한 금칙어가 남으면 막습니다.
 * 조언 다시 확인과 닉네임 검사 두 경로를 모두 봅니다. 안전 패턴은 시드 데이터를 그대로 씁니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
@ExtendWith(OutputCaptureExtension.class)
class BannedWordExceptionTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestUsers testUsers;
    @Autowired BannedWordFilter bannedWordFilter;
    @Autowired BannedWordExceptions bannedWordExceptions;
    @Autowired AdviceChecker adviceChecker;
    @Autowired NicknameValidator nicknameValidator;
    @Autowired SafetyPatternRepository safetyPatternRepository;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean LlmClient llmClient;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            mySheepRepository.findByUserId(userId).ifPresent(mySheepRepository::delete);
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
    }

    private void llmReturnsNone() {
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> {
            var constructor = ((Class<?>) inv.getArgument(1)).getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance("NONE", "");
        });
    }

    // ---- 검사기 ----

    @ParameterizedTest(name = "오탐 통과: {0}")
    @ValueSource(strings = {
            "불이 꺼져서 무서웠어요",
            "휴대폰이 꺼져 있었어요",
            "핸드폰이 꺼져서 연락을 못 했어요",
            "갑자기 화면이 꺼져버렸어요",
            "TV가 꺼져서 조용했어요",
            "꺼져가는 불씨도 다시 살아나요",
            "시험이 닥쳐서 불안했어요",
            "닥쳐올 일이 걱정돼도 괜찮아요",
            "어떤 일이 닥쳐도 응원할게요",
            "바닥 쳐도 다시 올라올 수 있어요",
            "그게 모든 일의 시발점이었어요",
            "시발역에서 기차를 탔어요",
            "다시 발걸음을 내디뎌 봐요",
            "불이 꺼져서 무서웠는데 시험이 닥쳐서 더 힘들었어요"})
    void 예외_표현이_감싼_금칙어는_넘어간다(String text) {
        assertThat(bannedWordFilter.contains(text)).isFalse();
    }

    @ParameterizedTest(name = "차단: {0}")
    @ValueSource(strings = {
            "꺼져",
            "그냥 꺼져",
            "꺼져버려",
            "꺼져라",
            "꺼져 줘",
            "꺼 져",
            "입 닥쳐",
            "닥쳐라",
            "닥쳐",
            "시발",
            "시발 점점 짜증나",
            "다 시발 같아",
            "불이 꺼져서 무서웠어요. 그냥 꺼져",
            "시발점 같은 소리 하네 시발",
            "시험이 닥쳐서 힘든 건 알겠는데 닥쳐"})
    void 예외로_감싸지_못한_금칙어가_남으면_막는다(String text) {
        assertThat(bannedWordFilter.contains(text)).isTrue();
    }

    @Test
    void 예외_파일은_클래스패스에서_읽는다() {
        assertThat(bannedWordExceptions.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void 예외_파일이_없으면_경고만_남기고_예외_없이_검사한다(CapturedOutput output) {
        BannedWordExceptions none = new BannedWordExceptions(new ClassPathResource("seed/no-such-file.json"), objectMapper);
        BannedWordFilter filter = new BannedWordFilter(seededRepository(), none);

        assertThat(none.size()).isZero();
        assertThat(output.getAll()).contains("금칙어 예외 파일을 읽지 못해");
        assertThat(filter.contains("불이 꺼져서 무서웠어요")).isTrue();
        assertThat(filter.contains("포근한 양털")).isFalse();
    }

    @ParameterizedTest(name = "형식 오류: {0}")
    @ValueSource(strings = {"{ 꺼져: [", "[\"불이 꺼져\"]", "null", ""})
    void 예외_파일_형식이_틀려도_예외_없이_검사한다(String content, CapturedOutput output) {
        BannedWordExceptions broken = new BannedWordExceptions(resource(content), objectMapper);
        BannedWordFilter filter = new BannedWordFilter(seededRepository(), broken);

        assertThat(broken.size()).isZero();
        assertThat(output.getAll()).contains("예외 없이 검사합니다");
        assertThat(filter.contains("불이 꺼져서 무서웠어요")).isTrue();
    }

    @Test
    void 형식이_틀린_항목과_금칙어가_없는_표현은_건너뛴다() {
        String json = """
                {"_comment": "설명", "꺼져": ["불이 꺼져", 3, "", "아무 상관 없는 말"], "닥쳐": "닥쳐올"}
                """;
        BannedWordFilter filter = new BannedWordFilter(seededRepository(), new BannedWordExceptions(resource(json), objectMapper));

        assertThat(filter.contains("불이 꺼져서 무서웠어요")).isFalse();
        assertThat(filter.contains("아무 상관 없는 말 꺼져")).isTrue();
        assertThat(filter.contains("닥쳐올 일")).isTrue();
    }

    private SafetyPatternRepository seededRepository() {
        List<SafetyPattern> seeded = safetyPatternRepository.findAllByCategory(PatternCategory.BANNED_WORD);
        SafetyPatternRepository repository = mock(SafetyPatternRepository.class);
        when(repository.findAllByCategory(PatternCategory.BANNED_WORD)).thenReturn(seeded);
        return repository;
    }

    private static ByteArrayResource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    // ---- 조언 다시 확인 ----

    @ParameterizedTest(name = "조언 통과: {0}")
    @ValueSource(strings = {
            "나도 불이 꺼져서 무서웠던 적이 있어요. 괜찮아질 거예요",
            "휴대폰이 꺼져 있었으면 연락이 안 될 수도 있죠",
            "시험이 닥쳐서 불안한 마음 이해해요",
            "지금이 새로운 시발점이 될 수 있어요"})
    void 오탐_표현이_든_조언은_LLM_판정으로_넘어간다(String content) {
        llmReturnsNone();

        AdviceCheckResult result = adviceChecker.check(content);

        assertThat(result).isEqualTo(AdviceCheckResult.pass());
        verify(llmClient).completeJson(any(LlmRequest.class), any());
    }

    @ParameterizedTest(name = "조언 차단: {0}")
    @ValueSource(strings = {
            "불이 꺼져서 무서웠다고? 그냥 꺼져",
            "입 닥쳐",
            "시발 그걸 왜 고민해"})
    void 실제_욕설이_든_조언은_계속_보낼_수_없다(String content) {
        AdviceCheckResult result = adviceChecker.check(content);

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.BLOCKED);
        assertThat(result.category()).isEqualTo(AdviceCategory.BANNED_WORD);
        verifyNoInteractions(llmClient);
    }

    // ---- 닉네임 ----

    @ParameterizedTest(name = "닉네임 통과: {0}")
    @ValueSource(strings = {"불이꺼져요", "불이 꺼져요", "시발점", "닥쳐올 봄"})
    void 오탐_표현이_든_닉네임은_쓸_수_있다(String nickname) {
        assertThat(nicknameValidator.check(nickname)).isEmpty();
    }

    @ParameterizedTest(name = "닉네임 차단: {0}")
    @ValueSource(strings = {"꺼져라", "꺼져 목동", "닥쳐 목동", "시발목동", "불이꺼져 꺼져"})
    void 실제_욕설이_든_닉네임은_계속_쓸_수_없다(String nickname) {
        assertThat(nicknameValidator.check(nickname)).contains(NicknameReason.NOT_ALLOWED);
    }

    @Test
    void 사용_가능_확인_API에서_불이꺼져요는_쓸_수_있다() throws Exception {
        mockMvc.perform(get("/auth/nickname/availability").param("nickname", "불이꺼져요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/auth/nickname/availability").param("nickname", "꺼져 목동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_ALLOWED"));
    }

    @Test
    void 오탐_표현으로_닉네임을_바꿀_수_있다() throws Exception {
        TestUsers.Login me = testUsers.signIn("banned-exception@test.com", "다정한 목동");
        createdUserIds.add(me.userId());

        mockMvc.perform(put("/users/me/nickname").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"불이꺼져요\"}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(me.userId()).orElseThrow().getNickname()).isEqualTo("불이꺼져요");

        mockMvc.perform(put("/users/me/nickname").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"꺼져 목동\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NICKNAME_UNAVAILABLE"));
        assertThat(userRepository.findById(me.userId()).orElseThrow().getNickname()).isEqualTo("불이꺼져요");
    }
}
