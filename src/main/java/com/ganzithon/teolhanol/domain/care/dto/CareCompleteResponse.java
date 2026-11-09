package com.ganzithon.teolhanol.domain.care.dto;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;

/** 케어 마무리 결과 (CARE-03) */
public record CareCompleteResponse(Summary summary, Wool wool) {

    /** 오늘 받은 말 카드. text는 케어 방식별 형식이며 여러 줄일 수 있음 */
    public record Summary(CareType careType, String text) {
    }

    /**
     * 털 결과.
     *
     * @param added   이번에 쌓인 털. 위기 신호가 나온 대화이거나 이미 마무리한 기록이면 0
     * @param total   누적 털
     * @param stage   현재 단계(1~5)
     * @param stageUp 이번에 단계가 올랐는지
     */
    public record Wool(int added, int total, int stage, boolean stageUp) {
    }
}
