package com.ganzithon.teolhanol.global.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SheepStageTest {

    @ParameterizedTest(name = "나의 양 털 {0}개 → {1}단계")
    @CsvSource({"0,1", "2,1", "3,2", "5,2", "6,3", "8,3", "9,4", "11,4", "12,5", "100,5"})
    void 나의_양_단계(int wool, int level) {
        assertThat(SheepStage.ofMySheep(wool).getLevel()).isEqualTo(level);
    }

    @ParameterizedTest(name = "고민 양 털 {0}개 → {1}단계")
    @CsvSource({"0,1", "1,2", "2,2", "3,3", "5,3", "6,4", "9,4", "10,5"})
    void 고민_양_단계(int wool, int level) {
        assertThat(SheepStage.ofWorrySheep(wool).getLevel()).isEqualTo(level);
    }
}
