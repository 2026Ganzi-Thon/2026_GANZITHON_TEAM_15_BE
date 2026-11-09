package com.ganzithon.teolhanol.domain.care.dto;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;

/** 케어 시작 결과. message는 선택한 방식으로 건네는 AI 첫 메시지입니다. */
public record CareStartResponse(Long diaryId, CareType careType, String message) {
}
