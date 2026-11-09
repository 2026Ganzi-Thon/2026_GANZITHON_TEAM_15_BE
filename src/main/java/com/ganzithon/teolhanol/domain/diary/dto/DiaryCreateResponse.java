package com.ganzithon.teolhanol.domain.diary.dto;

/** 오늘의 마음 생성 결과. firstMessage는 케어 대화 첫 말풍선입니다. */
public record DiaryCreateResponse(Long diaryId, String firstMessage) {
}
