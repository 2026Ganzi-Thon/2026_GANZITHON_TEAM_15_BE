package com.ganzithon.teolhanol.global.seed;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import com.ganzithon.teolhanol.domain.safety.repository.SafetyPatternRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 서버 시작 시 고정 목장 8개와 안전 패턴을 넣습니다.
 * 이미 있는 데이터는 건너뛰므로 여러 번 실행해도 안전합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataInitializer implements ApplicationRunner {

    private static final String FARMS = "seed/farms.json";
    private static final String SAFETY_PATTERNS = "seed/safety-patterns.json";

    private final FarmRepository farmRepository;
    private final SafetyPatternRepository safetyPatternRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int farms = seedFarms();
        int patterns = seedSafetyPatterns();
        log.info("Seed data: farms +{}, safety patterns +{}", farms, patterns);
    }

    int seedFarms() {
        List<FarmSeed> seeds = read(FARMS, new TypeReference<>() {
        });
        int added = 0;
        for (FarmSeed seed : seeds) {
            if (!farmRepository.existsByCode(seed.code())) {
                farmRepository.save(new Farm(seed.code(), seed.name(), seed.description(), seed.sortOrder()));
                added++;
            }
        }
        return added;
    }

    int seedSafetyPatterns() {
        Map<String, Object> raw = read(SAFETY_PATTERNS, new TypeReference<>() {
        });
        int added = 0;
        for (PatternCategory category : PatternCategory.values()) {
            Object values = raw.get(category.name());
            if (!(values instanceof List<?> list)) {
                continue;
            }
            for (Object value : list) {
                String pattern = String.valueOf(value);
                if (category == PatternCategory.PII) {
                    Pattern.compile(pattern); // 잘못된 정규식이면 시작 시점에 바로 알 수 있게 합니다.
                }
                if (!safetyPatternRepository.existsByCategoryAndPattern(category, pattern)) {
                    safetyPatternRepository.save(new SafetyPattern(pattern, category));
                    added++;
                }
            }
        }
        return added;
    }

    private <T> T read(String path, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("시드 파일을 읽지 못했습니다: " + path, e);
        }
    }

    record FarmSeed(String code, String name, String description, int sortOrder) {
    }
}
