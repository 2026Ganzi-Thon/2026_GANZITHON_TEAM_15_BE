package com.ganzithon.teolhanol.domain.diary.service;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;

/** 한 줄 돌아보기 문장 조각. '불안한 날엔 위로받기를 자주 골랐어요'처럼 자연스럽게 읽히도록 감정·케어 방식마다 표현을 따로 둡니다. */
final class ReviewPhrases {

    private ReviewPhrases() {
    }

    static String sentence(Emotion emotion, CareType careType) {
        return day(emotion) + " " + care(careType) + " 자주 골랐어요";
    }

    private static String day(Emotion emotion) {
        return switch (emotion) {
            case JOY -> "기쁜 날엔";
            case CALM -> "평온한 날엔";
            case TIRED -> "지친 날엔";
            case ANXIOUS -> "불안한 날엔";
            case DEPRESSED -> "우울한 날엔";
            case LONELY -> "외로운 날엔";
            case ANGRY -> "화가 난 날엔";
            case COMPLICATED -> "마음이 복잡한 날엔";
        };
    }

    /** 목적격 조사까지 포함 (모든 케어 방식 이름이 '기'로 끝나 '를'을 씁니다) */
    private static String care(CareType careType) {
        return switch (careType) {
            case LISTEN -> "이야기 들어주기를";
            case COMFORT -> "위로받기를";
            case ORGANIZE -> "생각 정리하기를";
            case SOLVE -> "해결 방법 찾기를";
        };
    }
}
