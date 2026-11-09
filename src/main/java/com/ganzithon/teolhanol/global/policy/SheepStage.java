package com.ganzithon.teolhanol.global.policy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 양 단계 기준표 (기능명세서 5장).
 * 나의 양은 털 3올마다 한 단계(누적 털), 고민 양은 받은 조언 수로 단계를 정합니다.
 */
@Getter
@RequiredArgsConstructor
public enum SheepStage {

    SHORN(1, "털 깎은 양", 0, 0),
    FLUFFY(2, "보송한 양", 3, 1),
    COZY(3, "포근한 양", 6, 3),
    PUFFY(4, "몽글몽글 양", 9, 6),
    CLOUD(5, "구름 양", 12, 10);

    private final int level;
    private final String label;
    private final int myWoolThreshold;
    private final int worryWoolThreshold;

    /** 나의 양: 누적 털(케어 완료 수)에 해당하는 단계 */
    public static SheepStage ofMySheep(int woolCount) {
        SheepStage result = SHORN;
        for (SheepStage stage : values()) {
            if (woolCount >= stage.myWoolThreshold) {
                result = stage;
            }
        }
        return result;
    }

    /** 고민 양: 받은 조언 수에 해당하는 단계 */
    public static SheepStage ofWorrySheep(int woolCount) {
        SheepStage result = SHORN;
        for (SheepStage stage : values()) {
            if (woolCount >= stage.worryWoolThreshold) {
                result = stage;
            }
        }
        return result;
    }

    public static SheepStage ofLevel(int level) {
        for (SheepStage stage : values()) {
            if (stage.level == level) {
                return stage;
            }
        }
        throw new IllegalArgumentException("알 수 없는 양 단계: " + level);
    }

    /** 다음 단계. 마지막 단계면 null */
    public SheepStage next() {
        return isMax() ? null : ofLevel(level + 1);
    }

    public boolean isMax() {
        return this == CLOUD;
    }
}
