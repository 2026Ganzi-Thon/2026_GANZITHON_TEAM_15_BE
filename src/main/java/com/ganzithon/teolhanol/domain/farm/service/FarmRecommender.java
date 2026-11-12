package com.ganzithon.teolhanol.domain.farm.service;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import org.springframework.stereotype.Component;

/**
 * 감정과 케어 방식으로 목장 1곳을 고르는 규칙표 (FRM-07). 대화 원문은 보지 않습니다.
 * 목장 code 는 seed/farms.json 의 CAREER, RELATIONSHIP, BURNOUT, SELF 입니다.
 *
 * <ul>
 *   <li>TIRED(지침): 일·공부에 소진된 마음이 가장 가까워 BURNOUT</li>
 *   <li>LONELY(외로움)·ANGRY(화남): 대개 사람 사이에서 생기는 감정이라 RELATIONSHIP</li>
 *   <li>ANXIOUS(불안): 해결 방법을 찾고 싶어 한 경우(SOLVE)는 앞길 걱정일 가능성이 커 CAREER, 그 밖에는 SELF</li>
 *   <li>DEPRESSED(우울)·COMPLICATED(복잡함): 나를 향한 마음이 크므로 SELF</li>
 *   <li>JOY(기쁨)·CALM(평온): 뚜렷한 고민 신호가 없어 SOLVE 면 CAREER, 그 밖에는 SELF</li>
 * </ul>
 * 케어 방식이 없으면(null) 감정만으로 고릅니다.
 */
@Component
public class FarmRecommender {

    public String recommend(Emotion emotion, CareType careType) {
        boolean solve = careType == CareType.SOLVE;
        return switch (emotion) {
            case TIRED -> "BURNOUT";
            case LONELY, ANGRY -> "RELATIONSHIP";
            case ANXIOUS, JOY, CALM -> solve ? "CAREER" : "SELF";
            case DEPRESSED, COMPLICATED -> "SELF";
        };
    }
}
