package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 고민·조언·닉네임에 외부 연락으로 이어질 수 있는 개인정보(전화번호, 이메일, SNS 아이디, 오픈채팅 링크,
 * 계좌번호)가 있는지 검사합니다 (SAF-05). 패턴은 safety_patterns의 PII 카테고리(Java 정규식)이며
 * 1분간 메모리에 둡니다. 검사한 본문은 로그에 남기지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiiDetector {

    private static final long CACHE_MILLIS = 60_000L;

    private final SafetyPatternRepository safetyPatternRepository;

    private volatile List<Pattern> patterns = List.of();
    private volatile long loadedAt;

    public boolean containsPii(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (Pattern pattern : patterns()) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private List<Pattern> patterns() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > CACHE_MILLIS || patterns.isEmpty()) {
            patterns = safetyPatternRepository.findAllByCategory(PatternCategory.PII).stream()
                    .map(SafetyPattern::getPattern)
                    .map(PiiDetector::compile)
                    .filter(p -> p != null)
                    .toList();
            loadedAt = now;
        }
        return patterns;
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.warn("잘못된 PII 정규식을 건너뜁니다: {}", regex);
            return null;
        }
    }
}
