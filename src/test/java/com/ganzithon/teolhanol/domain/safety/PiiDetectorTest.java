package com.ganzithon.teolhanol.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.safety.service.PiiDetector;
import com.ganzithon.teolhanol.domain.user.service.NicknameReason;
import com.ganzithon.teolhanol.domain.user.service.NicknameValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PiiDetectorTest {

    @Autowired PiiDetector detector;
    @Autowired NicknameValidator nicknameValidator;

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678로 연락 주세요",
            "01012345678",
            "010 1234 5678",
            "010.1234.5678",
            "02-123-4567이에요",
            "031-1234-5678",
            "메일은 hello.wool@example.com 입니다",
            "인스타 @wool_sheep 로 와요",
            "@wool_sheep",
            "카톡 아이디 woolsheep12",
            "카톡: wool123",
            "텔레그램 wool_sheep",
            "DM wool_sheep",
            "https://open.kakao.com/o/abc123 로 오세요",
            "국민 123456-12-123456",
            "계좌 110-123-456789"})
    void 개인정보_패턴은_차단된다(String text) {
        assertThat(detector.containsPii(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3시 30분에 만나요",
            "2026-11-12",
            "2026-11-12 오후 3시",
            "100-200명이 왔어요",
            "점수 010점",
            "010점 만점에 7점이에요",
            "1월 2일부터 10일까지 시험이에요",
            "하루에 3000원씩 모았어요",
            "admission 준비로 힘들어요",
            "회의 시간이 12:30 - 13:30 이에요",
            "고민이 많아서 잠이 안 와요. 이메일이 쌓여서 스트레스예요",
            "인스타 보면 비교하게 돼요",
            "카톡 답장이 늦어서 서운해요",
            "오픈 채팅방에서 있었던 일이에요"})
    void 평범한_문장은_통과한다(String text) {
        assertThat(detector.containsPii(text)).isFalse();
    }

    @Test
    void 비어_있는_입력은_안전하다() {
        assertThat(detector.containsPii(null)).isFalse();
        assertThat(detector.containsPii("   ")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0101234567", "카톡wool123"})
    void 개인정보가_든_닉네임은_쓸_수_없다(String nickname) {
        assertThat(nicknameValidator.check(nickname)).contains(NicknameReason.NOT_ALLOWED);
    }

    @Test
    void 평범한_닉네임은_이_필터에_걸리지_않는다() {
        assertThat(nicknameValidator.check("포근한 양")).isNotEqualTo(java.util.Optional.of(NicknameReason.NOT_ALLOWED));
    }
}
