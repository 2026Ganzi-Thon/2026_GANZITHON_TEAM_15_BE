package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 금칙어 검사 (SAF-04 1차). safety_patterns의 BANNED_WORD(욕설·혐오·성적 표현, 자해 조장 표현)를 부분 일치로 찾습니다.
 * 띄어쓰기·대소문자 차이는 무시합니다. 조언 다시 확인({@link AdviceChecker})과 닉네임 검사가 함께 씁니다.
 * <p>
 * 평범한 표현이 막히지 않도록(#93) 금칙어가 걸린 자리마다 그 자리를 감싸는 예외 표현({@link BannedWordExceptions})이
 * 있으면 그 자리는 넘어갑니다. 예외로 감싸지 못한 자리가 하나라도 남으면 막습니다.
 * 예: "불이 꺼져서 무서웠어요"는 통과, "불이 꺼져서 무서웠어요. 그냥 꺼져"는 차단.
 * <p>
 * 패턴은 자주 바뀌지 않아 1분간 메모리에 두고 씁니다. 검사한 글은 로그에 남기지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class BannedWordFilter {

    private static final long CACHE_MILLIS = 60_000L;

    private final SafetyPatternRepository safetyPatternRepository;
    private final BannedWordExceptions exceptions;

    private volatile List<String> patterns = List.of();
    private volatile long loadedAt;

    /** 예외 표현으로 감싸지 못한 금칙어가 하나라도 들어 있으면 true */
    public boolean contains(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        BannedWordText target = BannedWordText.of(text);
        for (String pattern : patterns()) {
            if (hasUnexcusedMatch(target, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnexcusedMatch(BannedWordText target, String word) {
        int at = target.normalized.indexOf(word);
        if (at < 0) {
            return false;
        }
        List<Pattern> allowed = exceptions.forWord(word);
        if (allowed.isEmpty()) {
            return true;
        }
        List<int[]> allowedSpans = spans(target.lowered, allowed);
        while (at >= 0) {
            int start = target.origin(at);
            int end = target.origin(at + word.length() - 1) + 1;
            if (!covered(allowedSpans, start, end)) {
                return true;
            }
            at = target.normalized.indexOf(word, at + 1);
        }
        return false;
    }

    /** 원문(소문자)에서 예외 표현이 나온 구간 [시작, 끝) 목록. 겹쳐 나오는 경우도 모두 찾습니다. */
    private static List<int[]> spans(String lowered, List<Pattern> expressions) {
        List<int[]> spans = new ArrayList<>();
        for (Pattern expression : expressions) {
            Matcher matcher = expression.matcher(lowered);
            int from = 0;
            while (from < lowered.length() && matcher.find(from)) {
                spans.add(new int[] {matcher.start(), matcher.end()});
                from = matcher.start() + 1;
            }
        }
        return spans;
    }

    private static boolean covered(List<int[]> spans, int start, int end) {
        for (int[] span : spans) {
            if (span[0] <= start && end <= span[1]) {
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
                    .map(BannedWordText::normalize)
                    .filter(p -> !p.isEmpty())
                    .toList();
            loadedAt = now;
        }
        return patterns;
    }
}
