package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 금칙어 검사 (SAF-04 1차). safety_patterns의 BANNED_WORD(욕설·혐오·성적 표현, 자해 조장 표현)를 부분 일치로 찾습니다.
 * 띄어쓰기·대소문자 차이는 무시합니다. 조언 다시 확인({@link AdviceChecker})과 닉네임 검사가 함께 씁니다.
 * 패턴은 자주 바뀌지 않아 1분간 메모리에 두고 씁니다. 검사한 글은 로그에 남기지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class BannedWordFilter {

    private static final long CACHE_MILLIS = 60_000L;

    private final SafetyPatternRepository safetyPatternRepository;

    private volatile List<String> patterns = List.of();
    private volatile long loadedAt;

    /** 금칙어가 하나라도 들어 있으면 true */
    public boolean contains(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        for (String pattern : patterns()) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private List<String> patterns() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > CACHE_MILLIS || patterns.isEmpty()) {
            patterns = safetyPatternRepository.findAllByCategory(PatternCategory.BANNED_WORD).stream()
                    .map(SafetyPattern::getPattern)
                    .map(BannedWordFilter::normalize)
                    .filter(p -> !p.isEmpty())
                    .toList();
            loadedAt = now;
        }
        return patterns;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
