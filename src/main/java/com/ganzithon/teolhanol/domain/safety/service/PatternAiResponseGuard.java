package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * safety_patterns의 AI_OUTPUT 패턴(부분 일치)으로 검사하는 가드. 띄어쓰기·대소문자 차이는 무시합니다.
 * 패턴은 자주 바뀌지 않아 1분간 메모리에 두고 씁니다. 응답 본문은 로그에 남기지 않습니다.
 */
@Component
@RequiredArgsConstructor
class PatternAiResponseGuard implements AiResponseGuard {

    static final String SAFE_REPLY = "그 부분은 제가 정확히 말씀드리기 어려워요. "
            + "몸이나 약에 관한 고민은 의사나 전문 상담사와 꼭 이야기해 보세요. "
            + "저는 지금 마음이 어떤지, 천천히 더 들어볼게요.";
    private static final long CACHE_MILLIS = 60_000L;

    private final SafetyPatternRepository safetyPatternRepository;

    private volatile List<String> patterns = List.of();
    private volatile long loadedAt;

    @Override
    public boolean violates(String text) {
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

    @Override
    public String safeReply() {
        return SAFE_REPLY;
    }

    private List<String> patterns() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > CACHE_MILLIS || patterns.isEmpty()) {
            patterns = safetyPatternRepository.findAllByCategory(PatternCategory.AI_OUTPUT).stream()
                    .map(SafetyPattern::getPattern).map(PatternAiResponseGuard::normalize).toList();
            loadedAt = now;
        }
        return patterns;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
