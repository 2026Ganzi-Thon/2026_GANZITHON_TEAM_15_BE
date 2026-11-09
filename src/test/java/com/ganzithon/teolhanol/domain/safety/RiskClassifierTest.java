package com.ganzithon.teolhanol.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ganzithon.teolhanol.domain.safety.dto.RiskResult;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisLog;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisSource;
import com.ganzithon.teolhanol.domain.safety.repository.CrisisLogRepository;
import com.ganzithon.teolhanol.domain.safety.service.RiskClassifier;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.llm.LlmClient;
import com.ganzithon.teolhanol.global.llm.LlmException;
import com.ganzithon.teolhanol.global.llm.LlmRequest;
import com.ganzithon.teolhanol.support.TestUsers;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestUsers.class)
class RiskClassifierTest {

    @Autowired RiskClassifier riskClassifier;
    @Autowired CrisisLogRepository crisisLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired TestUsers testUsers;
    @MockitoBean LlmClient llmClient;

    User user;

    @BeforeEach
    void setUp() {
        cleanUp();
        user = userRepository.findById(testUsers.signIn("risk@test.com", "위험도 목동").userId()).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        crisisLogRepository.deleteAll();
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void llmReturns(String level, String reason) {
        when(llmClient.completeJson(any(LlmRequest.class), any())).thenAnswer(inv -> newVerdict(inv.getArgument(1), level, reason));
    }

    @SuppressWarnings("unchecked")
    private static <T> T newVerdict(Class<T> type, String level, String reason) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (T) constructor.newInstance(level, reason);
    }

    private User reload() {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    void 위기_키워드가_걸리면_LLM이_안전이라_해도_위기이고_배너가_켜진다() {
        llmReturns("safe", "NONE");

        RiskResult result = riskClassifier.classify("요즘 너무 힘들어서 죽고 싶다는 생각이 들어요", CrisisSource.MESSAGE, user);

        assertThat(result.level()).isEqualTo(RiskLevel.CRISIS);
        assertThat(result.keywordHit()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("KEYWORD");
        LocalDateTime until = reload().getCrisisBannerUntil();
        assertThat(until).isAfter(LocalDateTime.now().plusHours(23)).isBefore(LocalDateTime.now().plusHours(25));
    }

    @Test
    void 키워드는_띄어쓰기가_달라도_걸린다() {
        llmReturns("safe", "NONE");

        RiskResult result = riskClassifier.classify("죽 고 싶 어", CrisisSource.WORRY, user);

        assertThat(result.keywordHit()).isTrue();
        assertThat(result.level()).isEqualTo(RiskLevel.CRISIS);
    }

    @Test
    void 키워드가_없어도_LLM이_위기로_보면_위기() {
        llmReturns("crisis", "SUICIDAL_IDEATION");

        RiskResult result = riskClassifier.classify("모두에게 짐이 되느니 조용히 떠나는 게 낫겠어요", CrisisSource.MESSAGE, user);

        assertThat(result.level()).isEqualTo(RiskLevel.CRISIS);
        assertThat(result.keywordHit()).isFalse();
        assertThat(result.llmLevel()).isEqualTo(RiskLevel.CRISIS);
        assertThat(result.reasonCode()).isEqualTo("SUICIDAL_IDEATION");
        assertThat(reload().getCrisisBannerUntil()).isNotNull();
    }

    @Test
    void LLM이_주의로_보면_주의이고_배너는_켜지지_않는다() {
        llmReturns("caution", "HOPELESSNESS");

        RiskResult result = riskClassifier.classify("아무것도 의미 없는 것 같아요", CrisisSource.MESSAGE, user);

        assertThat(result.level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(reload().getCrisisBannerUntil()).isNull();
    }

    @Test
    void 안전한_글은_안전() {
        llmReturns("safe", "NONE");

        RiskResult result = riskClassifier.classify("오늘 발표가 걱정돼서 잠이 안 와요", CrisisSource.WORRY, user);

        assertThat(result.level()).isEqualTo(RiskLevel.SAFE);
        assertThat(result.llmFailed()).isFalse();
    }

    @Test
    void LLM이_실패하면_키워드_없을_때_주의() {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));

        RiskResult result = riskClassifier.classify("오늘 발표가 걱정돼요", CrisisSource.MESSAGE, user);

        assertThat(result.level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(result.llmFailed()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("LLM_FAILED");
        assertThat(reload().getCrisisBannerUntil()).isNull();
    }

    @Test
    void LLM이_실패해도_키워드가_걸리면_위기() {
        when(llmClient.completeJson(any(LlmRequest.class), any()))
                .thenThrow(new LlmException(LlmException.Type.UNAVAILABLE, "no key"));

        RiskResult result = riskClassifier.classify("자해하고 싶은 마음이 들어요", CrisisSource.MESSAGE, user);

        assertThat(result.level()).isEqualTo(RiskLevel.CRISIS);
        assertThat(result.llmFailed()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("KEYWORD");
        assertThat(reload().getCrisisBannerUntil()).isNotNull();
    }

    @Test
    void 알_수_없는_등급을_받으면_실패로_보고_주의() {
        llmReturns("whatever", "X");

        RiskResult result = riskClassifier.classify("그냥 피곤해요", CrisisSource.MESSAGE, user);

        assertThat(result.level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(result.llmFailed()).isTrue();
    }

    @Test
    void 판정은_crisis_logs에_남고_본문은_저장되지_않는다() {
        llmReturns("caution", " hopelessness ");

        riskClassifier.classify("비밀스러운 본문 내용", CrisisSource.WORRY, user);

        CrisisLog log = crisisLogRepository.findAll().get(0);
        assertThat(log.getSource()).isEqualTo(CrisisSource.WORRY);
        assertThat(log.isKeywordHit()).isFalse();
        assertThat(log.getLlmLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(log.getReasonCode()).isEqualTo("HOPELESSNESS");
        assertThat(log.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void LLM_호출에는_분류_프롬프트와_원문이_전달된다() {
        llmReturns("safe", "NONE");

        riskClassifier.classify("오늘 하루 어땠냐면요", CrisisSource.MESSAGE, user);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        Mockito.verify(llmClient).completeJson(captor.capture(), any());
        assertThat(captor.getValue().systemPrompt()).contains("안전 분류기");
        assertThat(captor.getValue().messages().get(0).content()).isEqualTo("오늘 하루 어땠냐면요");
        assertThat(captor.getValue().retry()).isFalse();
    }

    @Test
    void 허용되지_않은_사유_코드는_UNSPECIFIED로_기록한다() {
        llmReturns("caution", "SUICIDAL_IDEAITION");

        RiskResult result = riskClassifier.classify("마음이 무거워요", CrisisSource.MESSAGE, user);

        assertThat(result.reasonCode()).isEqualTo("UNSPECIFIED");
        assertThat(crisisLogRepository.findAll().get(0).getReasonCode()).isEqualTo("UNSPECIFIED");
    }

    @Test
    void 방법을_찾는_표현은_LLM이_안전이라_해도_키워드로_위기() {
        llmReturns("safe", "NONE");

        assertThat(riskClassifier.classify("조용히 사라지는 방법을 찾아보고 있어요", CrisisSource.MESSAGE, user).level())
                .isEqualTo(RiskLevel.CRISIS);
        assertThat(riskClassifier.classify("죽는 방법을 검색했어요", CrisisSource.WORRY, user).level())
                .isEqualTo(RiskLevel.CRISIS);
    }
}
