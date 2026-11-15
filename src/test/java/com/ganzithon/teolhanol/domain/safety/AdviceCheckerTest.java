package com.ganzithon.teolhanol.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ganzithon.teolhanol.domain.safety.dto.AdviceCategory;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckResult;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckStatus;
import com.ganzithon.teolhanol.domain.safety.service.AdviceChecker;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 조언 다시 확인 (SAF-04). 안전 패턴은 시드 데이터를 그대로 씁니다. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class AdviceCheckerTest {

    @Autowired AdviceChecker adviceChecker;
    @Autowired PolicyProperties policy;
    @MockitoBean LlmClient llmClient;

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

    // ---- 1차 금칙어 ----

    @ParameterizedTest(name = "욕설·혐오·성적 표현: {0}")
    @ValueSource(strings = {
            "씨발 그냥 참아",
            "그런 걸로 우는 건 병신 같아",
            "병 신 같은 소리 하지 마",
            "닥치고 그냥 해 존나 쉬움",
            "한남충들은 원래 그래",
            "틀딱들 말은 듣지 마",
            "야동 보면서 스트레스 풀어"})
    void 금칙어가_있으면_LLM을_부르지_않고_보낼_수_없다(String content) {
        AdviceCheckResult result = adviceChecker.check(content);

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.BLOCKED);
        assertThat(result.category()).isEqualTo(AdviceCategory.BANNED_WORD);
        assertThat(result.suggestion()).isNull();
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest(name = "자해 조장: {0}")
    @ValueSource(strings = {
            "그렇게 힘들면 그냥 죽어버려",
            "그럴 거면 죽어 버리지 그래",
            "옥상에서 뛰어내려 버려",
            "힘들면 자해해 봐 나아질걸",
            "손목 그어 봐"})
    void 자해를_부추기는_말은_LLM을_부르지_않고_보낼_수_없다(String content) {
        AdviceCheckResult result = adviceChecker.check(content);

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.BLOCKED);
        assertThat(result.category()).isEqualTo(AdviceCategory.BANNED_WORD);
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest(name = "말리거나 경험을 나누는 말은 금칙어가 아님: {0}")
    @ValueSource(strings = {
            "자해하면 안 돼요. 힘들면 109에 전화해 봐요",
            "나도 죽고 싶을 만큼 힘든 때가 있었어. 지금은 괜찮아졌어",
            "무리하지 말고 오늘은 푹 쉬어요"})
    void 자해를_말리거나_경험을_나누는_말은_LLM_판정으로_넘어간다(String content) {
        llmReturns("NONE", "");

        AdviceCheckResult result = adviceChecker.check(content);

        assertThat(result).isEqualTo(AdviceCheckResult.pass());
        verify(llmClient).completeJson(any(LlmRequest.class), any());
    }

    // ---- 2차 LLM 어조 분류 ----

    @ParameterizedTest(name = "{1}: {0}")
    @CsvSource(delimiter = '|', value = {
            "그건 네가 처음부터 잘못한 거야 | BLAME | 그 상황에서 많이 당황했을 것 같아. 다음엔 조금 다르게 해 봐도 괜찮아.",
            "정신 차려. 그런 걸로 힘들어하면 안 돼 | LECTURE | 많이 지쳤구나. 조금 쉬었다가 천천히 다시 해 봐도 괜찮아.",
            "너만 힘든 거 아니야. 다들 그러고 살아 | COMPARE | 많이 힘들었겠다. 비슷한 마음을 겪어 본 사람으로서 응원할게.",
            "그게 고민이야? 별걸로 다 힘들어하네 ㅋㅋ | MOCK | 그 일이 마음에 크게 남았구나. 이야기해 줘서 고마워.",
            "너 그거 우울증이야. 약 먹어야 해 | DIAGNOSIS | 요즘 마음이 많이 무거워 보여. 믿을 만한 전문가와 이야기해 봐도 좋을 것 같아.",
            "교회 나가 보면 다 해결돼 | SOLICIT | 마음 기댈 곳이 있으면 좋겠다. 편한 사람에게 털어놓아 보는 것도 도움이 될 거야."})
    void 어조가_날카로우면_분류와_바꿀_문장을_제안한다(String content, AdviceCategory category, String suggestion) {
        llmReturns(category.name(), suggestion);

        AdviceCheckResult result = adviceChecker.check(content);

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.SUGGEST);
        assertThat(result.category()).isEqualTo(category);
        assertThat(result.suggestion()).isEqualTo(suggestion);
        assertThat(result.suggestion().codePointCount(0, result.suggestion().length()))
                .isLessThanOrEqualTo(policy.wool().maxLength());
    }

    @Test
    void 따뜻한_조언은_통과한다() {
        llmReturns("NONE", "");

        AdviceCheckResult result = adviceChecker.check("나도 비슷한 일이 있었어. 천천히 해도 괜찮아, 응원할게!");

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.PASS);
        assertThat(result.category()).isNull();
        assertThat(result.suggestion()).isNull();
    }

    @Test
    void 분류_요청은_조언_프롬프트와_정책_타임아웃으로_보낸다() {
        llmReturns("none", null);
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);

        adviceChecker.check("오늘 하루도 고생 많았어요");

        verify(llmClient).completeJson(request.capture(), any());
        assertThat(request.getValue().timeout()).isEqualTo(Duration.ofSeconds(policy.safety().llmTimeoutSeconds()));
        assertThat(request.getValue().retry()).isFalse();
        assertThat(request.getValue().systemPrompt())
                .contains("조언 어조 검토기")
                .contains(policy.wool().maxLength() + "자 이내")
                .doesNotContain("{maxLength}");
        assertThat(request.getValue().messages()).hasSize(1);
        assertThat(request.getValue().messages().getFirst().content()).isEqualTo("오늘 하루도 고생 많았어요");
    }

    @Test
    void 분류_코드는_대소문자와_공백을_가리지_않는다() {
        llmReturns(" lecture ", "조금 쉬어 가도 괜찮아요.");

        assertThat(adviceChecker.check("그냥 참고 해").category()).isEqualTo(AdviceCategory.LECTURE);
    }

    // ---- 바꿀 문장 길이·내용 ----

    @Test
    void 바꿀_문장이_조언_최대_길이와_같으면_그대로_제안한다() {
        String exact = "가".repeat(policy.wool().maxLength());
        llmReturns("BLAME", exact);

        AdviceCheckResult result = adviceChecker.check("그건 네 탓이야");

        assertThat(result.suggestion()).isEqualTo(exact);
    }

    @Test
    void 바꿀_문장이_조언_최대_길이를_넘으면_문장_없이_분류만_돌려준다() {
        llmReturns("BLAME", "가".repeat(policy.wool().maxLength() + 1));

        AdviceCheckResult result = adviceChecker.check("그건 네 탓이야");

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.SUGGEST);
        assertThat(result.category()).isEqualTo(AdviceCategory.BLAME);
        assertThat(result.suggestion()).isNull();
    }

    @Test
    void 바꿀_문장에_금칙어가_있으면_문장_없이_분류만_돌려준다() {
        llmReturns("MOCK", "존나 힘들었겠다. 응원할게");

        AdviceCheckResult result = adviceChecker.check("별걸로 다 힘들어하네");

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.SUGGEST);
        assertThat(result.category()).isEqualTo(AdviceCategory.MOCK);
        assertThat(result.suggestion()).isNull();
    }

    @ParameterizedTest(name = "바꿀 문장 \"{0}\" → 제안 없음")
    @ValueSource(strings = {"", "   ", "\"\"", "너만 힘든 거 아니야"})
    void 바꿀_문장이_비었거나_원문과_같으면_문장_없이_분류만_돌려준다(String suggestion) {
        llmReturns("COMPARE", suggestion);

        AdviceCheckResult result = adviceChecker.check("너만 힘든 거 아니야");

        assertThat(result.status()).isEqualTo(AdviceCheckStatus.SUGGEST);
        assertThat(result.category()).isEqualTo(AdviceCategory.COMPARE);
        assertThat(result.suggestion()).isNull();
    }

    @Test
    void 바꿀_문장_전체를_감싼_따옴표는_벗긴다() {
        llmReturns("LECTURE", "“오늘은 푹 쉬어도 괜찮아.”");

        assertThat(adviceChecker.check("정신 차려").suggestion()).isEqualTo("오늘은 푹 쉬어도 괜찮아.");
    }

    @Test
    void 문장_안의_따옴표는_그대로_둔다() {
        llmReturns("LECTURE", "'괜찮아'라는 말을 먼저 해 주고 싶어.");

        assertThat(adviceChecker.check("정신 차려").suggestion()).isEqualTo("'괜찮아'라는 말을 먼저 해 주고 싶어.");
    }

    // ---- LLM 실패 시 금칙어 판정만으로 ----

    @ParameterizedTest
    @EnumSource(LlmException.Type.class)
    void LLM이_실패하거나_시간이_넘으면_금칙어_판정만으로_통과시킨다(LlmException.Type type) {
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenThrow(new LlmException(type, "fail"));

        assertThat(adviceChecker.check("너만 힘든 거 아니야")).isEqualTo(AdviceCheckResult.pass());
    }

    @Test
    void LLM이_실패해도_금칙어는_보낼_수_없다() {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));

        assertThat(adviceChecker.check("씨발 힘내").status()).isEqualTo(AdviceCheckStatus.BLOCKED);
    }

    @Test
    void LLM에서_예기치_않은_오류가_나도_통과시킨다() {
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenThrow(new IllegalStateException("boom"));

        assertThat(adviceChecker.check("다들 그러고 살아")).isEqualTo(AdviceCheckResult.pass());
    }

    @Test
    void LLM_응답이_비어_있으면_통과시킨다() {
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenReturn(null);

        assertThat(adviceChecker.check("다들 그러고 살아")).isEqualTo(AdviceCheckResult.pass());
    }

    @ParameterizedTest(name = "분류 \"{0}\" → PASS")
    @ValueSource(strings = {"ANGRY", "BANNED_WORD", ""})
    void LLM이_모르는_분류를_돌려주면_형식_오류로_보고_통과시킨다(String category) {
        llmReturns(category, "부드럽게 바꾼 문장이에요.");

        assertThat(adviceChecker.check("다들 그러고 살아")).isEqualTo(AdviceCheckResult.pass());
    }

    @Test
    void 빈_조언은_LLM을_부르지_않는다() {
        assertThat(adviceChecker.check("   ")).isEqualTo(AdviceCheckResult.pass());
        assertThat(adviceChecker.check(null)).isEqualTo(AdviceCheckResult.pass());
        verifyNoInteractions(llmClient);
    }

    // ---- 노출·로그 ----

    @Test
    void 조언_본문과_바꿀_문장은_로그에_남기지_않는다(CapturedOutput output) {
        llmReturns("COMPARE", "비밀스러운제안문장이에요");
        adviceChecker.check("비밀스러운조언본문이에요 너만 힘든 거 아니야");
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));
        adviceChecker.check("비밀스러운두번째본문이에요");
        adviceChecker.check("비밀스러운금칙어본문 씨발");

        assertThat(output.getAll())
                .contains("조언 확인")
                .doesNotContain("비밀스러운");
    }

    @ParameterizedTest
    @EnumSource(AdviceCategory.class)
    void 모든_분류에_사용자에게_보여_줄_이름과_안내_문구가_있다(AdviceCategory category) {
        assertThat(category.getLabel()).isNotBlank();
        assertThat(category.getMessage()).isNotBlank().matches(".*[.?]$").doesNotContain("하지 마");
    }
}
