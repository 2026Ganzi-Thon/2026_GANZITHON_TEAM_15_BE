package com.ganzithon.teolhanol.domain.diary.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 감정 일기의 대표 감정 (기능명세서 5장 감정 목록) */
@Getter
@RequiredArgsConstructor
public enum Emotion {

    JOY("기쁨"),
    CALM("평온"),
    TIRED("지침"),
    ANXIOUS("불안"),
    DEPRESSED("우울"),
    LONELY("외로움"),
    ANGRY("화남"),
    COMPLICATED("복잡함");

    private final String label;
}
