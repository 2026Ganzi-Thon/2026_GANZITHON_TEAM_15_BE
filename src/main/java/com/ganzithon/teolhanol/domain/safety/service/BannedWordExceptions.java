package com.ganzithon.teolhanol.domain.safety.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 금칙어 오탐 예외 표현 (#93). {@code seed/banned-word-exceptions.json}을 서버 시작 때 한 번 읽습니다.
 * DB가 아니라 클래스패스에서 읽으므로 운영 DB를 고치지 않아도 서버를 다시 시작하면 적용됩니다.
 * <p>
 * 예외 표현의 대소문자는 무시합니다. 예외 표현 안의 띄어쓰기 자리는 글에서 띄어 써도 붙여 써도 되지만,
 * 붙여 쓴 글자 사이에는 띄어쓰기가 없어야 맞습니다. 그래서 "시발점"은 "시발 점점"을 감싸지 않습니다.
 * 파일이 없거나 형식이 틀리면 경고만 남기고 예외 없이 동작합니다.
 */
@Slf4j
@Component
public class BannedWordExceptions {

    static final String RESOURCE = "seed/banned-word-exceptions.json";

    /** 정규화한 금칙어 → 예외 표현 정규식 */
    private final Map<String, List<Pattern>> byWord;

    @Autowired
    public BannedWordExceptions(ObjectMapper objectMapper) {
        this(new ClassPathResource(RESOURCE), objectMapper);
    }

    public BannedWordExceptions(Resource resource, ObjectMapper objectMapper) {
        this.byWord = load(resource, objectMapper);
        log.info("금칙어 예외 표현: 금칙어 {}개, 표현 {}개", byWord.size(),
                byWord.values().stream().mapToInt(List::size).sum());
    }

    /** 이 금칙어(정규화한 값)의 예외 표현. 없으면 빈 목록 */
    List<Pattern> forWord(String normalizedWord) {
        return byWord.getOrDefault(normalizedWord, List.of());
    }

    /** 예외 표현이 하나라도 있는 금칙어 수 */
    public int size() {
        return byWord.size();
    }

    private static Map<String, List<Pattern>> load(Resource resource, ObjectMapper objectMapper) {
        Map<String, Object> raw;
        try (InputStream in = resource.getInputStream()) {
            raw = objectMapper.readValue(in, new TypeReference<>() {
            });
        } catch (IOException | RuntimeException e) {
            log.warn("금칙어 예외 파일을 읽지 못해 예외 없이 검사합니다: {} ({})", resource.getDescription(),
                    e.getClass().getSimpleName());
            return Map.of();
        }
        if (raw == null) {
            log.warn("금칙어 예외 파일이 비어 있어 예외 없이 검사합니다: {}", resource.getDescription());
            return Map.of();
        }

        Map<String, List<Pattern>> result = new HashMap<>();
        raw.forEach((key, value) -> {
            if (key.startsWith("_")) {
                return; // _comment 같은 설명 항목
            }
            String word = BannedWordText.normalize(key);
            if (word.isEmpty() || !(value instanceof List<?> list)) {
                log.warn("금칙어 예외 항목의 형식이 맞지 않아 건너뜁니다: {}", key);
                return;
            }
            List<Pattern> patterns = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String expression) || expression.isBlank()) {
                    log.warn("금칙어 예외 표현의 형식이 맞지 않아 건너뜁니다: {}", key);
                    continue;
                }
                if (!BannedWordText.normalize(expression).contains(word)) {
                    log.warn("금칙어 예외 표현에 금칙어가 없어 건너뜁니다: {} / {}", key, expression);
                    continue;
                }
                patterns.add(toPattern(expression));
            }
            if (!patterns.isEmpty()) {
                result.computeIfAbsent(word, k -> new ArrayList<>()).addAll(patterns);
            }
        });
        result.replaceAll((word, patterns) -> List.copyOf(patterns));
        return Map.copyOf(result);
    }

    /** 띄어쓰기 자리는 공백이 있어도 없어도 되게, 나머지 글자는 그대로 맞춥니다. 소문자로 바꾼 글에 씁니다. */
    private static Pattern toPattern(String expression) {
        String[] tokens = BannedWordText.lower(expression.strip()).split("\\s+");
        List<String> quoted = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            quoted.add(Pattern.quote(token));
        }
        return Pattern.compile(String.join("\\s*", quoted));
    }
}
