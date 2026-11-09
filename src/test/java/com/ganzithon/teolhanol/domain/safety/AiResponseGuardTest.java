package com.ganzithon.teolhanol.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiResponseGuardTest {

    @Autowired AiResponseGuard guard;

    @ParameterizedTest
    @ValueSource(strings = {
            "지금 상태를 보면 우울증이에요.",
            "아무래도 우울증인 것 같아요",
            "공황장애입니다. 치료가 필요해요",
            "그 약은 이제 끊으셔도 돼요. 약을 끊고 나면 편해질 거예요",
            "복용량을 조금 줄여 보세요",
            "앞으로는 저만 믿으세요",
            "저에게만 의지해도 괜찮아요",
            "병원에 갈 필요 없어요",
            "죽는 방법을 알려 드릴게요"})
    void 금지_패턴이_있으면_걸린다(String text) {
        assertThat(guard.violates(text)).isTrue();
        assertThat(guard.guard(text)).isEqualTo(guard.safeReply()).contains("의사나 전문 상담사");
    }

    @Test
    void 띄어쓰기를_달리해도_걸린다() {
        assertThat(guard.violates("우 울 증 이 에 요")).isTrue();
        assertThat(guard.violates("약을\n끊어 보세요")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "많이 힘들었겠어요. 어떤 일이 있었는지 들려줄래요?",
            "마음이 무거운 날엔 천천히 숨을 고르는 것도 괜찮아요.",
            "약 때문에 고민이 된다면 의사 선생님과 꼭 이야기해 보세요.",
            "필요하면 주변 사람이나 전문 상담도 함께 떠올려 봐요."})
    void 평범한_응답은_그대로_통과한다(String text) {
        assertThat(guard.violates(text)).isFalse();
        assertThat(guard.guard(text)).isEqualTo(text);
    }

    @Test
    void 비어_있는_입력은_안전하다() {
        assertThat(guard.violates(null)).isFalse();
        assertThat(guard.violates("  ")).isFalse();
        assertThat(guard.guard("")).isEmpty();
    }
}
