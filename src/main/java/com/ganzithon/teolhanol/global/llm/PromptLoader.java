package com.ganzithon.teolhanol.global.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * src/main/resources/prompts/{name}.txt 프롬프트를 읽어 캐시합니다.
 * 프롬프트는 코드와 분리해 두고, 기능별로 파일을 추가합니다 (예: care-comfort.txt, risk-classification.txt).
 */
@Component
public class PromptLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, this::read);
    }

    /** 공통 안전 지침 뒤에 기능별 프롬프트를 붙입니다. */
    public String withSafetyGuide(String name) {
        return load("common-safety") + "\n\n" + load(name);
    }

    private String read(String name) {
        try (InputStream in = new ClassPathResource("prompts/" + name + ".txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일이 없습니다: prompts/" + name + ".txt", e);
        }
    }
}
