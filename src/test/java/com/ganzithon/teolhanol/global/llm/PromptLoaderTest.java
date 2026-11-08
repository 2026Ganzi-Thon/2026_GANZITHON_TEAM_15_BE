package com.ganzithon.teolhanol.global.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PromptLoaderTest {

    private final PromptLoader loader = new PromptLoader();

    @Test
    void 공통_안전_지침을_읽는다() {
        assertThat(loader.load("common-safety")).contains("진단하지 않습니다").contains("109");
    }

    @Test
    void 없는_프롬프트는_바로_알려준다() {
        assertThatThrownBy(() -> loader.load("no-such-prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-prompt");
    }
}
