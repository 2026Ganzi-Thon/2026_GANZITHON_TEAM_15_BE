package com.ganzithon.teolhanol.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SeedDataInitializerTest {

    @Autowired SeedDataInitializer initializer;
    @Autowired FarmRepository farmRepository;
    @Autowired SafetyPatternRepository safetyPatternRepository;
    @Autowired PolicyProperties policy;

    @Test
    void 서버가_뜨면_고정_목장_4곳이_순서대로_들어가_있다() {
        List<String> names = farmRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Farm::getSortOrder))
                .map(Farm::getName)
                .toList();

        assertThat(names).containsExactly("진로·취업", "관계", "번아웃", "나 자신");
        assertThat(farmRepository.findAll()).extracting(Farm::getTabName)
                .containsExactlyInAnyOrder("진로", "관계", "번아웃", "나");
    }

    @Test
    void 다시_실행해도_중복으로_들어가지_않는다() {
        long patterns = safetyPatternRepository.count();

        assertThat(initializer.seedFarms()).isZero();
        assertThat(initializer.seedSafetyPatterns()).isZero();
        assertThat(farmRepository.count()).isEqualTo(4);
        assertThat(safetyPatternRepository.count()).isEqualTo(patterns);
    }

    @Test
    void 모든_분류의_안전_패턴이_들어가_있다() {
        for (PatternCategory category : PatternCategory.values()) {
            assertThat(safetyPatternRepository.findAllByCategory(category)).as(category.name()).isNotEmpty();
        }
    }

    @Test
    void 개인정보_정규식은_연락처를_잡고_평범한_문장은_통과시킨다() {
        List<Pattern> pii = safetyPatternRepository.findAllByCategory(PatternCategory.PII).stream()
                .map(SafetyPattern::getPattern)
                .map(Pattern::compile)
                .toList();

        assertThat(List.of("010-1234-5678", "01012345678", "연락 주세요 a.b@gmail.com",
                "https://open.kakao.com/o/abc", "인스타 @sheep_love", "카톡 아이디: sheep123",
                "110-123-456789로 보내줘"))
                .allMatch(text -> pii.stream().anyMatch(p -> p.matcher(text).find()));

        assertThat(List.of("2026-11-08에 면접을 봤어요", "200자 안에 쓰기가 어려워요",
                "시험이 3일 남았어요", "10시-12시 사이에 공부해요"))
                .noneMatch(text -> pii.stream().anyMatch(p -> p.matcher(text).find()));
    }

    @Test
    void 정책_수치가_설정_파일에서_읽힌다() {
        assertThat(policy.diary().dailyLimit()).isEqualTo(3);
        assertThat(policy.care().minUserMessagesForWool()).isEqualTo(3);
        assertThat(policy.care().maxUserMessages()).isEqualTo(10);
        assertThat(policy.auth().nicknameMinLength()).isEqualTo(2);
        assertThat(policy.auth().nicknameMaxLength()).isEqualTo(12);
        assertThat(policy.auth().passwordMinLength()).isEqualTo(8);
        assertThat(policy.worry().woolCap()).isEqualTo(10);
        assertThat(policy.worry().pageSize()).isEqualTo(20);
        assertThat(policy.worry().protectedViewMinSentWools()).isEqualTo(5);
        assertThat(policy.wool().maxLength()).isEqualTo(100);
        assertThat(policy.auth().maxLoginFailures()).isEqualTo(5);
        assertThat(policy.safety().crisisBannerHours()).isEqualTo(24);
    }
}
